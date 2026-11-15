package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.ChildDeviceState
import com.example.data.model.FocusConstants
import com.example.data.model.ParentControlRules

@Composable
fun ChildDeviceOverviewCard(
    childName: String,
    deviceState: ChildDeviceState?,
    controls: ParentControlRules?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            text = "${deviceState?.childName?.ifBlank { childName } ?: childName} ka Phone",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (deviceState != null) {
                                "${deviceState.deviceModel} • ${deviceState.androidVersion}"
                            } else {
                                "Child phone se live sync ka intezar hai..."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (deviceState != null && deviceState.batteryPercent >= 0L) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (deviceState.isCharging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryStd,
                                contentDescription = "Battery",
                                modifier = Modifier.size(16.dp)
                            )
                            Text("${deviceState.batteryPercent}%", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            if (deviceState != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricPill(
                        label = "Phone Screen Time",
                        value = "${deviceState.totalPhoneScreenMinutesToday} min",
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        label = "Abhi Open App",
                        value = deviceState.currentForegroundApp.ifBlank { "Focus Garden" },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricPill(
                        label = "Aaj Study",
                        value = "${deviceState.todayStudyMinutes} min (${deviceState.todayCompletedSessions} sess)",
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        label = "Device Sync",
                        value = if (deviceState.supervisionServiceRunning) "Live 🟢" else "Offline ⚪",
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!deviceState.usageAccessGranted) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ Child phone par abhi Usage Access permission pending hai (Family tab > Allow Usage).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                if (deviceState.recentBlockedAttempt.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🚨 Recent Alert: ${deviceState.recentBlockedAttempt}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
            if (controls != null && controls.dailyScreenLimitMinutes > 0L) {
                val today = FocusConstants.todayIsoDate()
                val bonus = if (controls.bonusDay == today) controls.bonusMinutesToday else 0L
                Text(
                    text = "Limit Rule: ${controls.dailyScreenLimitMinutes}m daily" +
                        if (bonus > 0L) " (+${bonus}m bonus aaj)" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemoteControlsCard(
    controls: ParentControlRules?,
    onToggleLock: (Boolean, String?) -> Unit,
    onToggleStudyOnly: (Boolean) -> Unit,
    onSetDailyLimit: (Long) -> Unit,
    onGrantBonus: (Long) -> Unit,
    onToggleBedtime: (Boolean) -> Unit,
    onSetParentPin: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isLocked = controls?.isPhoneLocked == true
    var customLockMsg by rememberSaveable(controls?.lockMessage) {
        mutableStateOf(controls?.lockMessage ?: "Abhi padhai ka time hai! Phone side rakho.")
    }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf(controls?.parentPin ?: "") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Screen Time, Limits & Lock 🔒",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = customLockMsg,
                onValueChange = { customLockMsg = it },
                label = { Text("Phone Lock Screen Message") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    clearActiveTextInput(focusManager, textToolbar, keyboardController)
                    onToggleLock(!isLocked, customLockMsg)
                },
                colors = if (isLocked) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                } else {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("parent_lock_phone_button")
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(if (isLocked) "Child Phone Unlock Karein 🔓" else "Child Phone Abhi Lock Karein 🔒")
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("📚 Study-Only Mode", style = MaterialTheme.typography.titleMedium)
                    Text("Doosre apps kholne par turant study alert", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = controls?.studyOnlyMode == true,
                    onCheckedChange = onToggleStudyOnly,
                    modifier = Modifier.testTag("parent_study_only_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("🌙 Bedtime Lock (22:00 - 06:00)", style = MaterialTheme.typography.titleMedium)
                    Text("Raat ko automatic phone lock", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = controls?.bedtimeEnabled == true,
                    onCheckedChange = onToggleBedtime,
                    modifier = Modifier.testTag("parent_bedtime_switch")
                )
            }

            HorizontalDivider()

            Text("⏳ Daily Phone Screen Limit", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(0L to "No Limit", 30L to "30m", 60L to "1h", 90L to "1.5h", 120L to "2h", 180L to "3h")
                    .forEach { (mins, label) ->
                        FilterChip(
                            selected = (controls?.dailyScreenLimitMinutes ?: 0L) == mins,
                            onClick = { onSetDailyLimit(mins) },
                            label = { Text(label) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        )
                    }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onGrantBonus(15L) },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Text("+15m Bonus 🎁")
                }
                OutlinedButton(
                    onClick = {
                        clearActiveTextInput(focusManager, textToolbar, keyboardController)
                        pinInput = controls?.parentPin ?: ""
                        showPinDialog = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Pin, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(if (controls?.parentPin.isNullOrBlank()) "Set PIN" else "PIN: ****")
                }
            }
        }
    }

    if (showPinDialog) {
        SafeAlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Parent Security PIN 🔐") },
            text = {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 4) pinInput = it.filter(Char::isDigit) },
                    label = { Text("4-digit PIN (blank = remove)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = { clearDialogInput ->
                Button(
                    onClick = {
                        clearDialogInput()
                        onSetParentPin(pinInput)
                        showPinDialog = false
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = { clearDialogInput ->
                TextButton(
                    onClick = {
                        clearDialogInput()
                        showPinDialog = false
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
private fun MetricPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
