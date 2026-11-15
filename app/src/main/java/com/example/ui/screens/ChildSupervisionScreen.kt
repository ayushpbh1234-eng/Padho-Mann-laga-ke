package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.FocusConstants
import com.example.data.model.UserProfile
import com.example.supervision.ChildSupervisionService
import com.example.supervision.DeviceUsageCollector
import com.example.supervision.ScreenShareService
import com.example.ui.ChildSupervisionState
import com.example.ui.components.SafeAlertDialog
import com.example.ui.components.clearActiveTextInput

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChildSupervisionScreen(
    profile: UserProfile,
    userDisplayName: String,
    supervisionState: ChildSupervisionState,
    onGeneratePairingCode: (String) -> Unit,
    onDisconnectParent: (linkId: String, enteredPin: String) -> Unit,
    onRefreshTelemetry: () -> Unit,
    onStartAssignedTask: (subject: String, durationMinutes: Int) -> Unit,
    onScreenShareStatusMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current
    val keyboardController = LocalSoftwareKeyboardController.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    var hasUsageAccess by remember(supervisionState.localSnapshot) {
        mutableStateOf(DeviceUsageCollector.hasUsageAccess(context))
    }
    var hasNotifPermission by remember(supervisionState.localSnapshot) {
        mutableStateOf(DeviceUsageCollector.hasNotificationsPermission(context))
    }
    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
        onRefreshTelemetry()
    }
    val screenShareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val resultData = result.data
        if (result.resultCode == Activity.RESULT_OK && resultData != null) {
            ScreenShareService.start(context, result.resultCode, resultData)
            onScreenShareStatusMessage("Live Screen Share chalu ho gaya 📺 (Parent ab screen dekh sakte hain)")
        } else {
            onScreenShareStatusMessage("Screen Share permission cancel ki gayi.")
        }
    }

    var pendingPinAction by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var pinDialogError by remember { mutableStateOf<String?>(null) }

    val controls = supervisionState.parentControls
    val hasPin = !controls?.parentPin.isNullOrBlank()
    val childName = profile.displayName.ifBlank { userDisplayName }
    val snapshot = supervisionState.localSnapshot

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Family Pairing Code & Linked Parent Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Parent Connect 🔗",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (supervisionState.familyLinks.isNotEmpty()) {
                    supervisionState.familyLinks.forEach { link ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "🛡️ Connected Parent: ${link.parentName.ifBlank { "Parent" }}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Study & phone activity sync chalu hai",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        clearActiveTextInput(focusManager, textToolbar, keyboardController)
                                        if (hasPin) {
                                            pinInput = ""
                                            pendingPinAction = { pin -> onDisconnectParent(link.id, pin) }
                                        } else {
                                            onDisconnectParent(link.id, "")
                                        }
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text("Unlink")
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Parent ke phone par Parent role chuno aur niche diya 6-digit code unke app mein daalo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val activeCode = supervisionState.activePairingCode?.code
                if (!activeCode.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Parent Link Code:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = activeCode,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.testTag("active_pairing_code_text")
                                )
                            }
                            IconButton(
                                onClick = { clipboard.setText(AnnotatedString(activeCode)) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copy pairing code"
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = { onGeneratePairingCode(childName) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("generate_pairing_code_button")
                ) {
                    Text(if (activeCode == null) "Generate 6-Digit Parent Code 🔗" else "Naya Code Banao 🔄")
                }
            }
        }

        // Live Screen Share (Permission ke Saath) Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (supervisionState.screenShareRunning) Icons.Filled.ScreenShare else Icons.Filled.StopScreenShare,
                        contentDescription = null,
                        tint = if (supervisionState.screenShareRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Live Screen Share (Permission) 📺",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = if (supervisionState.screenShareRunning) {
                        "Screen Share abhi ON hai — aapki poori screen linked Parent dekh sakte hain. Notification se ya niche button se kabhi bhi band karein."
                    } else {
                        "Poori screen Parent ke saath share karne ke liye niche button dabakar system permission allow karein. Bina permission screen share nahi hogi."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        if (supervisionState.screenShareRunning) {
                            ScreenShareService.stop(context)
                            onScreenShareStatusMessage("Live Screen Share band kar diya gaya.")
                        } else {
                            val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                            if (mgr != null) {
                                screenShareLauncher.launch(mgr.createScreenCaptureIntent())
                            } else {
                                onScreenShareStatusMessage("Screen Share is device par available nahi hai.")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_screen_share_button")
                ) {
                    Icon(
                        imageVector = if (supervisionState.screenShareRunning) Icons.Filled.StopScreenShare else Icons.Filled.ScreenShare,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(if (supervisionState.screenShareRunning) "Stop Screen Share ⏹️" else "Allow & Start Screen Share 📺")
                }
            }
        }

        // Device Permissions & Sync Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Permissions & Sync 📱",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                PermissionStatusRow(
                    title = "Usage Access (Apps & Screen Time)",
                    granted = hasUsageAccess,
                    buttonLabel = if (hasUsageAccess) "Settings" else "Allow Usage",
                    testTag = "grant_usage_access_button",
                    onClick = { DeviceUsageCollector.openUsageAccessSettings(context) }
                )

                PermissionStatusRow(
                    title = "Notifications (Reminders & Alerts)",
                    granted = hasNotifPermission,
                    buttonLabel = if (hasNotifPermission) "Allowed" else "Allow Alerts",
                    testTag = "grant_notifications_button",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            clearActiveTextInput(focusManager, textToolbar, keyboardController)
                            if (supervisionState.serviceRunning) {
                                if (hasPin) {
                                    pinInput = ""
                                    pendingPinAction = { _ -> ChildSupervisionService.stop(context) }
                                } else {
                                    ChildSupervisionService.stop(context)
                                }
                            } else {
                                ChildSupervisionService.start(context)
                                onRefreshTelemetry()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("toggle_supervision_service_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(if (supervisionState.serviceRunning) "Sync Band Karein" else "Sync Chalu Karein")
                    }
                    OutlinedButton(
                        onClick = {
                            hasUsageAccess = DeviceUsageCollector.hasUsageAccess(context)
                            hasNotifPermission = DeviceUsageCollector.hasNotificationsPermission(context)
                            onRefreshTelemetry()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("refresh_device_sync_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh device telemetry",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Sync")
                    }
                }

                if (snapshot != null) {
                    Text(
                        text = "Aaj phone usage: ${snapshot.totalPhoneScreenMinutesToday} min • Battery: ${snapshot.batteryPercent}% • Apps scanned: ${snapshot.installedApps.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Parent Updates & Tasks
        if (controls != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AdminPanelSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Parent Updates & Tasks 📝",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    val today = FocusConstants.todayIsoDate()
                    val bonusToday = if (controls.bonusDay == today) controls.bonusMinutesToday else 0L
                    val effectiveLimit = controls.dailyScreenLimitMinutes + bonusToday
                    val usedToday = snapshot?.totalPhoneScreenMinutesToday ?: 0L
                    if (controls.dailyScreenLimitMinutes > 0L) {
                        val ratio = (usedToday.toFloat() / effectiveLimit.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                        Text(
                            text = "Daily Screen Limit: $usedToday / $effectiveLimit min" +
                                if (bonusToday > 0L) " (+${bonusToday}m bonus)" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RuleStatusBadge("Phone Lock", if (controls.isPhoneLocked) "ON 🔒" else "Unlocked")
                        RuleStatusBadge("Study-Only", if (controls.studyOnlyMode) "ON 📚" else "Off")
                        RuleStatusBadge(
                            "Bedtime",
                            if (controls.bedtimeEnabled) "${controls.bedtimeStart}-${controls.bedtimeEnd}" else "Off"
                        )
                        RuleStatusBadge("Blocked Apps", "${controls.blockedPackages.size} apps")
                    }

                    if (controls.parentNudgeText.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                                Text(
                                    text = "Parent Message: ${controls.parentNudgeText}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    if (controls.assignedSubject.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "📝 Parent Assigned Study Task",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    val mins = controls.assignedDurationMinutes.takeIf { it > 0L } ?: 25L
                                    Text(
                                        text = "${controls.assignedSubject} • $mins min",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Button(
                                    onClick = {
                                        onStartAssignedTask(
                                            controls.assignedSubject,
                                            controls.assignedDurationMinutes.toInt()
                                        )
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text("Shuru")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (pendingPinAction != null) {
        SafeAlertDialog(
            onDismissRequest = {
                pinDialogError = null
                pendingPinAction = null
            },
            title = { Text("Parent PIN Daalein 🔒") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 4) {
                                pinInput = it.filter(Char::isDigit)
                                pinDialogError = null
                            }
                        },
                        label = { Text("4-digit Parent PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinDialogError != null) {
                        Text(
                            text = pinDialogError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = { clearDialogInput ->
                Button(
                    onClick = {
                        val action = pendingPinAction
                        if (pinInput == (controls?.parentPin ?: "")) {
                            clearDialogInput()
                            pinDialogError = null
                            pendingPinAction = null
                            action?.invoke(pinInput)
                        } else {
                            pinDialogError = "Galat Parent PIN ❌"
                        }
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Verify")
                }
            },
            dismissButton = { clearDialogInput ->
                TextButton(
                    onClick = {
                        clearDialogInput()
                        pinDialogError = null
                        pendingPinAction = null
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    granted: Boolean,
    buttonLabel: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = if (granted) "Granted" else "Needed",
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag(testTag)
        ) {
            Text(buttonLabel)
        }
    }
}

@Composable
private fun RuleStatusBadge(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
