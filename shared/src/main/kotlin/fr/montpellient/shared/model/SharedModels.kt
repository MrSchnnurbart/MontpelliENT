package fr.montpellient.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class SharedAffluenceLevel {
    UNKNOWN,
    LOW,
    MEDIUM,
    HIGH,
    FULL
}

@Serializable
data class SharedAffluence(
    val restaurantId: String,
    val level: SharedAffluenceLevel,
    val waitingMinutes: Int,
    val reportedAtEpochSec: Long
)

@Serializable
data class SharedCrousMealItem(
    val category: String,
    val name: String
)

@Serializable
data class SharedCrousMenu(
    val id: String,
    val restaurantId: String,
    val dateIso: String,
    val period: String, // "midi" ou "soir"
    val items: List<SharedCrousMealItem>
)

@Serializable
data class SharedCrousRestaurant(
    val id: String,
    val name: String,
    val campus: String,
    val address: String,
    val openingHours: String,
    val latitude: Double,
    val longitude: Double,
    val hasIzly: Boolean = true
)

@Serializable
data class SharedCampusLibrary(
    val id: String,
    val name: String,
    val campus: String,
    val openingHours: String,
    val totalSeats: Int,
    val availableSeats: Int,
    val isOpen: Boolean
)

@Serializable
data class SharedCampusEvent(
    val id: String,
    val title: String,
    val category: String,
    val dateIso: String,
    val location: String,
    val description: String
)

@Serializable
data class PushNotificationPayload(
    val id: String,
    val title: String,
    val body: String,
    val type: String, // "grade", "timetable", "crous", "general"
    val timestampEpochSec: Long
)
