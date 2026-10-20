package net.fallhammer.initiative.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/** A backup file read back in, ready to restore. */
class Snapshot(
    val exportedAt: LocalDateTime,
    val items: List<ItemRow>,
    val events: List<EventRow>,
    val runs: List<RunRow>,
    val alarms: List<AlarmRow>,
)

/**
 * The nightly backup: everything in the database as one JSON file, written to a
 * folder the person picked, where Syncthing or anything else can copy it on.
 * The file is replaced each night; versions are the syncing tool's job.
 */
object Backup {
    const val FILE_NAME = "initiative-backup.json"

    /** Written just before a restore, so a restore can itself be undone. */
    const val BEFORE_RESTORE = "initiative-before-restore.json"
    private const val MIME = "application/json"
    private const val FORMAT = 1

    /** Writes the backup; returns false when the folder can't be reached. */
    suspend fun write(context: Context, dao: ItemDao, folder: Uri, now: LocalDateTime, name: String = FILE_NAME): Boolean {
        val text = json(dao.all(), dao.events(), dao.runs(), dao.alarms(), now).toString(2)
        return runCatching {
            val resolver = context.contentResolver
            val treeId = DocumentsContract.getTreeDocumentId(folder)
            val file = findChild(context, folder, treeId, name)
                ?: DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(folder, treeId), MIME, name)
                ?: return false
            resolver.openOutputStream(file, "wt")?.use { it.write(text.toByteArray()) } ?: return false
            true
        }.getOrDefault(false)
    }

    /** Reads a backup file; null when it isn't one this app can restore. */
    fun read(context: Context, file: Uri): Snapshot? = runCatching {
        context.contentResolver.openInputStream(file)?.use { it.readBytes().decodeToString() }?.let(::parse)
    }.getOrNull()

    /** Replaces everything in the database with [snapshot], all at once or not at all. */
    suspend fun restore(db: InitiativeDb, snapshot: Snapshot) {
        val dao = db.items()
        db.withTransaction {
            dao.clearAlarms()
            dao.clearRuns()
            dao.clearEvents()
            dao.clearItems()
            dao.insertAll(snapshot.items)
            dao.insertEvents(snapshot.events)
            dao.insertRuns(snapshot.runs)
            dao.insertAlarms(snapshot.alarms)
        }
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

    /** The whole database as JSON, one array per table, with a format number for restores. */
    fun json(items: List<ItemRow>, events: List<EventRow>, runs: List<RunRow>, alarms: List<AlarmRow>, now: LocalDateTime): JSONObject =
        JSONObject()
            .set("format", FORMAT)
            .set("exportedAt", now.toString())
            .set("items", JSONArray(items.map(::itemJson)))
            .set("events", JSONArray(events.map { JSONObject().set("id", it.id).set("itemId", it.itemId).set("fromStatus", it.fromStatus).set("toStatus", it.toStatus).set("at", it.at).set("note", it.note) }))
            .set("runs", JSONArray(runs.map { JSONObject().set("id", it.id).set("titleKey", it.titleKey).set("minutes", it.minutes).set("at", it.at) }))
            .set("alarms", JSONArray(alarms.map { JSONObject().set("itemId", it.itemId).set("ackedAt", it.ackedAt).set("snoozes", it.snoozes).set("snoozedUntil", it.snoozedUntil).set("lastRung", it.lastRung) }))

    /** The backup's text back as rows. Throws on anything that isn't a format-1 backup. */
    fun parse(text: String): Snapshot {
        val root = JSONObject(text)
        require(root.getInt("format") == FORMAT) { "unknown backup format" }
        return Snapshot(
            exportedAt = LocalDateTime.parse(root.getString("exportedAt")),
            items = root.getJSONArray("items").objects().map(::itemRow),
            events = root.getJSONArray("events").objects().map {
                EventRow(it.getLong("id"), it.getLong("itemId"), it.str("fromStatus"), it.getString("toStatus"), it.getString("at"), it.str("note").orEmpty())
            },
            runs = root.getJSONArray("runs").objects().map { RunRow(it.getLong("id"), it.getString("titleKey"), it.getInt("minutes"), it.getString("at")) },
            alarms = root.getJSONArray("alarms").objects().map {
                AlarmRow(it.getLong("itemId"), it.str("ackedAt"), it.getInt("snoozes"), it.str("snoozedUntil"), it.getInt("lastRung"))
            },
        )
    }

    /** One item as JSON; also how the edit form keeps a draft across a restart. */
    fun itemJson(r: ItemRow): JSONObject = JSONObject()
        .set("id", r.id).set("title", r.title).set("titleKey", r.titleKey).set("notes", r.notes).set("kind", r.kind)
        .set("start", r.start).set("end", r.end).set("wall", r.wall).set("leadMinutes", r.leadMinutes)
        .set("partnerAlert", r.partnerAlert).set("durationMinutes", r.durationMinutes)
        .set("earliestStart", r.earliestStart).set("deadline", r.deadline).set("priority", r.priority)
        .set("effort", r.effort).set("skipCount", r.skipCount).set("pinnedSlot", r.pinnedSlot)
        .set("notBefore", r.notBefore).set("recFreq", r.recFreq).set("recInterval", r.recInterval)
        .set("recDays", r.recDays).set("templateId", r.templateId).set("missPolicy", r.missPolicy)
        .set("status", r.status).set("createdAt", r.createdAt).set("doneAt", r.doneAt).set("activeSince", r.activeSince)

    fun itemRow(o: JSONObject) = ItemRow(
        id = o.getLong("id"),
        title = o.getString("title"),
        titleKey = o.getString("titleKey"),
        notes = o.str("notes").orEmpty(),
        kind = o.getString("kind"),
        start = o.str("start"),
        end = o.str("end"),
        wall = o.getString("wall"),
        leadMinutes = o.getInt("leadMinutes"),
        partnerAlert = o.getBoolean("partnerAlert"),
        durationMinutes = o.getInt("durationMinutes"),
        earliestStart = o.str("earliestStart"),
        deadline = o.str("deadline"),
        priority = o.getString("priority"),
        effort = o.getString("effort"),
        skipCount = o.getInt("skipCount"),
        pinnedSlot = o.str("pinnedSlot"),
        notBefore = o.str("notBefore"),
        recFreq = o.str("recFreq"),
        recInterval = o.getInt("recInterval"),
        recDays = o.str("recDays").orEmpty(),
        templateId = if (o.isNull("templateId")) null else o.getLong("templateId"),
        missPolicy = o.getString("missPolicy"),
        status = o.getString("status"),
        createdAt = o.getString("createdAt"),
        doneAt = o.str("doneAt"),
        activeSince = o.str("activeSince"),
    )

    /** A short name for the chosen folder, for the settings screen. */
    fun folderName(folder: String): String =
        Uri.parse(folder).lastPathSegment?.substringAfter(':')?.ifEmpty { null } ?: folder

    // Empty fields stay in the file as null, so every record lists every field.
    private fun JSONObject.set(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)

    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else getString(key)

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
}
