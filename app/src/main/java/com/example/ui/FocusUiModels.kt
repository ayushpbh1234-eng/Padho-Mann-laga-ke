package com.example.ui

import com.example.data.model.ChildDeviceState
import com.example.data.model.ChildScreenShare
import com.example.data.model.FamilyLink
import com.example.data.model.FocusConstants
import com.example.data.model.PairingCode
import com.example.data.model.ParentControlRules
import com.example.data.model.StudySession
import com.example.data.model.UserProfile
import com.example.supervision.DeviceUsageSnapshot

sealed interface GardenDataState {
    data object Loading : GardenDataState
    data class Ready(
        val profile: UserProfile,
        val sessions: List<StudySession>
    ) : GardenDataState
    data class Error(val message: String) : GardenDataState
}

sealed interface StudySetupUiState {
    data object Idle : StudySetupUiState
    data object Analyzing : StudySetupUiState
    data class Error(val message: String) : StudySetupUiState
}

data class FocusTimerUiState(
    val durationMinutes: Int = 25,
    val totalSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isBreakMode: Boolean = false,
    val isWilted: Boolean = false,
    val selectedSubject: String = "",
    val selectedTopic: String? = null,
    val sessionGoal: String = "",
    val selectedSound: String = FocusConstants.SOUNDS.first(),
    val isAudioMuted: Boolean = false,
    val plantEmoji: String = "🌰",
    val petMessage: String = "Padhai shuru karo, plant ko paani do.",
    val rewardMessage: String = "Session poora karoge toh random reward milega.",
    val rouletteMessage: String? = null,
    val showDistractionDialog: Boolean = false,
    val diaryStoryNonce: Int = 0
) {
    val progress: Float
        get() = if (totalSeconds <= 0) 0f else (1f - remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)

    val formattedTime: String
        get() {
            val mins = remainingSeconds / 60
            val secs = remainingSeconds % 60
            return "%02d:%02d".format(mins, secs)
        }
}

data class ChildSupervisionState(
    val familyLinks: List<FamilyLink> = emptyList(),
    val activePairingCode: PairingCode? = null,
    val parentControls: ParentControlRules? = null,
    val localSnapshot: DeviceUsageSnapshot? = null,
    val serviceRunning: Boolean = false,
    val screenShareRunning: Boolean = false,
    val temporarilyUnlockedUntilMs: Long = 0L
)

data class ParentDashboardState(
    val linkedChildren: List<FamilyLink> = emptyList(),
    val selectedChildUid: String? = null,
    val childProfile: UserProfile? = null,
    val childDeviceState: ChildDeviceState? = null,
    val childControls: ParentControlRules? = null,
    val childScreenShare: ChildScreenShare? = null,
    val childSessions: List<StudySession> = emptyList(),
    val isLoadingChild: Boolean = false,
    val errorMessage: String? = null
)
