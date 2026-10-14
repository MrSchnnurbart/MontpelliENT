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

    data class FetchResult(
        val messages: List<MailMessage>,
        val totalMessages: Int,
        val lowestFetchedSeq: Int
    )

    suspend fun fetchInboxMessages(
        username: String,
        password: String,
        offsetFromEnd: Int = 0,
        pageSize: Int = 10
    ): MailResult<FetchResult> = withContext(Dispatchers.IO) {
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

            // Extraire le nombre total de messages dans la boîte: "* <N> EXISTS"
            var totalCount = 0
            for (line in selectResp.lines) {
                val existsMatch = Regex("\\*\\s+([0-9]+)\\s+EXISTS").find(line)
                if (existsMatch != null) {
                    totalCount = existsMatch.groupValues[1].toIntOrNull() ?: 0
                }
            }

            if (totalCount == 0) {
                val logoutTag = nextTag()
                writer.println("$logoutTag LOGOUT")
                readUntilTaggedResponse(reader, logoutTag)
                return@withContext MailResult.Success(FetchResult(emptyList(), 0, 0))
            }

            // Calculer la plage de séquences (1-indexed) de la fin vers le début
            val endSeq = (totalCount - offsetFromEnd).coerceAtLeast(1)
            val startSeq = (endSeq - pageSize + 1).coerceAtLeast(1)

            // 4. Récupération des messages de startSeq à endSeq
            val fetchTag = nextTag()
            writer.println("$fetchTag FETCH $startSeq:$endSeq (UID FLAGS INTERNALDATE ENVELOPE BODY.PEEK[TEXT]<0.200>)")
            val fetchLines = readUntilTaggedResponse(reader, fetchTag)

            val messages = parseFetchResponses(fetchLines.lines)

            // 5. Déconnexion
            val logoutTag = nextTag()
            writer.println("$logoutTag LOGOUT")
            readUntilTaggedResponse(reader, logoutTag)

            MailResult.Success(FetchResult(messages, totalCount, startSeq))
        } catch (e: Exception) {
            MailResult.Error("Erreur lors de la communication IMAP: ${e.message}", e)
        } finally {
            runCatching { socket?.close() }
        }
    }

    data class ParsedMimeMessage(
        val bodyText: String,
        val bodyHtml: String?,
        val attachments: List<fr.montpellient.core.model.MailAttachment>
    )

    /**
     * Récupère le corps textuel intégral d'un message donné via son UID.
     */
    suspend fun fetchMessageBody(
        username: String,
        password: String,
        uid: String
    ): MailResult<String> {
        val full = fetchFullMessageDetails(username, password, uid)
        return when (full) {
            is MailResult.Success -> MailResult.Success(full.data.bodyText)
            is MailResult.Error -> full
        }
    }

    /**
     * Récupère l'intégralité d'un message (HTML, texte brut, et pièces jointes) via son UID.
     */
    suspend fun fetchFullMessageDetails(
        username: String,
        password: String,
        uid: String
    ): MailResult<ParsedMimeMessage> = withContext(Dispatchers.IO) {
        var socket: SSLSocket? = null
        try {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            socket = factory.createSocket(host, port) as SSLSocket
            socket.soTimeout = 20000

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

            // Récupération du message MIME complet (BODY.PEEK[])
            val fetchTag = nextTag()
            writer.println("$fetchTag UID FETCH $uid (BODY.PEEK[])")
            val fetchLines = readUntilTaggedResponse(reader, fetchTag)

            val logoutTag = nextTag()
            writer.println("$logoutTag LOGOUT")
            readUntilTaggedResponse(reader, logoutTag)

            val parsed = parseFullMime(fetchLines.lines)
            MailResult.Success(parsed)
        } catch (e: Exception) {
            MailResult.Error("Erreur lors de la récupération du message complet: ${e.message}", e)
        } finally {
            runCatching { socket?.close() }
        }
    }

    private fun parseFullMime(lines: List<String>): ParsedMimeMessage {
        if (lines.isEmpty()) return ParsedMimeMessage("", null, emptyList())

        // 1. Isoler les lignes contenues dans la réponse FETCH
        val contentLines = mutableListOf<String>()
        var started = false
        for (line in lines) {
            if (!started && line.startsWith("* ") && line.contains("FETCH")) {
                started = true
                continue
            }
            if (started) {
                if (line.startsWith("A") && (line.contains(" OK") || line.contains(" NO") || line.contains(" BAD"))) {
                    break
                }
                contentLines.add(line)
            }
        }

        if (contentLines.isNotEmpty() && contentLines.last().trim() == ")") {
            contentLines.removeAt(contentLines.lastIndex)
        }

        if (contentLines.isEmpty()) {
            return ParsedMimeMessage("", null, emptyList())
        }

        // 2. Détecter un boundary multipart dans les en-têtes
        val headerEndIdx = contentLines.indexOfFirst { it.isBlank() }
        val headerBlock = if (headerEndIdx != -1) contentLines.subList(0, headerEndIdx).joinToString("\n")
                          else contentLines.take(100).joinToString("\n")

        var boundary: String? = null
        val bMatch = Regex("boundary=[\"']?([^\"'\\r\\n;]+)[\"']?", RegexOption.IGNORE_CASE).find(headerBlock)
        if (bMatch != null) {
            boundary = bMatch.groupValues[1].trim()
        }

        if (boundary == null) {
            for (line in contentLines) {
                if (line.startsWith("--") && line.length > 4 && !line.startsWith("---")) {
                    boundary = line.removePrefix("--").trim().removeSuffix("--").trim()
                    break
                }
            }
        }

        var textContent: String? = null
        var htmlContent: String? = null
        val attachments = mutableListOf<fr.montpellient.core.model.MailAttachment>()

        if (boundary != null) {
            val partBoundary = "--$boundary"
            val parts = mutableListOf<List<String>>()
            var currentPart = mutableListOf<String>()
            var inParts = false

            for (line in contentLines) {
                if (line.startsWith(partBoundary)) {
                    if (inParts && currentPart.isNotEmpty()) {
                        parts.add(currentPart)
                        currentPart = mutableListOf()
                    }
                    inParts = true
                    if (line.startsWith("$partBoundary--")) {
                        inParts = false
                    }
                } else if (inParts) {
                    currentPart.add(line)
                }
            }
            if (currentPart.isNotEmpty()) {
                parts.add(currentPart)
            }

            for (part in parts) {
                val headerLines = mutableListOf<String>()
                val bodyLines = mutableListOf<String>()
                var readingHeaders = true
                for (l in part) {
                    if (readingHeaders) {
                        if (l.isBlank()) {
                            readingHeaders = false
                        } else {
                            headerLines.add(l)
                        }
                    } else {
                        bodyLines.add(l)
                    }
                }

                val allHeaders = headerLines.joinToString("\n")
                val contentType = Regex("Content-Type:\\s*([^;\\r\\n]+)", RegexOption.IGNORE_CASE).find(allHeaders)?.groupValues?.get(1)?.trim()?.lowercase() ?: "text/plain"
                val encoding = Regex("Content-Transfer-Encoding:\\s*([^;\\r\\n]+)", RegexOption.IGNORE_CASE).find(allHeaders)?.groupValues?.get(1)?.trim()?.lowercase() ?: ""
                val charsetName = Regex("charset=[\"']?([^\"'\\s;]+)[\"']?", RegexOption.IGNORE_CASE).find(allHeaders)?.groupValues?.get(1) ?: "UTF-8"
                val charset = runCatching { java.nio.charset.Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)

                val filenameMatch = Regex("(?:filename|name)=[\"']?([^\"'\\r\\n;]+)[\"']?", RegexOption.IGNORE_CASE).find(allHeaders)
                val isAttachment = allHeaders.contains("Content-Disposition: attachment", ignoreCase = true) || filenameMatch != null || (!contentType.startsWith("text/") && !contentType.startsWith("multipart/"))

                if (isAttachment) {
                    val rawName = filenameMatch?.groupValues?.get(1) ?: "piece_jointe"
                    val cleanFileName = decodeMimeWords(rawName).trim().trim('"', '\'')
                    val rawPayload = bodyLines.joinToString("\n")
                    val bytes = if (encoding.contains("base64")) {
                        runCatching {
                            val cleanB64 = rawPayload.replace("\n", "").replace("\r", "").replace(" ", "")
                            java.util.Base64.getDecoder().decode(cleanB64)
                        }.getOrNull()
                    } else {
                        rawPayload.toByteArray(charset)
                    }
                    if (bytes != null && bytes.isNotEmpty()) {
                        attachments.add(
                            fr.montpellient.core.model.MailAttachment(
                                id = "att_${attachments.size + 1}",
                                fileName = cleanFileName,
                                mimeType = contentType,
                                sizeBytes = bytes.size.toLong(),
                                data = bytes
                            )
                        )
                    }
                } else if (contentType.contains("text/html")) {
                    val rawPayload = bodyLines.joinToString("\n")
                    val decoded = decodePayload(rawPayload, encoding, charset)
                    if (decoded.isNotBlank()) {
                        htmlContent = decoded
                    }
                } else if (contentType.contains("text/plain")) {
                    val rawPayload = bodyLines.joinToString("\n")
                    val decoded = decodePayload(rawPayload, encoding, charset)
                    if (decoded.isNotBlank()) {
                        textContent = decoded
                    }
                } else if (contentType.contains("multipart/")) {
                    val subParsed = parseFullMime(part)
                    if (textContent == null && subParsed.bodyText.isNotBlank()) textContent = subParsed.bodyText
                    if (htmlContent == null && subParsed.bodyHtml != null) htmlContent = subParsed.bodyHtml
                    attachments.addAll(subParsed.attachments)
                }
            }
        } else {
            val raw = contentLines.joinToString("\n")
            var encoding = ""
            var charset = Charsets.UTF_8
            for (line in contentLines.take(30)) {
                if (line.contains("Content-Transfer-Encoding: base64", ignoreCase = true)) encoding = "base64"
                if (line.contains("Content-Transfer-Encoding: quoted-printable", ignoreCase = true)) encoding = "quoted-printable"
                val csMatch = Regex("charset=[\"']?([^\"'\\s;]+)[\"']?", RegexOption.IGNORE_CASE).find(line)
                if (csMatch != null) {
                    runCatching { charset = java.nio.charset.Charset.forName(csMatch.groupValues[1]) }
                }
            }
            val decoded = decodePayload(raw, encoding, charset)
            if (decoded.contains("<html", ignoreCase = true) || decoded.contains("<body", ignoreCase = true) || decoded.contains("<p>", ignoreCase = true)) {
                htmlContent = decoded
            } else {
                textContent = decoded
            }
        }

        val finalPlain = textContent ?: (htmlContent?.let { cleanHtmlIfPresent(it) } ?: "")
        return ParsedMimeMessage(
            bodyText = finalPlain.trim(),
            bodyHtml = htmlContent,
            attachments = attachments
        )
    }

    private fun decodePayload(payload: String, encoding: String, charset: java.nio.charset.Charset): String {
        return when {
            encoding.contains("base64") -> {
                runCatching {
                    val cleanB64 = payload.replace("\n", "").replace("\r", "").replace(" ", "")
                    val decoded = java.util.Base64.getDecoder().decode(cleanB64)
                    String(decoded, charset)
                }.getOrDefault(payload)
            }
            encoding.contains("quoted-printable") -> {
                decodeQuotedPrintable(payload)
            }
            else -> {
                decodeMimeWords(payload)
            }
        }
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

        fun flushMessage() {
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
                currentId = ""
                subject = "(Sans objet)"
                senderName = "Université de Montpellier"
                senderEmail = "service@umontpellier.fr"
                preview = ""
                date = LocalDateTime.now()
                isRead = false
            }
        }

        for (line in lines) {
            if (line.startsWith("* ") && line.contains("FETCH")) {
                flushMessage()

                val uidMatch = Regex("UID\\s+([0-9]+)").find(line)
                currentId = uidMatch?.groupValues?.get(1) ?: (messages.size + 1).toString()
                isRead = line.contains("\\Seen")

                // Parser INTERNALDATE en premier comme secours fiable
                val internalDateMatch = Regex("INTERNALDATE\\s+\"([^\"]+)\"").find(line)
                if (internalDateMatch != null) {
                    val parsedInternal = parseImapDate(internalDateMatch.groupValues[1])
                    if (parsedInternal != null) {
                        date = parsedInternal
                    }
                }

                // Parser l'ENVELOPE IMAP:
                // Format: ENVELOPE ("date" "subject" ((from-name nil from-mailbox from-host)) ... )
                val envIndex = line.indexOf("ENVELOPE (")
                if (envIndex != -1) {
                    val envContent = line.substring(envIndex + 10)
                    val tokens = extractQuotedOrNils(envContent)
                    if (tokens.size >= 2) {
                        // token 0 = date, token 1 = subject
                        val rawDate = tokens[0]
                        if (rawDate != "NIL" && rawDate.isNotBlank()) {
                            val parsedDate = parseImapDate(rawDate)
                            if (parsedDate != null) {
                                date = parsedDate
                            }
                        }

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
                if (line.trim() == ")") {
                    flushMessage()
                } else {
                    val cleanLine = cleanMessagePreview(line)
                    if (cleanLine.isNotBlank() && preview.isBlank()) {
                        preview = cleanLine.take(140)
                    }
                }
            }
        }

        flushMessage()
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

        if (text.startsWith("--") ||
            text.startsWith("Content-", ignoreCase = true) ||
            text.startsWith("Mime-", ignoreCase = true) ||
            text.startsWith("Received:", ignoreCase = true) ||
            text.startsWith("Return-Path:", ignoreCase = true) ||
            text.startsWith("X-", ignoreCase = true) ||
            text.startsWith("User-Agent:", ignoreCase = true)
        ) {
            return ""
        }

        text = decodeQuotedPrintable(text)
        text = decodeMimeWords(text)
        return text.trim()
    }

    private fun decodeQuotedPrintable(input: String): String {
        return try {
            val bytes = mutableListOf<Byte>()
            var i = 0
            while (i < input.length) {
                if (input[i] == '=') {
                    if (i + 1 < input.length && (input[i + 1] == '\r' || input[i + 1] == '\n')) {
                        // Soft line break: sauter le '=' et le retour à la ligne
                        i++
                        if (i < input.length && input[i] == '\r') i++
                        if (i < input.length && input[i] == '\n') i++
                    } else if (i + 2 < input.length && input.substring(i + 1, i + 3).matches(Regex("[0-9A-Fa-f]{2}"))) {
                        val b = input.substring(i + 1, i + 3).toInt(16).toByte()
                        bytes.add(b)
                        i += 3
                    } else {
                        bytes.add('='.code.toByte())
                        i++
                    }
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

    private fun parseImapDate(raw: String): LocalDateTime? {
        val cleaned = raw.replace(Regex("\\s*\\([^)]*\\)$"), "")
            .replace(Regex("\\s+"), " ")
            .replace("-", " ")
            .trim()

        val locales = listOf(Locale.ENGLISH, Locale.FRENCH, Locale.US)
        val patterns = listOf(
            "d MMM yyyy HH:mm:ss Z",
            "EEE, d MMM yyyy HH:mm:ss Z",
            "d MMMM yyyy HH:mm:ss Z",
            "EEE, d MMMM yyyy HH:mm:ss Z",
            "d MMM yyyy HH:mm:ss z",
            "EEE, d MMM yyyy HH:mm:ss z",
            "d MMM yyyy HH:mm:ss",
            "EEE, d MMM yyyy HH:mm:ss"
        )

        for (loc in locales) {
            for (pat in patterns) {
                val parsed = runCatching {
                    val fmt = DateTimeFormatter.ofPattern(pat, loc)
                    val zdt = ZonedDateTime.parse(cleaned, fmt)
                    zdt.withZoneSameInstant(java.time.ZoneId.of("Europe/Paris")).toLocalDateTime()
                }.getOrNull() ?: runCatching {
                    val fmt = DateTimeFormatter.ofPattern(pat, loc)
                    LocalDateTime.parse(cleaned, fmt)
                }.getOrNull()

                if (parsed != null) return parsed
            }
        }
        return null
    }

    companion object {
        const val IMAP_HOST = "imap.umontpellier.fr"
        const val IMAP_PORT = 993
    }
}
