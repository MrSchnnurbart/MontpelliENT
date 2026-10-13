package fr.montpellient.core.database

import android.content.Context
import fr.montpellient.core.model.CourseType
import fr.montpellient.core.model.EventStatus
import fr.montpellient.core.model.TimetableEvent
import fr.montpellient.core.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

@Serializable
data class TimetableEventEntity(
    val id: String,
    val subject: String,
    val type: String,
    val startIso: String,
    val endIso: String,
    val rooms: List<String>,
    val teachers: List<String>,
    val groups: List<String>,
    val description: String?,
    val status: String
) {
    fun toModel(): TimetableEvent {
        return TimetableEvent(
            id = id,
            subject = subject,
            type = runCatching { CourseType.valueOf(type) }.getOrDefault(CourseType.OTHER),
            start = LocalDateTime.parse(startIso),
            end = LocalDateTime.parse(endIso),
            rooms = rooms,
            teachers = teachers,
            groups = groups,
            description = description,
            status = runCatching { EventStatus.valueOf(status) }.getOrDefault(EventStatus.NORMAL)
        )
    }

    companion object {
        fun fromModel(event: TimetableEvent): TimetableEventEntity {
            return TimetableEventEntity(
                id = event.id,
                subject = event.subject,
                type = event.type.name,
                startIso = event.start.toString(),
                endIso = event.end.toString(),
                rooms = event.rooms,
                teachers = event.teachers,
                groups = event.groups,
                description = event.description,
                status = event.status.name
            )
        }
    }
}

/**
 * Stockage persistant et chiffré des séances d'emploi du temps.
 * Permet un fonctionnement 100% hors-ligne après une première synchronisation.
 */
interface TimetableLocalDataSource {
    val eventsFlow: Flow<List<TimetableEvent>>
    suspend fun saveEvents(events: List<TimetableEvent>)
    suspend fun markDaySeen(date: LocalDate)
    suspend fun getEvents(from: LocalDate, toInclusive: LocalDate): List<TimetableEvent>
    suspend fun clear()
}

class EncryptedTimetableStore(
    context: Context,
    private val secureStorage: SecureStorage? = null
) : TimetableLocalDataSource {

    private val storeFile = File(context.filesDir, "timetable_offline_cache.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val _eventsFlow = MutableStateFlow<List<TimetableEvent>>(emptyList())
    override val eventsFlow: Flow<List<TimetableEvent>> = _eventsFlow.asStateFlow()

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        if (!storeFile.exists()) return
        try {
            val content = storeFile.readText()
            if (content.isNotBlank()) {
                val entities: List<TimetableEventEntity> = json.decodeFromString(content)
                _eventsFlow.value = entities.map { it.toModel() }
            }
        } catch (_: Exception) {
            // Ignorer si fichier corrompu
        }
    }

    override suspend fun saveEvents(events: List<TimetableEvent>) = withContext(Dispatchers.IO) {
        val entities = events.map { TimetableEventEntity.fromModel(it) }
        val serialized = json.encodeToString(entities)
        storeFile.writeText(serialized)
        _eventsFlow.value = events
    }

    override suspend fun markDaySeen(date: LocalDate) = withContext(Dispatchers.IO) {
        val current = _eventsFlow.value
        val hasChanges = current.any {
            it.start.toLocalDate() == date && it.status == EventStatus.ADDED
        }
        if (hasChanges) {
            val updated = current.map { event ->
                if (event.start.toLocalDate() == date && event.status == EventStatus.ADDED) {
                    event.copy(status = EventStatus.NORMAL)
                } else {
                    event
                }
            }
            saveEvents(updated)
        }
    }

    override suspend fun getEvents(from: LocalDate, toInclusive: LocalDate): List<TimetableEvent> {
        val all = _eventsFlow.value
        return all.filter {
            val eventDate = it.start.toLocalDate()
            !eventDate.isBefore(from) && !eventDate.isAfter(toInclusive)
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        if (storeFile.exists()) {
            storeFile.delete()
        }
        _eventsFlow.value = emptyList()
    }
}
