package fr.montpellient.connector.mail

import fr.montpellient.core.model.FolderRole
import fr.montpellient.core.model.MailAddress
import fr.montpellient.core.model.MailFolder
import fr.montpellient.core.model.MailMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

sealed class MailResult<out T> {
    data class Success<T>(val data: T) : MailResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : MailResult<Nothing>()
}

/**
 * Client léger et natif IMAP sur SSL (`imap.umontpellier.fr:993`) sans dépendance lourde externe.
 *
 * Conçu pour :
 * - Lister les dossiers étudiants (INBOX, Envoyés, Corbeille...)
 * - Récupérer les en-têtes (expéditeur, sujet, date, lu/non-lu)
 * - Télécharger le corps du message (TEXT / ENVELOPE)
 * - Déconnexion propre (LOGOUT)
 */
class ImapClient(
    val host: String = IMAP_HOST,
    val port: Int = IMAP_PORT
) {
    private var tagCounter = 1

    private fun nextTag(): String = "A${tagCounter++}"

    suspend fun fetchInboxMessages(
        username: String,
        password: String,
        limit: Int = 20
    ): MailResult<List<MailMessage>> = withContext(Dispatchers.IO) {
        var socket: SSLSocket? = null
        try {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            socket = factory.createSocket(host, port) as SSLSocket
            socket.soTimeout = 15000

            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
            val writer = PrintWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8), true)

            // 1. Lire la bannière d'accueil du serveur IMAP (* OK ...)
            val greeting = reader.readLine() ?: return@withContext MailResult.Error("Connexion fermée par le serveur IMAP.")
            if (!greeting.startsWith("* OK")) {
                return@withContext MailResult.Error("Bannière IMAP invalide: $greeting")
            }

            // 2. Authentification (LOGIN)
            val loginTag = nextTag()
            writer.println("$loginTag LOGIN $username \"$password\"")
            val loginResp = readUntilTaggedResponse(reader, loginTag)
            if (!loginResp.statusOk) {
                return@withContext MailResult.Error("Échec d'authentification IMAP: ${loginResp.message}")
            }

            // 3. Sélection du dossier INBOX (SELECT INBOX)
            val selectTag = nextTag()
            writer.println("$selectTag SELECT INBOX")
            val selectResp = readUntilTaggedResponse(reader, selectTag)
            if (!selectResp.statusOk) {
                return@withContext MailResult.Error("Impossible d'ouvrir INBOX: ${selectResp.message}")
            }

            // 4. Récupération du nombre de messages récents
            val fetchTag = nextTag()
            // On demande les messages récents (1:limit ou 1:*)
            writer.println("$fetchTag FETCH 1:$limit (UID FLAGS ENVELOPE BODY.PEEK[TEXT]<0.200>)")
            val fetchLines = readUntilTaggedResponse(reader, fetchTag)

            val messages = parseFetchResponses(fetchLines.lines)

            // 5. Déconnexion
            val logoutTag = nextTag()
            writer.println("$logoutTag LOGOUT")
            readUntilTaggedResponse(reader, logoutTag)

            MailResult.Success(messages)
        } catch (e: Exception) {
            MailResult.Error("Erreur lors de la communication IMAP: ${e.message}", e)
        } finally {
            runCatching { socket?.close() }
        }
    }

    private data class TaggedResponse(val statusOk: Boolean, val message: String, val lines: List<String>)

    private fun readUntilTaggedResponse(reader: BufferedReader, tag: String): TaggedResponse {
        val lines = mutableListOf<String>()
        while (true) {
            val line = reader.readLine() ?: break
            lines.add(line)
            if (line.startsWith("$tag OK")) {
                return TaggedResponse(true, line.substringAfter("$tag OK").trim(), lines)
            } else if (line.startsWith("$tag NO") || line.startsWith("$tag BAD")) {
                return TaggedResponse(false, line.substringAfter(tag).trim(), lines)
            }
        }
        return TaggedResponse(false, "Réponse inattendue du serveur IMAP", lines)
    }

    private fun parseFetchResponses(lines: List<String>): List<MailMessage> {
        val messages = mutableListOf<MailMessage>()
        var currentId = ""
        var subject = "(Sans objet)"
        var senderName = ""
        var senderEmail = "service@umontpellier.fr"
        var isRead = false
        var date = LocalDateTime.now()
        var preview = ""

        for (line in lines) {
            if (line.startsWith("* ") && line.contains("FETCH")) {
                val uidMatch = Regex("UID\\s+([0-9]+)").find(line)
                currentId = uidMatch?.groupValues?.get(1) ?: (messages.size + 1).toString()
                isRead = line.contains("\\Seen")

                // Extraction simple de l'ENVELOPE si présent
                val subjMatch = Regex("\"([^\"]+)\"").findAll(line).map { it.groupValues[1] }.toList()
                if (subjMatch.isNotEmpty()) {
                    subject = subjMatch.firstOrNull() ?: "(Sans objet)"
                }
            } else if (!line.startsWith("A") && line.isNotBlank()) {
                preview = line.take(120).trim()
            }

            if (currentId.isNotBlank() && line.contains(")")) {
                messages.add(
                    MailMessage(
                        id = currentId,
                        folderId = "INBOX",
                        from = MailAddress(name = senderName.ifEmpty { "Université de Montpellier" }, email = senderEmail),
                        to = listOf(MailAddress(name = null, email = "etudiant@etu.umontpellier.fr")),
                        subject = subject,
                        preview = preview.ifEmpty { "Nouveau message universitaire" },
                        bodyText = preview,
                        date = date,
                        isRead = isRead
                    )
                )
                currentId = ""
            }
        }
        return messages
    }

    companion object {
        const val IMAP_HOST = "imap.umontpellier.fr"
        const val IMAP_PORT = 993
    }
}
