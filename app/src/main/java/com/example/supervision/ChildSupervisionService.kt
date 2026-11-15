package com.example.supervision

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.R
import com.example.data.model.ChildDeviceState
import com.example.data.model.FocusConstants
import com.example.data.model.ParentControlRules
import com.example.data.repository.FocusGardenRepository
import com.example.notifications.FocusNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChildSupervisionService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var controlsJob: Job? = null
    private var latestControls: ParentControlRules? = null
    private var lastAlertPackage: String? = null
    private var lastAlertTimeMs: Long = 0L
    private var lastNudgeNonceSeen: Long = -1L
    private var lastBlockedAttemptNote: String = ""

    private lateinit var notifier: FocusNotificationHelper
    private lateinit var repository: FocusGardenRepository

    override fun onCreate() {
        super.onCreate()
        notifier = FocusNotificationHelper(applicationContext)
        val dbId = getString(R.string.firestore_database_id)
        repository = FocusGardenRepository(FirebaseFirestore.getInstance(dbId), Firebase.auth)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        val notification = notifier.buildSupervisionForegroundNotification(
            "Live device & study supervision chalu hai"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FocusNotificationHelper.NOTIF_ID_SUPERVISION_FOREGROUND,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(FocusNotificationHelper.NOTIF_ID_SUPERVISION_FOREGROUND, notification)
        }
        _isRunning.value = true

        val uid = currentUser.uid
        val displayName = currentUser.displayName ?: currentUser.email ?: "Student"

        controlsJob?.cancel()
        controlsJob = serviceScope.launch {
            repository.observeParentControls(uid).collectLatest { rules ->
                latestControls = rules
                if (rules != null && rules.parentNudgeNonce > 0L && rules.parentNudgeNonce != lastNudgeNonceSeen) {
                    if (lastNudgeNonceSeen != -1L && rules.parentNudgeText.isNotBlank()) {
                        notifier.notifyParentNudge("Parent", rules.parentNudgeText)
                    }
                    lastNudgeNonceSeen = rules.parentNudgeNonce
                }
            }
        }

        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (isActive) {
                try {
                    val snapshot = DeviceUsageCollector.collectDeviceAppsAndUsage(applicationContext)
                    val controls = latestControls
                    evaluateRestrictions(snapshot, controls)

                    val deviceState = ChildDeviceState(
                        childUid = uid,
                        childName = displayName,
                        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(100),
                        androidVersion = "Android ${Build.VERSION.RELEASE}",
                        batteryPercent = snapshot.batteryPercent.toLong(),
                        isCharging = snapshot.isCharging,
                        usageAccessGranted = snapshot.usageAccessGranted,
                        notificationsGranted = snapshot.notificationsGranted,
                        supervisionServiceRunning = true,
                        currentForegroundApp = snapshot.currentForegroundApp,
                        currentForegroundPackage = snapshot.currentForegroundPackage,
                        totalPhoneScreenMinutesToday = snapshot.totalPhoneScreenMinutesToday,
                        recentBlockedAttempt = lastBlockedAttemptNote,
                        installedApps = snapshot.installedApps
                    )
                    repository.publishChildDeviceState(deviceState)
                } catch (e: Exception) {
                    Log.w(TAG, "Supervision sync cycle error: ${e.message}")
                }
                delay(15_000L)
            }
        }

        return START_STICKY
    }

    private fun evaluateRestrictions(
        snapshot: DeviceUsageSnapshot,
        controls: ParentControlRules?
    ) {
        if (controls == null) return
        val pkg = snapshot.currentForegroundPackage
        if (pkg.isBlank() || pkg == applicationContext.packageName) return

        val today = FocusConstants.todayIsoDate()
        val effectiveBonus = if (controls.bonusDay == today) controls.bonusMinutesToday else 0L
        val limitReached = controls.dailyScreenLimitMinutes > 0L &&
            snapshot.totalPhoneScreenMinutesToday >= (controls.dailyScreenLimitMinutes + effectiveBonus)
        val bedtimeLocked = controls.bedtimeEnabled &&
            DeviceUsageCollector.isWithinBedtime(controls.bedtimeStart, controls.bedtimeEnd)
        val appBlocked = pkg in controls.blockedPackages
        val restricted = controls.isPhoneLocked || controls.studyOnlyMode || bedtimeLocked || limitReached || appBlocked
        if (!restricted) return

        val now = System.currentTimeMillis()
        if (pkg == lastAlertPackage && (now - lastAlertTimeMs) < 45_000L) return
        lastAlertPackage = pkg
        lastAlertTimeMs = now

        val timeStamp = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        val reason = when {
            controls.isPhoneLocked -> "Parent ne phone lock kiya hai"
            bedtimeLocked -> "Bedtime lock (${controls.bedtimeStart}-${controls.bedtimeEnd})"
            limitReached -> "Daily screen limit poora ho gaya"
            controls.studyOnlyMode -> "Study-Only mode chalu hai"
            else -> "Parent ne ${snapshot.currentForegroundApp} block kiya hai"
        }
        lastBlockedAttemptNote = "${snapshot.currentForegroundApp} ($timeStamp) — $reason"

        notifier.notifyParentLockOrBlockedApp(
            title = "🔒 ${snapshot.currentForegroundApp} band hai!",
            body = "$reason. Focus Garden par wapas aao!"
        )
    }

    private fun stopSelfCleanly() {
        _isRunning.value = false
        monitorJob?.cancel()
        controlsJob?.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        _isRunning.value = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "ChildSupervisionSvc"
        const val ACTION_START = "com.example.supervision.ACTION_START"
        const val ACTION_STOP = "com.example.supervision.ACTION_STOP"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, ChildSupervisionService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ChildSupervisionService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }
    }
}
