package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StudyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val studyName = intent?.getStringExtra(EXTRA_STUDY_NAME).orEmpty()
        val helper = FocusNotificationHelper(context)
        helper.sendTestReminderNotification(studyName)
    }

    companion object {
        const val EXTRA_STUDY_NAME = "extra_study_name"
        const val EXTRA_ALARM_TIME = "extra_alarm_time"
    }
}
