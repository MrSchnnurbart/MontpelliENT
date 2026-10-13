package fr.montpellient.core.data.real

import fr.montpellient.connector.mail.ImapClient
import fr.montpellient.connector.mail.MailResult
import fr.montpellient.connector.mail.SmtpClient
import fr.montpellient.connector.mail.SmtpResult
import fr.montpellient.core.data.MailRepository
import fr.montpellient.core.model.FolderRole
import fr.montpellient.core.model.MailFolder
import fr.montpellient.core.model.MailMessage
import fr.montpellient.core.security.SecureStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Dépôt réel pour la messagerie étudiante UM.
 * - Récupère les e-mails via IMAPS (`imap.umontpellier.fr:993`) en utilisant les identifiants stockés dans le Keystore.
 * - Envoi de messages via SMTP (`smtp.umontpellier.fr:587`).
 */
class RealMailRepository(
    private val secureStorage: SecureStorage,
    private val imapClient: ImapClient = ImapClient(),
    private val smtpClient: SmtpClient = SmtpClient()
) : MailRepository {

    private val _messages = MutableStateFlow<List<MailMessage>>(emptyList())
    private val _hasMore = MutableStateFlow(false)
    private var totalServerMessages = 0
    private var currentFetchedCount = 0

    override val hasMore: Flow<Boolean> = _hasMore.asStateFlow()

    override val folders: Flow<List<MailFolder>> = _messages.map { msgs ->
        val unread = msgs.count { !it.isRead }
        listOf(
            MailFolder(id = "INBOX", name = "Boîte de réception", role = FolderRole.INBOX, unreadCount = unread, totalCount = msgs.size),
            MailFolder(id = "SENT", name = "Messages envoyés", role = FolderRole.SENT, unreadCount = 0, totalCount = 0),
            MailFolder(id = "TRASH", name = "Corbeille", role = FolderRole.TRASH, unreadCount = 0, totalCount = 0)
        )
    }

    override fun messages(folderId: String): Flow<List<MailMessage>> =
        _messages.map { list -> list.filter { it.folderId == folderId } }

    override fun message(id: String): Flow<MailMessage?> =
        _messages.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun setRead(id: String, read: Boolean) {
        _messages.value = _messages.value.map {
            if (it.id == id) it.copy(isRead = read) else it
        }
    }

    override suspend fun setFlagged(id: String, flagged: Boolean) {
        _messages.value = _messages.value.map {
            if (it.id == id) it.copy(isFlagged = flagged) else it
        }
    }

    override suspend fun loadFullBody(id: String): String? {
        val existing = _messages.value.firstOrNull { it.id == id }
        if (existing != null && existing.bodyText.length > 200 && !existing.bodyText.startsWith("FLAGS")) {
            return existing.bodyText
        }

        val creds = secureStorage.getCredentials() ?: return existing?.bodyText
        val result = imapClient.fetchMessageBody(creds.first, creds.second, id)
        if (result is MailResult.Success && result.data.isNotBlank()) {
            val fullBody = result.data
            _messages.value = _messages.value.map {
                if (it.id == id) it.copy(bodyText = fullBody) else it
            }
            return fullBody
        }
        return existing?.bodyText
    }

    override suspend fun loadMore(): Boolean {
        val creds = secureStorage.getCredentials() ?: return false
        if (currentFetchedCount >= totalServerMessages) {
            _hasMore.value = false
            return false
        }

        val result = imapClient.fetchInboxMessages(
            username = creds.first,
            password = creds.second,
            offsetFromEnd = currentFetchedCount,
            pageSize = 10
        )
        return when (result) {
            is MailResult.Success -> {
                val newMsgs = result.data.messages
                totalServerMessages = result.data.totalMessages
                currentFetchedCount += newMsgs.size
                _messages.value = (_messages.value + newMsgs).distinctBy { it.id }
                val canLoadMore = result.data.lowestFetchedSeq > 1
                _hasMore.value = canLoadMore
                canLoadMore
            }
            is MailResult.Error -> {
                android.util.Log.e("RealMailRepository", "Erreur loadMore IMAP: ${result.message}")
                false
            }
        }
    }

    override suspend fun refresh() {
        val creds = secureStorage.getCredentials()
        if (creds == null) {
            android.util.Log.e("RealMailRepository", "Pas d'identifiants trouvés dans le Keystore pour IMAP")
            return
        }
        android.util.Log.i("RealMailRepository", "Démarrage récupération IMAP pour ${creds.first}...")
        val result = imapClient.fetchInboxMessages(
            username = creds.first,
            password = creds.second,
            offsetFromEnd = 0,
            pageSize = 10
        )
        when (result) {
            is MailResult.Success -> {
                val msgs = result.data.messages
                totalServerMessages = result.data.totalMessages
                currentFetchedCount = msgs.size
                android.util.Log.i("RealMailRepository", "Succès IMAP: ${msgs.size}/$totalServerMessages messages reçus")
                _messages.value = msgs
                _hasMore.value = result.data.lowestFetchedSeq > 1
            }
            is MailResult.Error -> {
                android.util.Log.e("RealMailRepository", "Erreur IMAP: ${result.message}", result.cause)
            }
        }
    }

    suspend fun sendEmail(toEmail: String, subject: String, body: String): SmtpResult {
        val creds = secureStorage.getCredentials() ?: return SmtpResult.Error("Non authentifié.")
        val fromEmail = "${creds.first}@etu.umontpellier.fr"
        return smtpClient.sendMail(creds.first, creds.second, fromEmail, toEmail, subject, body)
    }
}
