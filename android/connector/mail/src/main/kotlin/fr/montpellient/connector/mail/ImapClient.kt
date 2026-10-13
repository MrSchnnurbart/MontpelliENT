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

    /**
     * Récupère le corps textuel intégral d'un message donné via son UID.
     */
    suspend fun fetchMessageBody(
        username: String,
        password: String,
        uid: String
    ): MailResult<String> = withContext(Dispatchers.IO) {
        var socket: SSLSocket? = null
        try {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            socket = factory.createSocket(host, port) as SSLSocket
            socket.soTimeout = 15000

            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
            val writer = PrintWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8), true)

            val greeting = reader.readLine() ?: return@withContext MailResult.Error("Connexion fermée")
            if (!greeting.startsWith("* OK")) return@withContext MailResult.Error("Bannière IMAP invalide")

            val loginTag = nextTag()
            writer.println("$loginTag LOGIN $username \"$password\"")
            val loginResp = readUntilTaggedResponse(reader, loginTag)
            if (!loginResp.statusOk) return@withContext MailResult.Error("Échec login IMAP")

            val selectTag = nextTag()
            writer.println("$selectTag SELECT INBOX")
            val selectResp = readUntilTaggedResponse(reader, selectTag)
            if (!selectResp.statusOk) return@withContext MailResult.Error("Échec SELECT INBOX")

            // Récupération du corps textuel complet (BODY.PEEK[TEXT])
            val fetchTag = nextTag()
            writer.println("$fetchTag UID FETCH $uid (BODY.PEEK[TEXT])")
            val fetchLines = readUntilTaggedResponse(reader, fetchTag)

            val logoutTag = nextTag()
            writer.println("$logoutTag LOGOUT")
            readUntilTaggedResponse(reader, logoutTag)

            val fullText = parseFullBody(fetchLines.lines)
            MailResult.Success(fullText)
        } catch (e: Exception) {
            MailResult.Error("Erreur lors de la récupération du corps du message: ${e.message}", e)
        } finally {
            runCatching { socket?.close() }
        }
    }

    private fun parseFullBody(lines: List<String>): String {
        if (lines.isEmpty()) return ""
        val bodyBuilder = java.lang.StringBuilder()
        var insideBody = false
        var isQuotedPrintable = false
        var isBase64 = false
        var charset = Charsets.UTF_8

        for (line in lines) {
            if (line.startsWith("* ") && line.contains("FETCH")) {
                insideBody = true
                continue
            }
            if (insideBody) {
                if (line.startsWith("A") && (line.contains(" OK") || line.contains(" NO") || line.contains(" BAD"))) {
                    break
                }
                if (line == ")" || line.trim() == ")") {
                    break
                }

                // Détecter Content-Transfer-Encoding ou Content-Type dans le corps si headers MIME multipart
                if (line.contains("Content-Transfer-Encoding: base64", ignoreCase = true)) {
                    isBase64 = true
                    continue
                }
                if (line.contains("Content-Transfer-Encoding: quoted-printable", ignoreCase = true)) {
                    isQuotedPrintable = true
                    continue
                }
                if (line.contains("charset=", ignoreCase = true)) {
                    val csMatch = Regex("charset=[\"']?([^\"'\\s;]+)[\"']?", RegexOption.IGNORE_CASE).find(line)
                    if (csMatch != null) {
                        runCatching { charset = java.nio.charset.Charset.forName(csMatch.groupValues[1]) }
                    }
                    continue
                }
                if (line.startsWith("--") && line.contains("boundary", ignoreCase = true)) {
                    continue
                }
                if (line.startsWith("Content-Type:", ignoreCase = true)) {
                    continue
                }

                bodyBuilder.append(line).append("\n")
            }
        }

        var text = bodyBuilder.toString().trim()
        if (isBase64) {
            text = runCatching {
                val cleanB64 = text.replace("\n", "").replace("\r", "").replace(" ", "")
                val decoded = java.util.Base64.getDecoder().decode(cleanB64)
                String(decoded, charset)
            }.getOrDefault(text)
        } else if (isQuotedPrintable) {
            text = decodeQuotedPrintable(text)
        } else {
            text = decodeQuotedPrintable(text)
        }

        text = decodeMimeWords(text)
        return cleanHtmlIfPresent(text)
    }

    private fun cleanHtmlIfPresent(raw: String): String {
        if (!raw.contains("<html", ignoreCase = true) && !raw.contains("<body", ignoreCase = true) && !raw.contains("<div", ignoreCase = true) && !raw.contains("<p>", ignoreCase = true)) {
            return raw
        }
        return raw
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("</div>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
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
        var senderName = "Université de Montpellier"
        var senderEmail = "service@umontpellier.fr"
        var isRead = false
        var date = LocalDateTime.now()
        var preview = ""

        for (line in lines) {
            if (line.startsWith("* ") && line.contains("FETCH")) {
                val uidMatch = Regex("UID\\s+([0-9]+)").find(line)
                currentId = uidMatch?.groupValues?.get(1) ?: (messages.size + 1).toString()
                isRead = line.contains("\\Seen")

                // Parser l'ENVELOPE IMAP:
                // Format: ENVELOPE ("date" "subject" ((from-name nil from-mailbox from-host)) ... )
                val envIndex = line.indexOf("ENVELOPE (")
                if (envIndex != -1) {
                    val envContent = line.substring(envIndex + 10)
                    val tokens = extractQuotedOrNils(envContent)
                    if (tokens.size >= 2) {
                        // token 0 = date, token 1 = subject
                        val rawSubject = tokens[1]
                        if (rawSubject != "NIL" && rawSubject.isNotBlank()) {
                            subject = decodeMimeWords(rawSubject)
                        }

                        // Extraction de l'expéditeur dans les tokens suivants ou regex
                        val fromMatch = Regex("\\(\\(\"([^\"]+)\"\\s+NIL\\s+\"([^\"]+)\"\\s+\"([^\"]+)\"\\)\\)").find(line)
                        if (fromMatch != null) {
                            senderName = decodeMimeWords(fromMatch.groupValues[1])
                            senderEmail = "${fromMatch.groupValues[2]}@${fromMatch.groupValues[3]}"
                        } else {
                            val simpleFrom = Regex("\\(\\(NIL\\s+NIL\\s+\"([^\"]+)\"\\s+\"([^\"]+)\"\\)\\)").find(line)
                            if (simpleFrom != null) {
                                senderEmail = "${simpleFrom.groupValues[1]}@${simpleFrom.groupValues[2]}"
                                senderName = senderEmail.substringBefore("@")
                            }
                        }
                    }
                } else {
                    // Fallback regex
                    val subjMatch = Regex("\"([^\"]+)\"").findAll(line).map { it.groupValues[1] }.toList()
                    if (subjMatch.size >= 2) {
                        subject = decodeMimeWords(subjMatch[1])
                    }
                }
            } else if (!line.startsWith("A") && line.isNotBlank()) {
                val cleanLine = cleanMessagePreview(line)
                if (cleanLine.isNotBlank() && preview.isBlank()) {
                    preview = cleanLine.take(140)
                }
            }

            // Clôture du message s'il s'agit d'une ligne de fin FETCH ou s'il y a une parenthèse fermante
            if (currentId.isNotBlank() && (line.trim().endsWith(")") || line.trim() == ")")) {
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
                subject = "(Sans objet)"
                senderName = "Université de Montpellier"
                senderEmail = "service@umontpellier.fr"
                preview = ""
            }
        }

        // Si le dernier message n'a pas été clôturé par une parenthèse isolée
        if (currentId.isNotBlank()) {
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
        }

        return messages.reversed()
    }

    private fun extractQuotedOrNils(text: String): List<String> {
        val list = mutableListOf<String>()
        var i = 0
        while (i < text.length && list.size < 5) {
            when (text[i]) {
                '"' -> {
                    val end = text.indexOf('"', i + 1)
                    if (end != -1) {
                        list.add(text.substring(i + 1, end))
                        i = end + 1
                    } else {
                        i++
                    }
                }
                'N' -> {
                    if (text.startsWith("NIL", i)) {
                        list.add("NIL")
                        i += 3
                    } else {
                        i++
                    }
                }
                else -> i++
            }
        }
        return list
    }

    private fun cleanMessagePreview(raw: String): String {
        var text = raw.replace(Regex("FLAGS\\s*\\([^)]*\\)"), "")
            .replace(Regex("BODY\\[[^\\]]*\\]"), "")
            .replace(Regex("\\{[0-9]+\\}"), "")
            .trim()

        text = decodeQuotedPrintable(text)
        text = decodeMimeWords(text)
        return text.trim()
    }

    private fun decodeQuotedPrintable(input: String): String {
        return try {
            val bytes = mutableListOf<Byte>()
            var i = 0
            while (i < input.length) {
                if (input[i] == '=' && i + 2 < input.length && input.substring(i + 1, i + 3).matches(Regex("[0-9A-Fa-f]{2}"))) {
                    val b = input.substring(i + 1, i + 3).toInt(16).toByte()
                    bytes.add(b)
                    i += 3
                } else {
                    bytes.add(input[i].code.toByte())
                    i++
                }
            }
            String(bytes.toByteArray(), Charsets.UTF_8)
        } catch (_: Exception) {
            input
        }
    }

    private fun decodeMimeWords(input: String): String {
        val regex = Regex("=\\?([^?]+)\\?([BbQq])\\?([^?]+)\\?=")
        return regex.replace(input) { match ->
            val charsetName = match.groupValues[1]
            val encoding = match.groupValues[2].uppercase()
            val encodedData = match.groupValues[3]

            try {
                val charset = runCatching { java.nio.charset.Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
                if (encoding == "B") {
                    val decodedBytes = java.util.Base64.getDecoder().decode(encodedData)
                    String(decodedBytes, charset)
                } else if (encoding == "Q") {
                    val qpString = encodedData.replace('_', ' ')
                    decodeQuotedPrintable(qpString)
                } else {
                    match.value
                }
            } catch (_: Exception) {
                match.value
            }
        }
    }

    companion object {
        const val IMAP_HOST = "imap.umontpellier.fr"
        const val IMAP_PORT = 993
    }
}
