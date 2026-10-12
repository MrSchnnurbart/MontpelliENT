package fr.montpellient.connector.cas

import fr.montpellient.core.security.SecureStorage
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * CookieJar persistant en mémoire et chiffré dans le SecureStorage.
 * Conserve la session CAS (TGC, CASTGC) et les cookies JSESSIONID des services (ENT, ADE, Moodle).
 */
class SecureCookieJar(
    private val secureStorage: SecureStorage? = null
) : CookieJar {

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    init {
        loadPersistedCookies()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val currentCookies = cookieStore.getOrPut(host) { mutableListOf() }
        val now = System.currentTimeMillis()

        cookies.forEach { newCookie ->
            // Supprimer l'ancien cookie équivalent s'il existe
            currentCookies.removeAll { it.name == newCookie.name && it.matches(url) }
            // N'ajouter que si non expiré
            if (newCookie.expiresAt > now) {
                currentCookies.add(newCookie)
            }
        }
        persistCookies()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val matchingCookies = mutableListOf<Cookie>()

        cookieStore.forEach { (_, cookies) ->
            // Nettoyer les cookies expirés
            cookies.removeAll { it.expiresAt <= now }
            cookies.forEach { cookie ->
                if (cookie.matches(url)) {
                    matchingCookies.add(cookie)
                }
            }
        }
        return matchingCookies
    }

    fun clear() {
        cookieStore.clear()
        secureStorage?.removeSecret(STORAGE_KEY_COOKIES)
    }

    private fun persistCookies() {
        if (secureStorage == null) return
        val serialized = cookieStore.flatMap { (_, cookies) -> cookies }
            .joinToString(";") { serializeCookie(it) }
        secureStorage.saveSecret(STORAGE_KEY_COOKIES, serialized)
    }

    private fun loadPersistedCookies() {
        if (secureStorage == null) return
        val serialized = secureStorage.getSecret(STORAGE_KEY_COOKIES) ?: return
        val now = System.currentTimeMillis()
        serialized.split(";").filter { it.isNotBlank() }.forEach { raw ->
            deserializeCookie(raw)?.let { cookie ->
                if (cookie.expiresAt > now) {
                    cookieStore.getOrPut(cookie.domain) { mutableListOf() }.add(cookie)
                }
            }
        }
    }

    private fun serializeCookie(cookie: Cookie): String {
        return "${cookie.name}|${cookie.value}|${cookie.domain}|${cookie.path}|${cookie.expiresAt}|${cookie.secure}|${cookie.httpOnly}"
    }

    private fun deserializeCookie(raw: String): Cookie? {
        val parts = raw.split("|")
        if (parts.size < 7) return null
        return try {
            val builder = Cookie.Builder()
                .name(parts[0])
                .value(parts[1])
                .domain(parts[2])
                .path(parts[3])
                .expiresAt(parts[4].toLong())
            if (parts[5].toBoolean()) builder.secure()
            if (parts[6].toBoolean()) builder.httpOnly()
            builder.build()
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val STORAGE_KEY_COOKIES = "cas_session_cookies"
    }
}
