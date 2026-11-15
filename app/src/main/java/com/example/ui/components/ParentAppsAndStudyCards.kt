package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.unit.dp
import com.example.data.model.ChildDeviceState
import com.example.data.model.FocusConstants
import com.example.data.model.StudySession
import com.example.data.model.UserProfile

@Composable
fun InstalledAppsManagerCard(
    deviceState: ChildDeviceState?,
    blockedPackages: List<String>,
    ownPackageName: String,
    onToggleBlockedApp: (packageName: String, blocked: Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }

    val apps = remember(deviceState?.installedApps, searchQuery, showSystemApps, blockedPackages) {
        (deviceState?.installedApps ?: emptyList())
            .filter { it.packageName != ownPackageName }
            .filter { showSystemApps || !it.isSystemApp || it.minutesToday > 0L || it.packageName in blockedPackages }
            .filter {
                searchQuery.isBlank() ||
                    it.appName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
            }
            .take(35)
    }

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
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Child Phone Apps (${apps.size})", style = MaterialTheme.typography.titleLarge)
                }
                TextButton(
                    onClick = { showSystemApps = !showSystemApps },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(if (showSystemApps) "User Apps" else "All Apps")
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("App search karo (YouTube, Instagram...)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (apps.isEmpty()) {
                Text(
                    text = "Abhi app usage list sync nahi hui. Child ke phone par 'Family > Sync' aur Usage Access allow karein.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                apps.forEachIndexed { idx, app ->
                    if (idx > 0) HorizontalDivider()
                    val isBlocked = app.packageName in blockedPackages
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${app.minutesToday} min aaj • ${if (isBlocked) "BLOCKED 🚫" else "Allowed"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isBlocked,
                            onCheckedChange = { onToggleBlockedApp(app.packageName, it) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParentNudgeAndAssignCard(
    childProfile: UserProfile?,
    onSendNudge: (String) -> Unit,
    onAssignStudyTask: (subject: String, durationMinutes: Long, alarmTime: String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var nudgeText by remember { mutableStateOf("") }
    var subjectText by rememberSaveable(childProfile?.subjectsText) {
        mutableStateOf(
            FocusConstants.parseSubjects(childProfile?.subjectsText.orEmpty()).firstOrNull()
                ?: "Revision"
        )
    }
    var selectedMins by remember { mutableLongStateOf(25L) }

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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Instant Message & Study Task 📢", style = MaterialTheme.typography.titleLarge)
            }

            OutlinedTextField(
                value = nudgeText,
                onValueChange = { nudgeText = it },
                label = { Text("Child ke phone par notification bhejo") },
                placeholder = { Text("Jaise: Beta 10 min mein padhai shuru karo!") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    clearActiveTextInput(focusManager, textToolbar, keyboardController)
                    onSendNudge(nudgeText)
                    nudgeText = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("parent_send_nudge_button")
            ) {
                Text("Send Instant Notification 📢")
            }

            HorizontalDivider()

            OutlinedTextField(
                value = subjectText,
                onValueChange = { subjectText = it },
                label = { Text("Assign Subject / Topic") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15L, 25L, 45L, 60L).forEach { mins ->
                    FilterChip(
                        selected = selectedMins == mins,
                        onClick = { selectedMins = mins },
                        label = { Text("$mins min") },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    clearActiveTextInput(focusManager, textToolbar, keyboardController)
                    onAssignStudyTask(subjectText, selectedMins, childProfile?.alarmTime ?: "")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
            ) {
                Text("Study Task Assign Karo 📝")
            }
        }
    }
}

@Composable
fun ChildStudyProgressCard(
    childProfile: UserProfile?,
    sessions: List<StudySession>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Child Study & Focus Report 📚", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = "Current Goal: ${childProfile?.currentStudy?.ifBlank { "Abhi set nahi hua" } ?: "Loading..."}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (!childProfile?.subjectsText.isNullOrBlank()) {
                Text(
                    text = "Subjects: ${childProfile?.subjectsText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (sessions.isEmpty()) {
                Text(
                    text = "Abhi tak koi synced study session nahi mila.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                sessions.take(6).forEachIndexed { idx, sess ->
                    if (idx > 0) HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${sess.subject} (${sess.durationMinutes} min)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${sess.day} • ${if (sess.done) "Complete ✅" else "Distracted (${sess.distractionReasons.joinToString()}) 🥀"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
