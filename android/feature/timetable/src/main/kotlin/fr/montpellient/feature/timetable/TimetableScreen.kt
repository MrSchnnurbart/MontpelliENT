package fr.montpellient.feature.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Today

import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.montpellient.core.data.AppContainer
import fr.montpellient.core.designsystem.component.EmptyState
import fr.montpellient.core.designsystem.component.RefreshableContent
import fr.montpellient.core.designsystem.component.StatusChip
import fr.montpellient.core.designsystem.theme.LocalIsDarkTheme
import fr.montpellient.core.designsystem.theme.SubjectColors
import fr.montpellient.core.designsystem.util.DateFormats
import fr.montpellient.core.model.CourseType
import fr.montpellient.core.model.EventStatus
import fr.montpellient.core.model.TimetableEvent
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

@Composable
fun TimetableRoute(container: AppContainer) {
    val vm: TimetableViewModel = viewModel { TimetableViewModel(container) }
    val events by vm.eventsByDay.collectAsStateWithLifecycle()
    val refreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    TimetableScreen(
        today = vm.today,
        windowStart = vm.windowStart,
        windowEnd = vm.windowEnd,
        eventsByDay = events,
        isRefreshing = refreshing,
        onRefresh = vm::refresh,
        onImportIcal = vm::importCustomIcalUrl,
        onDaySeen = vm::markDaySeen,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    today: LocalDate,
    windowStart: LocalDate,
    windowEnd: LocalDate,
    eventsByDay: Map<LocalDate, List<TimetableEvent>>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onImportIcal: suspend (String) -> Result<Unit> = { Result.success(Unit) },
    onDaySeen: (LocalDate) -> Unit = {},
) {

    val pageCount = ChronoUnit.DAYS.between(windowStart, windowEnd).toInt() + 1
    val todayPage = ChronoUnit.DAYS.between(windowStart, today).toInt()
    val pager = rememberPagerState(initialPage = todayPage) { pageCount }
    val scope = rememberCoroutineScope()
    val selectedDate = windowStart.plusDays(pager.currentPage.toLong())
    var detail by remember { mutableStateOf<TimetableEvent?>(null) }

    LaunchedEffect(selectedDate) {
        onDaySeen(selectedDate)
    }

    fun goTo(date: LocalDate) {
        val page = ChronoUnit.DAYS.between(windowStart, date).toInt().coerceIn(0, pageCount - 1)
        scope.launch { pager.animateScrollToPage(page) }
    }

    var showImportDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.timetable_title)) },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Outlined.Link, contentDescription = "Importer lien ADE")
                    }
                    IconButton(onClick = { goTo(today) }) {
                        Icon(Icons.Outlined.Today, contentDescription = stringResource(R.string.timetable_today))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            WeekStrip(
                selected = selectedDate,
                today = today,
                hasEvents = { eventsByDay[it]?.any { e -> !e.isCancelled } == true },
                onSelect = ::goTo,
            )
            HorizontalDivider()
            RefreshableContent(isRefreshing = isRefreshing, onRefresh = onRefresh) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                    val date = windowStart.plusDays(page.toLong())
                    DayAgenda(date = date, events = eventsByDay[date].orEmpty(), onEventClick = { detail = it })
                }
            }
        }
    }

    if (showImportDialog) {
        AdeImportDialog(
            onDismiss = { showImportDialog = false },
            onImport = { url ->
                onImportIcal(url)
            }
        )
    }

    detail?.let { event ->
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            EventDetail(event)
        }
    }
}

@Composable
fun AdeImportDialog(
    onDismiss: () -> Unit,
    onImport: suspend (String) -> Result<Unit>
) {
    var urlInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Importer mon EDT ADE")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Collez ici le lien d'export iCal de votre agenda ADE Campus (trouvable sur l'ENT ou ADE > Options > Exporter mon agenda).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("URL iCal (.ics ou direct_cal.jsp)") },
                    placeholder = { Text("https://ade.umontpellier.fr/...") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    singleLine = false,
                    maxLines = 3
                )
                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (successMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(successMessage!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                if (isLoading) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Téléchargement de l'emploi du temps...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = {
                    if (urlInput.isBlank()) {
                        errorMessage = "Veuillez renseigner une URL valide."
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        val res = onImport(urlInput.trim())
                        isLoading = false
                        if (res.isSuccess) {
                            successMessage = "Emploi du temps importé avec succès !"
                            kotlinx.coroutines.delay(1000)
                            onDismiss()
                        } else {
                            errorMessage = res.exceptionOrNull()?.message ?: "Erreur de téléchargement"
                        }
                    }
                },
                enabled = !isLoading && urlInput.isNotBlank()
            ) {
                Text("Importer")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Annuler")
            }
        }
    )
}


@Composable
private fun WeekStrip(
    selected: LocalDate,
    today: LocalDate,
    hasEvents: (LocalDate) -> Boolean,
    onSelect: (LocalDate) -> Unit,
) {
    val monday = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSelect(monday.minusWeeks(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.timetable_previous_week))
            }
            Text(
                DateFormats.dayFull(selected),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { onSelect(monday.plusWeeks(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.timetable_next_week))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (0 until 7).forEach { i ->
                val day = monday.plusDays(i.toLong())
                val isSelected = day == selected
                val isToday = day == today

                val pillBackground = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surface
                }
                val labelColor = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val numberColor = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    isToday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
                val dotColor = when {
                    !hasEvents(day) -> MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.primary
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(pillBackground)
                        .clickable { onSelect(day) }
                        .padding(vertical = 6.dp),
                ) {
                    Text(DateFormats.dayName(day), style = MaterialTheme.typography.labelMedium, color = labelColor)
                    Text(
                        DateFormats.dayNumber(day),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = numberColor,
                    )
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayAgenda(date: LocalDate, events: List<TimetableEvent>, onEventClick: (TimetableEvent) -> Unit) {
    if (events.isEmpty()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
                EmptyState(
                    icon = Icons.Outlined.EventAvailable,
                    title = stringResource(if (weekend) R.string.timetable_empty_weekend else R.string.timetable_empty),
                    modifier = Modifier.padding(top = 48.dp),
                )
            }
        }
        return
    }
    val now = LocalDateTime.now()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(events, key = { it.id }) { event ->
            EventCard(event, isNow = !now.isBefore(event.start) && now.isBefore(event.end), onClick = { onEventClick(event) })
        }
    }
}

@Composable
fun EventCard(event: TimetableEvent, isNow: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dark = LocalIsDarkTheme.current
    val accent = SubjectColors.accent(event.subject, dark)
    val cancelled = event.isCancelled
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (cancelled) MaterialTheme.colorScheme.surfaceContainerHighest
            else SubjectColors.container(event.subject, dark),
        ),
        border = if (isNow) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(if (cancelled) MaterialTheme.colorScheme.outline else accent))
            Column(Modifier.padding(14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        DateFormats.timeRange(event.start, event.end),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    StatusRow(event, isNow)
                }
                Text(
                    event.subject,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (cancelled) MaterialTheme.colorScheme.onSurfaceVariant else SubjectColors.onContainer(event.subject, dark),
                    textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                )
                val details = listOfNotNull(
                    event.rooms.joinToString().takeIf { it.isNotBlank() },
                    event.teachers.joinToString().takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatusRow(event: TimetableEvent, isNow: Boolean) {
    val cs = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (isNow) StatusChip(stringResource(R.string.timetable_now), cs.primary, cs.onPrimary)
        when (event.status) {
            EventStatus.CANCELLED -> StatusChip(stringResource(R.string.timetable_status_cancelled), cs.errorContainer, cs.onErrorContainer)
            EventStatus.MODIFIED -> StatusChip(stringResource(R.string.timetable_status_modified), cs.tertiaryContainer, cs.onTertiaryContainer)
            EventStatus.ADDED -> StatusChip(stringResource(R.string.timetable_status_added), cs.secondaryContainer, cs.onSecondaryContainer)
            EventStatus.NORMAL -> Unit
        }
        if (event.type != CourseType.CM && event.type != CourseType.OTHER) {
            StatusChip(typeLabel(event.type), cs.surface.copy(alpha = 0.7f), cs.onSurface)
        }
    }
}

@Composable
fun typeLabel(type: CourseType): String = stringResource(
    when (type) {
        CourseType.CM -> R.string.timetable_type_cm
        CourseType.TD -> R.string.timetable_type_td
        CourseType.TP -> R.string.timetable_type_tp
        CourseType.EXAM -> R.string.timetable_type_exam
        CourseType.OTHER -> R.string.timetable_type_other
    },
)

@Composable
private fun EventDetail(event: TimetableEvent) {
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(event.subject, style = MaterialTheme.typography.headlineSmall)
        Text(
            "${DateFormats.dayFull(event.start.toLocalDate())} · ${DateFormats.timeRange(event.start, event.end)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        StatusRow(event, isNow = false)
        DetailLine(Icons.Outlined.Place, event.rooms.joinToString().ifBlank { "—" })
        DetailLine(Icons.Outlined.Person, event.teachers.joinToString().ifBlank { "—" })
        DetailLine(Icons.Outlined.Group, event.groups.joinToString().ifBlank { "—" })
        event.description?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DetailLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
