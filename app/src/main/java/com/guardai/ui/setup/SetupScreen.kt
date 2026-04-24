package com.guardai.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    overlayGranted: Boolean,
    accessibilityGranted: Boolean,
    captureActive: Boolean,
    statusMessage: String,
    diagnostics: String,
    lastEjectResult: String,
    capturedFrameCount: Long,
    captureResolution: String,
    currentPackageName: String,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestProjection: () -> Unit,
    onStopProtection: () -> Unit,
    onLockCurrentApp: () -> Unit,
    onUnlockAll: () -> Unit,
) {
    var showEjectAlert by rememberSaveable(lastEjectResult) {
        mutableStateOf(lastEjectResult.isNotBlank())
    }

    if (showEjectAlert && lastEjectResult.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showEjectAlert = false },
            title = { Text("Eject result") },
            text = { Text(lastEjectResult) },
            confirmButton = {
                TextButton(onClick = { showEjectAlert = false }) {
                    Text("Dismiss")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GuardAI") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (lastEjectResult.isNotBlank()) {
                EjectResultCard(
                    result = lastEjectResult,
                    onDismissAlert = { showEjectAlert = false },
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Personal APK bootstrap",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = statusMessage)
                    if (diagnostics.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = diagnostics,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (lastEjectResult.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Last eject result: $lastEjectResult",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (captureResolution.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Resolution: $captureResolution")
                    }
                    if (capturedFrameCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Captured frames: $capturedFrameCount")
                    }
                    if (currentPackageName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Current app: $currentPackageName")
                    }
                }
            }

            StatusCard(
                title = "Overlay permission",
                ready = overlayGranted,
                buttonLabel = "Grant overlay",
                onClick = onRequestOverlay,
            )

            StatusCard(
                title = "Accessibility service",
                ready = accessibilityGranted,
                buttonLabel = "Open accessibility settings",
                onClick = onRequestAccessibility,
            )

            StatusCard(
                title = "Screen capture session",
                ready = captureActive,
                buttonLabel = if (captureActive) "Stop protection" else "Start capture session",
                onClick = if (captureActive) onStopProtection else onRequestProjection,
            )

            DebugCard(
                currentPackageName = currentPackageName,
                onLockCurrentApp = onLockCurrentApp,
                onUnlockAll = onUnlockAll,
            )
        }
    }
}

@Composable
private fun EjectResultCard(
    result: String,
    onDismissAlert: () -> Unit,
) {
    val success = result.contains("success=true")
    val containerColor = if (success) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (success) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (success) "Latest eject succeeded" else "Latest eject failed",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = result,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onDismissAlert) {
                Text("Dismiss alert")
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    ready: Boolean,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (ready) "Ready" else "Not ready",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (ready) {
                OutlinedButton(onClick = onClick) {
                    Text(buttonLabel)
                }
            } else {
                Button(onClick = onClick) {
                    Text(buttonLabel)
                }
            }
        }
    }
}

@Composable
private fun DebugCard(
    currentPackageName: String,
    onLockCurrentApp: () -> Unit,
    onUnlockAll: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Debug actions", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (currentPackageName.isBlank()) {
                    "No app tracked yet. Open the target app, then return here."
                } else {
                    "Ready to lock: $currentPackageName"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onLockCurrentApp,
                modifier = Modifier.fillMaxWidth(),
                enabled = currentPackageName.isNotBlank(),
            ) {
                Text("Lock Current App")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onUnlockAll,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Unlock All")
            }
        }
    }
}
