package fr.montpellient.connector.cas

import fr.montpellient.core.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class CasAuthResult {
    data class Success(val serviceTicket: String? = null, val redirectUrl: String? = null) : CasAuthResult()
    data class InvalidCredentials(val message: String) : CasAuthResult()
    data class RequiresMfa(val mfaUrl: String) : CasAuthResult()
    data class NetworkError(val cause: Throwable) : CasAuthResult()
    data class ServerError(val code: Int, val message: String) : CasAuthResult()
}

/**
 * Client d'authentification CAS Université de Montpellier (cas.umontpellier.fr).
 *
 * Fonctionnalités :
 * - Négociation directe du formulaire CAS (GET -> extraction execution/lt -> POST).
 * - Enregistrement local sécurisé (Keystore) des identifiants et cookies de session.
 * - Réauthentification automatique en cas d'expiration de session.
 * - Bascule transparente vers WebView si challenge MFA/2FA détecté.
 */
class CasAuthClient(
    val secureStorage: SecureStorage,
    val cookieJar: SecureCookieJar = SecureCookieJar(secureStorage),
    customOkHttpClient: OkHttpClient? = null
) {
    val okHttpClient: OkHttpClient = customOkHttpClient ?: OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Tente de connecter l'étudiant au serveur CAS de l'UM.
     * Si [serviceUrl] est fourni, CAS valide le service et redirige avec un ticket de service (`?ticket=ST-...`).
     */
    suspend fun login(
        login: String,
        password: String,
        serviceUrl: String = DEFAULT_ENT_SERVICE_URL
    ): CasAuthResult = withContext(Dispatchers.IO) {
        try {
            val loginPageUrl = buildCasLoginUrl(serviceUrl)

            // Étape 1 : Récupération du formulaire CAS et des tokens CSRF/execution
            val getRequest = Request.Builder()
                .url(loginPageUrl)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            val getResponse: Response = okHttpClient.newCall(getRequest).execute()
            if (!getResponse.isSuccessful) {
                return@withContext CasAuthResult.ServerError(getResponse.code, "Erreur d'accès au CAS UM: ${getResponse.message}")
            }

            val html = getResponse.body?.string() ?: ""
            val form = CasFormParser.parse(html, loginPageUrl)

            if (form.isAlreadyAuthenticated) {
                val currentUrl = getResponse.request.url.toString()
                val ticket = extractTicket(currentUrl)
                secureStorage.saveCredentials(login, password)
                return@withContext CasAuthResult.Success(serviceTicket = ticket, redirectUrl = currentUrl)
            }

            if (form.requiresMfa) {
                return@withContext CasAuthResult.RequiresMfa(mfaUrl = loginPageUrl)
            }

            // Étape 2 : Envoi des identifiants au formulaire CAS
            val formBuilder = FormBody.Builder()
                .add("username", login)
                .add("password", password)
                .add("_eventId", form.eventId)

            form.execution?.let { formBuilder.add("execution", it) }
            form.lt?.let { formBuilder.add("lt", it) }
            form.hiddenFields.forEach { (k, v) -> formBuilder.add(k, v) }

            val postRequest = Request.Builder()
                .url(form.actionUrl)
                .header("User-Agent", USER_AGENT)
                .post(formBuilder.build())
                .build()

            val postResponse: Response = okHttpClient.newCall(postRequest).execute()
            val finalUrl = postResponse.request.url.toString()
            val responseBody = postResponse.body?.string() ?: ""

            // Analyse de la réponse post-authentification
            val parsedPost = CasFormParser.parse(responseBody, finalUrl)

            // 1. Erreur explicite retournée par CAS (identifiants invalides)
            if (parsedPost.errorMessage != null && !parsedPost.isAlreadyAuthenticated) {
                return@withContext CasAuthResult.InvalidCredentials(parsedPost.errorMessage)
            }

            // 2. Détection de demande 2FA/MFA
            if (parsedPost.requiresMfa) {
                return@withContext CasAuthResult.RequiresMfa(mfaUrl = finalUrl)
            }

            // 3. Présence d'un ticket de service ou redirection vers le service cible
            val ticket = extractTicket(finalUrl)
            if (ticket != null || finalUrl.startsWith(serviceUrl) || parsedPost.isAlreadyAuthenticated) {
                secureStorage.saveCredentials(login, password)
                return@withContext CasAuthResult.Success(serviceTicket = ticket, redirectUrl = finalUrl)
            }

            // Si on est toujours sur la page login avec un nouveau token execution sans message explicite
            if (parsedPost.execution != null) {
                return@withContext CasAuthResult.InvalidCredentials("Identifiant ou mot de passe incorrect.")
            }

            // Par défaut si redirection valide
            secureStorage.saveCredentials(login, password)
            CasAuthResult.Success(serviceTicket = ticket, redirectUrl = finalUrl)
        } catch (e: IOException) {
            CasAuthResult.NetworkError(e)
        } catch (e: Exception) {
            CasAuthResult.NetworkError(e)
        }
    }

    /**
     * Déconnexion complète : efface les cookies et vide les identifiants sécurisés.
     */
    suspend fun logout(): Boolean = withContext(Dispatchers.IO) {
        try {
            val logoutRequest = Request.Builder()
                .url(CAS_LOGOUT_URL)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            okHttpClient.newCall(logoutRequest).execute().close()
        } catch (_: Exception) {
            // Ignorer l'erreur réseau éventuelle lors du logout distant
        } finally {
            cookieJar.clear()
            secureStorage.clearCredentials()
        }
        true
    }

    /**
     * Tente de réutiliser les identifiants stockés dans le Keystore pour régénérer la session.
     */
    suspend fun reauthenticate(): CasAuthResult? {
        val creds = secureStorage.getCredentials() ?: return null
        return login(creds.first, creds.second)
    }

    private fun buildCasLoginUrl(serviceUrl: String): String {
        return "$CAS_BASE_URL/login?service=${java.net.URLEncoder.encode(serviceUrl, "UTF-8")}"
    }

    private fun extractTicket(url: String): String? {
        val uri = java.net.URI.create(url)
        val query = uri.query ?: return null
        return query.split("&")
            .map { it.split("=") }
            .firstOrNull { it.size == 2 && it[0] == "ticket" }
            ?.get(1)
    }

    companion object {
        const val CAS_BASE_URL = "https://cas.umontpellier.fr/cas"
        const val CAS_LOGOUT_URL = "https://cas.umontpellier.fr/cas/logout"
        const val DEFAULT_ENT_SERVICE_URL = "https://ent.umontpellier.fr"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; MontpelliENT Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
