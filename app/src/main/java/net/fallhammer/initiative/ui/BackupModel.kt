package net.fallhammer.initiative.ui

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.fallhammer.initiative.R
import net.fallhammer.initiative.alarms.Notifier
import net.fallhammer.initiative.alarms.Scheduler
import net.fallhammer.initiative.data.Backup
import net.fallhammer.initiative.data.Prefs
import net.fallhammer.initiative.data.Snapshot
import java.time.LocalDateTime

/**
 * The backup setting as shown: the chosen folder's short name, the last backup,
 * and, while a restore waits to be confirmed, when that backup was made.
 */
data class BackupState(val folder: String? = null, val last: LocalDateTime? = null, val restoreFrom: LocalDateTime? = null)

/**
 * The nightly backup's folder, Back up now, and restore. The nightly run itself
 * is in the Scheduler.
 */
class BackupModel(private val parts: BoardParts) {
    private val graph get() = parts.graph

    /** A backup read in and waiting for Replace everything. Not kept across a restart: pick the file again. */
    private var pending: Snapshot? = null

    private val _state = MutableStateFlow(BackupState())
    val state: StateFlow<BackupState> = _state.asStateFlow()

    /** Call off the main thread. */
    fun load(prefs: Prefs) {
        _state.value = _state.value.copy(folder = prefs.backupFolder?.let(Backup::folderName), last = prefs.lastBackup)
    }

    fun setFolder(folder: Uri) {
        runCatching {
            parts.app.contentResolver.takePersistableUriPermission(
                folder,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        parts.scope.launch {
            withContext(Dispatchers.IO) { graph.prefs.backupFolder = folder.toString() }
            backUpNow()
        }
    }

    fun backUpNow() {
        parts.scope.launch {
            val now = LocalDateTime.now()
            val ok = withContext(Dispatchers.IO) {
                val prefs = graph.prefs
                val folder = prefs.backupFolder ?: return@withContext false
                Backup.write(parts.app, graph.dao, Uri.parse(folder), now).also { if (it) prefs.lastBackup = now }
                    .also { load(prefs) }
            }
            parts.show(Flash(parts.text(if (ok) R.string.backup_written else R.string.backup_failed)))
        }
    }

    /** Reads a chosen backup file and asks before replacing anything. */
    fun pickRestore(file: Uri) {
        parts.scope.launch {
            val snapshot = withContext(Dispatchers.IO) { Backup.read(parts.app, file) }
            if (snapshot == null) {
                parts.show(Flash(parts.text(R.string.restore_not_backup)))
                return@launch
            }
            pending = snapshot
            _state.value = _state.value.copy(restoreFrom = snapshot.exportedAt)
        }
    }

    fun cancelRestore() {
        pending = null
        _state.value = _state.value.copy(restoreFrom = null)
    }

    /**
     * Replaces everything with the chosen backup. What was there is saved first
     * to the backup folder, when one is chosen, as initiative-before-restore.json.
     */
    fun confirmRestore() {
        val snapshot = pending ?: return
        cancelRestore()
        parts.scope.launch {
            withContext(Dispatchers.IO) {
                val prefs = graph.prefs
                prefs.backupFolder?.let { Backup.write(parts.app, graph.dao, Uri.parse(it), LocalDateTime.now(), Backup.BEFORE_RESTORE) }
                graph.dao.alarms().forEach { Notifier.cancelRung(parts.app, it.itemId) }
                Backup.restore(graph.db, snapshot)
                prefs.focus = null
                prefs.shown = null
            }
            Scheduler.sync(parts.app)
            parts.show(Flash(parts.text(R.string.restore_done)))
        }
    }
}
