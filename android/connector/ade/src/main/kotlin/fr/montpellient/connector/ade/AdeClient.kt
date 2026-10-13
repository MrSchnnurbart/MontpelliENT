package fr.montpellient.connector.ade

import fr.montpellient.connector.cas.CasAuthClient
import fr.montpellient.core.model.TimetableEvent
import fr.montpellient.core.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

sealed class AdeFetchResult {
    data class Success(val events: List<TimetableEvent>, val rawIcs: String) : AdeFetchResult()
    data class Error(val message: String, val cause: Throwable? = null) : AdeFetchResult()
}

/**
 * Client pour récupérer l'emploi du temps ADE Campus UM.
 * Supporte :
 * 1. Téléchargement via URL directe iCal (ex: export ADE ou lien direct_cal.jsp)
 * 2. Authentification via session CAS si le serveur ADE l'exige.
 */
class AdeClient(
    private val casAuthClient: CasAuthClient,
    private val secureStorage: SecureStorage,
    private val okHttpClient: OkHttpClient = casAuthClient.okHttpClient
) {

    /**
     * Récupère les séances de cours.
     * Si [customIcalUrl] n'est pas fourni, tente de lire l'URL enregistrée dans [SecureStorage].
     */
    suspend fun fetchTimetable(customIcalUrl: String? = null): AdeFetchResult = withContext(Dispatchers.IO) {
        val targetUrl = customIcalUrl ?: secureStorage.getSecret(STORAGE_KEY_ADE_URL)
        if (targetUrl.isNullOrBlank()) {
            return@withContext AdeFetchResult.Error("Aucune URL d'export ADE configurée.")
        }

        try {
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", CasAuthClient.USER_AGENT)
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()

            // Si redirection vers CAS login, tenter une réauthentification
            if (response.request.url.toString().contains("cas.umontpellier.fr")) {
                response.close()
                val reauth = casAuthClient.reauthenticate()
                if (reauth is fr.montpellient.connector.cas.CasAuthResult.Success) {
                    val retryResponse = okHttpClient.newCall(request).execute()
                    if (!retryResponse.isSuccessful) {
                        return@withContext AdeFetchResult.Error("Échec HTTP ${retryResponse.code} après réauthentification CAS.")
                    }
                    val body = retryResponse.body?.string() ?: ""
                    val events = IcsTimetableParser.parse(body)
                    return@withContext AdeFetchResult.Success(events = events, rawIcs = body)
                } else {
                    return@withContext AdeFetchResult.Error("Session CAS expirée et réauthentification échouée.")
                }
            }

            if (!response.isSuccessful) {
                return@withContext AdeFetchResult.Error("Erreur serveur ADE: code HTTP ${response.code}")
            }

            val icsContent = response.body?.string() ?: ""
            if (!icsContent.contains("BEGIN:VCALENDAR")) {
                return@withContext AdeFetchResult.Error("Le contenu retourné n'est pas un calendrier iCalendar valide.")
            }

            // Sauvegarder l'URL si valide
            if (customIcalUrl != null) {
                secureStorage.saveSecret(STORAGE_KEY_ADE_URL, customIcalUrl)
            }

            val events = IcsTimetableParser.parse(icsContent)
            AdeFetchResult.Success(events = events, rawIcs = icsContent)
        } catch (e: IOException) {
            AdeFetchResult.Error("Erreur réseau lors de la récupération de l'EDT", e)
        } catch (e: Exception) {
            AdeFetchResult.Error("Erreur inattendue lors de la récupération de l'EDT", e)
        }
    }

    suspend fun getSavedIcalUrl(): String? = withContext(Dispatchers.IO) {
        secureStorage.getSecret(STORAGE_KEY_ADE_URL)
    }

    companion object {
        const val STORAGE_KEY_ADE_URL = "ade_export_url"
    }

}
