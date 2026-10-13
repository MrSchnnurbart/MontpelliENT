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
import kotlinx.coroutines.flow.map
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

@Serializable
data class CourseModuleEntity(
    val id: Long,
    val name: String,
    val type: String,
    val description: String? = null,
    val fileName: String? = null,
    val fileSizeBytes: Long? = null,
    val url: String? = null,
    val isDownloaded: Boolean = false
) {
    fun toModel(): CourseModule = CourseModule(
        id = id,
        name = name,
        type = runCatching { ModuleType.valueOf(type) }.getOrDefault(ModuleType.OTHER),
        description = description,
        fileName = fileName,
        fileSizeBytes = fileSizeBytes,
        url = url,
        isDownloaded = isDownloaded
    )

    companion object {
        fun fromModel(mod: CourseModule): CourseModuleEntity = CourseModuleEntity(
            id = mod.id,
            name = mod.name,
            type = mod.type.name,
            description = mod.description,
            fileName = mod.fileName,
            fileSizeBytes = mod.fileSizeBytes,
            url = mod.url,
            isDownloaded = mod.isDownloaded
        )
    }
}

@Serializable
data class CourseSectionEntity(
    val id: Long,
    val courseId: Long,
    val name: String,
    val summary: String? = null,
    val modules: List<CourseModuleEntity> = emptyList()
) {
    fun toModel(): CourseSection = CourseSection(
        id = id,
        name = name,
        summary = summary,
        modules = modules.map { it.toModel() }
    )

    companion object {
        fun fromModel(sec: CourseSection, courseId: Long): CourseSectionEntity = CourseSectionEntity(
            id = sec.id,
            courseId = courseId,
            name = sec.name,
            summary = sec.summary,
            modules = sec.modules.map { CourseModuleEntity.fromModel(it) }
        )
    }
}

interface CoursesLocalDataSource {
    val coursesFlow: Flow<List<MoodleCourse>>
    val assignmentsFlow: Flow<List<Assignment>>
    fun sectionsFlow(courseId: Long): Flow<List<CourseSection>>
    suspend fun saveCourses(courses: List<MoodleCourse>)
    suspend fun saveAssignments(assignments: List<Assignment>)
    suspend fun saveSections(courseId: Long, sections: List<CourseSection>)
    suspend fun clear()
}

class EncryptedCoursesStore(
    context: Context
) : CoursesLocalDataSource {

    private val coursesFile = File(context.filesDir, "courses_offline_cache.json")
    private val assignmentsFile = File(context.filesDir, "assignments_offline_cache.json")
    private val sectionsFile = File(context.filesDir, "sections_offline_cache.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val _coursesFlow = MutableStateFlow<List<MoodleCourse>>(emptyList())
    override val coursesFlow: Flow<List<MoodleCourse>> = _coursesFlow.asStateFlow()

    private val _assignmentsFlow = MutableStateFlow<List<Assignment>>(emptyList())
    override val assignmentsFlow: Flow<List<Assignment>> = _assignmentsFlow.asStateFlow()

    private val _sectionsState = MutableStateFlow<Map<Long, List<CourseSection>>>(emptyMap())

    override fun sectionsFlow(courseId: Long): Flow<List<CourseSection>> =
        _sectionsState.map { it[courseId].orEmpty() }

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
        if (sectionsFile.exists()) {
            runCatching {
                val list: List<CourseSectionEntity> = json.decodeFromString(sectionsFile.readText())
                val grouped = list.groupBy { it.courseId }.mapValues { (_, secList) -> secList.map { it.toModel() } }
                _sectionsState.value = grouped
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

    override suspend fun saveSections(courseId: Long, sections: List<CourseSection>) = withContext(Dispatchers.IO) {
        val updated = _sectionsState.value.toMutableMap()
        updated[courseId] = sections
        _sectionsState.value = updated

        val allEntities = updated.flatMap { (cid, secs) ->
            secs.map { CourseSectionEntity.fromModel(it, cid) }
        }
        sectionsFile.writeText(json.encodeToString(allEntities))
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        coursesFile.delete()
        assignmentsFile.delete()
        sectionsFile.delete()
        _coursesFlow.value = emptyList()
        _assignmentsFlow.value = emptyList()
        _sectionsState.value = emptyMap()
    }
}
