package fr.montpellient.core.data.demo

import fr.montpellient.core.data.CampusRepository
import fr.montpellient.core.data.CoursesRepository
import fr.montpellient.core.data.CrousRepository
import fr.montpellient.core.data.GradesRepository
import fr.montpellient.core.data.MailRepository
import fr.montpellient.core.data.ProfileRepository
import fr.montpellient.core.data.TimetableRepository
import fr.montpellient.core.model.Affluence
import fr.montpellient.core.model.AffluenceLevel
import fr.montpellient.core.model.MailFolder
import fr.montpellient.core.model.MailMessage
import fr.montpellient.core.model.StudentProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalDateTime

/** Latence simulée d'une synchro en mode démo. */
private const val DEMO_SYNC_DELAY_MS = 700L

class DemoProfileRepository(data: DemoData) : ProfileRepository {
    override val profile: Flow<StudentProfile?> = flowOf(data.profile)
}

class DemoTimetableRepository(private val data: DemoData) : TimetableRepository {
    override fun events(from: LocalDate, toInclusive: LocalDate) = flowOf(data.events(from, toInclusive))
    override suspend fun refresh() = delay(DEMO_SYNC_DELAY_MS)
}

class DemoCoursesRepository(private val data: DemoData) : CoursesRepository {
    override val courses = flowOf(data.courses)
    override val assignments = flowOf(data.assignments)
    override val announcements = flowOf(data.announcements)
    override fun course(courseId: Long) = flowOf(data.courses.firstOrNull { it.id == courseId })
    override fun sections(courseId: Long) = flowOf(data.sections(courseId))
    override suspend fun refresh() = delay(DEMO_SYNC_DELAY_MS)
}

class DemoGradesRepository(data: DemoData) : GradesRepository {
    private val state = MutableStateFlow(data.gradeReports)
    override val reports = state
    override val moodleGrades = flowOf(data.moodleGrades)
    override suspend fun markAllSeen() = state.update { reports ->
        reports.map { r -> r.copy(entries = r.entries.map { it.copy(isNew = false) }) }
    }
    override suspend fun refresh() = delay(DEMO_SYNC_DELAY_MS)
}

class DemoMailRepository(private val data: DemoData) : MailRepository {
    private val state = MutableStateFlow(data.messages)

    override val folders: Flow<List<MailFolder>> = state.map { messages ->
        data.folders.map { folder ->
            val inFolder = messages.filter { it.folderId == folder.id }
            folder.copy(unreadCount = inFolder.count { !it.isRead }, totalCount = inFolder.size)
        }
    }

    override fun messages(folderId: String): Flow<List<MailMessage>> =
        state.map { list -> list.filter { it.folderId == folderId }.sortedByDescending { it.date } }

    override fun message(id: String): Flow<MailMessage?> = state.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun setRead(id: String, read: Boolean) =
        state.update { list -> list.map { if (it.id == id) it.copy(isRead = read) else it } }

    override suspend fun setFlagged(id: String, flagged: Boolean) =
        state.update { list -> list.map { if (it.id == id) it.copy(isFlagged = flagged) else it } }

    override suspend fun refresh() = delay(DEMO_SYNC_DELAY_MS)
}

class DemoCrousRepository(private val data: DemoData) : CrousRepository {
    private val restaurantsState = MutableStateFlow(data.restaurants)
    private val affluenceState = MutableStateFlow(data.restaurants.associate { it.id to data.affluence(it.id) })

    override val restaurants = restaurantsState
    override fun restaurant(id: String) = restaurantsState.map { list -> list.firstOrNull { it.id == id } }
    override fun menus(restaurantId: String) = flowOf(data.menus(restaurantId))
    override fun affluence(restaurantId: String) = affluenceState.map {
        it[restaurantId] ?: Affluence(restaurantId, AffluenceLevel.UNKNOWN, 0, null)
    }
    override val allAffluence: Flow<Map<String, Affluence>> = affluenceState

    override suspend fun setFavourite(id: String, favourite: Boolean) =
        restaurantsState.update { list -> list.map { if (it.id == id) it.copy(isFavourite = favourite) else it } }

    override suspend fun reportAffluence(restaurantId: String, level: AffluenceLevel) {
        affluenceState.update { map ->
            val previous = map[restaurantId]
            map + (restaurantId to Affluence(restaurantId, level, (previous?.reports ?: 0) + 1, LocalDateTime.now()))
        }
    }

    override suspend fun refresh() = delay(DEMO_SYNC_DELAY_MS)
}

class DemoCampusRepository(data: DemoData) : CampusRepository {
    override val news = flowOf(data.news)
    override val events = flowOf(data.events.sortedBy { it.start })
    override val libraries = flowOf(data.libraries)
    override suspend fun refresh() = delay(DEMO_SYNC_DELAY_MS)
}
