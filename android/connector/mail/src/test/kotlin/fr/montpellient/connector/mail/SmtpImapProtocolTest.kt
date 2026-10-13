package fr.montpellient.connector.mail

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class SmtpImapProtocolTest {

    @Test
    fun testBase64EncodingForSmtpAuth() {
        val login = "e2024001"
        val password = "MySecretPassword123"

        val encodedLogin = Base64.getEncoder().encodeToString(login.toByteArray())
        val encodedPass = Base64.getEncoder().encodeToString(password.toByteArray())

        assertEquals("ZTIwMjQwMDE=", encodedLogin)
        assertEquals(login, String(Base64.getDecoder().decode(encodedLogin)))
        assertEquals(password, String(Base64.getDecoder().decode(encodedPass)))
    }

    @Test
    fun testParseFetchResponses() {
        val lines = listOf(
            "* 1 FETCH (UID 101 FLAGS (\\Seen) ENVELOPE (\"Wed, 8 Jul 2026 11:56:04 +0200\" \"Bienvenue à l'Université de Montpellier\" ((\"Scolarité UM\" NIL \"scolarite\" \"umontpellier.fr\"))))",
            "Ceci est un message de test",
            ")"
        )
        val client = ImapClient()
        // test helper via reflection or making it internal
        val method = ImapClient::class.java.getDeclaredMethod("parseFetchResponses", List::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(client, lines) as List<fr.montpellient.core.model.MailMessage>

        assertEquals(1, result.size)
        assertEquals("Bienvenue à l'Université de Montpellier", result[0].subject)
        assertEquals("Scolarité UM", result[0].from.name)
        assertEquals("scolarite@umontpellier.fr", result[0].from.email)
    }
}
