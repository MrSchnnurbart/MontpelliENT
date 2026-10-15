package fr.montpellient.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import fr.montpellient.core.data.appContainer
import fr.montpellient.core.model.CourseType
import fr.montpellient.core.model.TimetableEvent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class TimetableGlanceWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer
        val today = LocalDate.now()
        // Charger une fenêtre de 3 semaines pour permettre la navigation fluide entre les jours
        val windowStart = today.minusDays(7)
        val windowEnd = today.plusDays(14)
        val allEvents = container.timetable.events(windowStart, windowEnd).first()
        val eventsByDay = allEvents.groupBy { it.start.toLocalDate() }

        provideContent {
            GlanceTheme {
                TimetableWidgetContent(
                    today = today,
                    eventsByDay = eventsByDay
                )
            }
        }
    }

    @Composable
    private fun TimetableWidgetContent(
        today: LocalDate,
        eventsByDay: Map<LocalDate, List<TimetableEvent>>
    ) {
        val dayOffset = currentState(DAY_OFFSET_KEY) ?: 0
        android.util.Log.d("TimetableWidget", "Widget recomposed: dayOffset=$dayOffset")
        val targetDate = today.plusDays(dayOffset.toLong())
        val events = eventsByDay[targetDate].orEmpty().sortedBy { it.start }
        val now = LocalDateTime.now()

        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
        val currentHourStr = now.format(timeFmt)

        val dateTitle = when {
            targetDate == today -> "Aujourd'hui"
            targetDate == today.plusDays(1) -> "Demain"
            targetDate == today.minusDays(1) -> "Hier"
            else -> targetDate.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH))
                .replaceFirstChar { it.uppercase() }
        }

        val baseSubtitle = targetDate.format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH))
        val dateSubtitle = if (targetDate == today) "$baseSubtitle • Il est $currentHourStr" else baseSubtitle

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(18.dp)
                .padding(12.dp)
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                // Barre de navigation (◀ | Date | ▶)
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = GlanceModifier
                            .size(42.dp)
                            .cornerRadius(21.dp)
                            .background(GlanceTheme.colors.surfaceVariant)
                            .clickable(actionRunCallback<ChangeDayAction>(actionParametersOf(ChangeDayAction.DeltaKey to -1))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "◀",
                            style = TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .clickable(actionRunCallback<ChangeDayAction>(actionParametersOf(ChangeDayAction.ResetKey to true))),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = dateTitle,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = if (dayOffset != 0) "$dateSubtitle • (Retour)" else dateSubtitle,
                            style = TextStyle(
                                color = if (dayOffset != 0) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 10.sp,
                                fontWeight = if (dayOffset != 0) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center
                            ),
                            maxLines = 1
                        )
                    }

                    Box(
                        modifier = GlanceModifier
                            .size(42.dp)
                            .cornerRadius(21.dp)
                            .background(GlanceTheme.colors.surfaceVariant)
                            .clickable(actionRunCallback<ChangeDayAction>(actionParametersOf(ChangeDayAction.DeltaKey to 1))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "▶",
                            style = TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                if (events.isEmpty()) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .padding(top = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aucun cours prévu ce jour-là 🎉",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                } else {
                    val activeEvent = if (targetDate == today) {
                        events.firstOrNull { !it.isCancelled && !now.isBefore(it.start) && now.isBefore(it.end) }
                    } else null
                    val nextUpcomingEvent = if (targetDate == today && activeEvent == null) {
                        events.firstOrNull { !it.isCancelled && now.isBefore(it.start) }
                    } else null

                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(events) { event ->
                            val isNow = event == activeEvent
                            val isNext = event == nextUpcomingEvent
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            ) {
                                TimetableEventItem(event, isNow, isNext)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TimetableEventItem(event: TimetableEvent, isNow: Boolean, isNext: Boolean = false) {
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
        val isCancelled = event.isCancelled
        val timeStr = "${event.start.format(timeFmt)} - ${event.end.format(timeFmt)}"

        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(12.dp)
                .background(
                    when {
                        isNow -> GlanceTheme.colors.primaryContainer
                        isNext -> GlanceTheme.colors.surfaceVariant
                        else -> GlanceTheme.colors.surfaceVariant
                    }
                )
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Indicateur vertical accent
                Box(
                    modifier = GlanceModifier
                        .width(if (isNow || isNext) 5.dp else 4.dp)
                        .height(40.dp)
                        .cornerRadius(2.dp)
                        .background(
                            when {
                                isCancelled -> GlanceTheme.colors.error
                                isNow -> GlanceTheme.colors.primary
                                isNext -> GlanceTheme.colors.primary
                                else -> GlanceTheme.colors.outline
                            }
                        )
                ) {}

                Spacer(GlanceModifier.width(10.dp))

                Column(modifier = GlanceModifier.defaultWeight()) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isNow) {
                            Box(
                                modifier = GlanceModifier
                                    .cornerRadius(6.dp)
                                    .background(GlanceTheme.colors.primary)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "● $timeStr (EN COURS)",
                                    style = TextStyle(
                                        color = GlanceTheme.colors.onPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        } else if (isNext) {
                            Box(
                                modifier = GlanceModifier
                                    .cornerRadius(6.dp)
                                    .background(GlanceTheme.colors.surface)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "▶ $timeStr (À SUIVRE)",
                                    style = TextStyle(
                                        color = GlanceTheme.colors.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        } else {
                            Text(
                                text = timeStr,
                                style = TextStyle(
                                    color = if (isCancelled) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }

                        if (isCancelled) {
                            Spacer(GlanceModifier.width(6.dp))
                            Text(
                                text = "[ANNULÉ]",
                                style = TextStyle(
                                    color = GlanceTheme.colors.error,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        } else if (event.type != CourseType.CM && event.type != CourseType.OTHER) {
                            Spacer(GlanceModifier.width(6.dp))
                            Text(
                                text = "[${event.type.name}]",
                                style = TextStyle(
                                    color = if (isNow) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    Spacer(GlanceModifier.height(3.dp))

                    Text(
                        text = event.subject,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        maxLines = 1
                    )

                    val details = listOfNotNull(
                        event.rooms.joinToString(", ").takeIf { it.isNotBlank() }?.let { "📍 $it" },
                        event.teachers.firstOrNull()?.let { "👤 $it" }
                    ).joinToString("  ")

                    if (details.isNotBlank()) {
                        Spacer(GlanceModifier.height(2.dp))
                        Text(
                            text = details,
                            style = TextStyle(
                                color = if (isNow) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 11.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    companion object {
        val DAY_OFFSET_KEY = intPreferencesKey("day_offset")
    }
}

class ChangeDayAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val delta = parameters[DeltaKey] ?: 0
        val reset = parameters[ResetKey] ?: false
        android.util.Log.d("ChangeDayAction", "onAction called: delta=$delta, reset=$reset, glanceId=$glanceId")
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val mutable = prefs.toMutablePreferences()
            if (reset) {
                mutable[TimetableGlanceWidget.DAY_OFFSET_KEY] = 0
            } else {
                val current = prefs[TimetableGlanceWidget.DAY_OFFSET_KEY] ?: 0
                mutable[TimetableGlanceWidget.DAY_OFFSET_KEY] = current + delta
            }
            android.util.Log.d("ChangeDayAction", "new day_offset=${mutable[TimetableGlanceWidget.DAY_OFFSET_KEY]}")
            mutable
        }
        TimetableGlanceWidget().update(context, glanceId)
    }

    companion object {
        val DeltaKey = ActionParameters.Key<Int>("delta")
        val ResetKey = ActionParameters.Key<Boolean>("reset")
    }
}

class TimetableGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimetableGlanceWidget()
}
