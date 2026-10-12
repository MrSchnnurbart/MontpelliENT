package fr.montpellient.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.montpellient.core.data.AppContainer
import fr.montpellient.core.data.settings.SettingsRules
import fr.montpellient.core.designsystem.component.SectionHeader
import fr.montpellient.core.model.AppSettings
import fr.montpellient.core.model.ThemeMode
import fr.montpellient.core.model.ThemePalette
import fr.montpellient.core.model.TopLevelDestination
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(container: AppContainer) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionHeader(stringResource(R.string.theme_section))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.theme_dark))
                            Switch(
                                checked = settings.themeMode == ThemeMode.DARK,
                                onCheckedChange = { isDark ->
                                    scope.launch {
                                        container.settings.update {
                                            it.copy(themeMode = if (isDark) ThemeMode.DARK else ThemeMode.LIGHT)
                                        }
                                    }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.theme_amoled))
                            Switch(
                                checked = settings.amoledBlack,
                                onCheckedChange = { amoled ->
                                    scope.launch {
                                        container.settings.update { it.copy(amoledBlack = amoled) }
                                    }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.theme_dynamic))
                            Switch(
                                checked = settings.dynamicColor,
                                onCheckedChange = { dyn ->
                                    scope.launch {
                                        container.settings.update { it.copy(dynamicColor = dyn) }
                                    }
                                }
                            )
                        }

                        if (!settings.dynamicColor) {
                            Text(stringResource(R.string.theme_palette), style = MaterialTheme.typography.titleSmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                ThemePalette.entries.filter { it != ThemePalette.CUSTOM }.forEach { pal ->
                                    val isCurrent = settings.palette == pal
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(pal.seed))
                                            .clickable {
                                                scope.launch {
                                                    container.settings.update { it.copy(palette = pal) }
                                                }
                                            }
                                    )
                                }
                            }

                            // Sélecteur de couleur libre
                            var hexInput by remember { mutableStateOf("#%06X".format(0xFFFFFF and settings.customSeedColor.toInt())) }
                            Text(stringResource(R.string.theme_custom_color), style = MaterialTheme.typography.titleSmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(settings.seedColor))
                                )
                                OutlinedTextField(
                                    value = hexInput,
                                    onValueChange = { hexInput = it },
                                    label = { Text(stringResource(R.string.theme_custom_hex)) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        val cleaned = hexInput.trim().removePrefix("#")
                                        val parsed = cleaned.toLongOrNull(16)
                                        if (parsed != null) {
                                            val fullColor = 0xFF000000L or (parsed and 0xFFFFFFL)
                                            scope.launch {
                                                container.settings.update {
                                                    it.copy(
                                                        palette = ThemePalette.CUSTOM,
                                                        customSeedColor = fullColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.theme_apply))
                                }
                            }
                        }
                    }
                }
            }


            item {
                SectionHeader(stringResource(R.string.nav_section))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.nav_tabs_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TopLevelDestination.entries.forEach { dest ->
                            val isSelected = dest in settings.tabs
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            container.settings.update {
                                                it.copy(tabs = SettingsRules.toggleTab(it.tabs, dest))
                                            }
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(dest.name)
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        scope.launch {
                                            container.settings.update {
                                                it.copy(tabs = SettingsRules.toggleTab(it.tabs, dest))
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(stringResource(R.string.session_section))
                Button(
                    onClick = {
                        scope.launch {
                            container.settings.update { SettingsRules.signOut(it) }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.session_disconnect))
                }
            }
        }
    }
}
