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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FocusConstants
import com.example.data.model.UserProfile
import com.example.ui.FocusTimerUiState
import com.example.ui.components.BotanicalGardenCanvas
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusScreen(
    profile: UserProfile,
    timerState: FocusTimerUiState,
    onSelectDuration: (Int) -> Unit,
    onSelectSubject: (String) -> Unit,
    onSelectTopic: (String?) -> Unit,
    onSessionGoalChanged: (String) -> Unit,
    onSelectSound: (String) -> Unit,
    onToggleMute: () -> Unit,
    onStartSession: () -> Unit,
    onPauseOrResume: () -> Unit,
    onStartQuickBreak: () -> Unit,
    onGiveUp: () -> Unit,
    onSpinRoulette: (String, List<String>) -> Unit,
    onSaveSubjectsText: (String) -> Unit,
    onPickDistractionReason: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var subjectsInput by remember(profile.subjectsText) { mutableStateOf(profile.subjectsText) }
    LaunchedEffect(profile.subjectsText) {
        if (subjectsInput != profile.subjectsText) {
            subjectsInput = profile.subjectsText
        }
    }
    val parsedSubjects = remember(subjectsInput) {
        FocusConstants.parseSubjects(subjectsInput)
    }
    val matchingTopics = remember(profile.syllabusTopics, timerState.selectedSubject) {
        if (timerState.selectedSubject.isBlank()) {
            profile.syllabusTopics
        } else {
            val filtered = profile.syllabusTopics.filter {
                it.contains(timerState.selectedSubject, ignoreCase = true)
            }
            filtered.ifEmpty { profile.syllabusTopics }
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Living Plant & Focus Timer Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (timerState.isBreakMode || timerState.isPaused) {
                    Surface(
                        color = if (timerState.isPaused) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = when {
                                timerState.isPaused -> "⏸️ Paused"
                                timerState.isBreakMode -> "☕ Mindful Break Mode"
                                else -> ""
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                BotanicalGardenCanvas(
                    progress = timerState.progress,
                    isRunning = timerState.isRunning && !timerState.isPaused,
                    isWilted = timerState.isWilted,
                    isBreakMode = timerState.isBreakMode,
                    plantEmoji = timerState.plantEmoji
                )

                Text(
                    text = timerState.petMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (timerState.isWilted) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("pet_message_text")
                )

                Text(
                    text = timerState.formattedTime,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 44.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("focus_timer_text")
                )

                if (timerState.isRunning && timerState.sessionGoal.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "🎯 Target: ${timerState.sessionGoal}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Duration Selector (Study & Break)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FocusConstants.DURATION_OPTIONS.forEach { (mins, label) ->
                        FilterChip(
                            selected = timerState.durationMinutes == mins,
                            onClick = { onSelectDuration(mins) },
                            enabled = !timerState.isRunning,
                            label = { Text(label) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("duration_${mins}_chip")
                        )
                    }
                }

                // User's Own Subject Selector Chips
                if (!timerState.isBreakMode) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Apna subject chuno",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (parsedSubjects.isEmpty()) {
                            Text(
                                text = "Niche Subject Roulette mein apne subjects add karein.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                parsedSubjects.forEach { subj ->
                                    FilterChip(
                                        selected = timerState.selectedSubject == subj,
                                        onClick = { onSelectSubject(subj) },
                                        enabled = !timerState.isRunning,
                                        label = { Text(subj) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    )
                                }
                            }
                        }
                    }

                    // Syllabus Topics for selected subject
                    if (matchingTopics.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Syllabus topic (optional)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                matchingTopics.take(6).forEach { topic ->
                                    val selected = timerState.selectedTopic == topic
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onSelectTopic(if (selected) null else topic) },
                                        enabled = !timerState.isRunning,
                                        label = { Text(topic) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    )
                                }
                            }
                        }
                    }

                    // Session Target / Mini Goal
                    if (!timerState.isRunning) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = timerState.sessionGoal,
                                onValueChange = onSessionGoalChanged,
                                label = { Text("Is session ka target (optional)") },
                                placeholder = { Text("Jaise: 1 chapter ya 20 MCQs") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("session_goal_input")
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("1 chapter", "20 MCQs", "Revision", "Short notes").forEach { presetGoal ->
                                    AssistChip(
                                        onClick = { onSessionGoalChanged(presetGoal) },
                                        label = { Text(presetGoal) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    )
                                }
                            }
                        }
                    }
                }

                // Sound Selector + Audio Mute Toggle
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Ambient sound",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = onToggleMute,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("mute_audio_button")
                        ) {
                            Icon(
                                imageVector = if (timerState.isAudioMuted) {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                },
                                contentDescription = if (timerState.isAudioMuted) {
                                    "Unmute ambient sound"
                                } else {
                                    "Mute ambient sound"
                                },
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FocusConstants.SOUNDS.forEach { snd ->
                            FilterChip(
                                selected = timerState.selectedSound == snd,
                                onClick = { onSelectSound(snd) },
                                label = { Text(snd) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            )
                        }
                    }
                }

                // Start / Pause-Resume / Quick Break / Give Up Controls
                if (!timerState.isRunning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onStartSession,
                            enabled = timerState.isBreakMode || parsedSubjects.isNotEmpty(),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("start_focus_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (timerState.isBreakMode) "Break Shuru" else "Shuru karo")
                        }

                        if (!timerState.isBreakMode) {
                            OutlinedButton(
                                onClick = onStartQuickBreak,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("quick_break_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Coffee,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("5m Break")
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPauseOrResume,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("pause_resume_button")
                        ) {
                            Icon(
                                imageVector = if (timerState.isPaused) {
                                    Icons.Filled.PlayArrow
                                } else {
                                    Icons.Filled.Pause
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (timerState.isPaused) "Jari Rakho" else "Roko")
                        }

                        OutlinedButton(
                            onClick = onGiveUp,
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("give_up_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Chhodo")
                        }
                    }
                }
            }
        }

        // Reward Card
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CardGiftcard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Reward 🎁",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = timerState.rewardMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .padding(12.dp)
                            .testTag("reward_text")
                    )
                }
            }
        }

        // Subject Roulette Card
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
                        imageVector = Icons.Filled.Casino,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Subject Roulette 🎲",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedTextField(
                    value = subjectsInput,
                    onValueChange = { subjectsInput = it },
                    label = { Text("Tumhare subjects (comma se alag karein)") },
                    placeholder = { Text("Jaise: Anatomy, Physiology, Biochemistry") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("subjects_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSpinRoulette(subjectsInput, profile.syllabusTopics) },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("roulette_spin_button")
                    ) {
                        Text("Random chuno")
                    }
                    Button(
                        onClick = { onSaveSubjectsText(subjectsInput) },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("save_subjects_button")
                    ) {
                        Text("Save")
                    }
                }

                if (timerState.rouletteMessage != null) {
                    Text(
                        text = timerState.rouletteMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("roulette_result_text")
                    )
                }
            }
        }
    }

    if (timerState.showDistractionDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = "Abhi tum kya kar rahi thi? 🤔",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "App chhodne ka reason chuno taaki Mirror mein pattern dikhe.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FocusConstants.DISTRACTION_REASONS.forEach { reason ->
                        OutlinedButton(
                            onClick = { onPickDistractionReason(reason) },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("distraction_reason_$reason")
                        ) {
                            Text(reason)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
