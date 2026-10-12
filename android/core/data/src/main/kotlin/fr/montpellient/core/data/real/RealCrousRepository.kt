package fr.montpellient.core.data.real

import fr.montpellient.core.data.CrousRepository
import fr.montpellient.core.data.demo.DemoData
import fr.montpellient.core.model.*
import fr.montpellient.shared.model.SharedAffluence
import fr.montpellient.shared.model.SharedAffluenceLevel
import fr.montpellient.shared.model.SharedCrousRestaurant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDateTime

/**
 * Dépôt réel Crous & Affluence connectant le backend Ktor avec fallback démo transparent.
 */
class RealCrousRepository(
    private val okHttpClient: OkHttpClient,
    private val demoData: DemoData,
    private val serverBaseUrl: String = "http://10.0.2.2:8080/api/crous"
) : CrousRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val _restaurantsState = MutableStateFlow<List<Restaurant>>(demoData.restaurants)
    private val _affluenceState = MutableStateFlow<Map<String, Affluence>>(
        demoData.restaurants.associate { it.id to demoData.affluence(it.id) }
    )

    override val restaurants: Flow<List<Restaurant>> = _restaurantsState.asStateFlow()
    override val allAffluence: Flow<Map<String, Affluence>> = _affluenceState.asStateFlow()

    override fun restaurant(id: String): Flow<Restaurant?> =
        restaurants.map { list -> list.firstOrNull { it.id == id } }

    override fun menus(restaurantId: String): Flow<List<DailyMenu>> =
        kotlinx.coroutines.flow.flowOf(demoData.menus(restaurantId))

    override fun affluence(restaurantId: String): Flow<Affluence> =
        allAffluence.map { it[restaurantId] ?: demoData.affluence(restaurantId) }

    override suspend fun setFavourite(id: String, favourite: Boolean) {
        _restaurantsState.update { list ->
            list.map { if (it.id == id) it.copy(isFavourite = favourite) else it }
        }
    }

    override suspend fun reportAffluence(restaurantId: String, level: AffluenceLevel) {
        withContext(Dispatchers.IO) {
            val sharedLevel = when (level) {
                AffluenceLevel.LOW -> SharedAffluenceLevel.LOW
                AffluenceLevel.MEDIUM -> SharedAffluenceLevel.MEDIUM
                AffluenceLevel.HIGH -> SharedAffluenceLevel.HIGH
                AffluenceLevel.UNKNOWN -> SharedAffluenceLevel.UNKNOWN
            }
            runCatching {
                val payload = SharedAffluence(
                    restaurantId = restaurantId,
                    level = sharedLevel,
                    waitingMinutes = when (level) {
                        AffluenceLevel.LOW -> 5
                        AffluenceLevel.MEDIUM -> 15
                        AffluenceLevel.HIGH -> 30
                        AffluenceLevel.UNKNOWN -> 0
                    },
                    reportedAtEpochSec = System.currentTimeMillis() / 1000
                )
                val reqBody = json.encodeToString(SharedAffluence.serializer(), payload)
                    .toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("$serverBaseUrl/affluence/$restaurantId")
                    .post(reqBody)
                    .build()
                okHttpClient.newCall(req).execute()
            }
            // Mettre à jour l'état local
            _affluenceState.update { map ->
                map + (restaurantId to Affluence(restaurantId, level, (map[restaurantId]?.reports ?: 0) + 1, LocalDateTime.now()))
            }
        }
    }

    override suspend fun refresh() {
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url("$serverBaseUrl/restaurants").get().build()
                val resp = okHttpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val sharedList: List<SharedCrousRestaurant> = json.decodeFromString(body)
                    val mapped = sharedList.map { s ->
                        Restaurant(
                            id = s.id,
                            name = s.name,
                            type = RestaurantType.RU,
                            campus = s.campus,
                            address = s.address,
                            latitude = s.latitude,
                            longitude = s.longitude,
                            openingHours = s.openingHours,
                            isOpenNow = true,
                            isFavourite = false
                        )
                    }
                    if (mapped.isNotEmpty()) {
                        _restaurantsState.value = mapped
                    }
                }
            }

            runCatching {
                val req = Request.Builder().url("$serverBaseUrl/affluence").get().build()
                val resp = okHttpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val sharedList: List<SharedAffluence> = json.decodeFromString(body)
                    val mapped = sharedList.associate { s ->
                        s.restaurantId to Affluence(
                            restaurantId = s.restaurantId,
                            level = when (s.level) {
                                SharedAffluenceLevel.LOW -> AffluenceLevel.LOW
                                SharedAffluenceLevel.MEDIUM -> AffluenceLevel.MEDIUM
                                SharedAffluenceLevel.HIGH, SharedAffluenceLevel.FULL -> AffluenceLevel.HIGH
                                SharedAffluenceLevel.UNKNOWN -> AffluenceLevel.UNKNOWN
                            },
                            reports = 1,
                            updatedAt = LocalDateTime.now()
                        )
                    }
                    if (mapped.isNotEmpty()) {
                        _affluenceState.value = mapped
                    }
                }
            }
        }
    }
}
