package fr.montpellient.connector.cas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CasFormParserTest {

    @Test
    fun parseStandardCasLoginForm() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>CAS UM - Connexion</title></head>
            <body>
                <form id="fm1" action="/cas/login?service=https%3A%2F%2Fent.umontpellier.fr" method="post">
                    <input type="hidden" name="execution" value="e1s1" />
                    <input type="hidden" name="_eventId" value="submit" />
                    <input type="hidden" name="geolocation" value="" />
                    <input type="text" name="username" />
                    <input type="password" name="password" />
                    <input type="submit" value="SE CONNECTER" />
                </form>
            </body>
            </html>
        """.trimIndent()

        val parsed = CasFormParser.parse(html, "https://cas.umontpellier.fr/cas/login")

        assertEquals("https://cas.umontpellier.fr/cas/login?service=https%3A%2F%2Fent.umontpellier.fr", parsed.actionUrl)
        assertEquals("e1s1", parsed.execution)
        assertNull(parsed.lt)
        assertEquals("submit", parsed.eventId)
        assertTrue(parsed.hiddenFields.containsKey("geolocation"))
        assertFalse(parsed.isAlreadyAuthenticated)
        assertNull(parsed.errorMessage)
    }

    @Test
    fun parseCasLoginFormWithError() {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="alert alert-danger" id="msg">Identifiant ou mot de passe incorrect.</div>
                <form id="fm1" action="/cas/login" method="post">
                    <input type="hidden" name="execution" value="e1s2" />
                    <input type="text" name="username" />
                    <input type="password" name="password" />
                </form>
            </body>
            </html>
        """.trimIndent()

        val parsed = CasFormParser.parse(html, "https://cas.umontpellier.fr/cas/login")
        assertEquals("Identifiant ou mot de passe incorrect.", parsed.errorMessage)
        assertEquals("e1s2", parsed.execution)
    }

    @Test
    fun parseAlreadyAuthenticatedPage() {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="alert alert-success" id="msg">Connexion réussie.</div>
            </body>
            </html>
        """.trimIndent()

        val parsed = CasFormParser.parse(html, "https://cas.umontpellier.fr/cas/login")
        assertTrue(parsed.isAlreadyAuthenticated)
        assertNull(parsed.execution)
    }
}
