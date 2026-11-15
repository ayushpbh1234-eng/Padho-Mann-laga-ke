package com.example.supervision

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.data.model.PhoneAppUsageInfo
import java.util.Calendar

data class DeviceUsageSnapshot(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val usageAccessGranted: Boolean,
    val notificationsGranted: Boolean,
    val currentForegroundApp: String,
    val currentForegroundPackage: String,
    val totalPhoneScreenMinutesToday: Long,
    val installedApps: List<PhoneAppUsageInfo>
)

object DeviceUsageCollector {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasNotificationsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun openUsageAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun readBatteryStatus(context: Context): Pair<Int, Boolean> {
        val intent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return -1 to false

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val percent = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return percent to charging
    }

    fun collectDeviceAppsAndUsage(context: Context, maxApps: Int = 55): DeviceUsageSnapshot {
        val (batteryPercent, isCharging) = readBatteryStatus(context)
        val usageGranted = hasUsageAccess(context)
        val notifGranted = hasNotificationsPermission(context)

        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }

        val appInfoByPkg = linkedMapOf<String, Pair<String, Boolean>>()
        resolveInfos.forEach { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@forEach
            if (!appInfoByPkg.containsKey(pkg)) {
                val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty().ifBlank { pkg }
                val isSystem = (ri.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                appInfoByPkg[pkg] = label to isSystem
            }
        }

        val minutesByPkg = mutableMapOf<String, Long>()
        var foregroundPkg = context.packageName
        var foregroundAppName = "Focus Garden"

        if (usageGranted) {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usm != null) {
                val now = System.currentTimeMillis()
                val startOfDay = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
                stats?.forEach { stat ->
                    val pkg = stat.packageName ?: return@forEach
                    val mins = stat.totalTimeInForeground / 60_000L
                    if (mins > 0L) {
                        minutesByPkg[pkg] = (minutesByPkg[pkg] ?: 0L) + mins
                        if (!appInfoByPkg.containsKey(pkg)) {
                            val label = resolveAppName(pm, pkg)
                            appInfoByPkg[pkg] = label to false
                        }
                    }
                }

                // Determine latest foreground app from recent UsageEvents
                val events = usm.queryEvents(now - 5 * 60_000L, now)
                val event = UsageEvents.Event()
                var latestTimestamp = 0L
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    val isResume = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                    } else {
                        @Suppress("DEPRECATION")
                        event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                    }
                    if (isResume && event.timeStamp >= latestTimestamp) {
                        latestTimestamp = event.timeStamp
                        foregroundPkg = event.packageName ?: foregroundPkg
                    }
                }
                foregroundAppName = appInfoByPkg[foregroundPkg]?.first ?: resolveAppName(pm, foregroundPkg)
            }
        }

        val combinedApps = appInfoByPkg.entries
            .map { (pkg, meta) ->
                PhoneAppUsageInfo(
                    packageName = pkg,
                    appName = meta.first.take(60),
                    minutesToday = (minutesByPkg[pkg] ?: 0L).coerceIn(0L, 1440L),
                    isSystemApp = meta.second
                )
            }
            .sortedWith(compareByDescending<PhoneAppUsageInfo> { it.minutesToday }.thenBy { it.appName.lowercase() })
            .take(maxApps)

        val totalMinutesToday = minutesByPkg.values.sum().coerceIn(0L, 1440L)

        return DeviceUsageSnapshot(
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            usageAccessGranted = usageGranted,
            notificationsGranted = notifGranted,
            currentForegroundApp = foregroundAppName.take(100),
            currentForegroundPackage = foregroundPkg.take(200),
            totalPhoneScreenMinutesToday = totalMinutesToday,
            installedApps = combinedApps
        )
    }

    private fun resolveAppName(pm: PackageManager, packageName: String): String {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(info).toString().trim().ifBlank { packageName }
        } catch (_: Exception) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    fun isWithinBedtime(
        bedtimeStart: String?,
        bedtimeEnd: String?,
        now: Calendar = Calendar.getInstance()
    ): Boolean {
        val startMins = parseHhMmMinutes(bedtimeStart) ?: return false
        val endMins = parseHhMmMinutes(bedtimeEnd) ?: return false
        val nowMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return if (startMins <= endMins) {
            nowMins in startMins until endMins
        } else {
            nowMins >= startMins || nowMins < endMins
        }
    }

    private fun parseHhMmMinutes(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        val parts = value.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }
}
