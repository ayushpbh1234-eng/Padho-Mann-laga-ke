package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.supervision.ScreenShareService
import java.util.Calendar

interface FocusNotifier {
    fun ensureChannels()
    fun notifySessionComplete(subject: String, durationMinutes: Int, isBreak: Boolean)
    fun notifyBackgroundDistraction(subject: String)
    fun clearBackgroundDistraction()
    fun scheduleReverseAlarm(alarmTime: String, currentStudy: String)
    fun sendTestReminderNotification(currentStudy: String)
    fun notifyParentLockOrBlockedApp(title: String, body: String)
    fun notifyParentNudge(parentName: String, message: String)
}

class FocusNotificationHelper(context: Context) : FocusNotifier {
    private val appContext = context.applicationContext

    override fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val focusChannel = NotificationChannel(
                CHANNEL_FOCUS_SESSIONS,
                "Focus Sessions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Session completion celebrations and gentle focus return nudges"
            }

            val reminderChannel = NotificationChannel(
                CHANNEL_STUDY_REMINDERS,
                "Reverse Study Alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily study start time reminders"
            }

            val supervisionChannel = NotificationChannel(
                CHANNEL_PARENTAL_SUPERVISION,
                "Parental Supervision",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Live parental phone supervision alerts, app limits, and parent messages"
            }

            val serviceChannel = NotificationChannel(
                CHANNEL_SUPERVISION_SERVICE,
                "Device Sync Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing status notification while device sync is active"
            }

            val screenShareChannel = NotificationChannel(
                CHANNEL_SCREEN_SHARE,
                "Live Screen Share",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Visible ongoing indicator whenever live screen sharing with Parent is active"
            }

            manager.createNotificationChannel(focusChannel)
            manager.createNotificationChannel(reminderChannel)
            manager.createNotificationChannel(supervisionChannel)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(screenShareChannel)
        }
    }

    fun buildSupervisionForegroundNotification(statusText: String): Notification {
        ensureChannels()
        val openAppIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            appContext,
            NOTIF_ID_SUPERVISION_FOREGROUND,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(appContext, CHANNEL_SUPERVISION_SERVICE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Focus Garden • Family Connect Active 🌱")
            .setContentText(statusText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingOpen)
            .build()
    }

    fun buildScreenShareForegroundNotification(): Notification {
        ensureChannels()
        val openAppIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            appContext,
            NOTIF_ID_SCREEN_SHARE_FOREGROUND,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(appContext, ScreenShareService::class.java).apply {
            action = ScreenShareService.ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            appContext,
            NOTIF_ID_SCREEN_SHARE_FOREGROUND + 100,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(appContext, CHANNEL_SCREEN_SHARE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Focus Garden • Live Screen Share ON 📺")
            .setContentText("Aapki screen linked Parent ke saath share ho rahi hai. Band karne ke liye Stop dabaein.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingOpen)
            .addAction(0, "Stop Share", pendingStop)
            .build()
    }

    override fun notifySessionComplete(subject: String, durationMinutes: Int, isBreak: Boolean) {
        ensureChannels()
        if (!canPostNotifications()) return
        val title = if (isBreak) {
            "☕ Break poora hua!"
        } else {
            "🌳 Shabash! Plant poora khil gaya!"
        }
        val body = if (isBreak) {
            "$durationMinutes minute ka break khatam. Agle focus session ke liye ready?"
        } else {
            "$subject ($durationMinutes min) complete! Tumhare garden mein ek naya tree uga."
        }
        postNotification(
            id = NOTIF_ID_SESSION_COMPLETE,
            channelId = CHANNEL_FOCUS_SESSIONS,
            title = title,
            body = body
        )
    }

    override fun notifyBackgroundDistraction(subject: String) {
        ensureChannels()
        if (!canPostNotifications()) return
        postNotification(
            id = NOTIF_ID_DISTRACTION_NUDGE,
            channelId = CHANNEL_FOCUS_SESSIONS,
            title = "🥀 Tumhara plant udaas ho raha hai...",
            body = "$subject ka focus session beech mein ruka hai. Wapas aao!"
        )
    }

    override fun clearBackgroundDistraction() {
        try {
            NotificationManagerCompat.from(appContext).cancel(NOTIF_ID_DISTRACTION_NUDGE)
        } catch (_: SecurityException) {
        }
    }

    override fun notifyParentLockOrBlockedApp(title: String, body: String) {
        ensureChannels()
        if (!canPostNotifications()) return
        postNotification(
            id = NOTIF_ID_PARENT_LOCK,
            channelId = CHANNEL_PARENTAL_SUPERVISION,
            title = title,
            body = body
        )
    }

    override fun notifyParentNudge(parentName: String, message: String) {
        ensureChannels()
        if (!canPostNotifications()) return
        val sender = parentName.ifBlank { "Parent" }
        postNotification(
            id = NOTIF_ID_PARENT_NUDGE,
            channelId = CHANNEL_PARENTAL_SUPERVISION,
            title = "📢 $sender ka message:",
            body = message
        )
    }

    override fun scheduleReverseAlarm(alarmTime: String, currentStudy: String) {
        ensureChannels()
        val parts = alarmTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return
        if (hour !in 0..23 || minute !in 0..59) return

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = Calendar.getInstance()
        val trigger = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(appContext, StudyAlarmReceiver::class.java).apply {
            putExtra(StudyAlarmReceiver.EXTRA_STUDY_NAME, currentStudy)
            putExtra(StudyAlarmReceiver.EXTRA_ALARM_TIME, alarmTime)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                trigger.timeInMillis,
                pendingIntent
            )
        } catch (_: SecurityException) {
        }
    }

    override fun sendTestReminderNotification(currentStudy: String) {
        ensureChannels()
        if (!canPostNotifications()) return
        val studyLabel = currentStudy.ifBlank { "Padhai" }
        postNotification(
            id = NOTIF_ID_REVERSE_ALARM,
            channelId = CHANNEL_STUDY_REMINDERS,
            title = "⏰ $studyLabel ka time ho gaya!",
            body = "Tumhara syllabus tumhe dekh raha hai... 👀 Chalo sirf ek chhota session shuru karo!"
        )
    }

    private fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        }
    }

    private fun postNotification(id: Int, channelId: String, title: String, body: String) {
        val openAppIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            appContext,
            id,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingOpen)
            .build()

        try {
            NotificationManagerCompat.from(appContext).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        const val CHANNEL_FOCUS_SESSIONS = "focus_sessions"
        const val CHANNEL_STUDY_REMINDERS = "study_reminders"
        const val CHANNEL_PARENTAL_SUPERVISION = "parental_supervision"
        const val CHANNEL_SUPERVISION_SERVICE = "supervision_service"
        const val CHANNEL_SCREEN_SHARE = "screen_share_service"
        private const val NOTIF_ID_SESSION_COMPLETE = 1001
        private const val NOTIF_ID_DISTRACTION_NUDGE = 1002
        const val NOTIF_ID_REVERSE_ALARM = 1003
        private const val NOTIF_ID_PARENT_LOCK = 1004
        private const val NOTIF_ID_PARENT_NUDGE = 1005
        const val NOTIF_ID_SUPERVISION_FOREGROUND = 1006
        const val NOTIF_ID_SCREEN_SHARE_FOREGROUND = 1007
        private const val ALARM_REQUEST_CODE = 2001
    }
}
