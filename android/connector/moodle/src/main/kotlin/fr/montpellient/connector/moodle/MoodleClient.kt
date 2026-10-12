package fr.montpellient.connector.moodle

import fr.montpellient.connector.cas.CasAuthClient
import fr.montpellient.connector.cas.CasAuthResult
import fr.montpellient.core.model.Assignment
import fr.montpellient.core.model.CourseModule
import fr.montpellient.core.model.CourseSection
import fr.montpellient.core.model.ModuleType
import fr.montpellient.core.model.MoodleCourse
import fr.montpellient.core.model.SubmissionStatus
import fr.montpellient.core.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

sealed class MoodleResult<out T> {
    data class Success<T>(val data: T) : MoodleResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : MoodleResult<Nothing>()
}

/**
 * Client pour l'API Web Services Moodle de l'Université de Montpellier (`moodle.umontpellier.fr`).
 *
 * Supporte :
 * 1. Négociation SSO / Jeton Moodle Mobile via CAS ou `login/token.php`
 * 2. Récupération des cours (`core_enrol_get_users_courses`)
 * 3. Récupération des devoirs et dates limites (`mod_assign_get_assignments`)
 * 4. Statut de soumission (`mod_assign_get_submission_status`)
 * 5. Sections et modules d'un cours (`core_course_get_contents`)
 */
class MoodleClient(
    private val casAuthClient: CasAuthClient,
    private val secureStorage: SecureStorage,
    private val okHttpClient: OkHttpClient = casAuthClient.okHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /**
     * Récupère le jeton Moodle (Moodle Mobile WebService token).
     * Si non présent dans SecureStorage, tente de l'obtenir via les identifiants CAS.
     */
    suspend fun getOrFetchToken(): String? = withContext(Dispatchers.IO) {
        val cachedToken = secureStorage.getSecret(STORAGE_KEY_MOODLE_TOKEN)
        if (!cachedToken.isNullOrBlank()) return@withContext cachedToken

        val creds = secureStorage.getCredentials() ?: return@withContext null

        try {
            val url = "$MOODLE_BASE_URL/login/token.php"
            val form = FormBody.Builder()
                .add("username", creds.first)
                .add("password", creds.second)
                .add("service", "moodle_mobile_app")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", CasAuthClient.USER_AGENT)
                .post(form)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: ""
            val tokenResponse = runCatching { json.decodeFromString<MoodleTokenResponse>(body) }.getOrNull()
            val token = tokenResponse?.token

            if (!token.isNullOrBlank()) {
                secureStorage.saveSecret(STORAGE_KEY_MOODLE_TOKEN, token)
                return@withContext token
            }
        } catch (_: Exception) {
            // Échec réseau ou token non autorisé
        }
        null
    }

    /**
     * Récupère les informations de l'étudiant et son userId Moodle.
     */
    suspend fun fetchSiteInfo(token: String): MoodleResult<MoodleSiteInfoResponse> =
        callMoodleFunction(token, "core_webservice_get_site_info")

    /**
     * Récupère la liste des cours inscrits de l'étudiant.
     */
    suspend fun fetchEnrolledCourses(token: String, userId: Long): MoodleResult<List<MoodleCourse>> = withContext(Dispatchers.IO) {
        val params = mapOf("userid" to userId.toString())
        val result: MoodleResult<List<MoodleEnrolledCourseDto>> = callMoodleFunction(token, "core_enrol_get_users_courses", params)

        when (result) {
            is MoodleResult.Success -> {
                val courses = result.data.map { dto ->
                    MoodleCourse(
                        id = dto.id,
                        fullName = cleanHtml(dto.fullName),
                        shortName = dto.shortName,
                        category = dto.category?.toString(),
                        progressPercent = dto.progress?.toInt(),
                        lastAccess = dto.lastAccess?.let {
                            if (it > 0) LocalDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneId.of("Europe/Paris")) else null
                        },
                        isFavourite = dto.isFavourite ?: false
                    )
                }
                MoodleResult.Success(courses)
            }
            is MoodleResult.Error -> result
        }
    }

    /**
     * Récupère tous les devoirs (mod_assign) associés aux cours de l'étudiant.
     */
    suspend fun fetchAssignments(token: String, courseIds: List<Long> = emptyList()): MoodleResult<List<Assignment>> = withContext(Dispatchers.IO) {
        val params = mutableMapOf<String, String>()
        courseIds.forEachIndexed { index, id ->
            params["courseids[$index]"] = id.toString()
        }

        val result: MoodleResult<MoodleAssignmentsResponse> = callMoodleFunction(token, "mod_assign_get_assignments", params)

        when (result) {
            is MoodleResult.Success -> {
                val assignments = mutableListOf<Assignment>()
                result.data.courses.forEach { courseDto ->
                    courseDto.assignments.forEach { assignDto ->
                        val dueDateTime = if (assignDto.dueDate > 0) {
                            LocalDateTime.ofInstant(Instant.ofEpochSecond(assignDto.dueDate), ZoneId.of("Europe/Paris"))
                        } else null

                        assignments.add(
                            Assignment(
                                id = assignDto.id,
                                courseId = assignDto.course,
                                courseName = cleanHtml(courseDto.fullName),
                                name = cleanHtml(assignDto.name),
                                dueDate = dueDateTime,
                                status = SubmissionStatus.NOT_SUBMITTED,
                                description = cleanHtml(assignDto.intro.orEmpty())
                            )
                        )
                    }
                }
                MoodleResult.Success(assignments)
            }
            is MoodleResult.Error -> result
        }
    }

    /**
     * Récupère les sections et modules d'un cours Moodle.
     */
    suspend fun fetchCourseSections(token: String, courseId: Long): MoodleResult<List<CourseSection>> = withContext(Dispatchers.IO) {
        val params = mapOf("courseid" to courseId.toString())
        val result: MoodleResult<List<MoodleCourseSectionDto>> = callMoodleFunction(token, "core_course_get_contents", params)

        when (result) {
            is MoodleResult.Success -> {
                val sections = result.data.map { secDto ->
                    val modules = secDto.modules.map { modDto ->
                        val firstFile = modDto.contents.firstOrNull()
                        CourseModule(
                            id = modDto.id,
                            name = cleanHtml(modDto.name),
                            type = mapModuleType(modDto.modName),
                            description = cleanHtml(modDto.description.orEmpty()),
                            fileName = firstFile?.fileName,
                            fileSizeBytes = firstFile?.fileSize,
                            url = modDto.url ?: firstFile?.fileUrl,
                            isDownloaded = false
                        )
                    }
                    CourseSection(
                        id = secDto.id,
                        name = cleanHtml(secDto.name),
                        summary = cleanHtml(secDto.summary.orEmpty()),
                        modules = modules
                    )
                }
                MoodleResult.Success(sections)
            }
            is MoodleResult.Error -> result
        }
    }

    private suspend inline fun <reified T> callMoodleFunction(
        token: String,
        wsFunction: String,
        params: Map<String, String> = emptyMap()
    ): MoodleResult<T> = withContext(Dispatchers.IO) {
        val formBuilder = FormBody.Builder()
            .add("wstoken", token)
            .add("wsfunction", wsFunction)
            .add("moodlewsrestformat", "json")

        params.forEach { (k, v) -> formBuilder.add(k, v) }

        val request = Request.Builder()
            .url(MOODLE_WS_ENDPOINT)
            .header("User-Agent", CasAuthClient.USER_AGENT)
            .post(formBuilder.build())
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext MoodleResult.Error("Erreur HTTP Moodle: code ${response.code}")
            }

            val body = response.body?.string() ?: ""
            if (body.contains("\"exception\":") || body.contains("\"errorcode\":")) {
                return@withContext MoodleResult.Error("Erreur Moodle API: $body")
            }

            val parsed: T = json.decodeFromString(body)
            MoodleResult.Success(parsed)
        } catch (e: IOException) {
            MoodleResult.Error("Erreur réseau lors de l'appel Moodle", e)
        } catch (e: Exception) {
            MoodleResult.Error("Erreur de parsing de la réponse Moodle: ${e.message}", e)
        }
    }

    private fun mapModuleType(modName: String): ModuleType = when (modName.lowercase()) {
        "resource" -> ModuleType.FILE
        "folder" -> ModuleType.FOLDER
        "url" -> ModuleType.URL
        "page" -> ModuleType.PAGE
        "assign" -> ModuleType.ASSIGN
        "forum" -> ModuleType.FORUM
        "quiz" -> ModuleType.QUIZ
        "label" -> ModuleType.LABEL
        else -> ModuleType.OTHER
    }

    private fun cleanHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .trim()
    }

    companion object {
        const val MOODLE_BASE_URL = "https://moodle.umontpellier.fr"
        const val MOODLE_WS_ENDPOINT = "$MOODLE_BASE_URL/webservice/rest/server.php"
        const val STORAGE_KEY_MOODLE_TOKEN = "moodle_ws_token"
        const val STORAGE_KEY_MOODLE_USER_ID = "moodle_user_id"
    }
}
