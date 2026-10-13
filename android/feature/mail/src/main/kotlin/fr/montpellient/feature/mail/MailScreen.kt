package fr.montpellient.feature.mail

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.montpellient.core.data.AppContainer
import fr.montpellient.core.designsystem.component.EmptyState
import fr.montpellient.core.designsystem.component.RefreshableContent
import fr.montpellient.core.designsystem.util.DateFormats
import fr.montpellient.core.model.MailMessage
import kotlinx.coroutines.launch

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
                }
            }
        }
    }

    selectedMail?.let { mail ->
        var fullBodyText by remember(mail.id) { mutableStateOf(mail.bodyText) }
        var isLoadingBody by remember(mail.id) { mutableStateOf(false) }

        LaunchedEffect(mail.id) {
            container.mail.setRead(mail.id, true)
            if (mail.bodyText.length <= 200 || mail.bodyText == mail.preview) {
                isLoadingBody = true
                val fetched = runCatching { container.mail.loadFullBody(mail.id) }.getOrNull()
                if (!fetched.isNullOrBlank()) {
                    fullBodyText = fetched
                }
                isLoadingBody = false
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
                    text = mail.subject,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "De : ${mail.from.display} <${mail.from.email}>",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = DateFormats.dateTime(mail.date),
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

                Text(
                    text = if (fullBodyText.isNotBlank()) fullBodyText else mail.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3
                )

                if (mail.attachments.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Pièces jointes :", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    mail.attachments.forEach { att ->
                        AssistChip(
                            onClick = {},
                            label = { Text("${att.fileName} (${DateFormats.fileSize(att.sizeBytes)})") },
                            leadingIcon = { Icon(Icons.Outlined.AttachFile, contentDescription = null) }
                        )
                    }
                }
            }
        }
    }
}
