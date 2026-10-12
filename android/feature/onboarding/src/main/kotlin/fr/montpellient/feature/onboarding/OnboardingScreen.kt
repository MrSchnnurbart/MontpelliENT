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

    if (showLoginDialog) {
        CasLoginDialog(
            container = container,
            onDismiss = { showLoginDialog = false },
            onSuccess = {
                showLoginDialog = false
                onEnterApp()
            }
        )
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
