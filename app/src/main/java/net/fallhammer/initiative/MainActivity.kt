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

/** The home screen. Every unlock lands here. */
class MainActivity : ComponentActivity() {
    private val vm: BoardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            InitiativeTheme { BoardScreen(vm) }
        }
    }

    // Recalculate on every open.
    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    // Pressing Home while already home closes the drawer.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.setDrawer(false)
    }
}
