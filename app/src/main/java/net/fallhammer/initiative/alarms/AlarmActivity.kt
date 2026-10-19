package net.fallhammer.initiative.alarms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import initiative.core.AlarmState
import initiative.core.Escalation
import initiative.core.Item
import kotlinx.coroutines.launch
import net.fallhammer.initiative.data.InitiativeDb
import net.fallhammer.initiative.data.toItem
import net.fallhammer.initiative.data.toState
import net.fallhammer.initiative.ui.Ink
import net.fallhammer.initiative.ui.InitiativeTheme
import java.time.format.DateTimeFormatter

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
            val dao = InitiativeDb.get(this@AlarmActivity).items()
            val item = dao.byId(id)?.toItem()
            val state = dao.alarm(id)?.toState() ?: AlarmState()
            if (item == null || !item.isOpen || state.ackedAt != null) finish() else shown = item to state
        }
    }

    @androidx.compose.runtime.Composable
    private fun AlarmScreen(item: Item, state: AlarmState) {
        val left = Escalation.snoozesLeft(item, state)
        Column(
            Modifier.fillMaxSize().background(Ink.bg).systemBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("NOW", fontSize = 16.sp, color = Ink.dim, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(item.title, fontSize = 48.sp, lineHeight = 54.sp, fontWeight = FontWeight.Bold, color = Ink.text)
            Text(
                "${item.start?.format(CLOCK).orEmpty()} · ${Notifier.wallLabel(item.wall)}",
                fontSize = 22.sp,
                color = Ink.dim,
            )
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = { act { AlarmActions.onIt(this, item.id) } },
                modifier = Modifier.fillMaxWidth().height(88.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink.accent, contentColor = Ink.bg),
            ) { Text("On it", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
            if (left > 0) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { act { AlarmActions.snooze(this, item.id) } },
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                ) { Text("Snooze 5 min ($left left)", fontSize = 22.sp, color = Ink.text) }
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
        private val CLOCK = DateTimeFormatter.ofPattern("h:mm a")

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
