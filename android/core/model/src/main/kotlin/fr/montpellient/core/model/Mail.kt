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
    val data: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MailAttachment
        if (id != other.id) return false
        if (fileName != other.fileName) return false
        if (mimeType != other.mimeType) return false
        if (sizeBytes != other.sizeBytes) return false
        if (data != null) {
            if (other.data == null) return false
            if (!data.contentEquals(other.data)) return false
        } else if (other.data != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + (data?.contentHashCode() ?: 0)
        return result
    }
}

data class MailMessage(
    val id: String,
    val folderId: String,
    val from: MailAddress,
    val to: List<MailAddress>,
    val cc: List<MailAddress> = emptyList(),
    val subject: String,
    val preview: String,
    val bodyText: String,
    val bodyHtml: String? = null,
    val date: LocalDateTime,
    val isRead: Boolean,
    val isFlagged: Boolean = false,
    val attachments: List<MailAttachment> = emptyList(),
)
