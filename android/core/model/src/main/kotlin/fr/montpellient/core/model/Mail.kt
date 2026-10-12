package fr.montpellient.core.model

import java.time.LocalDateTime

enum class FolderRole { INBOX, SENT, DRAFTS, TRASH, SPAM, ARCHIVE, OTHER }

data class MailFolder(
    val id: String,
    val name: String,
    val role: FolderRole,
    val unreadCount: Int = 0,
    val totalCount: Int = 0,
)

data class MailAddress(val name: String?, val email: String) {
    val display: String get() = name?.takeIf { it.isNotBlank() } ?: email
}

data class MailAttachment(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
)

data class MailMessage(
    val id: String,
    val folderId: String,
    val from: MailAddress,
    val to: List<MailAddress>,
    val cc: List<MailAddress> = emptyList(),
    val subject: String,
    val preview: String,
    val bodyText: String,
    val date: LocalDateTime,
    val isRead: Boolean,
    val isFlagged: Boolean = false,
    val attachments: List<MailAttachment> = emptyList(),
)
