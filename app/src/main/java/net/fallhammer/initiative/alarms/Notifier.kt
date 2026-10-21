package net.fallhammer.initiative.alarms

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import initiative.core.AlarmState
import initiative.core.Board
import initiative.core.Escalation
import initiative.core.Formats
import initiative.core.Item
import initiative.core.Rung
import initiative.core.WallClock
import net.fallhammer.initiative.MainActivity
import net.fallhammer.initiative.R
import net.fallhammer.initiative.TAG
import java.time.Duration
import java.time.LocalDateTime

/** The lock-screen card and the rung notifications. No badges anywhere. */
object Notifier {
    private const val CH_CARD = "board"
    private const val CH_NOTICE = "notice"
    private const val CH_NUDGE = "nudge"
    private const val CH_ALARM = "alarm"
    private const val CARD_ID = 1

    fun rungId(itemId: Long): Int = 1000 + (itemId % 1_000_000).toInt()

    // Rung 3 gets its own notification: Android only opens a full-screen alarm
    // for a newly posted notification, never for an update to the nudge.
    private fun alarmId(itemId: Long): Int = 2_000_000 + (itemId % 1_000_000).toInt()

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        fun channel(id: String, name: String, importance: Int, setup: NotificationChannel.() -> Unit = {}) =
            NotificationChannel(id, name, importance).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setup()
            }
        nm.createNotificationChannels(
            listOf(
                channel(CH_CARD, context.getString(R.string.channel_card), NotificationManager.IMPORTANCE_LOW) {
                    description = context.getString(R.string.channel_card_about)
                },
                channel(CH_NOTICE, context.getString(R.string.channel_notice), NotificationManager.IMPORTANCE_LOW) {
                    description = context.getString(R.string.channel_notice_about)
                },
                channel(CH_NUDGE, context.getString(R.string.channel_nudge), NotificationManager.IMPORTANCE_HIGH) {
                    description = context.getString(R.string.channel_nudge_about)
                    enableVibration(true)
                },
                channel(CH_ALARM, context.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH) {
                    description = context.getString(R.string.channel_alarm_about)
                    enableVibration(true)
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                },
            ),
        )
    }

    @SuppressLint("MissingPermission")
    fun showCard(context: Context, board: Board, now: LocalDateTime, focus: Item? = null) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) {
            Log.i(TAG, "card skipped: notifications are off")
            return
        }
        val current = board.now.item
        val next = board.next
        // A task back from a wait says so first: it's what to do when this one ends.
        val back = board.waits.firstOrNull { it.ready(now) && it.id != current?.id }
        val nextAt = board.nextAt
        val b = NotificationCompat.Builder(context, CH_CARD)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(
                when {
                    focus != null -> context.getString(R.string.card_focus, focus.title)
                    current != null -> context.getString(R.string.card_now, current.title)
                    else -> context.getString(R.string.free_time)
                },
            )
            .setContentText(
                when {
                    back != null -> back.nextStep?.let { context.getString(R.string.card_ready, back.title, it.title) }
                        ?: context.getString(R.string.card_ready_plain, back.title)
                    next == null -> board.now.reason
                    nextAt != null -> context.getString(R.string.next_task_at, next.title, Formats.clock(nextAt))
                    else -> context.getString(R.string.next_float, next.title)
                },
            )
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openBoard(context))
        val start = next?.start
        if (next != null && next.isAnchor && start != null) {
            // A live countdown, so the card needs no refresh between boundaries.
            b.setWhen(millis(start)).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        } else {
            b.setShowWhen(false)
        }
        nm.notify(CARD_ID, b.build())
        Log.i(TAG, "card: ${focus?.let { "focus " + it.title } ?: current?.title ?: "Free time"}")
    }

    fun hideCard(context: Context) = NotificationManagerCompat.from(context).cancel(CARD_ID)

    @SuppressLint("MissingPermission")
    fun showRung(context: Context, anchor: Item, rung: Rung, state: AlarmState, now: LocalDateTime) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) {
            Log.w(TAG, "rung ${rung.level} for ${anchor.title} not shown: notifications are off")
            return
        }
        val start = anchor.start ?: return
        val mins = Duration.between(now, start).toMinutes().coerceAtLeast(0)
        val (channel, title) = when (rung) {
            Rung.NOTICE -> CH_NOTICE to context.getString(R.string.rung_notice, anchor.title, mins.toInt())
            Rung.NUDGE -> CH_NUDGE to context.getString(R.string.rung_nudge, anchor.title, mins.toInt())
            Rung.ALARM -> CH_ALARM to context.getString(R.string.rung_alarm, anchor.title)
        }
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(Formats.anchorLine(anchor))
            .setCategory(if (rung == Rung.ALARM) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(if (rung == Rung.NOTICE) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openBoard(context))
            .setOngoing(rung == Rung.ALARM)
            .addAction(0, context.getString(R.string.alarm_on_it), AlarmActionReceiver.pending(context, anchor.id, AlarmActionReceiver.ON_IT))
        if (Escalation.snoozesLeft(anchor, state) > 0) {
            b.addAction(0, context.getString(R.string.notify_snooze), AlarmActionReceiver.pending(context, anchor.id, AlarmActionReceiver.SNOOZE))
        }
        if (rung == Rung.ALARM) b.setFullScreenIntent(AlarmActivity.pending(context, anchor.id), true)
        val n = b.build()
        // The alarm sound repeats until On it or Snooze.
        if (rung == Rung.ALARM) {
            n.flags = n.flags or Notification.FLAG_INSISTENT
            nm.cancel(rungId(anchor.id))
            nm.notify(alarmId(anchor.id), n)
        } else {
            nm.notify(rungId(anchor.id), n)
        }
    }

    fun cancelRung(context: Context, itemId: Long) {
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(rungId(itemId))
        nm.cancel(alarmId(itemId))
    }

    fun openBoard(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun millis(t: LocalDateTime): Long = WallClock.instant(t).toEpochMilli()
}
