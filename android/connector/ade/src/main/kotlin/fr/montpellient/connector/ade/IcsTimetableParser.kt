package fr.montpellient.connector.ade

import fr.montpellient.core.model.CourseType
import fr.montpellient.core.model.EventStatus
import fr.montpellient.core.model.TimetableEvent
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Parseur iCalendar (.ics) léger, robuste et performant, sans bibliothèque externe lourde.
 * Conçu spécifiquement pour le format ADE Campus de l'Université de Montpellier :
 * - Gestion du repli de ligne RFC 5545 (lignes commençant par espace ou tabulation).
 * - Support des dates UTC (Z) et avec fuseau (TZID=Europe/Paris).
 * - Extraction du type de cours (CM, TD, TP, EXAM) depuis le résumé ou la description.
 * - Découpage des salles, enseignants, promotions/groupes.
 */
object IcsTimetableParser {

    private val UTC_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val LOCAL_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val DATE_ONLY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val PARIS_ZONE = ZoneId.of("Europe/Paris")

    fun parse(icsContent: String): List<TimetableEvent> {
        val unfoldedLines = unfoldLines(icsContent)
        val events = mutableListOf<TimetableEvent>()

        var inEvent = false
        var uid: String? = null
        var summary: String? = null
        var description: String? = null
        var location: String? = null
        var dtStart: LocalDateTime? = null
        var dtEnd: LocalDateTime? = null
        var status = EventStatus.NORMAL

        for (line in unfoldedLines) {
            val trimmed = line.trim()
            if (trimmed == "BEGIN:VEVENT") {
                inEvent = true
                uid = null
                summary = null
                description = null
                location = null
                dtStart = null
                dtEnd = null
                status = EventStatus.NORMAL
                continue
            }

            if (trimmed == "END:VEVENT") {
                if (inEvent && uid != null && dtStart != null && dtEnd != null) {
                    val subjectName = cleanSubjectName(summary ?: "Séance")
                    val courseType = detectCourseType(summary, description)
                    val rooms = parseRooms(location)
                    val teachers = parseTeachers(description)
                    val groups = parseGroups(description)

                    if (summary?.contains("ANNULÉ", ignoreCase = true) == true ||
                        description?.contains("ANNULÉ", ignoreCase = true) == true
                    ) {
                        status = EventStatus.CANCELLED
                    }

                    events.add(
                        TimetableEvent(
                            id = uid,
                            subject = subjectName,
                            type = courseType,
                            start = dtStart,
                            end = dtEnd,
                            rooms = rooms,
                            teachers = teachers,
                            groups = groups,
                            description = description,
                            status = status
                        )
                    )
                }
                inEvent = false
                continue
            }

            if (!inEvent) continue

            val colonIdx = trimmed.indexOf(':')
            if (colonIdx == -1) continue

            val keyPart = trimmed.substring(0, colonIdx)
            val valuePart = unescapeIcsText(trimmed.substring(colonIdx + 1))

            val key = keyPart.split(";")[0].uppercase()

            when (key) {
                "UID" -> uid = valuePart
                "SUMMARY" -> summary = valuePart
                "DESCRIPTION" -> description = valuePart
                "LOCATION" -> location = valuePart
                "DTSTART" -> dtStart = parseDateTime(keyPart, valuePart)
                "DTEND" -> dtEnd = parseDateTime(keyPart, valuePart)
                "STATUS" -> {
                    if (valuePart.equals("CANCELLED", ignoreCase = true)) {
                        status = EventStatus.CANCELLED
                    }
                }
            }
        }

        return events.sortedBy { it.start }
    }

    /** Déplie les lignes longues RFC 5545 */
    private fun unfoldLines(content: String): List<String> {
        val rawLines = content.lines()
        val result = mutableListOf<String>()
        val current = StringBuilder()

        for (line in rawLines) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                current.append(line.substring(1))
            } else {
                if (current.isNotEmpty()) {
                    result.add(current.toString())
                    current.clear()
                }
                current.append(line)
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }

    private fun parseDateTime(keyPart: String, value: String): LocalDateTime? {
        val cleanValue = value.trim()
        return try {
            if (cleanValue.endsWith("Z")) {
                val utcTime = LocalDateTime.parse(cleanValue, UTC_FORMATTER)
                ZonedDateTime.of(utcTime, ZoneId.of("UTC"))
                    .withZoneSameInstant(PARIS_ZONE)
                    .toLocalDateTime()
            } else if (cleanValue.contains("T")) {
                LocalDateTime.parse(cleanValue, LOCAL_FORMATTER)
            } else {
                // Événement journée entière
                java.time.LocalDate.parse(cleanValue, DATE_ONLY_FORMATTER).atStartOfDay()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun unescapeIcsText(text: String): String {
        return text.replace("\\n", "\n")
            .replace("\\N", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
    }

    private fun cleanSubjectName(raw: String): String {
        // Enlève les préfixes ADE fréquents ex: "CM - ", "TD : "
        return raw.replace(Regex("^(CM|TD|TP|EXAM|CC)\\s*[-:]\\s*", RegexOption.IGNORE_CASE), "").trim()
    }

    private fun detectCourseType(summary: String?, description: String?): CourseType {
        val text = "${summary.orEmpty()} ${description.orEmpty()}".uppercase()
        return when {
            text.contains("EXAM") || text.contains("PARTIEL") || text.contains("CONTRÔLE") || text.contains("CC") -> CourseType.EXAM
            text.contains("TP") || text.contains("TRAVAUX PRATIQUES") -> CourseType.TP
            text.contains("TD") || text.contains("TRAVAUX DIRIGÉS") -> CourseType.TD
            text.contains("CM") || text.contains("COURS MAGISTRAL") -> CourseType.CM
            else -> CourseType.OTHER
        }
    }

    private fun parseRooms(location: String?): List<String> {
        if (location.isNullOrBlank()) return emptyList()
        return location.split(Regex("[,;\\n]"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun parseTeachers(description: String?): List<String> {
        if (description.isNullOrBlank()) return emptyList()
        // Dans ADE UM, les enseignants sont souvent listés sur des lignes dédiées ou précédés de "Intervenant(s) :"
        val lines = description.lines()
        val teachers = mutableListOf<String>()
        var recording = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Intervenant", ignoreCase = true) ||
                trimmed.startsWith("Enseignant", ignoreCase = true)
            ) {
                val afterColon = trimmed.substringAfter(":", "").trim()
                if (afterColon.isNotBlank()) teachers.add(afterColon)
                recording = true
                continue
            }
            if (recording) {
                if (trimmed.startsWith("Matière", ignoreCase = true) ||
                    trimmed.startsWith("Groupe", ignoreCase = true) ||
                    trimmed.startsWith("Salle", ignoreCase = true) ||
                    trimmed.isBlank()
                ) {
                    recording = false
                } else {
                    teachers.add(trimmed)
                }
            }
        }
        return teachers
    }

    private fun parseGroups(description: String?): List<String> {
        if (description.isNullOrBlank()) return emptyList()
        val lines = description.lines()
        val groups = mutableListOf<String>()
        var recording = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Groupe", ignoreCase = true) ||
                trimmed.startsWith("Promotion", ignoreCase = true)
            ) {
                val afterColon = trimmed.substringAfter(":", "").trim()
                if (afterColon.isNotBlank()) groups.add(afterColon)
                recording = true
                continue
            }
            if (recording) {
                if (trimmed.startsWith("Matière", ignoreCase = true) ||
                    trimmed.startsWith("Salle", ignoreCase = true) ||
                    trimmed.isBlank()
                ) {
                    recording = false
                } else {
                    groups.add(trimmed)
                }
            }
        }
        return groups
    }
}
