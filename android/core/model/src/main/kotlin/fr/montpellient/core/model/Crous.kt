package fr.montpellient.core.model

import java.time.LocalDate
import java.time.LocalDateTime

enum class RestaurantType { RU, CAFETERIA, BRASSERIE, FOOD_TRUCK }

data class Restaurant(
    val id: String,
    val name: String,
    val type: RestaurantType,
    val campus: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val openingHours: String,
    val isOpenNow: Boolean,
    val isFavourite: Boolean = false,
)

data class MenuStation(val name: String, val dishes: List<String>)

data class Meal(val name: String, val stations: List<MenuStation>)

data class DailyMenu(
    val restaurantId: String,
    val date: LocalDate,
    val meals: List<Meal>,
)

enum class AffluenceLevel { LOW, MEDIUM, HIGH, UNKNOWN }

/** Affluence collaborative (agrégée par le serveur). */
data class Affluence(
    val restaurantId: String,
    val level: AffluenceLevel,
    val reports: Int,
    val updatedAt: LocalDateTime?,
)
