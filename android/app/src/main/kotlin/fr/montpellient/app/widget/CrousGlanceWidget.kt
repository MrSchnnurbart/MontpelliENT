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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import fr.montpellient.core.data.appContainer
import fr.montpellient.core.model.AffluenceLevel
import fr.montpellient.core.model.Restaurant
import kotlinx.coroutines.flow.first

class CrousGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer
        val restaurants = container.crous.restaurants.first()
        val fav = restaurants.firstOrNull { it.isFavourite } ?: restaurants.firstOrNull()
        val affluence = fav?.let { container.crous.affluence(it.id).first() }
        val menus = fav?.let { container.crous.menus(it.id).first() } ?: emptyList()
        val todayMenu = menus.firstOrNull()

        provideContent {
            GlanceTheme {
                CrousWidgetContent(fav, affluence?.level, todayMenu)
            }
        }
    }

    @Composable
    private fun CrousWidgetContent(
        restaurant: Restaurant?,
        affluenceLevel: AffluenceLevel?,
        menu: fr.montpellient.core.model.DailyMenu?
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .padding(14.dp)
        ) {
            if (restaurant != null) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CROUS FAVORITE",
                            style = TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                        Spacer(GlanceModifier.defaultWeight())
                        val affluenceLabel = if (!restaurant.isOpenNow) {
                            "Fermé"
                        } else when (affluenceLevel) {
                            AffluenceLevel.LOW -> "🟢 Faible"
                            AffluenceLevel.MEDIUM -> "🟡 Modéré"
                            AffluenceLevel.HIGH -> "🔴 Forte"
                            else -> "⚪ Inconnu"
                        }
                        Text(
                            text = affluenceLabel,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        )
                    }

                    Spacer(GlanceModifier.height(4.dp))

                    Text(
                        text = restaurant.name,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        maxLines = 1
                    )

                    Spacer(GlanceModifier.height(2.dp))

                    Text(
                        text = "📍 ${restaurant.campus} • ${restaurant.openingHours}",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp
                        ),
                        maxLines = 1
                    )

                    Spacer(GlanceModifier.height(6.dp))

                    // Menu du jour multi-lignes
                    val stations = menu?.meals?.firstOrNull()?.stations.orEmpty()
                    if (stations.isNotEmpty()) {
                        Column(modifier = GlanceModifier.fillMaxWidth()) {
                            stations.take(3).forEach { station ->
                                val icon = when {
                                    station.name.contains("entrée", ignoreCase = true) -> "🥗"
                                    station.name.contains("plat", ignoreCase = true) -> "🍲"
                                    station.name.contains("dessert", ignoreCase = true) -> "🍰"
                                    station.name.contains("garniture", ignoreCase = true) -> "🍚"
                                    else -> "🍽️"
                                }
                                val dishesStr = station.dishes.take(2).joinToString(", ")
                                Text(
                                    text = "$icon ${station.name} : $dishesStr",
                                    style = TextStyle(
                                        color = GlanceTheme.colors.onSurface,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    maxLines = 1
                                )
                                Spacer(GlanceModifier.height(2.dp))
                            }
                        }
                    } else {
                        Text(
                            text = "🍽️ Menu du jour disponible sur place",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            maxLines = 1
                        )
                    }
                }
            } else {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Aucun restaurant CROUS",
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

class CrousGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CrousGlanceWidget()
}
