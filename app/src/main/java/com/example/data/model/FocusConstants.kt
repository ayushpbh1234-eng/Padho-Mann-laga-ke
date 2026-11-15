package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class MoodOption(
    val key: String,
    val emoji: String,
    val label: String,
    val guidance: String
)

data class SoundStats(
    val sound: String,
    val sessionCount: Int,
    val distractionCount: Int
) {
    val distractionRate: Double
        get() = if (sessionCount == 0) Double.MAX_VALUE else distractionCount.toDouble() / sessionCount
}

data class MirrorEntry(
    val reason: String,
    val count: Int
)

data class SubjectBreakdown(
    val subject: String,
    val totalMinutes: Long,
    val completedSessions: Int
)

enum class ReverseAlarmState {
    NOT_SET,
    UPCOMING,
    REMINDER_NEEDED,
    STARTED_TODAY
}

data class ReverseAlarmStatus(
    val state: ReverseAlarmState,
    val message: String
)

object FocusConstants {
    const val DEFAULT_SUBJECTS = ""

    val STAGES = listOf("🌰", "🌱", "🌿", "🪴", "🌷", "🌻", "🌳")

    val PET_MESSAGES = listOf(
        "Plant abhi chhota hai, aage badho!",
        "Accha chal raha hai!",
        "Ek aur push!",
        "Almost there, tum kar sakti ho!"
    )

    val QUOTES = listOf(
        "Chhota kaam bhi aage badhata hai.",
        "Ek page bhi ek jeet hai.",
        "Aaj thoda aur, kal aur aasan.",
        "Tum jitna focus karogi, utna nikhroge."
    )

    val STICKERS = listOf("⭐", "🎉", "🦋", "🍀", "🏆", "🌈")

    val SOUNDS = listOf("Barish 🌧", "Cafe ☕", "Lo-fi 🎶", "Silence 🤫")

    val DISTRACTION_REASONS = listOf(
        "Phone",
        "Social media",
        "Neend/thakan",
        "Bahar ka kaam",
        "Kuch aur"
    )

    val MOODS = listOf(
        MoodOption(
            key = "thaki",
            emoji = "😴",
            label = "Thaki",
            guidance = "Sirf ek page padho. Bas shuru karna hai."
        ),
        MoodOption(
            key = "bored",
            emoji = "🥱",
            label = "Bored",
            guidance = "Ek chhota topic lo aur 10 minute ka target rakho."
        ),
        MoodOption(
            key = "energetic",
            emoji = "⚡",
            label = "Energetic",
            guidance = "Ek mushkil problem ya chapter ka bada hissa lo!"
        )
    )

    val CHALLENGES = listOf(
        "Sirf ek page padhna hai.",
        "Ek example solve karna hai.",
        "Ek sawaal likhna hai notes mein.",
        "Ek chhota summary khud ko bolna hai."
    )

    val DURATION_OPTIONS = listOf(
        10 to "10 min",
        25 to "25 min",
        45 to "45 min (Deep)",
        5 to "5 min Break ☕"
    )

    fun moodGuidance(key: String?): String? =
        MOODS.firstOrNull { it.key == key }?.guidance

    fun challengeAt(index: Long, customChallenges: List<String> = emptyList()): String {
        val list = customChallenges.ifEmpty { CHALLENGES }
        val safeIdx = ((index % list.size) + list.size) % list.size
        return list[safeIdx.toInt()]
    }

    fun todayIsoDate(date: Date = Date()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

    fun greetingForHour(hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): String {
        val prefix = when {
            hourOfDay < 12 -> "Good morning"
            hourOfDay < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        return "$prefix! Aaj ka focus plan dekho."
    }

    fun parseSubjects(subjectsText: String): List<String> =
        subjectsText.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun evaluateReverseAlarm(
        alarmTime: String?,
        todaySessionCount: Int,
        now: Calendar = Calendar.getInstance()
    ): ReverseAlarmStatus {
        if (alarmTime.isNullOrBlank()) {
            return ReverseAlarmStatus(
                state = ReverseAlarmState.NOT_SET,
                message = "Time set karo, padhai ka reminder notification milega."
            )
        }
        val parts = alarmTime.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull()
        val m = parts.getOrNull(1)?.toIntOrNull()
        if (h == null || m == null || h !in 0..23 || m !in 0..59) {
            return ReverseAlarmStatus(
                state = ReverseAlarmState.NOT_SET,
                message = "Time set karo, padhai ka reminder notification milega."
            )
        }
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (now.before(target)) {
            return ReverseAlarmStatus(
                state = ReverseAlarmState.UPCOMING,
                message = "Padhai $alarmTime pe shuru karni hai (notification scheduled 🔔)."
            )
        }
        return if (todaySessionCount == 0) {
            ReverseAlarmStatus(
                state = ReverseAlarmState.REMINDER_NEEDED,
                message = "Tumhara syllabus tumhe dekh raha hai... 👀 Chalo shuru karo!"
            )
        } else {
            ReverseAlarmStatus(
                state = ReverseAlarmState.STARTED_TODAY,
                message = "Acha kiya, aaj padhai shuru ho chuki hai! ✅"
            )
        }
    }

    fun computeSoundStats(sessions: List<StudySession>): List<SoundStats> {
        return SOUNDS.map { sound ->
            val matching = sessions.filter { it.sound == sound }
            SoundStats(
                sound = sound,
                sessionCount = matching.size,
                distractionCount = matching.sumOf { it.distractionCount.toInt() }
            )
        }
    }

    fun computeBestSound(stats: List<SoundStats>): String? {
        return stats.filter { it.sessionCount > 0 }
            .minByOrNull { it.distractionRate }
            ?.sound
    }

    fun computeMirrorStats(sessions: List<StudySession>): List<MirrorEntry> {
        val counts = mutableMapOf<String, Int>()
        sessions.forEach { session ->
            session.distractionReasons.forEach { reason ->
                val label = reason.ifBlank { "Unknown" }
                counts[label] = (counts[label] ?: 0) + 1
            }
        }
        return counts.entries
            .sortedByDescending { it.value }
            .map { MirrorEntry(reason = it.key, count = it.value) }
    }

    fun computeStreakDays(sessions: List<StudySession>, referenceDate: Date = Date()): Int {
        val completedDays = sessions.filter { it.done && it.day.isNotBlank() }
            .map { it.day }
            .toSet()
        if (completedDays.isEmpty()) return 0

        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance().apply { time = referenceDate }
        val todayStr = fmt.format(cal.time)
        if (todayStr !in completedDays) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }

        var streak = 0
        while (fmt.format(cal.time) in completedDays) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    fun computeTotalFocusMinutes(sessions: List<StudySession>, onlyDay: String? = null): Long {
        return sessions
            .filter { it.done && (onlyDay == null || it.day == onlyDay) }
            .sumOf { it.durationMinutes }
    }

    fun computeSubjectBreakdown(sessions: List<StudySession>): List<SubjectBreakdown> {
        return sessions.filter { it.done }
            .groupBy { it.subject.ifBlank { "Study" } }
            .map { (subject, items) ->
                SubjectBreakdown(
                    subject = subject,
                    totalMinutes = items.sumOf { it.durationMinutes },
                    completedSessions = items.size
                )
            }
            .sortedByDescending { it.totalMinutes }
    }

    fun buildDiaryStory(todaySessions: List<StudySession>): String {
        if (todaySessions.isEmpty()) {
            return "Aaj abhi koi session nahi hua. Pehla session shuru karo!"
        }
        val doneCount = todaySessions.count { it.done }
        val totalDistractions = todaySessions.sumOf { it.distractionCount.toInt() }
        val subjects = todaySessions.map { it.subject }.distinct().joinToString(", ")
        val distractionText = if (totalDistractions > 0) {
            "$totalDistractions baar distract hui."
        } else {
            "Ek bhi baar distract nahi hui!"
        }
        return "Aaj tumne ${todaySessions.size} session kiye, $doneCount poore hue. Padhe: $subjects. $distractionText Kal aur badhiya karna. 🌿"
    }
}
