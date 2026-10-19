package net.fallhammer.initiative.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import initiative.core.Effort
import initiative.core.Freq
import initiative.core.Item
import initiative.core.Kind
import initiative.core.MissPolicy
import initiative.core.Priority
import initiative.core.Recurrence
import initiative.core.Status
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
    val partnerAlert: Boolean,
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
)

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
}

@Database(entities = [ItemRow::class, EventRow::class, RunRow::class], version = 1, exportSchema = false)
abstract class InitiativeDb : RoomDatabase() {
    abstract fun items(): ItemDao

    companion object {
        @Volatile private var instance: InitiativeDb? = null

        fun get(context: Context): InitiativeDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, InitiativeDb::class.java, "initiative.db")
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
    partnerAlert = partnerAlert,
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
    partnerAlert = partnerAlert,
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
)
