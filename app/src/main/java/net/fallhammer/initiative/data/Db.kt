package net.fallhammer.initiative.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import initiative.core.AlarmState
import initiative.core.Effort
import initiative.core.Freq
import initiative.core.Item
import initiative.core.Kind
import initiative.core.MarkIcon
import initiative.core.MissPolicy
import initiative.core.Priority
import initiative.core.Recurrence
import initiative.core.Status
import initiative.core.Steps
import initiative.core.WallStrength
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** Flat row for [Item]. Dates are ISO strings and enums are names, so the file stays readable. */
@Entity(tableName = "items")
data class ItemRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val titleKey: String,
    val notes: String,
    val kind: String,
    val start: String?,
    val end: String?,
    val wall: String,
    val leadMinutes: Int,
    val durationMinutes: Int,
    val earliestStart: String?,
    val deadline: String?,
    val priority: String,
    val effort: String,
    val skipCount: Int,
    val pinnedSlot: String?,
    val notBefore: String?,
    val recFreq: String?,
    val recInterval: Int,
    val recDays: String,
    val templateId: Long?,
    val missPolicy: String,
    val status: String,
    val createdAt: String,
    val doneAt: String?,
    val activeSince: String?,
    /** See core's Steps: one per line. */
    val steps: String = "",
    val waitUntil: String? = null,
    val workedMinutes: Int = 0,
    /** A marker's icon; see core's MarkIcon. */
    val icon: String = "FLAG",
    /** Never shared with anyone paired. */
    val neverShare: Boolean = false,
    /** A side task: see core's Item.side. */
    val side: Boolean = false,
    /** A shared task's lasting id, the same on both phones; see core's SharedTasks. */
    val uid: String? = null,
    /** The paired person it's shared with. */
    val pairId: Long? = null,
    /** Made on this phone, so changeable here. */
    val owned: Boolean = true,
    /** The other person's box: done on their side. */
    val theirDone: Boolean = false,
    /** Just for the other person: not on this board. */
    val forThem: Boolean = false,
)

/**
 * Someone paired: their phone's own ID, and how this phone shows them and
 * shares with them. The nickname and color are this phone's alone. Keys are
 * kept in Android's key store, never here, so backups never hold them.
 */
@Entity(tableName = "peers")
data class PeerRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val peerId: String,
    val nickname: String,
    val initial: String,
    /** Which of the pair colors; see PairColors. */
    val color: Int,
    /** See core's ShareLevel. */
    val level: String = "TICKED",
    val shareReport: Boolean = false,
    val showMark: Boolean = true,
    val showLine: Boolean = true,
    val showMarks: Boolean = true,
    val seenAt: String? = null,
    /** Sync with them over Bluetooth whenever close by; works when both phones have it on. */
    val syncBluetooth: Boolean = false,
    /** Sync with them on the same Wi-Fi, local network only; works when both phones have it on. */
    val syncWifi: Boolean = false,
)

/** An item ticked to share with one person. */
@Entity(tableName = "shares", primaryKeys = ["peer", "itemId"])
data class ShareRow(val peer: Long, val itemId: Long)

/** The last peek from one person, as text; see core's PeekFormat. Read-only, and never backed up. */
@Entity(tableName = "peeks")
data class PeekRow(@PrimaryKey val peer: Long, val body: String)

/** The event log: every status change, with a timestamp. */
@Entity(tableName = "events")
data class EventRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val fromStatus: String?,
    val toStatus: String,
    val at: String,
    val note: String = "",
)

/** One timed run of a repeated task, for duration learning. */
@Entity(tableName = "runs")
data class RunRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titleKey: String,
    val minutes: Int,
    val at: String,
)

/** Escalation progress for one anchor. */
@Entity(tableName = "alarms")
data class AlarmRow(
    @PrimaryKey val itemId: Long,
    val ackedAt: String?,
    val snoozes: Int,
    val snoozedUntil: String?,
    val lastRung: Int,
)

fun AlarmRow.toState() = AlarmState(
    ackedAt = ackedAt?.let(LocalDateTime::parse),
    snoozes = snoozes,
    snoozedUntil = snoozedUntil?.let(LocalDateTime::parse),
    lastRung = lastRung,
)

fun AlarmState.toRow(itemId: Long) = AlarmRow(itemId, ackedAt?.toString(), snoozes, snoozedUntil?.toString(), lastRung)

@Dao
interface ItemDao {
    @Query("SELECT * FROM items")
    fun observeAll(): Flow<List<ItemRow>>

    @Query("SELECT * FROM items")
    suspend fun all(): List<ItemRow>

    @Query("SELECT * FROM items WHERE titleKey = :key ORDER BY id DESC LIMIT 1")
    suspend fun latestWithKey(key: String): ItemRow?

    @Insert
    suspend fun insert(row: ItemRow): Long

    @Insert
    suspend fun insertAll(rows: List<ItemRow>)

    @Update
    suspend fun update(row: ItemRow)

    @Query("UPDATE items SET durationMinutes = :minutes WHERE titleKey = :key AND (status IN ('PENDING', 'ACTIVE') OR recFreq IS NOT NULL)")
    suspend fun setDuration(key: String, minutes: Int)

    @Insert
    suspend fun insertEvent(row: EventRow)

    @Insert
    suspend fun insertRun(row: RunRow)

    @Query("SELECT minutes FROM runs WHERE titleKey = :key ORDER BY id DESC LIMIT 10")
    suspend fun recentRuns(key: String): List<Int>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun byId(id: Long): ItemRow?

    @Query("SELECT * FROM alarms")
    suspend fun alarms(): List<AlarmRow>

    @Query("SELECT * FROM events ORDER BY id")
    suspend fun events(): List<EventRow>

    @Query("SELECT * FROM runs ORDER BY id")
    suspend fun runs(): List<RunRow>

    /** Items that got a given event note since a time, such as automatic skips. */
    @Query("SELECT DISTINCT itemId FROM events WHERE note = :note AND at >= :since")
    suspend fun itemsWithNote(note: String, since: String): List<Long>

    @Query("SELECT * FROM alarms WHERE itemId = :id")
    suspend fun alarm(id: Long): AlarmRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAlarm(row: AlarmRow)

    /**
     * Removes a series' open times from [from] (a date) onward, except [keep], so
     * they can be made again from the edited series. Done and missed times stay.
     */
    @Query(
        "DELETE FROM items WHERE templateId = :templateId AND id != :keep AND status IN ('PENDING', 'ACTIVE') " +
            "AND COALESCE(start, earliestStart) >= :from",
    )
    suspend fun deleteOpenInstances(templateId: Long, from: String, keep: Long)

    /**
     * Once, when Quick tasks' default dropped to 15 minutes: open Quick tasks still
     * on the old default move down, unless a real length has been learned for them.
     */
    @Query(
        "UPDATE items SET durationMinutes = :to WHERE effort = 'QUICK' AND durationMinutes = :from " +
            "AND status IN ('PENDING', 'ACTIVE') AND titleKey NOT IN (SELECT titleKey FROM runs)",
    )
    suspend fun shortenQuickDefaults(from: Int, to: Int)

    /** Deleted for good: off the ledger, as if it never was. */
    @Query("DELETE FROM items WHERE id IN (:ids)")
    suspend fun deleteItems(ids: List<Long>)

    @Query("DELETE FROM events WHERE itemId = :id")
    suspend fun deleteEventsOf(id: Long)

    /** Forgets an anchor's alarm progress, so the ladder runs again for a new time. */
    @Query("DELETE FROM alarms WHERE itemId = :id")
    suspend fun clearAlarm(id: Long)

    // Restore: empty every table, then put the backup's rows back with their ids.
    @Query("DELETE FROM items")
    suspend fun clearItems()

    @Query("DELETE FROM events")
    suspend fun clearEvents()

    @Query("DELETE FROM runs")
    suspend fun clearRuns()

    @Query("DELETE FROM alarms")
    suspend fun clearAlarms()

    @Insert
    suspend fun insertEvents(rows: List<EventRow>)

    @Insert
    suspend fun insertRuns(rows: List<RunRow>)

    @Insert
    suspend fun insertAlarms(rows: List<AlarmRow>)

    // Pairs: the people, what's ticked for each, and their last peeks.
    @Query("SELECT * FROM peers ORDER BY id")
    fun observePeers(): Flow<List<PeerRow>>

    @Query("SELECT * FROM peers ORDER BY id")
    suspend fun peers(): List<PeerRow>

    @Insert
    suspend fun insertPeer(row: PeerRow): Long

    @Update
    suspend fun updatePeer(row: PeerRow)

    @Query("DELETE FROM peers WHERE id = :id")
    suspend fun deletePeer(id: Long)

    @Query("SELECT * FROM shares")
    fun observeShares(): Flow<List<ShareRow>>

    @Query("SELECT * FROM shares")
    suspend fun shares(): List<ShareRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun share(row: ShareRow)

    @Query("DELETE FROM shares WHERE peer = :peer AND itemId = :itemId")
    suspend fun unshare(peer: Long, itemId: Long)

    @Query("DELETE FROM shares WHERE peer = :peer")
    suspend fun deleteSharesOf(peer: Long)

    @Query("SELECT * FROM peeks")
    fun observePeeks(): Flow<List<PeekRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPeek(row: PeekRow)

    @Query("DELETE FROM peeks WHERE peer = :peer")
    suspend fun deletePeekOf(peer: Long)

    @Query("DELETE FROM peers")
    suspend fun clearPeers()

    @Query("DELETE FROM shares")
    suspend fun clearShares()

    @Query("DELETE FROM peeks")
    suspend fun clearPeeks()

    @Insert
    suspend fun insertPeers(rows: List<PeerRow>)

    @Insert
    suspend fun insertShares(rows: List<ShareRow>)
}

@Database(
    entities = [ItemRow::class, EventRow::class, RunRow::class, AlarmRow::class, PeerRow::class, ShareRow::class, PeekRow::class],
    version = 8,
    exportSchema = true,
)
abstract class InitiativeDb : RoomDatabase() {
    abstract fun items(): ItemDao

    companion object {
        @Volatile private var instance: InitiativeDb? = null

        internal val ADD_ALARMS = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `alarms` (`itemId` INTEGER NOT NULL, `ackedAt` TEXT, " +
                        "`snoozes` INTEGER NOT NULL, `snoozedUntil` TEXT, `lastRung` INTEGER NOT NULL, PRIMARY KEY(`itemId`))",
                )
            }
        }

        internal val ADD_STEPS = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `items` ADD COLUMN `steps` TEXT NOT NULL DEFAULT ''")
            }
        }

        internal val ADD_WAITS = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `items` ADD COLUMN `waitUntil` TEXT")
                db.execSQL("ALTER TABLE `items` ADD COLUMN `workedMinutes` INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val ADD_MARKS = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `items` ADD COLUMN `icon` TEXT NOT NULL DEFAULT 'FLAG'")
            }
        }

        internal val ADD_PAIRS = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `items` ADD COLUMN `neverShare` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `peers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `peerId` TEXT NOT NULL, " +
                        "`nickname` TEXT NOT NULL, `initial` TEXT NOT NULL, `color` INTEGER NOT NULL, `level` TEXT NOT NULL, " +
                        "`shareReport` INTEGER NOT NULL, `showMark` INTEGER NOT NULL, `showLine` INTEGER NOT NULL, " +
                        "`showMarks` INTEGER NOT NULL, `seenAt` TEXT)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `shares` (`peer` INTEGER NOT NULL, `itemId` INTEGER NOT NULL, PRIMARY KEY(`peer`, `itemId`))",
                )
                db.execSQL("CREATE TABLE IF NOT EXISTS `peeks` (`peer` INTEGER NOT NULL, `body` TEXT NOT NULL, PRIMARY KEY(`peer`))")
            }
        }

        /**
         * Side tasks get their flag; the partner alert, never used, goes. SQLite
         * can't drop a column on older phones, so the table is copied without it.
         */
        internal val ADD_SIDE = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE `items_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `titleKey` TEXT NOT NULL, `notes` TEXT NOT NULL, `kind` TEXT NOT NULL, `start` TEXT, `end` TEXT, `wall` TEXT NOT NULL, `leadMinutes` INTEGER NOT NULL, `durationMinutes` INTEGER NOT NULL, `earliestStart` TEXT, `deadline` TEXT, `priority` TEXT NOT NULL, `effort` TEXT NOT NULL, `skipCount` INTEGER NOT NULL, `pinnedSlot` TEXT, `notBefore` TEXT, `recFreq` TEXT, `recInterval` INTEGER NOT NULL, `recDays` TEXT NOT NULL, `templateId` INTEGER, `missPolicy` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `doneAt` TEXT, `activeSince` TEXT, `steps` TEXT NOT NULL, `waitUntil` TEXT, `workedMinutes` INTEGER NOT NULL, `icon` TEXT NOT NULL, `neverShare` INTEGER NOT NULL, `side` INTEGER NOT NULL DEFAULT 0" +
                        ")",
                )
                db.execSQL("INSERT INTO `items_new` (`id`, `title`, `titleKey`, `notes`, `kind`, `start`, `end`, `wall`, `leadMinutes`, `durationMinutes`, `earliestStart`, `deadline`, `priority`, `effort`, `skipCount`, `pinnedSlot`, `notBefore`, `recFreq`, `recInterval`, `recDays`, `templateId`, `missPolicy`, `status`, `createdAt`, `doneAt`, `activeSince`, `steps`, `waitUntil`, `workedMinutes`, `icon`, `neverShare`) SELECT `id`, `title`, `titleKey`, `notes`, `kind`, `start`, `end`, `wall`, `leadMinutes`, `durationMinutes`, `earliestStart`, `deadline`, `priority`, `effort`, `skipCount`, `pinnedSlot`, `notBefore`, `recFreq`, `recInterval`, `recDays`, `templateId`, `missPolicy`, `status`, `createdAt`, `doneAt`, `activeSince`, `steps`, `waitUntil`, `workedMinutes`, `icon`, `neverShare` FROM `items`")
                db.execSQL("DROP TABLE `items`")
                db.execSQL("ALTER TABLE `items_new` RENAME TO `items`")
            }
        }

        /** Shared tasks: their lasting id, who they're shared with, whose they are, and the other box. */
        internal val ADD_SHARED = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `items` ADD COLUMN `uid` TEXT")
                db.execSQL("ALTER TABLE `items` ADD COLUMN `pairId` INTEGER")
                db.execSQL("ALTER TABLE `items` ADD COLUMN `owned` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `items` ADD COLUMN `theirDone` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `items` ADD COLUMN `forThem` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `peers` ADD COLUMN `syncBluetooth` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `peers` ADD COLUMN `syncWifi` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): InitiativeDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, InitiativeDb::class.java, "initiative.db")
                .addMigrations(ADD_ALARMS, ADD_STEPS, ADD_WAITS, ADD_MARKS, ADD_PAIRS, ADD_SIDE, ADD_SHARED)
                .build()
                .also { instance = it }
        }
    }
}

fun Item.toRow() = ItemRow(
    id = id,
    title = title,
    titleKey = titleKey,
    notes = notes,
    kind = kind.name,
    start = start?.toString(),
    end = end?.toString(),
    wall = wall.name,
    leadMinutes = leadMinutes,
    durationMinutes = durationMinutes,
    earliestStart = earliestStart?.toString(),
    deadline = deadline?.toString(),
    priority = priority.name,
    effort = effort.name,
    skipCount = skipCount,
    pinnedSlot = pinnedSlot?.toString(),
    notBefore = notBefore?.toString(),
    recFreq = recurrence?.freq?.name,
    recInterval = recurrence?.interval ?: 1,
    recDays = recurrence?.days?.joinToString(",") { it.name } ?: "",
    templateId = templateId,
    missPolicy = missPolicy.name,
    status = status.name,
    createdAt = createdAt.toString(),
    doneAt = doneAt?.toString(),
    activeSince = activeSince?.toString(),
    steps = Steps.encode(steps),
    waitUntil = waitUntil?.toString(),
    workedMinutes = workedMinutes,
    icon = icon.name,
    neverShare = neverShare,
    side = side,
    uid = uid,
    pairId = pairId,
    owned = owned,
    theirDone = theirDone,
    forThem = forThem,
)

fun ItemRow.toItem() = Item(
    id = id,
    title = title,
    notes = notes,
    kind = Kind.valueOf(kind),
    start = start?.let(LocalDateTime::parse),
    end = end?.let(LocalDateTime::parse),
    wall = WallStrength.valueOf(wall),
    leadMinutes = leadMinutes,
    durationMinutes = durationMinutes,
    earliestStart = earliestStart?.let(LocalDate::parse),
    deadline = deadline?.let(LocalDate::parse),
    priority = Priority.valueOf(priority),
    effort = Effort.valueOf(effort),
    skipCount = skipCount,
    pinnedSlot = pinnedSlot?.let(LocalDateTime::parse),
    notBefore = notBefore?.let(LocalDateTime::parse),
    recurrence = recFreq?.let { f ->
        Recurrence(
            Freq.valueOf(f),
            recInterval,
            recDays.split(",").filter { it.isNotEmpty() }.map(DayOfWeek::valueOf).toSet(),
        )
    },
    templateId = templateId,
    missPolicy = MissPolicy.valueOf(missPolicy),
    status = Status.valueOf(status),
    createdAt = LocalDateTime.parse(createdAt),
    doneAt = doneAt?.let(LocalDateTime::parse),
    activeSince = activeSince?.let(LocalDateTime::parse),
    steps = Steps.decode(steps),
    waitUntil = waitUntil?.let(LocalDateTime::parse),
    workedMinutes = workedMinutes,
    icon = runCatching { MarkIcon.valueOf(icon) }.getOrDefault(MarkIcon.FLAG),
    neverShare = neverShare,
    side = side,
    uid = uid,
    pairId = pairId,
    owned = owned,
    theirDone = theirDone,
    forThem = forThem,
)
