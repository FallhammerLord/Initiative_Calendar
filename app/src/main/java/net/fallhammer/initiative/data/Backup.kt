package net.fallhammer.initiative.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/**
 * The nightly backup: everything in the database as one JSON file, written to a
 * folder the person picked, where Syncthing or anything else can copy it on.
 * The file is replaced each night; versions are the syncing tool's job.
 */
object Backup {
    const val FILE_NAME = "initiative-backup.json"
    private const val MIME = "application/json"
    private const val FORMAT = 1

    /** Writes the backup; returns false when the folder can't be reached. */
    suspend fun write(context: Context, dao: ItemDao, folder: Uri, now: LocalDateTime): Boolean {
        val text = json(dao.all(), dao.events(), dao.runs(), dao.alarms(), now).toString(2)
        return runCatching {
            val resolver = context.contentResolver
            val treeId = DocumentsContract.getTreeDocumentId(folder)
            val file = findChild(context, folder, treeId, FILE_NAME)
                ?: DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(folder, treeId), MIME, FILE_NAME)
                ?: return false
            resolver.openOutputStream(file, "wt")?.use { it.write(text.toByteArray()) } ?: return false
            true
        }.getOrDefault(false)
    }

    private fun findChild(context: Context, tree: Uri, parentId: String, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        context.contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name) return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
            }
        }
        return null
    }

    /** The whole database as JSON, one array per table, with a format number for future restores. */
    fun json(items: List<ItemRow>, events: List<EventRow>, runs: List<RunRow>, alarms: List<AlarmRow>, now: LocalDateTime): JSONObject =
        JSONObject()
            .set("format", FORMAT)
            .set("exportedAt", now.toString())
            .set("items", JSONArray(items.map(::itemJson)))
            .set("events", JSONArray(events.map { JSONObject().set("id", it.id).set("itemId", it.itemId).set("fromStatus", it.fromStatus).set("toStatus", it.toStatus).set("at", it.at).set("note", it.note) }))
            .set("runs", JSONArray(runs.map { JSONObject().set("id", it.id).set("titleKey", it.titleKey).set("minutes", it.minutes).set("at", it.at) }))
            .set("alarms", JSONArray(alarms.map { JSONObject().set("itemId", it.itemId).set("ackedAt", it.ackedAt).set("snoozes", it.snoozes).set("snoozedUntil", it.snoozedUntil).set("lastRung", it.lastRung) }))

    private fun itemJson(r: ItemRow) = JSONObject()
        .set("id", r.id).set("title", r.title).set("titleKey", r.titleKey).set("notes", r.notes).set("kind", r.kind)
        .set("start", r.start).set("end", r.end).set("wall", r.wall).set("leadMinutes", r.leadMinutes)
        .set("partnerAlert", r.partnerAlert).set("durationMinutes", r.durationMinutes)
        .set("earliestStart", r.earliestStart).set("deadline", r.deadline).set("priority", r.priority)
        .set("effort", r.effort).set("skipCount", r.skipCount).set("pinnedSlot", r.pinnedSlot)
        .set("notBefore", r.notBefore).set("recFreq", r.recFreq).set("recInterval", r.recInterval)
        .set("recDays", r.recDays).set("templateId", r.templateId).set("missPolicy", r.missPolicy)
        .set("status", r.status).set("createdAt", r.createdAt).set("doneAt", r.doneAt).set("activeSince", r.activeSince)

    // Empty fields stay in the file as null, so every record lists every field.
    private fun JSONObject.set(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)

    /** A short name for the chosen folder, for the settings screen. */
    fun folderName(folder: String): String =
        Uri.parse(folder).lastPathSegment?.substringAfter(':')?.ifEmpty { null } ?: folder
}
