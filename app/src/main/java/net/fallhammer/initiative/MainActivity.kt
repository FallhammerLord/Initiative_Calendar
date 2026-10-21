package net.fallhammer.initiative

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import net.fallhammer.initiative.ui.BoardScreen
import net.fallhammer.initiative.ui.BoardViewModel
import net.fallhammer.initiative.ui.InitiativeTheme
import net.fallhammer.initiative.ui.Looks
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** The home screen. Every unlock lands here. */
class MainActivity : ComponentActivity() {
    private val vm: BoardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bars(light = false)
        setContent {
            val look by vm.settings.shown.collectAsStateWithLifecycle()
            val ornaments by vm.settings.ornaments.collectAsStateWithLifecycle()
            val texture by vm.settings.texture.collectAsStateWithLifecycle()
            // A light look gets dark status bar icons, so the clock and battery stay readable.
            val light = Looks.byId(look).palette.light
            LaunchedEffect(light) { bars(light) }
            InitiativeTheme(Looks.byId(look).let { if (ornaments) it.copy(texture = texture / 100f) else it.plain() }) { BoardScreen(vm) }
        }
    }

    private fun bars(light: Boolean) {
        val style = if (light) {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        } else {
            SystemBarStyle.dark(Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    // Recalculate on every open.
    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    // Pressing Home while already home goes back to the board.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.home()
    }
}
