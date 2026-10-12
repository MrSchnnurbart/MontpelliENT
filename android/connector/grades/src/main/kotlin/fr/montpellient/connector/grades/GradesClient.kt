package fr.montpellient.connector.grades

import fr.montpellient.core.model.GradeEntry
import fr.montpellient.core.model.GradeReport
import fr.montpellient.connector.cas.CasAuthClient
import fr.montpellient.core.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Client de récupération des notes et résultats officiels de l'Université de Montpellier.
 *
 * Utilise la session CAS pour interroger MonDossierWeb / l'espace scolarité Apogée.
 */
class GradesClient(
    private val casAuthClient: CasAuthClient,
    private val secureStorage: SecureStorage,
    private val okHttpClient: OkHttpClient = casAuthClient.okHttpClient
) {
    companion object {
        const val MON_DOSSIER_WEB_URL = "https://mondossierweb.umontpellier.fr/mdw/notes"
        const val ENT_NOTES_SERVICE_URL = "https://ent.umontpellier.fr/uPortal/p/notes-etudiant"
    }

    /**
     * Récupère le relevé de notes officiel de l'étudiant connecté.
     */
    suspend fun fetchGrades(): Result<List<GradeReport>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(MON_DOSSIER_WEB_URL)
                .header("User-Agent", "MontpelliENT/1.0 (Android; Unofficial)")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP error ${response.code} fetching grades"))
            }

            val body = response.body?.string().orEmpty()
            
            // Si redirigé vers CAS login, tenter réauthentification
            if (body.contains("cas.umontpellier.fr") || body.contains("identifiants")) {
                val creds = secureStorage.getCredentials()
                if (creds != null) {
                    val loginRes = casAuthClient.login(creds.first, creds.second, MON_DOSSIER_WEB_URL)
                    if (loginRes is fr.montpellient.connector.cas.CasAuthResult.Success) {
                        // Relancer la requête
                        val retryResponse = okHttpClient.newCall(request).execute()
                        val retryBody = retryResponse.body?.string().orEmpty()
                        val parsed = ApogeeGradesParser.parse(retryBody)
                        return@withContext Result.success(parsed)
                    }
                }
                return@withContext Result.failure(Exception("Session expired and CAS reauth failed"))
            }

            val reports = ApogeeGradesParser.parse(body)
            Result.success(reports)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
