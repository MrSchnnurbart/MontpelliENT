package fr.montpellient.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.montpellient.connector.cas.CasAuthResult
import fr.montpellient.core.data.AppContainer
import fr.montpellient.core.data.DefaultAppContainer
import fr.montpellient.core.model.SessionMode
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    container: AppContainer,
    onEnterApp: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showLoginDialog by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }

    if (isSyncing) {
        InitialSyncScreen(
            container = container,
            onFinished = onEnterApp
        )
    } else {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Outlined.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    stringResource(R.string.onboarding_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(Modifier.height(48.dp))

                Button(
                    onClick = {
                        scope.launch {
                            container.settings.update { it.copy(session = SessionMode.DEMO) }
                            onEnterApp()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.onboarding_demo_btn))
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { showLoginDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.onboarding_cas_btn))
                }

                Spacer(Modifier.height(32.dp))

                Text(
                    stringResource(R.string.onboarding_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    if (showLoginDialog) {
        CasLoginDialog(
            container = container,
            onDismiss = { showLoginDialog = false },
            onSuccess = {
                showLoginDialog = false
                isSyncing = true
            }
        )
    }
}

@Composable
fun InitialSyncScreen(
    container: AppContainer,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    var syncStep by remember { mutableStateOf(context.getString(R.string.sync_step_connected)) }
    var progress by remember { mutableStateOf(0.15f) }
    var icalUrlInput by remember { mutableStateOf("") }
    var needManualIcal by remember { mutableStateOf(false) }
    var isSavingIcal by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400)
        syncStep = context.getString(R.string.sync_step_cas)
        progress = 0.35f
        container.profile.reloadProfile()
        kotlinx.coroutines.delay(600)

        syncStep = context.getString(R.string.sync_step_timetable)
        progress = 0.55f
        val icalSaved = container.timetable.getCustomIcalUrl()
        if (icalSaved.isNullOrBlank()) {
            needManualIcal = true
        } else {
            runCatching { container.timetable.refresh() }
        }

        syncStep = context.getString(R.string.sync_step_services)
        progress = 0.85f
        runCatching { container.courses.refresh() }
        runCatching { container.grades.refresh() }

        if (icalSaved != null && icalSaved.isNotBlank()) {
            progress = 1.0f
            syncStep = context.getString(R.string.sync_step_done)
            kotlinx.coroutines.delay(800)
            onFinished()
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Outlined.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.sync_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                syncStep,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )

            if (needManualIcal) {
                Spacer(Modifier.height(32.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(R.string.sync_ade_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.sync_ade_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = icalUrlInput,
                            onValueChange = { icalUrlInput = it },
                            label = { Text(stringResource(R.string.sync_ade_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isSavingIcal
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = onFinished,
                                modifier = Modifier.weight(1f),
                                enabled = !isSavingIcal
                            ) {
                                Text(stringResource(R.string.sync_ade_skip))
                            }
                            Button(
                                onClick = {
                                    if (icalUrlInput.isNotBlank()) {
                                        isSavingIcal = true
                                        scope.launch {
                                            container.timetable.setCustomIcalUrl(icalUrlInput.trim())
                                            isSavingIcal = false
                                            onFinished()
                                        }
                                    } else {
                                        onFinished()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isSavingIcal
                            ) {
                                Text(stringResource(R.string.sync_ade_confirm))
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun CasLoginDialog(
    container: AppContainer,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.login_dialog_title))
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.login_security_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.login_field_username)) },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.login_field_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (isLoading) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.login_connecting), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (username.isBlank() || password.isBlank()) {
                        errorMessage = "Veuillez remplir votre identifiant et mot de passe."
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null

                    scope.launch {
                        val defaultContainer = container as? DefaultAppContainer
                        if (defaultContainer != null) {
                            val result = defaultContainer.casAuthClient.login(username.trim(), password)
                            when (result) {
                                is CasAuthResult.Success -> {
                                    container.settings.update { it.copy(session = SessionMode.AUTHENTICATED) }
                                    isLoading = false
                                    onSuccess()
                                }
                                is CasAuthResult.InvalidCredentials -> {
                                    isLoading = false
                                    errorMessage = result.message
                                }
                                is CasAuthResult.RequiresMfa -> {
                                    isLoading = false
                                    errorMessage = "Double authentification (MFA) requise pour ce compte."
                                }
                                is CasAuthResult.NetworkError -> {
                                    isLoading = false
                                    errorMessage = "Erreur réseau: ${result.cause.message ?: "serveur inaccessible"}"
                                }
                                is CasAuthResult.ServerError -> {
                                    isLoading = false
                                    errorMessage = "Erreur serveur (${result.code}): ${result.message}"
                                }
                            }
                        } else {
                            // Mode de secours
                            container.settings.update { it.copy(session = SessionMode.AUTHENTICATED) }
                            isLoading = false
                            onSuccess()
                        }
                    }
                },
                enabled = !isLoading && username.isNotBlank() && password.isNotBlank()
            ) {
                Text(stringResource(R.string.login_btn_submit))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.login_btn_cancel))
            }
        }
    )
}
