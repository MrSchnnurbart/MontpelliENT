package fr.montpellient.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import fr.montpellient.core.data.appContainer
import fr.montpellient.core.model.CourseType
import fr.montpellient.core.model.TimetableEvent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TimetableGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer
        val today = LocalDate.now()
        val events = container.timetable.events(today, today.plusDays(3)).first()
        val nextClass = events.firstOrNull { it.end.isAfter(java.time.LocalDateTime.now()) }

        provideContent {
            GlanceTheme {
                TimetableWidgetContent(nextClass)
            }
        }
    }

    @Composable
    private fun TimetableWidgetContent(nextClass: TimetableEvent?) {
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .padding(14.dp)
        ) {
            if (nextClass != null) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PROCHAIN COURS",
                            style = TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                        Spacer(GlanceModifier.defaultWeight())
                        Text(
                            text = "${nextClass.start.format(timeFmt)} - ${nextClass.end.format(timeFmt)}",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        )
                    }

                    Spacer(GlanceModifier.height(6.dp))

                    Text(
                        text = nextClass.subject,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        maxLines = 1
                    )

                    Spacer(GlanceModifier.height(4.dp))

                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Text(
                            text = "📍 ${nextClass.rooms.joinToString(", ")}",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        )
                        if (nextClass.teachers.isNotEmpty()) {
                            Text(
                                text = " • ${nextClass.teachers.first()}",
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Aucun cours à venir",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
    }
}

class TimetableGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimetableGlanceWidget()
}
