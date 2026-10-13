package fr.montpellient.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
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
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
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
import java.time.format.DateTimeFormatter
import java.util.Locale

class TimetableGlanceWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState<Preferences>(context, PreferencesGlanceStateDefinition, id)
        val dayOffset = prefs[DAY_OFFSET_KEY] ?: 0
        val targetDate = LocalDate.now().plusDays(dayOffset.toLong())

        val container = context.appContainer
        val events = container.timetable.events(targetDate, targetDate).first().sortedBy { it.start }

        provideContent {
            GlanceTheme {
                TimetableWidgetContent(
                    targetDate = targetDate,
                    dayOffset = dayOffset,
                    events = events
                )
            }
        }
    }

    @Composable
    private fun TimetableWidgetContent(
        targetDate: LocalDate,
        dayOffset: Int,
        events: List<TimetableEvent>
    ) {
        val today = LocalDate.now()
        val dateTitle = when {
            targetDate == today -> "Aujourd'hui"
            targetDate == today.plusDays(1) -> "Demain"
            targetDate == today.minusDays(1) -> "Hier"
            else -> targetDate.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH))
                .replaceFirstChar { it.uppercase() }
        }

        val dateSubtitle = targetDate.format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH))

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .padding(12.dp)
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                // Barre de navigation de jour (Précédent | Date | Suivant)
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(12.dp)
                            .background(GlanceTheme.colors.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .clickable(actionRunCallback<ChangeDayAction>(actionParametersOf(ChangeDayAction.DeltaKey to -1))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "◀",
                            style = TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontSize = 12.sp,
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
                            text = if (dayOffset != 0) "$dateSubtitle • (Aujourd'hui)" else dateSubtitle,
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
                            .cornerRadius(12.dp)
                            .background(GlanceTheme.colors.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .clickable(actionRunCallback<ChangeDayAction>(actionParametersOf(ChangeDayAction.DeltaKey to 1))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "▶",
                            style = TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                if (events.isEmpty()) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .padding(top = 16.dp),
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
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(events) { event ->
                            TimetableEventItem(event)
                            Spacer(GlanceModifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TimetableEventItem(event: TimetableEvent) {
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
        val isCancelled = event.isCancelled
        val timeStr = "${event.start.format(timeFmt)} - ${event.end.format(timeFmt)}"

        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(10.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bandeau d'accent vertical gauche
                Box(
                    modifier = GlanceModifier
                        .width(4.dp)
                        .height(38.dp)
                        .cornerRadius(2.dp)
                        .background(
                            if (isCancelled) GlanceTheme.colors.error
                            else GlanceTheme.colors.primary
                        )
                ) {}

                Spacer(GlanceModifier.width(8.dp))

                Column(modifier = GlanceModifier.defaultWeight()) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = timeStr,
                            style = TextStyle(
                                color = if (isCancelled) GlanceTheme.colors.error else GlanceTheme.colors.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
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
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    Spacer(GlanceModifier.height(2.dp))

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
                                color = GlanceTheme.colors.onSurfaceVariant,
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
        updateAppWidgetState(context, glanceId) { prefs ->
            if (reset) {
                prefs[TimetableGlanceWidget.DAY_OFFSET_KEY] = 0
            } else {
                val current = prefs[TimetableGlanceWidget.DAY_OFFSET_KEY] ?: 0
                prefs[TimetableGlanceWidget.DAY_OFFSET_KEY] = current + delta
            }
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
