package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FocusConstants
import com.example.data.model.StudySession

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GardenStatsCard(
    sessions: List<StudySession>,
    streakDays: Int,
    todayMinutes: Long,
    modifier: Modifier = Modifier
) {
    val completedSessions = remember(sessions) { sessions.filter { it.done } }
    val gardenFlora = remember(completedSessions) {
        val floraPalette = listOf("🌳", "🌻", "🌷", "🪴", "🌿")
        completedSessions.take(14).mapIndexed { index, session ->
            val pick = (session.subject.hashCode().let { if (it < 0) -it else it } + index) % floraPalette.size
            floraPalette[pick]
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Mera Focus Garden 🌿",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatTile(
                        icon = Icons.Filled.LocalFireDepartment,
                        tint = MaterialTheme.colorScheme.secondary,
                        value = "$streakDays Din",
                        label = "Streak",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stat_streak_tile")
                    )
                    StatTile(
                        icon = Icons.Filled.Timer,
                        tint = MaterialTheme.colorScheme.primary,
                        value = "${todayMinutes}m",
                        label = "Aaj Padhai",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stat_today_minutes_tile")
                    )
                    StatTile(
                        icon = Icons.Filled.Park,
                        tint = MaterialTheme.colorScheme.tertiary,
                        value = "${completedSessions.size}",
                        label = "Uge Trees",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stat_trees_tile")
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val totalMinutes = remember(sessions) {
                            FocusConstants.computeTotalFocusMinutes(sessions)
                        }
                        Text(
                            text = if (completedSessions.isEmpty()) {
                                "Terrarium abhi khaali hai"
                            } else {
                                "Tumhara Garden (${totalMinutes} min total focus)"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (gardenFlora.isEmpty()) {
                            Text(
                                text = "Pehla focus session poora karke apna pehla tree ugao 🌱",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                gardenFlora.forEach { plant ->
                                    Text(text = plant, fontSize = 24.sp)
                                }
                                if (completedSessions.size > gardenFlora.size) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = "+${completedSessions.size - gardenFlora.size} aur",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    tint: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
