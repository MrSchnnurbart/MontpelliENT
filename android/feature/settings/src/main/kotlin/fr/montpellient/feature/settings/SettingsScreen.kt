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
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import fr.montpellient.core.designsystem.util.NotificationHelper
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
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            NotificationHelper.sendNotification(
                context = context,
                channelId = NotificationHelper.CHANNEL_TEST,
                notificationId = 1001,
                title = "MontpelliENT",
                message = "Vos notifications fonctionnent parfaitement ! 🎓"
            )
            Toast.makeText(context, context.getString(R.string.notif_test_sent), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, context.getString(R.string.notif_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    val sendTestNotif = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                NotificationHelper.sendNotification(
                    context = context,
                    channelId = NotificationHelper.CHANNEL_TEST,
                    notificationId = 1001,
                    title = "MontpelliENT",
                    message = "Vos notifications fonctionnent parfaitement ! 🎓"
                )
                Toast.makeText(context, context.getString(R.string.notif_test_sent), Toast.LENGTH_SHORT).show()
            } else {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            NotificationHelper.sendNotification(
                context = context,
                channelId = NotificationHelper.CHANNEL_TEST,
                notificationId = 1001,
                title = "MontpelliENT",
                message = "Vos notifications fonctionnent parfaitement ! 🎓"
            )
            Toast.makeText(context, context.getString(R.string.notif_test_sent), Toast.LENGTH_SHORT).show()
        }
    }

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
                    val isMaxReached = settings.tabs.size >= AppSettings.MAX_TABS
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.nav_tabs_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isMaxReached) {
                            Text(
                                stringResource(R.string.nav_tabs_max_reached),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        TopLevelDestination.entries.forEach { dest ->
                            val isSelected = dest in settings.tabs
                            val isEnabled = isSelected || !isMaxReached
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = isEnabled) {
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
                                Text(
                                    dest.name,
                                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                                Checkbox(
                                    checked = isSelected,
                                    enabled = isEnabled,
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
                SectionHeader("Emploi du Temps ADE Campus")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    var icalUrl by remember { mutableStateOf("") }
                    var isSaving by remember { mutableStateOf(false) }
                    var statusText by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(Unit) {
                        icalUrl = container.timetable.getCustomIcalUrl() ?: ""
                    }

                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Lien d'export iCal de votre agenda ADE (trouvable dans l'ENT ou ADE > Options > Exporter mon agenda).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = icalUrl,
                            onValueChange = { icalUrl = it },
                            label = { Text("URL iCal (.ics ou direct_cal)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isSaving
                        )
                        if (statusText != null) {
                            Text(statusText!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Button(
                            onClick = {
                                if (icalUrl.isNotBlank()) {
                                    isSaving = true
                                    statusText = null
                                    scope.launch {
                                        val res = container.timetable.setCustomIcalUrl(icalUrl.trim())
                                        isSaving = false
                                        statusText = if (res.isSuccess) "Emploi du temps synchronisé avec succès !" else (res.exceptionOrNull()?.message ?: "Erreur")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isSaving && icalUrl.isNotBlank()
                        ) {
                            Text(if (isSaving) "Synchronisation..." else "Enregistrer et synchroniser l'EDT")
                        }
                    }
                }
            }

            item {
                SectionHeader(stringResource(R.string.notif_section))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.notif_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Emploi du temps
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(stringResource(R.string.notif_timetable), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.notif_timetable_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = settings.notifTimetable,
                                onCheckedChange = { checked ->
                                    scope.launch { container.settings.update { it.copy(notifTimetable = checked) } }
                                }
                            )
                        }

                        // Mail UM
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(stringResource(R.string.notif_mail), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.notif_mail_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = settings.notifMail,
                                onCheckedChange = { checked ->
                                    scope.launch { container.settings.update { it.copy(notifMail = checked) } }
                                }
                            )
                        }

                        // Notes & Résultats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(stringResource(R.string.notif_grades), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.notif_grades_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = settings.notifGrades,
                                onCheckedChange = { checked ->
                                    scope.launch { container.settings.update { it.copy(notifGrades = checked) } }
                                }
                            )
                        }

                        // Devoirs Moodle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(stringResource(R.string.notif_assignments), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.notif_assignments_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = settings.notifAssignments,
                                onCheckedChange = { checked ->
                                    scope.launch { container.settings.update { it.copy(notifAssignments = checked) } }
                                }
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        OutlinedButton(
                            onClick = { sendTestNotif() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.notif_test_button))
                        }
                    }
                }
            }

            item {
                SectionHeader(stringResource(R.string.session_section))
                val isDemo = settings.session == fr.montpellient.core.model.SessionMode.DEMO
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
                    Text(stringResource(if (isDemo) R.string.session_disconnect_demo else R.string.session_disconnect))
                }
            }
        }
    }
}

