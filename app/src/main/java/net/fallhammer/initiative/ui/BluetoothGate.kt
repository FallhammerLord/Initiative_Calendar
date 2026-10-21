package net.fallhammer.initiative.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import net.fallhammer.initiative.graph
import net.fallhammer.initiative.pairs.Nearby

/**
 * Before Pair or Ping: asks Android's Nearby devices permission the first time,
 * and to turn Bluetooth on when it's off, then goes ahead with [onReady]. Any
 * other problem (no Bluetooth at all) goes ahead too, and the model says so.
 */
// Turning Bluetooth on is only asked after Nearby devices is granted.
@SuppressLint("MissingPermission")
@Composable
fun rememberBluetoothGate(onReady: () -> Unit): () -> Unit {
    val nearby = LocalContext.current.graph.nearby
    val ready by rememberUpdatedState(onReady)
    val turnOn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (nearby.problem() == null) ready()
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        when {
            !granted.values.all { it } -> ready()
            nearby.problem() == Nearby.Problem.OFF -> turnOn.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            else -> ready()
        }
    }
    return {
        when (nearby.problem()) {
            Nearby.Problem.NOT_ALLOWED -> ask.launch(Nearby.PERMISSIONS)
            Nearby.Problem.OFF -> turnOn.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            else -> ready()
        }
    }
}
