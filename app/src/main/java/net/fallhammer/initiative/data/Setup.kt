package net.fallhammer.initiative.data

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** One-time permissions, shown on the board one at a time until done. */
enum class SetupStep(val text: String) {
    NOTIFICATIONS("Setup: tap to allow notifications, for alarms and the lock-screen card."),
    EXACT_ALARMS("Setup: tap to let alarms fire on the minute."),
    FULL_SCREEN("Setup: tap to allow full-screen alarms for Firm and Hard walls."),
    BATTERY("Setup: tap so battery saving can't delay alarms. Choose Allow."),
    OVERLAY("Setup: tap to let the board appear after you unlock. Allow “Display over other apps”."),
}

class Setup(private val context: Context) {

    fun missing(prefs: Prefs): List<SetupStep> = buildList {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) add(SetupStep.NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        ) add(SetupStep.EXACT_ALARMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        ) add(SetupStep.FULL_SCREEN)
        if (!context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)) add(SetupStep.BATTERY)
        if (prefs.boardOnUnlock && !prefs.canJumpToBoard()) add(SetupStep.OVERLAY)
    }

    fun open(step: SetupStep) {
        val pkg = Uri.parse("package:${context.packageName}")
        val intent = when (step) {
            SetupStep.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            SetupStep.EXACT_ALARMS -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
            SetupStep.FULL_SCREEN -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
            SetupStep.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            SetupStep.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
