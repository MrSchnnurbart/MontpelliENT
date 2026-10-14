package fr.montpellient.feature.mail

import android.content.Context
import android.content.Intent
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.montpellient.core.data.AppContainer
import fr.montpellient.core.designsystem.component.EmptyState
import fr.montpellient.core.designsystem.component.RefreshableContent
import fr.montpellient.core.designsystem.util.DateFormats
import fr.montpellient.core.model.MailAttachment
import fr.montpellient.core.model.MailMessage
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailRoute(container: AppContainer) {
    val messages by container.mail.messages("INBOX").collectAsStateWithLifecycle(emptyList())
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedMail by remember { mutableStateOf<MailMessage?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (messages.isEmpty()) {
            isRefreshing = true
            runCatching { container.mail.refresh() }
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mail_title)) }
            )
        }
    ) { padding ->
        RefreshableContent(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    runCatching { container.mail.refresh() }
                    isRefreshing = false
                }
            },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (messages.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Mail,
                    title = stringResource(R.string.mail_empty)
                )
            } else {
                val hasMore by container.mail.hasMore.collectAsStateWithLifecycle(false)
                var isLoadingMore by remember { mutableStateOf(false) }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        Card(
                            onClick = {
                                selectedMail = msg
                                if (!msg.isRead) {
                                    scope.launch { container.mail.setRead(msg.id, true) }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (msg.isRead) MaterialTheme.colorScheme.surfaceContainerLow
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (msg.isRead) MaterialTheme.colorScheme.surfaceVariant
                                    else MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = msg.from.display.take(1).uppercase(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (msg.isRead) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = msg.from.display,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (msg.isRead) FontWeight.Normal else FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = DateFormats.relativeShort(msg.date),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Text(
                                        text = msg.subject,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (msg.isRead) FontWeight.Medium else FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Text(
                                        text = msg.preview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (msg.attachments.isNotEmpty()) {
                                    Icon(
                                        Icons.Outlined.AttachFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp).padding(start = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (hasMore) {
                        item(key = "load_more_footer") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                isLoadingMore = true
                                                runCatching { container.mail.loadMore() }
                                                isLoadingMore = false
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Charger 10 mails plus anciens")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedMail?.let { mail ->
        var detailedMail by remember(mail.id) { mutableStateOf(mail) }
        var isLoadingBody by remember(mail.id) { mutableStateOf(false) }
        val context = LocalContext.current
        val isDark = isSystemInDarkTheme()

        var hasFetchedFull by remember(mail.id) { mutableStateOf(false) }

        LaunchedEffect(mail.id) {
            container.mail.setRead(mail.id, true)
            if (!hasFetchedFull && (detailedMail.bodyHtml == null || detailedMail.bodyText.startsWith("--"))) {
                isLoadingBody = true
                val fetched = runCatching { container.mail.getFullMessage(mail.id) }.getOrNull()
                if (fetched != null) {
                    detailedMail = fetched
                }
                isLoadingBody = false
                hasFetchedFull = true
            }
        }

        fun handleAttachmentClick(att: MailAttachment) {
            val attData = att.data
            if (attData != null && attData.isNotEmpty()) {
                try {
                    val cacheDir = File(context.cacheDir, "mail_attachments").apply { mkdirs() }
                    val file = File(cacheDir, att.fileName)
                    file.writeBytes(attData)
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, att.mimeType.ifEmpty { "*/*" })
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(intent, "Ouvrir ${att.fileName}"))
                } catch (e: Exception) {
                    Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Téléchargement de la pièce jointe...", Toast.LENGTH_SHORT).show()
            }
        }

        ModalBottomSheet(onDismissRequest = { selectedMail = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = detailedMail.subject,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "De : ${detailedMail.from.display} <${detailedMail.from.email}>",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = DateFormats.dateTime(detailedMail.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()

                if (isLoadingBody) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Chargement du message complet...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Pièces jointes
                if (detailedMail.attachments.isNotEmpty()) {
                    Text(
                        text = "Pièces jointes (${detailedMail.attachments.size}) :",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        detailedMail.attachments.forEach { att ->
                            ElevatedAssistChip(
                                onClick = { handleAttachmentClick(att) },
                                label = { Text("${att.fileName} (${DateFormats.fileSize(att.sizeBytes)})") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            )
                        }
                    }
                    HorizontalDivider()
                }

                // Corps du message : Vue HTML riche via WebView si disponible, sinon texte brut
                val html = detailedMail.bodyHtml
                if (!html.isNullOrBlank()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                settings.javaScriptEnabled = false
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.defaultFontSize = 15
                            }
                        },
                        update = { webView ->
                            val textColor = if (isDark) "#E2E8F0" else "#1E293B"
                            val linkColor = if (isDark) "#60A5FA" else "#00467F"
                            val styledHtml = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                <style>
                                    body {
                                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                                        font-size: 15px;
                                        line-height: 1.5;
                                        color: $textColor;
                                        background-color: transparent;
                                        margin: 0;
                                        padding: 4px;
                                        word-wrap: break-word;
                                    }
                                    a { color: $linkColor; }
                                    img { max-width: 100%; height: auto; border-radius: 6px; }
                                    table { max-width: 100%; border-collapse: collapse; }
                                    th, td { border: 1px solid ${if (isDark) "#4A5568" else "#CBD5E1"}; padding: 6px; }
                                    blockquote { border-left: 3px solid #60A5FA; margin-left: 0; padding-left: 10px; color: ${if (isDark) "#94A3B8" else "#64748B"}; }
                                </style>
                                </head>
                                <body>
                                $html
                                </body>
                                </html>
                            """.trimIndent()
                            webView.loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 250.dp, max = 650.dp)
                    )
                } else {
                    val rawContent = if (detailedMail.bodyText.isNotBlank()) detailedMail.bodyText else detailedMail.preview
                    val cleanText = rawContent
                        .lines()
                        .filterNot { it.startsWith("--") || it.startsWith("Content-", ignoreCase = true) || it.startsWith("Mime-", ignoreCase = true) }
                        .joinToString("\n")
                        .trim()

                    Text(
                        text = if (cleanText.isNotBlank()) cleanText else "Contenu textuel non disponible",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3
                    )
                }
            }
        }
    }
}
