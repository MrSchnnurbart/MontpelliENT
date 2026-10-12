package fr.montpellient.connector.moodle

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MoodleSiteInfoResponse(
    @SerialName("sitename") val siteName: String = "",
    @SerialName("username") val username: String = "",
    @SerialName("firstname") val firstName: String = "",
    @SerialName("lastname") val lastName: String = "",
    @SerialName("fullname") val fullName: String = "",
    @SerialName("lang") val lang: String = "",
    @SerialName("userid") val userId: Long = 0,
    @SerialName("userpictureurl") val userPictureUrl: String = "",
    @SerialName("functions") val functions: List<MoodleFunctionInfo> = emptyList()
)

@Serializable
data class MoodleFunctionInfo(
    @SerialName("name") val name: String,
    @SerialName("version") val version: String = ""
)

@Serializable
data class MoodleEnrolledCourseDto(
    @SerialName("id") val id: Long,
    @SerialName("shortname") val shortName: String,
    @SerialName("fullname") val fullName: String,
    @SerialName("category") val category: Long? = null,
    @SerialName("progress") val progress: Double? = null,
    @SerialName("lastaccess") val lastAccess: Long? = null,
    @SerialName("isfavourite") val isFavourite: Boolean? = false
)

@Serializable
data class MoodleCourseSectionDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("summary") val summary: String? = null,
    @SerialName("modules") val modules: List<MoodleModuleDto> = emptyList()
)

@Serializable
data class MoodleModuleDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("modname") val modName: String,
    @SerialName("description") val description: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("contents") val contents: List<MoodleContentDto> = emptyList()
)

@Serializable
data class MoodleContentDto(
    @SerialName("filename") val fileName: String? = null,
    @SerialName("filesize") val fileSize: Long? = null,
    @SerialName("fileurl") val fileUrl: String? = null
)

@Serializable
data class MoodleAssignmentsResponse(
    @SerialName("courses") val courses: List<MoodleCourseAssignmentsDto> = emptyList()
)

@Serializable
data class MoodleCourseAssignmentsDto(
    @SerialName("id") val id: Long,
    @SerialName("fullname") val fullName: String = "",
    @SerialName("assignments") val assignments: List<MoodleAssignmentDto> = emptyList()
)

@Serializable
data class MoodleAssignmentDto(
    @SerialName("id") val id: Long,
    @SerialName("cmid") val cmId: Long = 0,
    @SerialName("course") val course: Long,
    @SerialName("name") val name: String,
    @SerialName("duedate") val dueDate: Long = 0,
    @SerialName("intro") val intro: String? = null
)

@Serializable
data class MoodleSubmissionStatusResponse(
    @SerialName("lastattempt") val lastAttempt: MoodleLastAttemptDto? = null
)

@Serializable
data class MoodleLastAttemptDto(
    @SerialName("submission") val submission: MoodleSubmissionDto? = null,
    @SerialName("gradingstatus") val gradingStatus: String? = null
)

@Serializable
data class MoodleSubmissionDto(
    @SerialName("status") val status: String? = null,
    @SerialName("timemodified") val timeModified: Long = 0
)

@Serializable
data class MoodleTokenResponse(
    @SerialName("token") val token: String? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("errorcode") val errorCode: String? = null
)
