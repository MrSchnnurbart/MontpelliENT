package fr.montpellient.connector.cas

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Données extraites d'une page de formulaire CAS (`cas.umontpellier.fr/cas/login`).
 */
data class CasLoginForm(
    val actionUrl: String,
    val execution: String?,
    val lt: String?,
    val eventId: String = "submit",
    val hiddenFields: Map<String, String> = emptyMap(),
    val isAlreadyAuthenticated: Boolean = false,
    val requiresMfa: Boolean = false,
    val errorMessage: String? = null
)

object CasFormParser {

    /**
     * Analyse le document HTML de la page CAS pour extraire les jetons de sécurité (execution, lt, CSRF).
     */
    fun parse(html: String, baseUrl: String): CasLoginForm {
        val doc: Document = Jsoup.parse(html, baseUrl)

        // Détection d'un message d'erreur
        val errorElement = doc.selectFirst(".alert-danger, .errors, #msg.errors, .error, .banner-danger")
        val errorMessage = errorElement?.text()?.trim()

        // Vérification si déjà authentifié (redirection ou message de succès)
        val authSuccess = doc.selectFirst("#msg.success, .alert-success") != null ||
                html.contains("Connexion réussie") || html.contains("Log In Successful")

        // Recherche du formulaire de connexion
        val form = doc.selectFirst("form#fm1, form#loginForm, form[action*='login']")
        if (form == null) {
            val requiresMfa = html.contains("mfa") || html.contains("authenticator") || html.contains("2FA")
            return CasLoginForm(
                actionUrl = baseUrl,
                execution = null,
                lt = null,
                isAlreadyAuthenticated = authSuccess,
                requiresMfa = requiresMfa,
                errorMessage = errorMessage
            )
        }

        val action = form.absUrl("action").ifEmpty { baseUrl }
        val execution = form.selectFirst("input[name=execution]")?.attr("value")
        val lt = form.selectFirst("input[name=lt]")?.attr("value")
        val eventId = form.selectFirst("input[name=_eventId]")?.attr("value") ?: "submit"

        val hiddenFields = mutableMapOf<String, String>()
        form.select("input[type=hidden]").forEach { input ->
            val name = input.attr("name")
            val value = input.attr("value")
            if (name.isNotEmpty() && name != "execution" && name != "lt" && name != "_eventId") {
                hiddenFields[name] = value
            }
        }

        return CasLoginForm(
            actionUrl = action,
            execution = execution,
            lt = lt,
            eventId = eventId,
            hiddenFields = hiddenFields,
            isAlreadyAuthenticated = false,
            requiresMfa = false,
            errorMessage = errorMessage
        )
    }
}
