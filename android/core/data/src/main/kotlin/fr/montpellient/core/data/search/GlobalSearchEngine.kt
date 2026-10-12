package fr.montpellient.core.data.search

import fr.montpellient.core.data.AppContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.text.Normalizer
import java.time.LocalDate

enum class SearchCategory {
    COURSE,
    ASSIGNMENT,
    MAIL,
    EVENT,
    GRADE,
    RESTAURANT
}

data class SearchResultItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: SearchCategory,
    val targetTabHint: String
)

private fun String.stripAccents(): String {
    val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
    return "\\p{InCombiningDiacriticalMarks}+".toRegex().replace(normalized, "").lowercase()
}

/**
 * Moteur de recherche globale fédérée interrogeant simultanément l'ensemble des dépôts de l'application :
 * - Cours Moodle
 * - Devoirs à rendre
 * - Mails universitaires
 * - Événements & emplois du temps ADE
 * - Notes & UEs Apogée
 * - Restaurants & cafétérias CROUS
 */
class GlobalSearchEngine(private val container: AppContainer) {

    suspend fun search(query: String): List<SearchResultItem> {
        val q = query.trim().stripAccents()
        if (q.isBlank() || q.length < 2) return emptyList()

        val results = mutableListOf<SearchResultItem>()

        // 1. Cours Moodle
        runCatching {
            val courses = container.courses.courses.first()
            courses.filter { it.fullName.stripAccents().contains(q) || it.shortName.stripAccents().contains(q) }
                .forEach {
                    results.add(
                        SearchResultItem(
                            id = "course-${it.id}",
                            title = it.fullName,
                            subtitle = "Cours • ${it.category ?: "Université"}",
                            category = SearchCategory.COURSE,
                            targetTabHint = "COURSES"
                        )
                    )
                }
        }

        // 2. Devoirs Moodle
        runCatching {
            val assignments = container.courses.assignments.first()
            assignments.filter { it.name.stripAccents().contains(q) || it.courseName.stripAccents().contains(q) }
                .forEach {
                    results.add(
                        SearchResultItem(
                            id = "assign-${it.id}",
                            title = it.name,
                            subtitle = "Devoir • ${it.courseName}",
                            category = SearchCategory.ASSIGNMENT,
                            targetTabHint = "COURSES"
                        )
                    )
                }
        }

        // 3. Mails
        runCatching {
            val messages = container.mail.messages("INBOX").first()
            messages.filter { it.subject.stripAccents().contains(q) || it.from.display.stripAccents().contains(q) || it.preview.stripAccents().contains(q) }
                .forEach {
                    results.add(
                        SearchResultItem(
                            id = "mail-${it.id}",
                            title = it.subject,
                            subtitle = "Mail • De : ${it.from.display}",
                            category = SearchCategory.MAIL,
                            targetTabHint = "MAIL"
                        )
                    )
                }
        }

        // 4. Emploi du temps ADE (cours des 14 prochains jours)
        runCatching {
            val today = LocalDate.now()
            val events = container.timetable.events(today, today.plusDays(14)).first()
            events.filter { it.subject.stripAccents().contains(q) || it.rooms.any { r -> r.stripAccents().contains(q) } || it.teachers.any { t -> t.stripAccents().contains(q) } }
                .distinctBy { it.subject }
                .forEach {
                    results.add(
                        SearchResultItem(
                            id = "event-${it.id}",
                            title = it.subject,
                            subtitle = "Salle(s) : ${it.rooms.joinToString(", ")} • ${it.teachers.joinToString(", ")}",
                            category = SearchCategory.EVENT,
                            targetTabHint = "TIMETABLE"
                        )
                    )
                }
        }

        // 5. Notes & UEs
        runCatching {
            val reports = container.grades.reports.first()
            reports.flatMap { it.entries }
                .filter { it.label.stripAccents().contains(q) || it.code?.stripAccents()?.contains(q) == true }
                .distinctBy { it.id }
                .forEach {
                    results.add(
                        SearchResultItem(
                            id = "grade-${it.id}",
                            title = it.label,
                            subtitle = "UE • Code : ${it.code ?: "N/A"} • Note : ${it.grade ?: "En cours"}",
                            category = SearchCategory.GRADE,
                            targetTabHint = "GRADES"
                        )
                    )
                }
        }

        // 6. Restaurants CROUS
        runCatching {
            val restaurants = container.crous.restaurants.first()
            restaurants.filter { it.name.stripAccents().contains(q) || it.campus.stripAccents().contains(q) || it.address.stripAccents().contains(q) }
                .forEach {
                    results.add(
                        SearchResultItem(
                            id = "crous-${it.id}",
                            title = it.name,
                            subtitle = "CROUS • ${it.campus} (${it.openingHours})",
                            category = SearchCategory.RESTAURANT,
                            targetTabHint = "CROUS"
                        )
                    )
                }
        }

        return results
    }
}
