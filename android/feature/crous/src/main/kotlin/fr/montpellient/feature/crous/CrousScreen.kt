package fr.montpellient.feature.crous

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
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
import fr.montpellient.core.model.AffluenceLevel
import fr.montpellient.core.model.DailyMenu
import fr.montpellient.core.model.Restaurant
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrousRoute(container: AppContainer) {
    val restaurants by container.crous.restaurants.collectAsStateWithLifecycle(emptyList())
    val affluences by container.crous.allAffluence.collectAsStateWithLifecycle(emptyMap())
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedRestaurant by remember { mutableStateOf<Restaurant?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crous_title)) }
            )
        }
    ) { padding ->
        RefreshableContent(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    runCatching { container.crous.refresh() }
                    isRefreshing = false
                }
            },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (restaurants.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Restaurant,
                    title = stringResource(R.string.crous_empty)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(restaurants, key = { it.id }) { r ->
                        val affluence = affluences[r.id]
                        Card(
                            onClick = { selectedRestaurant = r },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            r.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "${r.campus} · ${r.openingHours}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch { container.crous.setFavourite(r.id, !r.isFavourite) }
                                        }
                                    ) {
                                        Icon(
                                            if (r.isFavourite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                            contentDescription = null,
                                            tint = if (r.isFavourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusChip(
                                        text = stringResource(if (r.isOpenNow) R.string.crous_open else R.string.crous_closed),
                                        container = if (r.isOpenNow) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        content = if (r.isOpenNow) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (r.isOpenNow) {
                                        val (affText, affColor, affOnColor) = when (affluence?.level) {
                                            AffluenceLevel.LOW -> Triple(stringResource(R.string.crous_affluence_low), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                            AffluenceLevel.MEDIUM -> Triple(stringResource(R.string.crous_affluence_med), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                                            AffluenceLevel.HIGH -> Triple(stringResource(R.string.crous_affluence_high), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                                            else -> Triple(stringResource(R.string.crous_affluence_unknown), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                                        }

                                        StatusChip(
                                            text = affText,
                                            container = affColor,
                                            content = affOnColor
                                        )
                                    } else {
                                        StatusChip(
                                            text = stringResource(R.string.crous_affluence_closed),
                                            container = MaterialTheme.colorScheme.surfaceVariant,
                                            content = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedRestaurant?.let { r ->
        val menus by container.crous.menus(r.id).collectAsStateWithLifecycle(emptyList())
        ModalBottomSheet(onDismissRequest = { selectedRestaurant = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(r.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(r.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                Text("Menu du jour", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                if (menus.isEmpty()) {
                    Text("Aucun menu publié", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val todayMenu = menus.first()
                    todayMenu.meals.forEach { meal ->
                        Text(meal.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        meal.stations.forEach { st ->
                            Text("${st.name} : ${st.dishes.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
