package net.fallhammer.initiative.ui

import android.app.Application
import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineScope
import net.fallhammer.initiative.AppGraph
import net.fallhammer.initiative.graph

/**
 * What each part of the board's state shares: the app and its services, the
 * view model's scope, and two hooks back to the board. [show] puts up a flash
 * line; [acted] marks that the person did something, which restarts the lapse clock.
 */
class BoardParts(
    val app: Application,
    val scope: CoroutineScope,
    val show: (Flash) -> Unit,
    val acted: () -> Unit,
) {
    val graph: AppGraph get() = app.graph

    fun text(@StringRes id: Int): String = app.getString(id)
}
