package fr.montpellient.core.model

import java.time.LocalDateTime

data class NewsItem(
    val id: String,
    val title: String,
    val summary: String,
    val source: String,
    val url: String,
    val publishedAt: LocalDateTime,
)

enum class EventCategory { PARTY, CULTURE, SPORT, CONFERENCE, ASSOCIATION, CAREER, OTHER }

/** Événement publié par une association (via le back-office) ou par l'UM. */
data class CampusEvent(
    val id: String,
    val title: String,
    val organizer: String,
    val description: String,
    val start: LocalDateTime,
    val end: LocalDateTime?,
    val location: String,
    val category: EventCategory,
    val url: String? = null,
)

/** Bibliothèque universitaire : horaires et disponibilité. */
data class Library(
    val id: String,
    val name: String,
    val campus: String,
    val todayHours: String,
    val isOpenNow: Boolean,
    /** Taux d'occupation 0..100, null si la donnée n'est pas disponible. */
    val occupancyPercent: Int? = null,
    val weeklyHours: List<Pair<String, String>> = emptyList(),
)
