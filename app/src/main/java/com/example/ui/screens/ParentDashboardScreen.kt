package com.example.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.ParentDashboardState
import com.example.ui.components.ChildDeviceOverviewCard
import com.example.ui.components.ChildStudyProgressCard
import com.example.ui.components.InstalledAppsManagerCard
import com.example.ui.components.LiveChildScreenCard
import com.example.ui.components.ParentNudgeAndAssignCard
import com.example.ui.components.RemoteControlsCard
import com.example.ui.components.SafeAlertDialog
import com.example.ui.components.clearActiveTextInput

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParentDashboardScreen(
    parentName: String,
    dashboardState: ParentDashboardState,
    onSelectChild: (String) -> Unit,
    onLinkChildByCode: (code: String, parentName: String, onDone: () -> Unit) -> Unit,
    onUnlinkChild: (linkId: String) -> Unit,
    onTogglePhoneLock: (Boolean, String?) -> Unit,
    onToggleStudyOnly: (Boolean) -> Unit,
    onSetDailyLimit: (Long) -> Unit,
    onGrantBonus: (Long) -> Unit,
    onToggleBedtime: (Boolean) -> Unit,
    onSetParentPin: (String) -> Unit,
    onToggleBlockedApp: (packageName: String, blocked: Boolean) -> Unit,
    onSendNudge: (String) -> Unit,
    onRequestScreenShare: () -> Unit,
    onAssignStudyTask: (subject: String, durationMinutes: Long, alarmTime: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showLinkDialog by remember { mutableStateOf(false) }
    var codeInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Parent Header & Child Selector
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
                        Icon(
                            imageVector = Icons.Filled.AdminPanelSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Parent Dashboard 🛡️",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Hello $parentName — Child study, apps aur live screen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (dashboardState.linkedChildren.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        dashboardState.linkedChildren.forEach { link ->
                            FilterChip(
                                selected = link.childUid == dashboardState.selectedChildUid,
                                onClick = { onSelectChild(link.childUid) },
                                label = { Text("📱 ${link.childName.ifBlank { "Child" }}") },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            clearActiveTextInput(focusManager, textToolbar, keyboardController)
                            codeInput = ""
                            showLinkDialog = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("open_link_child_dialog_button")
                    ) {
                        Icon(Icons.Filled.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Link Child Phone (+ Code)")
                    }

                    val activeLink = dashboardState.linkedChildren.firstOrNull {
                        it.childUid == dashboardState.selectedChildUid
                    }
                    if (activeLink != null) {
                        OutlinedButton(
                            onClick = { onUnlinkChild(activeLink.id) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.LinkOff, contentDescription = "Unlink child", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        val selectedLink = dashboardState.linkedChildren.firstOrNull {
            it.childUid == dashboardState.selectedChildUid
        }
        if (selectedLink == null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Abhi koi Child phone link nahi hai 🔗",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "1. Child ke phone par Focus Garden (Child) kholo.\n" +
                            "2. 'Family' tab mein jaakar 6-digit Parent Code generate karo.\n" +
                            "3. Upar 'Link Child Phone (+ Code)' dabakar woh code daalein.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LiveChildScreenCard(
                childName = selectedLink.childName.ifBlank { "Child" },
                screenShare = dashboardState.childScreenShare,
                onRequestScreenShare = onRequestScreenShare
            )

            ChildDeviceOverviewCard(
                childName = selectedLink.childName.ifBlank { "Child" },
                deviceState = dashboardState.childDeviceState,
                controls = dashboardState.childControls
            )

            RemoteControlsCard(
                controls = dashboardState.childControls,
                onToggleLock = onTogglePhoneLock,
                onToggleStudyOnly = onToggleStudyOnly,
                onSetDailyLimit = onSetDailyLimit,
                onGrantBonus = onGrantBonus,
                onToggleBedtime = onToggleBedtime,
                onSetParentPin = onSetParentPin
            )

            InstalledAppsManagerCard(
                deviceState = dashboardState.childDeviceState,
                blockedPackages = dashboardState.childControls?.blockedPackages ?: emptyList(),
                ownPackageName = context.packageName,
                onToggleBlockedApp = onToggleBlockedApp
            )

            ParentNudgeAndAssignCard(
                childProfile = dashboardState.childProfile,
                onSendNudge = onSendNudge,
                onAssignStudyTask = onAssignStudyTask
            )

            ChildStudyProgressCard(
                childProfile = dashboardState.childProfile,
                sessions = dashboardState.childSessions
            )
        }
    }

    if (showLinkDialog) {
        SafeAlertDialog(
            onDismissRequest = {
                showLinkDialog = false
            },
            title = { Text("Child Phone Link Karein 🔗") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Child ke phone par Family tab mein dikhne wala 6-letter Family Code yahan likhein.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { if (it.length <= 6) codeInput = it.uppercase() },
                        label = { Text("6-Digit Family Code") },
                        placeholder = { Text("AB12CD") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("link_child_code_input")
                    )
                }
            },
            confirmButton = { clearDialogInput ->
                Button(
                    onClick = {
                        clearDialogInput()
                        onLinkChildByCode(codeInput, parentName) {
                            showLinkDialog = false
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("link_child_submit_button")
                ) {
                    Text("Link Karein")
                }
            },
            dismissButton = { clearDialogInput ->
                TextButton(
                    onClick = {
                        clearDialogInput()
                        showLinkDialog = false
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
