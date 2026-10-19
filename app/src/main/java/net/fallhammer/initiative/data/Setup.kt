package net.fallhammer.initiative.data

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import net.fallhammer.initiative.R

/** One-time permissions, shown on the board one at a time until done. */
enum class SetupStep(@StringRes val text: Int) {
    NOTIFICATIONS(R.string.setup_notifications),
    EXACT_ALARMS(R.string.setup_exact_alarms),
    FULL_SCREEN(R.string.setup_full_screen),
    BATTERY(R.string.setup_battery),
    OVERLAY(R.string.setup_overlay),
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
