package fr.montpellient.core.model

import java.time.LocalDateTime

/** Type de séance ADE. */
enum class CourseType { CM, TD, TP, EXAM, OTHER }

/** État d'une séance par rapport à la synchro précédente. */
enum class EventStatus { NORMAL, MODIFIED, CANCELLED, ADDED }

/** Séance de l'emploi du temps (issue d'ADE Campus). Heures locales Europe/Paris. */
data class TimetableEvent(
    val id: String,
    val subject: String,
    val type: CourseType,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val rooms: List<String> = emptyList(),
    val teachers: List<String> = emptyList(),
    val groups: List<String> = emptyList(),
    val description: String? = null,
    val status: EventStatus = EventStatus.NORMAL,
) {
    val isCancelled: Boolean get() = status == EventStatus.CANCELLED
}
