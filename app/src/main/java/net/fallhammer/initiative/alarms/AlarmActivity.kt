package net.fallhammer.initiative.alarms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import initiative.core.AlarmState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import initiative.core.Escalation
import initiative.core.Formats
import initiative.core.Item
import kotlinx.coroutines.launch
import net.fallhammer.initiative.graph
import net.fallhammer.initiative.data.toItem
import net.fallhammer.initiative.data.toState
import net.fallhammer.initiative.R
import net.fallhammer.initiative.ui.Body
import net.fallhammer.initiative.ui.InitiativeTheme
import net.fallhammer.initiative.ui.Label
import net.fallhammer.initiative.ui.PrimaryButton
import net.fallhammer.initiative.ui.Screen
import net.fallhammer.initiative.ui.SecondaryButton
import net.fallhammer.initiative.ui.Space
import net.fallhammer.initiative.ui.Tap
import net.fallhammer.initiative.ui.Title
import net.fallhammer.initiative.ui.Type

/** Rung 3: shown full screen, over the lock screen, when a Firm or Hard anchor starts. */
class AlarmActivity : ComponentActivity() {
    private var shown by mutableStateOf<Pair<Item, AlarmState>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        load(intent)
        setContent {
            InitiativeTheme {
                shown?.let { (item, state) -> AlarmScreen(item, state) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        load(intent)
    }

    private fun load(intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        lifecycleScope.launch {
            val dao = graph.dao
            val item = dao.byId(id)?.toItem()
            val state = dao.alarm(id)?.toState() ?: AlarmState()
            if (item == null || !item.isOpen || state.ackedAt != null) finish() else shown = item to state
        }
    }

    @Composable
    private fun AlarmScreen(item: Item, state: AlarmState) {
        val left = Escalation.snoozesLeft(item, state)
        Screen {
            Column(Modifier.weight(1f).padding(horizontal = Space.s), verticalArrangement = Arrangement.Center) {
                Label(stringResource(R.string.now_label))
                Title(item.title, style = Type.hero)
                Body(Formats.anchorLine(item), style = Type.heading)
                Spacer(Modifier.height(Space.xxl * 1.5f))
                PrimaryButton(
                    stringResource(R.string.alarm_on_it),
                    { act { AlarmActions.onIt(this@AlarmActivity, item.id) } },
                    Modifier.fillMaxWidth(),
                    height = Tap.alarm,
                )
                if (left > 0) {
                    Spacer(Modifier.height(Space.l))
                    SecondaryButton(
                        pluralStringResource(R.plurals.alarm_snooze_left, left, left),
                        { act { AlarmActions.snooze(this@AlarmActivity, item.id) } },
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    private fun act(block: suspend () -> Unit) {
        lifecycleScope.launch {
            block()
            finish()
        }
    }

    companion object {
        private const val EXTRA_ID = "itemId"

        fun pending(context: Context, itemId: Long): PendingIntent = PendingIntent.getActivity(
            context,
            itemId.toInt(),
            Intent(context, AlarmActivity::class.java)
                .putExtra(EXTRA_ID, itemId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
