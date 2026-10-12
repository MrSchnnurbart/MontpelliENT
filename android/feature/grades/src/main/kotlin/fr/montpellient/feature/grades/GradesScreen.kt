package fr.montpellient.feature.grades

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.montpellient.connector.grades.GradesSimulator
import fr.montpellient.core.data.AppContainer
import fr.montpellient.core.designsystem.component.EmptyState
import fr.montpellient.core.designsystem.component.RefreshableContent
import fr.montpellient.core.designsystem.component.StatusChip
import fr.montpellient.core.model.GradeEntry
import fr.montpellient.core.model.GradeReport
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesRoute(container: AppContainer) {
    val reports by container.grades.reports.collectAsStateWithLifecycle(emptyList())
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedReportForSimulation by remember { mutableStateOf<GradeReport?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.grades_title)) },
                actions = {
                    if (reports.isNotEmpty()) {
                        IconButton(onClick = { selectedReportForSimulation = reports.firstOrNull() }) {
                            Icon(Icons.Outlined.Calculate, contentDescription = stringResource(R.string.grades_simulate))
                        }
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
                    runCatching { container.grades.refresh() }
                    isRefreshing = false
                }
            },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (reports.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Grade,
                    title = stringResource(R.string.grades_empty)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    reports.forEach { report ->
                        item(key = report.id) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${report.program} (${report.academicYear})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = { selectedReportForSimulation = report },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Outlined.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Simuler", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        items(report.entries, key = { it.id }) { entry ->
                            GradeEntryCard(entry = entry)
                        }
                    }
                }
            }
        }
    }

    selectedReportForSimulation?.let { report ->
        SimulatorModal(
            report = report,
            onDismiss = { selectedReportForSimulation = null }
        )
    }
}

@Composable
private fun SimulatorModal(
    report: GradeReport,
    onDismiss: () -> Unit
) {
    val overrides = remember { mutableStateMapOf<String, Double>() }
    val ues = remember(report) {
        report.entries.filter { it.level == 1 }
    }

    val simResult = remember(overrides.toMap()) {
        GradesSimulator.evaluateReport(report, overrides)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.grades_simulation_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = "Moyenne : ${simResult.simulatedAverage ?: simResult.currentAverage ?: "--"} / 20",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Statut : ${simResult.compensationStatus}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        simResult.missingPointsForPassing?.let { missing ->
                            Text(
                                text = "Points manquants : $missing pts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Text(
                    "Simuler des notes pour les UEs :",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ues, key = { it.id }) { ue ->
                        var textVal by remember { mutableStateOf(ue.grade ?: "") }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(ue.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                ue.credits?.let {
                                    Text("$it ECTS", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            OutlinedTextField(
                                value = textVal,
                                onValueChange = { input ->
                                    textVal = input
                                    val parsed = input.replace(',', '.').toDoubleOrNull()
                                    if (parsed != null && parsed in 0.0..20.0) {
                                        overrides[ue.id] = parsed
                                    } else {
                                        overrides.remove(ue.id)
                                    }
                                },
                                modifier = Modifier.width(90.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.grades_close))
            }
        }
    )
}

@Composable
private fun GradeEntryCard(entry: GradeEntry) {
    val paddingStart = (entry.level * 16).dp
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = paddingStart),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.level == 0) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    entry.code?.let {
                        Text(
                            "[$it] ",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        entry.label,
                        style = if (entry.level == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                        fontWeight = if (entry.level == 0) FontWeight.Bold else FontWeight.Medium
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    entry.credits?.let {
                        Text(
                            stringResource(R.string.grades_credits, it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    entry.session?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (entry.isNew) {
                        StatusChip(
                            text = stringResource(R.string.grades_new),
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                entry.grade?.let { g ->
                    Text(
                        g + (entry.scale?.let { "/$it" } ?: ""),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                entry.result?.let { res ->
                    Text(
                        res,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (res == "ADM") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
