package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FocusConstantsTest {

    @Test
    fun computeStreakDays_countsConsecutiveDaysIncludingYesterday() {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        val refDate = cal.time

        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = fmt.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val twoDaysAgo = fmt.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -2)
        val fourDaysAgo = fmt.format(cal.time)

        val sessions = listOf(
            StudySession(day = yesterday, subject = "Physics", durationMinutes = 25, done = true),
            StudySession(day = twoDaysAgo, subject = "Maths", durationMinutes = 25, done = true),
            StudySession(day = fourDaysAgo, subject = "History", durationMinutes = 10, done = true)
        )

        assertEquals(2, FocusConstants.computeStreakDays(sessions, refDate))
    }

    @Test
    fun computeTotalFocusMinutesAndSubjectBreakdown_aggregatesCompletedSessionsOnly() {
        val sessions = listOf(
            StudySession(day = "2026-11-14", subject = "Biology", durationMinutes = 25, done = true),
            StudySession(day = "2026-11-14", subject = "Biology", durationMinutes = 45, done = true),
            StudySession(day = "2026-11-13", subject = "Chemistry", durationMinutes = 10, done = true),
            StudySession(day = "2026-11-14", subject = "Chemistry", durationMinutes = 25, done = false)
        )

        assertEquals(70L, FocusConstants.computeTotalFocusMinutes(sessions, onlyDay = "2026-11-14"))
        assertEquals(80L, FocusConstants.computeTotalFocusMinutes(sessions))

        val breakdown = FocusConstants.computeSubjectBreakdown(sessions)
        assertEquals(2, breakdown.size)
        assertEquals("Biology", breakdown[0].subject)
        assertEquals(70L, breakdown[0].totalMinutes)
        assertEquals(2, breakdown[0].completedSessions)
        assertEquals("Chemistry", breakdown[1].subject)
        assertEquals(10L, breakdown[1].totalMinutes)
    }

    @Test
    fun evaluateReverseAlarm_handlesUpcomingAndReminderNeeded() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }
        val upcoming = FocusConstants.evaluateReverseAlarm("18:00", 0, now)
        assertEquals(ReverseAlarmState.UPCOMING, upcoming.state)

        val reminder = FocusConstants.evaluateReverseAlarm("08:00", 0, now)
        assertEquals(ReverseAlarmState.REMINDER_NEEDED, reminder.state)

        val started = FocusConstants.evaluateReverseAlarm("08:00", 1, now)
        assertEquals(ReverseAlarmState.STARTED_TODAY, started.state)
    }
}
