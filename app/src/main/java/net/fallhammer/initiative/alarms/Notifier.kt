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
import initiative.core.Item
import initiative.core.Rung
import initiative.core.WallStrength
import net.fallhammer.initiative.MainActivity
import net.fallhammer.initiative.R
import net.fallhammer.initiative.TAG
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The lock-screen card and the rung notifications. No badges anywhere. */
object Notifier {
    private const val CH_CARD = "board"
    private const val CH_NOTICE = "notice"
    private const val CH_NUDGE = "nudge"
    private const val CH_ALARM = "alarm"
    private const val CARD_ID = 1
    private val CLOCK = DateTimeFormatter.ofPattern("h:mm a")

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
                channel(CH_CARD, "Lock-screen card", NotificationManager.IMPORTANCE_LOW) {
                    description = "The Now card, silent, on the lock screen."
                },
                channel(CH_NOTICE, "Rung 1: quiet notice", NotificationManager.IMPORTANCE_LOW) {
                    description = "Silent heads-up at an anchor's lead time."
                },
                channel(CH_NUDGE, "Rung 2: nudge", NotificationManager.IMPORTANCE_HIGH) {
                    description = "Sound and vibration 5 minutes before an anchor."
                    enableVibration(true)
                },
                channel(CH_ALARM, "Rung 3: alarm", NotificationManager.IMPORTANCE_HIGH) {
                    description = "Full alarm when a Firm or Hard anchor starts."
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
    fun showCard(context: Context, board: Board) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) {
            Log.i(TAG, "card skipped: notifications are off")
            return
        }
        val now = board.now.item
        val next = board.next
        val b = NotificationCompat.Builder(context, CH_CARD)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(now?.let { "Now: ${it.title}" } ?: "Free time")
            .setContentText(next?.let { "Next: ${it.title}" } ?: board.now.reason)
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
        Log.i(TAG, "card: ${now?.title ?: "Free time"}")
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
            Rung.NOTICE -> CH_NOTICE to "${anchor.title} in $mins min"
            Rung.NUDGE -> CH_NUDGE to "${anchor.title} in $mins min. Time to move."
            Rung.ALARM -> CH_ALARM to "${anchor.title} now"
        }
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText("${start.format(CLOCK)} · ${wallLabel(anchor.wall)}")
            .setCategory(if (rung == Rung.ALARM) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(if (rung == Rung.NOTICE) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openBoard(context))
            .setOngoing(rung == Rung.ALARM)
            .addAction(0, "On it", AlarmActionReceiver.pending(context, anchor.id, AlarmActionReceiver.ON_IT))
        if (Escalation.snoozesLeft(anchor, state) > 0) {
            b.addAction(0, "Snooze 5 min", AlarmActionReceiver.pending(context, anchor.id, AlarmActionReceiver.SNOOZE))
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

    fun millis(t: LocalDateTime): Long = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun wallLabel(w: WallStrength) = when (w) {
        WallStrength.SOFT -> "Soft"
        WallStrength.FIRM -> "Firm"
        WallStrength.HARD -> "Hard"
    }
}
