package net.fallhammer.initiative

import android.content.Context
import initiative.core.DayWindow
import initiative.core.Placement
import net.fallhammer.initiative.data.Apps
import net.fallhammer.initiative.data.InitiativeDb
import net.fallhammer.initiative.data.Prefs
import net.fallhammer.initiative.data.Repository
import net.fallhammer.initiative.data.Setup
import net.fallhammer.initiative.pairs.LocalNet
import net.fallhammer.initiative.pairs.Nearby
import net.fallhammer.initiative.pairs.PairKeys
import net.fallhammer.initiative.pairs.PairSync

/**
 * The app's single set of services. Created once in [InitiativeApp]; screens,
 * receivers, and the scheduler all reach it through [Context.graph].
 */
class AppGraph(context: Context) {
    val window = DayWindow()
    val placement = Placement(window)
    val db = InitiativeDb.get(context)
    val dao = db.items()
    val repo = Repository(dao, placement)
    val prefs = Prefs(context)
    val apps = Apps(context)
    val setup = Setup(context)
    val nearby = Nearby(context)
    val localNet = LocalNet(context)
    val pairKeys = PairKeys()
    val pairSync = PairSync(dao, repo, placement, prefs, pairKeys::load)
}

val Context.graph: AppGraph get() = (applicationContext as InitiativeApp).graph
