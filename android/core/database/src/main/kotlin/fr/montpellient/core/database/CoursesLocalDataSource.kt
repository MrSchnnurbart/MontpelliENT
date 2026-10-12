package fr.montpellient.core.database

import android.content.Context
import fr.montpellient.core.model.Assignment
import fr.montpellient.core.model.CourseModule
import fr.montpellient.core.model.CourseSection
import fr.montpellient.core.model.ModuleType
import fr.montpellient.core.model.MoodleCourse
import fr.montpellient.core.model.SubmissionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime

@Serializable
data class MoodleCourseEntity(
    val id: Long,
    val fullName: String,
    val shortName: String,
    val category: String? = null,
    val progressPercent: Int? = null,
    val lastAccessIso: String? = null,
    val isFavourite: Boolean = false
) {
    fun toModel(): MoodleCourse = MoodleCourse(
        id = id,
        fullName = fullName,
        shortName = shortName,
        category = category,
        progressPercent = progressPercent,
        lastAccess = lastAccessIso?.let { LocalDateTime.parse(it) },
        isFavourite = isFavourite
    )

    companion object {
        fun fromModel(course: MoodleCourse): MoodleCourseEntity = MoodleCourseEntity(
            id = course.id,
            fullName = course.fullName,
            shortName = course.shortName,
            category = course.category,
            progressPercent = course.progressPercent,
            lastAccessIso = course.lastAccess?.toString(),
            isFavourite = course.isFavourite
        )
    }
}

@Serializable
data class AssignmentEntity(
    val id: Long,
    val courseId: Long,
    val courseName: String,
    val name: String,
    val dueDateIso: String? = null,
    val status: String,
    val description: String? = null,
    val grade: String? = null
) {
    fun toModel(): Assignment = Assignment(
        id = id,
        courseId = courseId,
        courseName = courseName,
        name = name,
        dueDate = dueDateIso?.let { LocalDateTime.parse(it) },
        status = runCatching { SubmissionStatus.valueOf(status) }.getOrDefault(SubmissionStatus.NOT_SUBMITTED),
        description = description,
        grade = grade
    )

    companion object {
        fun fromModel(assign: Assignment): AssignmentEntity = AssignmentEntity(
            id = assign.id,
            courseId = assign.courseId,
            courseName = assign.courseName,
            name = assign.name,
            dueDateIso = assign.dueDate?.toString(),
            status = assign.status.name,
            description = assign.description,
            grade = assign.grade
        )
    }
}

interface CoursesLocalDataSource {
    val coursesFlow: Flow<List<MoodleCourse>>
    val assignmentsFlow: Flow<List<Assignment>>
    suspend fun saveCourses(courses: List<MoodleCourse>)
    suspend fun saveAssignments(assignments: List<Assignment>)
    suspend fun clear()
}

class EncryptedCoursesStore(
    context: Context
) : CoursesLocalDataSource {

    private val coursesFile = File(context.filesDir, "courses_offline_cache.json")
    private val assignmentsFile = File(context.filesDir, "assignments_offline_cache.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val _coursesFlow = MutableStateFlow<List<MoodleCourse>>(emptyList())
    override val coursesFlow: Flow<List<MoodleCourse>> = _coursesFlow.asStateFlow()

    private val _assignmentsFlow = MutableStateFlow<List<Assignment>>(emptyList())
    override val assignmentsFlow: Flow<List<Assignment>> = _assignmentsFlow.asStateFlow()

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        if (coursesFile.exists()) {
            runCatching {
                val list: List<MoodleCourseEntity> = json.decodeFromString(coursesFile.readText())
                _coursesFlow.value = list.map { it.toModel() }
            }
        }
        if (assignmentsFile.exists()) {
            runCatching {
                val list: List<AssignmentEntity> = json.decodeFromString(assignmentsFile.readText())
                _assignmentsFlow.value = list.map { it.toModel() }
            }
        }
    }

    override suspend fun saveCourses(courses: List<MoodleCourse>) = withContext(Dispatchers.IO) {
        val entities = courses.map { MoodleCourseEntity.fromModel(it) }
        coursesFile.writeText(json.encodeToString(entities))
        _coursesFlow.value = courses
    }

    override suspend fun saveAssignments(assignments: List<Assignment>) = withContext(Dispatchers.IO) {
        val entities = assignments.map { AssignmentEntity.fromModel(it) }
        assignmentsFile.writeText(json.encodeToString(entities))
        _assignmentsFlow.value = assignments
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        coursesFile.delete()
        assignmentsFile.delete()
        _coursesFlow.value = emptyList()
        _assignmentsFlow.value = emptyList()
    }
}
