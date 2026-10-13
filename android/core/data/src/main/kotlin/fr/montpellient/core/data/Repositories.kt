package fr.montpellient.core.data

import fr.montpellient.core.model.Affluence
import fr.montpellient.core.model.AffluenceLevel
import fr.montpellient.core.model.Announcement
import fr.montpellient.core.model.Assignment
import fr.montpellient.core.model.CampusEvent
import fr.montpellient.core.model.CourseSection
import fr.montpellient.core.model.DailyMenu
import fr.montpellient.core.model.GradeReport
import fr.montpellient.core.model.Library
import fr.montpellient.core.model.MailFolder
import fr.montpellient.core.model.MailMessage
import fr.montpellient.core.model.MoodleCourse
import fr.montpellient.core.model.MoodleGrade
import fr.montpellient.core.model.NewsItem
import fr.montpellient.core.model.Restaurant
import fr.montpellient.core.model.StudentProfile
import fr.montpellient.core.model.TimetableEvent
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Contrats des dépôts. Les écrans observent des [Flow] (Room sera la source de vérité au J1) ;
 * `refresh()` déclenche une synchro de la source concernée.
 */
interface Refreshable {
    suspend fun refresh()
}

interface ProfileRepository {
    val profile: Flow<StudentProfile?>
    fun reloadProfile() {}
}

interface TimetableRepository : Refreshable {
    fun events(from: LocalDate, toInclusive: LocalDate): Flow<List<TimetableEvent>>
    suspend fun setCustomIcalUrl(url: String): Result<Unit> = Result.success(Unit)
    suspend fun getCustomIcalUrl(): String? = null
}


interface CoursesRepository : Refreshable {
    val courses: Flow<List<MoodleCourse>>
    val assignments: Flow<List<Assignment>>
    val announcements: Flow<List<Announcement>>
    fun course(courseId: Long): Flow<MoodleCourse?>
    fun sections(courseId: Long): Flow<List<CourseSection>>
}

interface GradesRepository : Refreshable {
    val reports: Flow<List<GradeReport>>
    val moodleGrades: Flow<List<MoodleGrade>>
    suspend fun markAllSeen()
    suspend fun clear() {}
}

interface MailRepository : Refreshable {
    val folders: Flow<List<MailFolder>>
    fun messages(folderId: String): Flow<List<MailMessage>>
    fun message(id: String): Flow<MailMessage?>
    suspend fun loadFullBody(id: String): String? = null
    suspend fun loadMore(): Boolean = false
    val hasMore: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(false)
    suspend fun setRead(id: String, read: Boolean)
    suspend fun setFlagged(id: String, flagged: Boolean)
}

interface CrousRepository : Refreshable {
    val restaurants: Flow<List<Restaurant>>
    fun restaurant(id: String): Flow<Restaurant?>
    fun menus(restaurantId: String): Flow<List<DailyMenu>>
    fun affluence(restaurantId: String): Flow<Affluence>
    val allAffluence: Flow<Map<String, Affluence>>
    suspend fun setFavourite(id: String, favourite: Boolean)
    suspend fun reportAffluence(restaurantId: String, level: AffluenceLevel)
}

interface CampusRepository : Refreshable {
    val news: Flow<List<NewsItem>>
    val events: Flow<List<CampusEvent>>
    val libraries: Flow<List<Library>>
}
