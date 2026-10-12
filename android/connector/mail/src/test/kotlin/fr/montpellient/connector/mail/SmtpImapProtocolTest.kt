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
}
