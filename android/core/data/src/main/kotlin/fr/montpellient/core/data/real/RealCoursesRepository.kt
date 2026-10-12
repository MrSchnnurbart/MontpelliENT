package fr.montpellient.core.data.real

import fr.montpellient.connector.moodle.MoodleClient
import fr.montpellient.connector.moodle.MoodleResult
import fr.montpellient.core.data.CoursesRepository
import fr.montpellient.core.database.CoursesLocalDataSource
import fr.montpellient.core.model.Announcement
import fr.montpellient.core.model.Assignment
import fr.montpellient.core.model.CourseSection
import fr.montpellient.core.model.MoodleCourse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Dépôt réel pour les cours et devoirs Moodle UM.
 * - Source de vérité locale : [CoursesLocalDataSource] pour l'accès hors-ligne instantané.
 * - [refresh] synchronise les cours inscrits et les devoirs à rendre via l'API Web Services Moodle.
 */
class RealCoursesRepository(
    private val moodleClient: MoodleClient,
    private val localDataSource: CoursesLocalDataSource
) : CoursesRepository {

    override val courses: Flow<List<MoodleCourse>> = localDataSource.coursesFlow

    override val assignments: Flow<List<Assignment>> = localDataSource.assignmentsFlow

    override val announcements: Flow<List<Announcement>> = flowOf(emptyList())

    override fun course(courseId: Long): Flow<MoodleCourse?> =
        courses.map { list -> list.firstOrNull { it.id == courseId } }

    override fun sections(courseId: Long): Flow<List<CourseSection>> = flowOf(emptyList())

    override suspend fun refresh() {
        val token = moodleClient.getOrFetchToken() ?: return
        val siteInfoResult = moodleClient.fetchSiteInfo(token)
        if (siteInfoResult !is MoodleResult.Success) return

        val userId = siteInfoResult.data.userId
        val coursesResult = moodleClient.fetchEnrolledCourses(token, userId)
        if (coursesResult is MoodleResult.Success) {
            val coursesList = coursesResult.data
            localDataSource.saveCourses(coursesList)

            // Récupération des devoirs pour ces cours
            val courseIds = coursesList.map { it.id }
            val assignResult = moodleClient.fetchAssignments(token, courseIds)
            if (assignResult is MoodleResult.Success) {
                localDataSource.saveAssignments(assignResult.data)
            }
        }
    }
}
