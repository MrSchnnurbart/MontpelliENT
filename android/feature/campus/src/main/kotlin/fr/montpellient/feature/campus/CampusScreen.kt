package fr.montpellient.feature.campus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.montpellient.core.data.AppContainer
import fr.montpellient.core.designsystem.component.EmptyState
import fr.montpellient.core.designsystem.component.RefreshableContent
import fr.montpellient.core.designsystem.component.StatusChip
import fr.montpellient.core.designsystem.util.DateFormats
import fr.montpellient.core.model.CampusEvent
import fr.montpellient.core.model.Library
import fr.montpellient.core.model.NewsItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusRoute(container: AppContainer) {
    val libraries by container.campus.libraries.collectAsStateWithLifecycle(emptyList())
    val events by container.campus.events.collectAsStateWithLifecycle(emptyList())
    val news by container.campus.news.collectAsStateWithLifecycle(emptyList())
    var selectedTab by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.campus_title)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.campus_tab_bu)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.campus_tab_events)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(R.string.campus_tab_news)) }
                )
            }

            RefreshableContent(
                isRefreshing = isRefreshing,
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        runCatching { container.campus.refresh() }
                        isRefreshing = false
                    }
                }
            ) {
                when (selectedTab) {
                    0 -> LibrariesList(libraries)
                    1 -> EventsList(events)
                    2 -> NewsList(news)
                }
            }
        }
    }
}

@Composable
private fun LibrariesList(libraries: List<Library>) {
    if (libraries.isEmpty()) {
        EmptyState(icon = Icons.Outlined.LocalLibrary, title = stringResource(R.string.bu_empty))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(libraries, key = { it.id }) { bu ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(bu.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        StatusChip(
                            text = if (bu.isOpenNow) "Ouvert" else "Fermé",
                            container = if (bu.isOpenNow) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            content = if (bu.isOpenNow) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("${bu.campus} · ${bu.todayHours}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    bu.occupancyPercent?.let { occ ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Occupation : $occ%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            LinearProgressIndicator(
                                progress = { occ / 100f },
                                modifier = Modifier.weight(1f).height(6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventsList(events: List<CampusEvent>) {
    if (events.isEmpty()) {
        EmptyState(icon = Icons.Outlined.Event, title = stringResource(R.string.events_empty))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(events, key = { it.id }) { ev ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(ev.organizer, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(ev.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(ev.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${DateFormats.dateTime(ev.start)} · ${ev.location}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun NewsList(news: List<NewsItem>) {
    if (news.isEmpty()) {
        EmptyState(icon = Icons.Outlined.Newspaper, title = stringResource(R.string.news_empty))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(news, key = { it.id }) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(item.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(DateFormats.relativeShort(item.publishedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
