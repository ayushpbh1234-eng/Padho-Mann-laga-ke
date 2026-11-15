package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String? = null,
    val displayName: String = "",
    val linkedParentUid: String? = null,
    val onboardingCompleted: Boolean = false,
    val currentStudy: String = "",
    val syllabusText: String = "",
    val studySummary: String = "",
    val syllabusTopics: List<String> = emptyList(),
    val customChallenges: List<String> = emptyList(),
    val mood: String? = null,
    val challengeIdx: Long = 0L,
    val alarmTime: String? = null,
    val subjectsText: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StudySession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorizedParentUids: List<String> = emptyList(),
    val day: String = "",
    val subject: String = "",
    val sound: String = "",
    val durationMinutes: Long = 25L,
    val done: Boolean = false,
    val rewardText: String? = null,
    val distractionReasons: List<String> = emptyList(),
    val distractionCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PairingCode(
    @DocumentId val id: String = "",
    val code: String = "",
    val childUid: String = "",
    val childName: String = "",
    val active: Boolean = true,
    val usedByParentUid: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FamilyLink(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val childUid: String = "",
    val parentName: String = "",
    val childName: String = "",
    val pairingCode: String = "",
    val status: String = "active",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PhoneAppUsageInfo(
    val packageName: String = "",
    val appName: String = "",
    val minutesToday: Long = 0L,
    @get:PropertyName("isSystemApp")
    @field:PropertyName("isSystemApp")
    val isSystemApp: Boolean = false
)

@IgnoreExtraProperties
data class ChildDeviceState(
    @DocumentId val id: String = "",
    val childUid: String = "",
    val childName: String = "",
    val deviceModel: String = "",
    val androidVersion: String = "",
    val batteryPercent: Long = -1L,
    @get:PropertyName("isCharging")
    @field:PropertyName("isCharging")
    val isCharging: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val supervisionServiceRunning: Boolean = false,
    val currentForegroundApp: String = "",
    val currentForegroundPackage: String = "",
    val totalPhoneScreenMinutesToday: Long = 0L,
    @get:PropertyName("isFocusSessionActive")
    @field:PropertyName("isFocusSessionActive")
    val isFocusSessionActive: Boolean = false,
    val activeFocusSubject: String = "",
    val todayStudyMinutes: Long = 0L,
    val todayCompletedSessions: Long = 0L,
    val streakDays: Long = 0L,
    val recentBlockedAttempt: String = "",
    val installedApps: List<PhoneAppUsageInfo> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ParentControlRules(
    @DocumentId val id: String = "",
    val childUid: String = "",
    val parentUid: String = "",
    @get:PropertyName("isPhoneLocked")
    @field:PropertyName("isPhoneLocked")
    val isPhoneLocked: Boolean = false,
    val lockMessage: String = "Abhi padhai ka time hai! Phone thodi der side rakho.",
    val dailyScreenLimitMinutes: Long = 0L,
    val bonusMinutesToday: Long = 0L,
    val bonusDay: String = "",
    val bedtimeEnabled: Boolean = false,
    val bedtimeStart: String = "22:00",
    val bedtimeEnd: String = "06:00",
    val blockedPackages: List<String> = emptyList(),
    val studyOnlyMode: Boolean = false,
    val parentNudgeText: String = "",
    val parentNudgeNonce: Long = 0L,
    val parentPin: String = "",
    val assignedSubject: String = "",
    val assignedDurationMinutes: Long = 0L,
    val assignedAlarmTime: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChildScreenShare(
    @DocumentId val id: String = "",
    val childUid: String = "",
    @get:PropertyName("isSharing")
    @field:PropertyName("isSharing")
    val isSharing: Boolean = false,
    val frameBase64: String = "",
    val frameWidth: Long = 0L,
    val frameHeight: Long = 0L,
    val frameSeq: Long = 0L,
    val captureMode: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
