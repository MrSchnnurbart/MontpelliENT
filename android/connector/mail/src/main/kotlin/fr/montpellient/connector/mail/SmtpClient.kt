package fr.montpellient.connector.mail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket
import java.util.Base64
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

sealed class SmtpResult {
    object Success : SmtpResult()
    data class Error(val message: String, val cause: Throwable? = null) : SmtpResult()
}

/**
 * Client SMTP léger avec support STARTTLS sur `smtp.umontpellier.fr:587`.
 * Permet l'envoi de courriels étudiants directement depuis l'application avec identifiants UM.
 */
class SmtpClient(
    val host: String = SMTP_HOST,
    val port: Int = SMTP_PORT
) {
    suspend fun sendMail(
        username: String,
        password: String,
        fromEmail: String,
        toEmail: String,
        subject: String,
        body: String
    ): SmtpResult = withContext(Dispatchers.IO) {
        var plainSocket: Socket? = null
        var tlsSocket: SSLSocket? = null
        try {
            // 1. Connexion en clair sur port 587
            plainSocket = Socket(host, port)
            plainSocket.soTimeout = 15000

            var reader = BufferedReader(InputStreamReader(plainSocket.inputStream, Charsets.UTF_8))
            var writer = PrintWriter(OutputStreamWriter(plainSocket.outputStream, Charsets.UTF_8), true)

            val greeting = reader.readLine() ?: return@withContext SmtpResult.Error("Aucune réponse du serveur SMTP")
            if (!greeting.startsWith("220")) {
                return@withContext SmtpResult.Error("Bannière SMTP invalide: $greeting")
            }

            // 2. EHLO initial
            writer.println("EHLO localhost")
            readSmtpResponse(reader)

            // 3. Bascule STARTTLS
            writer.println("STARTTLS")
            val startTlsResp = reader.readLine() ?: return@withContext SmtpResult.Error("Échec STARTTLS")
            if (!startTlsResp.startsWith("220")) {
                return@withContext SmtpResult.Error("Serveur SMTP a refusé STARTTLS: $startTlsResp")
            }

            // 4. Négociation TLS sur le socket existant
            val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            tlsSocket = sslFactory.createSocket(plainSocket, host, port, true) as SSLSocket
            tlsSocket.soTimeout = 15000

            reader = BufferedReader(InputStreamReader(tlsSocket.inputStream, Charsets.UTF_8))
            writer = PrintWriter(OutputStreamWriter(tlsSocket.outputStream, Charsets.UTF_8), true)

            // 5. Nouvel EHLO sous TLS
            writer.println("EHLO localhost")
            readSmtpResponse(reader)

            // 6. Authentification AUTH LOGIN
            writer.println("AUTH LOGIN")
            reader.readLine() // Demande de username en Base64

            val encodedUser = Base64.getEncoder().encodeToString(username.toByteArray())
            writer.println(encodedUser)
            reader.readLine() // Demande de password en Base64

            val encodedPass = Base64.getEncoder().encodeToString(password.toByteArray())
            writer.println(encodedPass)
            val authResp = reader.readLine() ?: return@withContext SmtpResult.Error("Pas de réponse d'authentification SMTP")
            if (!authResp.startsWith("235")) {
                return@withContext SmtpResult.Error("Échec de l'authentification SMTP: $authResp")
            }

            // 7. Enveloppe MAIL FROM & RCPT TO
            writer.println("MAIL FROM:<$fromEmail>")
            val mailFromResp = reader.readLine() ?: return@withContext SmtpResult.Error("Erreur MAIL FROM")
            if (!mailFromResp.startsWith("250")) return@withContext SmtpResult.Error("Refus MAIL FROM: $mailFromResp")

            writer.println("RCPT TO:<$toEmail>")
            val rcptResp = reader.readLine() ?: return@withContext SmtpResult.Error("Erreur RCPT TO")
            if (!rcptResp.startsWith("250")) return@withContext SmtpResult.Error("Refus RCPT TO: $rcptResp")

            // 8. Contenu DATA
            writer.println("DATA")
            val dataResp = reader.readLine() ?: return@withContext SmtpResult.Error("Erreur DATA")
            if (!dataResp.startsWith("354")) return@withContext SmtpResult.Error("Refus DATA: $dataResp")

            writer.println("From: <$fromEmail>")
            writer.println("To: <$toEmail>")
            writer.println("Subject: $subject")
            writer.println("Content-Type: text/plain; charset=UTF-8")
            writer.println()
            writer.println(body)
            writer.println(".") // Fin du message SMTP

            val finalResp = reader.readLine() ?: return@withContext SmtpResult.Error("Erreur validation envoi")
            if (!finalResp.startsWith("250")) {
                return@withContext SmtpResult.Error("Échec envoi mail: $finalResp")
            }

            writer.println("QUIT")
            SmtpResult.Success
        } catch (e: Exception) {
            SmtpResult.Error("Erreur lors de l'envoi du mail: ${e.message}", e)
        } finally {
            runCatching { tlsSocket?.close() }
            runCatching { plainSocket?.close() }
        }
    }

    private fun readSmtpResponse(reader: BufferedReader): String {
        val sb = StringBuilder()
        while (true) {
            val line = reader.readLine() ?: break
            sb.appendLine(line)
            // Ligne finale d'une réponse multi-lignes SMTP (ex: "250 OK" ou "250-..." pour les étapes intermédiaires)
            if (line.length >= 4 && line[3] == ' ') break
        }
        return sb.toString()
    }

    companion object {
        const val SMTP_HOST = "smtp.umontpellier.fr"
        const val SMTP_PORT = 587
    }
}
