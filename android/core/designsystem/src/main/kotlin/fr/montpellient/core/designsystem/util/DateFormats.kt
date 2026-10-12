package fr.montpellient.core.designsystem.util

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Formatage des dates/heures selon la langue courante de l'appli. */
object DateFormats {
    private val time: DateTimeFormatter get() = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val dayFull: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
    private val dayShort: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    private val dayNumber: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d", Locale.getDefault())
    private val dayName: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    private val dateTime: DateTimeFormatter
        get() = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.getDefault())

    fun time(t: LocalDateTime): String = t.format(time)
    fun timeRange(start: LocalDateTime, end: LocalDateTime): String = "${time(start)} – ${time(end)}"
    fun dayFull(d: LocalDate): String = d.format(dayFull).replaceFirstChar { it.titlecase(Locale.getDefault()) }
    fun dayShort(d: LocalDate): String = d.format(dayShort).replaceFirstChar { it.titlecase(Locale.getDefault()) }
    fun dayNumber(d: LocalDate): String = d.format(dayNumber)
    fun dayName(d: LocalDate): String = d.format(dayName).trimEnd('.').replaceFirstChar { it.titlecase(Locale.getDefault()) }
    fun dateTime(t: LocalDateTime): String = t.format(dateTime)

    /** Date courte relative pour les listes (heure si aujourd'hui, jour sinon). */
    fun relativeShort(t: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): String = when {
        t.toLocalDate() == now.toLocalDate() -> time(t)
        t.toLocalDate() == now.toLocalDate().minusDays(1) -> if (Locale.getDefault().language == "fr") "Hier" else "Yesterday"
        else -> dayShort(t.toLocalDate())
    }

    /** « dans 2 j », « dans 3 h », « en retard ». */
    fun countdown(target: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): String {
        val fr = Locale.getDefault().language == "fr"
        val d = Duration.between(now, target)
        if (d.isNegative) return if (fr) "En retard" else "Overdue"
        val days = d.toDays()
        val hours = d.toHours()
        return when {
            days >= 1 -> if (fr) "dans $days j" else "in $days d"
            hours >= 1 -> if (fr) "dans $hours h" else "in $hours h"
            else -> if (fr) "dans ${d.toMinutes()} min" else "in ${d.toMinutes()} min"
        }
    }

    fun fileSize(bytes: Long): String = when {
        bytes >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f Mo", bytes / 1_000_000.0)
        bytes >= 1_000 -> "${bytes / 1_000} Ko"
        else -> "$bytes o"
    }
}
