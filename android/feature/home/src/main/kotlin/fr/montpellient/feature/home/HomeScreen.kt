package fr.montpellient.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.montpellient.core.data.AppContainer
import fr.montpellient.core.designsystem.component.DashboardCard
import fr.montpellient.core.designsystem.component.RefreshableContent
import fr.montpellient.core.designsystem.util.DateFormats
import fr.montpellient.core.model.TopLevelDestination
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

import androidx.compose.material.icons.outlined.Search
import fr.montpellient.core.data.search.SearchResultItem
import fr.montpellient.core.data.search.SearchCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRoute(
    container: AppContainer,
    onNavigateTo: (TopLevelDestination) -> Unit
) {
    val profile by container.profile.profile.collectAsStateWithLifecycle(null)
    val today = remember { LocalDate.now() }
    val now = remember { LocalDateTime.now() }
    val events by container.timetable.events(today, today).collectAsStateWithLifecycle(emptyList())
    val assignments by container.courses.assignments.collectAsStateWithLifecycle(emptyList())
    val messages by container.mail.messages("INBOX").collectAsStateWithLifecycle(emptyList())
    val restaurants by container.crous.restaurants.collectAsStateWithLifecycle(emptyList())
    val campusEvents by container.campus.events.collectAsStateWithLifecycle(emptyList())

    val nextEvent = remember(events, now) {
        events.firstOrNull { it.end.isAfter(now) && !it.isCancelled }
    }
    val unreadMails = remember(messages) {
        messages.filter { !it.isRead }
    }
    val favRestaurant = remember(restaurants) {
        restaurants.firstOrNull { it.isFavourite } ?: restaurants.firstOrNull()
    }

    var isRefreshing by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.home_greeting, profile?.displayName ?: ""),
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { showSearchDialog = true }) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.home_search_title))
                    }
                }
            )
        }
    ) { padding ->

        RefreshableContent(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    runCatching { container.refreshAll() }
                    isRefreshing = false
                }
            },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Prochain cours
                item {
                    DashboardCard(
                        title = stringResource(R.string.home_card_next_course),
                        icon = Icons.Outlined.Schedule,
                        actionLabel = stringResource(R.string.home_see_all),
                        onAction = { onNavigateTo(TopLevelDestination.TIMETABLE) }
                    ) {
                        if (nextEvent != null) {
                            Column {
                                Text(
                                    DateFormats.timeRange(nextEvent.start, nextEvent.end),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    nextEvent.subject,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    nextEvent.rooms.joinToString(", "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                stringResource(R.string.home_no_courses),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 2. Devoirs urgents
                item {
                    val pendingAssignments = assignments.take(2)
                    DashboardCard(
                        title = stringResource(R.string.home_card_assignments),
                        icon = Icons.Outlined.Assignment,
                        actionLabel = stringResource(R.string.home_see_all),
                        onAction = { onNavigateTo(TopLevelDestination.COURSES) }
                    ) {
                        if (pendingAssignments.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                pendingAssignments.forEach { assign ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(assign.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            Text(assign.courseName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        assign.dueDate?.let {
                                            Text(
                                                DateFormats.countdown(it),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                stringResource(R.string.home_no_assignments),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 3. E-mails non lus
                item {
                    DashboardCard(
                        title = stringResource(R.string.home_card_mail),
                        icon = Icons.Outlined.Mail,
                        actionLabel = stringResource(R.string.home_see_all),
                        onAction = { onNavigateTo(TopLevelDestination.MAIL) }
                    ) {
                        Text(
                            "${unreadMails.size} message(s) non lu(s)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        unreadMails.firstOrNull()?.let { first ->
                            Text(
                                "Dernier : \"${first.subject}\" (${first.from.display})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 4. CROUS favori
                item {
                    DashboardCard(
                        title = stringResource(R.string.home_card_crous),
                        icon = Icons.Outlined.Restaurant,
                        actionLabel = stringResource(R.string.home_see_all),
                        onAction = { onNavigateTo(TopLevelDestination.CROUS) }
                    ) {
                        favRestaurant?.let { r ->
                            Text(r.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "${r.openingHours} · " + if (r.isOpenNow) "Ouvert" else "Fermé",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 5. Événements assos
                item {
                    DashboardCard(
                        title = stringResource(R.string.home_card_events),
                        icon = Icons.Outlined.Event,
                        actionLabel = stringResource(R.string.home_see_all),
                        onAction = { onNavigateTo(TopLevelDestination.CAMPUS) }
                    ) {
                        campusEvents.firstOrNull()?.let { ev ->
                            Text(ev.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("${ev.organizer} · ${DateFormats.dateTime(ev.start)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                }
            }
        }
    }

    if (showSearchDialog) {
        GlobalSearchModal(
            container = container,

            onDismiss = { showSearchDialog = false },
            onNavigateTo = { dest ->
                showSearchDialog = false
                onNavigateTo(dest)
            }
        )
    }
}

@Composable
private fun GlobalSearchModal(
    container: AppContainer,
    onDismiss: () -> Unit,
    onNavigateTo: (TopLevelDestination) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_search_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { newQ ->
                        query = newQ
                        scope.launch {
                            results = container.search.search(newQ)
                        }
                    },
                    label = { Text(stringResource(R.string.home_search_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) }
                )

                if (results.isEmpty() && query.length >= 2) {
                    Text(
                        stringResource(R.string.home_search_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(results.size, key = { results[it].id }) { idx ->
                            val item = results[idx]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDismiss()
                                        when (item.targetTabHint) {
                                            "COURSES" -> onNavigateTo(TopLevelDestination.COURSES)
                                            "TIMETABLE" -> onNavigateTo(TopLevelDestination.TIMETABLE)
                                            "GRADES" -> onNavigateTo(TopLevelDestination.GRADES)
                                            "MAIL" -> onNavigateTo(TopLevelDestination.MAIL)
                                            "CROUS" -> onNavigateTo(TopLevelDestination.CROUS)
                                            else -> Unit
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        }
    )
}

