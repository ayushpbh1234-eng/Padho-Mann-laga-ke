package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
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
