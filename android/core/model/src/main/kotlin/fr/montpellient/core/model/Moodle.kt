package fr.montpellient.core.model

import java.time.LocalDateTime

/** Cours Moodle auquel l'étudiant est inscrit. */
data class MoodleCourse(
    val id: Long,
    val fullName: String,
    val shortName: String,
    val category: String? = null,
    val progressPercent: Int? = null,
    val lastAccess: LocalDateTime? = null,
    val isFavourite: Boolean = false,
)

data class CourseSection(
    val id: Long,
    val name: String,
    val summary: String? = null,
    val modules: List<CourseModule> = emptyList(),
)

enum class ModuleType { FILE, FOLDER, URL, PAGE, ASSIGN, FORUM, QUIZ, LABEL, OTHER }

data class CourseModule(
    val id: Long,
    val name: String,
    val type: ModuleType,
    val description: String? = null,
    val fileName: String? = null,
    val fileSizeBytes: Long? = null,
    val url: String? = null,
    val isDownloaded: Boolean = false,
)

enum class SubmissionStatus { NOT_SUBMITTED, DRAFT, SUBMITTED, GRADED }

/** Devoir Moodle (mod_assign). */
data class Assignment(
    val id: Long,
    val courseId: Long,
    val courseName: String,
    val name: String,
    val dueDate: LocalDateTime?,
    val status: SubmissionStatus,
    val description: String? = null,
    val grade: String? = null,
) {
    fun isOverdue(now: LocalDateTime): Boolean =
        dueDate != null && dueDate.isBefore(now) &&
            (status == SubmissionStatus.NOT_SUBMITTED || status == SubmissionStatus.DRAFT)
}

/** Annonce d'un forum de cours. */
data class Announcement(
    val id: Long,
    val courseId: Long,
    val courseName: String,
    val title: String,
    val author: String,
    val date: LocalDateTime,
    val message: String,
)
