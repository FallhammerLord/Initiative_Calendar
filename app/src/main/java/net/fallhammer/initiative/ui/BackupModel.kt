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
import net.fallhammer.initiative.data.Backup
import net.fallhammer.initiative.data.Prefs
import java.time.LocalDateTime

/** The backup setting as shown: the chosen folder's short name and the last backup. */
data class BackupState(val folder: String? = null, val last: LocalDateTime? = null)

/** The nightly backup's folder, and Back up now. The nightly run itself is in the Scheduler. */
class BackupModel(private val parts: BoardParts) {
    private val graph get() = parts.graph

    private val _state = MutableStateFlow(BackupState())
    val state: StateFlow<BackupState> = _state.asStateFlow()

    /** Call off the main thread. */
    fun load(prefs: Prefs) {
        _state.value = BackupState(prefs.backupFolder?.let(Backup::folderName), prefs.lastBackup)
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
}
