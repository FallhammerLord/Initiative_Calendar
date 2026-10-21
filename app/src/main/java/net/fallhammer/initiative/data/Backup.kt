package net.fallhammer.initiative.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import initiative.core.Ics
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
    val peers: List<PeerRow> = emptyList(),
    val shares: List<ShareRow> = emptyList(),
)

/**
 * The nightly backup: everything in the database as one JSON file, written to a
 * folder the person picked, where Syncthing or anything else can copy it on.
 * Beside it goes a calendar file of anchors, for any calendar app. The files
 * are replaced each night; versions are the syncing tool's job.
 */
object Backup {
    const val FILE_NAME = "initiative-backup.json"
    const val CALENDAR_NAME = "initiative-anchors.ics"

    /** Written just before a restore, so a restore can itself be undone. */
    const val BEFORE_RESTORE = "initiative-before-restore.json"
    private const val MIME = "application/json"
    private const val CALENDAR_MIME = "text/calendar"
    private const val FORMAT = 1

    /** The nightly set: the JSON backup and the calendar file. False when either can't be written. */
    suspend fun writeAll(context: Context, dao: ItemDao, folder: Uri, now: LocalDateTime): Boolean {
        val items = dao.all()
        val json = write(context, dao, folder, now)
        val calendar = writeFile(context, folder, CALENDAR_NAME, CALENDAR_MIME, Ics.calendar(items.map { it.toItem() }, now))
        return json && calendar
    }

    /** Writes the JSON backup; returns false when the folder can't be reached. */
    suspend fun write(context: Context, dao: ItemDao, folder: Uri, now: LocalDateTime, name: String = FILE_NAME): Boolean =
        writeFile(context, folder, name, MIME, json(dao.all(), dao.events(), dao.runs(), dao.alarms(), now, dao.peers(), dao.shares()).toString(2))

    private fun writeFile(context: Context, folder: Uri, name: String, mime: String, text: String): Boolean {
        return runCatching {
            val resolver = context.contentResolver
            val treeId = DocumentsContract.getTreeDocumentId(folder)
            val file = findChild(context, folder, treeId, name)
                ?: DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(folder, treeId), mime, name)
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
            dao.clearPeeks()
            dao.clearShares()
            dao.clearPeers()
            dao.clearAlarms()
            dao.clearRuns()
            dao.clearEvents()
            dao.clearItems()
            dao.insertAll(snapshot.items)
            dao.insertEvents(snapshot.events)
            dao.insertRuns(snapshot.runs)
            dao.insertAlarms(snapshot.alarms)
            dao.insertPeers(snapshot.peers)
            dao.insertShares(snapshot.shares)
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

    /**
     * The whole database as JSON, one array per table, with a format number for
     * restores. Pairs keep their nicknames, colors, and ticks; never keys or peeks,
     * so a restored phone pairs again.
     */
    fun json(
        items: List<ItemRow>,
        events: List<EventRow>,
        runs: List<RunRow>,
        alarms: List<AlarmRow>,
        now: LocalDateTime,
        peers: List<PeerRow> = emptyList(),
        shares: List<ShareRow> = emptyList(),
    ): JSONObject =
        JSONObject()
            .set("format", FORMAT)
            .set("exportedAt", now.toString())
            .set("items", JSONArray(items.map(::itemJson)))
            .set("events", JSONArray(events.map { JSONObject().set("id", it.id).set("itemId", it.itemId).set("fromStatus", it.fromStatus).set("toStatus", it.toStatus).set("at", it.at).set("note", it.note) }))
            .set("runs", JSONArray(runs.map { JSONObject().set("id", it.id).set("titleKey", it.titleKey).set("minutes", it.minutes).set("at", it.at) }))
            .set("alarms", JSONArray(alarms.map { JSONObject().set("itemId", it.itemId).set("ackedAt", it.ackedAt).set("snoozes", it.snoozes).set("snoozedUntil", it.snoozedUntil).set("lastRung", it.lastRung) }))
            .set("peers", JSONArray(peers.map(::peerJson)))
            .set("shares", JSONArray(shares.map { JSONObject().set("peer", it.peer).set("itemId", it.itemId) }))

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
            // Backups from before pairs have none.
            peers = root.optJSONArray("peers")?.objects()?.map(::peerRow).orEmpty(),
            shares = root.optJSONArray("shares")?.objects()?.map { ShareRow(it.getLong("peer"), it.getLong("itemId")) }.orEmpty(),
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
        .set("steps", r.steps).set("waitUntil", r.waitUntil).set("workedMinutes", r.workedMinutes).set("icon", r.icon)
        .set("neverShare", r.neverShare)

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
        // Backups from before steps have none.
        steps = o.str("steps").orEmpty(),
        waitUntil = o.str("waitUntil"),
        workedMinutes = o.optInt("workedMinutes", 0),
        // Backups from before markers have no icon.
        icon = o.str("icon") ?: "FLAG",
        // Backups from before pairs share nothing by default anyway.
        neverShare = o.optBoolean("neverShare", false),
    )

    private fun peerJson(p: PeerRow): JSONObject = JSONObject()
        .set("id", p.id).set("peerId", p.peerId).set("nickname", p.nickname).set("initial", p.initial).set("color", p.color)
        .set("level", p.level).set("shareReport", p.shareReport).set("showMark", p.showMark).set("showLine", p.showLine)
        .set("showMarks", p.showMarks).set("seenAt", p.seenAt)

    private fun peerRow(o: JSONObject) = PeerRow(
        id = o.getLong("id"),
        peerId = o.getString("peerId"),
        nickname = o.getString("nickname"),
        initial = o.getString("initial"),
        color = o.getInt("color"),
        level = o.getString("level"),
        shareReport = o.getBoolean("shareReport"),
        showMark = o.getBoolean("showMark"),
        showLine = o.getBoolean("showLine"),
        showMarks = o.getBoolean("showMarks"),
        seenAt = o.str("seenAt"),
    )

    /** A short name for the chosen folder, for the settings screen. */
    fun folderName(folder: String): String =
        Uri.parse(folder).lastPathSegment?.substringAfter(':')?.ifEmpty { null } ?: folder

    // Empty fields stay in the file as null, so every record lists every field.
    private fun JSONObject.set(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)

    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else getString(key)

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
}
