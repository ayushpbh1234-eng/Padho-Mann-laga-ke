package com.example.supervision

import android.content.Context
import android.os.Build
import com.example.data.model.ChildDeviceState
import com.example.data.model.FocusConstants
import com.example.data.model.StudySession
import com.example.data.model.UserProfile
import com.example.data.repository.FocusGardenRepository
import com.example.ui.FocusTimerUiState

class ParentControlActions(
    private val repository: FocusGardenRepository,
    private val currentUserId: String
) {
    suspend fun syncChildDeviceState(
        context: Context,
        profile: UserProfile,
        sessions: List<StudySession>,
        timerState: FocusTimerUiState
    ): DeviceUsageSnapshot {
        val snapshot = DeviceUsageCollector.collectDeviceAppsAndUsage(context)
        val today = FocusConstants.todayIsoDate()
        val todayStudyMinutes = FocusConstants.computeTotalFocusMinutes(sessions, onlyDay = today)
        val todayCompletedSessions = sessions.count { it.day == today && it.done }.toLong()
        val streakDays = FocusConstants.computeStreakDays(sessions).toLong()

        val state = ChildDeviceState(
            childUid = currentUserId,
            childName = profile.displayName.ifBlank { "Student" },
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(100),
            androidVersion = "Android ${Build.VERSION.RELEASE}",
            batteryPercent = snapshot.batteryPercent.toLong(),
            isCharging = snapshot.isCharging,
            usageAccessGranted = snapshot.usageAccessGranted,
            notificationsGranted = snapshot.notificationsGranted,
            supervisionServiceRunning = ChildSupervisionService.isRunning.value,
            currentForegroundApp = snapshot.currentForegroundApp,
            currentForegroundPackage = snapshot.currentForegroundPackage,
            totalPhoneScreenMinutesToday = snapshot.totalPhoneScreenMinutesToday,
            isFocusSessionActive = timerState.isRunning && !timerState.isBreakMode,
            activeFocusSubject = timerState.selectedTopic?.takeIf { it.isNotBlank() }
                ?: timerState.selectedSubject,
            todayStudyMinutes = todayStudyMinutes,
            todayCompletedSessions = todayCompletedSessions,
            streakDays = streakDays,
            installedApps = snapshot.installedApps
        )
        repository.publishChildDeviceState(state)
        return snapshot
    }

    suspend fun toggleChildPhoneLock(
        childUid: String,
        locked: Boolean,
        lockMessage: String? = null
    ): Result<Unit> = repository.updateParentControls(childUid) { current ->
        buildMap {
            put("isPhoneLocked", locked)
            if (!lockMessage.isNullOrBlank()) {
                put("lockMessage", lockMessage.trim().take(200))
            } else if (current.lockMessage.isBlank()) {
                put("lockMessage", "Abhi padhai ka time hai! Phone side rakho.")
            }
        }
    }

    suspend fun toggleStudyOnlyMode(childUid: String, enabled: Boolean): Result<Unit> =
        repository.updateParentControls(childUid) { _ ->
            mapOf("studyOnlyMode" to enabled)
        }

    suspend fun setDailyScreenLimit(childUid: String, minutes: Long): Result<Unit> =
        repository.updateParentControls(childUid) { _ ->
            mapOf("dailyScreenLimitMinutes" to minutes.coerceIn(0L, 1440L))
        }

    suspend fun grantBonusMinutes(childUid: String, extraMinutes: Long): Result<Unit> =
        repository.updateParentControls(childUid) { current ->
            val today = FocusConstants.todayIsoDate()
            val currentBonus = if (current.bonusDay == today) current.bonusMinutesToday else 0L
            val nextBonus = (currentBonus + extraMinutes).coerceIn(0L, 480L)
            mapOf(
                "bonusDay" to today,
                "bonusMinutesToday" to nextBonus,
                "isPhoneLocked" to false
            )
        }

    suspend fun toggleBedtimeLock(
        childUid: String,
        enabled: Boolean,
        start: String = "22:00",
        end: String = "06:00"
    ): Result<Unit> = repository.updateParentControls(childUid) { _ ->
        mapOf(
            "bedtimeEnabled" to enabled,
            "bedtimeStart" to start.take(10),
            "bedtimeEnd" to end.take(10)
        )
    }

    suspend fun toggleBlockedPackage(
        childUid: String,
        packageName: String,
        blocked: Boolean
    ): Result<Unit> = repository.updateParentControls(childUid) { current ->
        val updated = if (blocked) {
            (current.blockedPackages + packageName).distinct().take(100)
        } else {
            current.blockedPackages.filterNot { it == packageName }
        }
        mapOf("blockedPackages" to updated)
    }

    suspend fun setParentPin(childUid: String, pin: String): Result<Unit> {
        val cleaned = pin.trim()
        if (cleaned.isNotEmpty() && !Regex("^\\d{4}$").matches(cleaned)) {
            return Result.failure(IllegalArgumentException("Parent PIN exactly 4 digits ka hona chahiye (ya khali chhodein)."))
        }
        return repository.updateParentControls(childUid) { _ ->
            mapOf("parentPin" to cleaned)
        }
    }

    suspend fun sendParentNudge(childUid: String, message: String): Result<Unit> {
        val trimmed = message.trim().take(200)
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Bhejne ke liye ek message likho."))
        }
        return repository.updateParentControls(childUid) { current ->
            val nextNonce = (current.parentNudgeNonce + 1L).coerceAtMost(999_999_999L)
            mapOf(
                "parentNudgeText" to trimmed,
                "parentNudgeNonce" to nextNonce
            )
        }
    }

    suspend fun assignStudyTask(
        childUid: String,
        subject: String,
        durationMinutes: Long,
        alarmTime: String
    ): Result<Unit> = repository.updateParentControls(childUid) { _ ->
        mapOf(
            "assignedSubject" to subject.trim().take(100),
            "assignedDurationMinutes" to durationMinutes.coerceIn(0L, 180L),
            "assignedAlarmTime" to alarmTime.trim().take(10)
        )
    }
}
