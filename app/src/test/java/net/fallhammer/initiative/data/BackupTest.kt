package net.fallhammer.initiative.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/** The backup file holds every table, every field, and a format number. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupTest {

    @Test
    fun jsonHoldsEveryTable() {
        val item = ItemRow(
            id = 7, title = "call landlord", titleKey = "call landlord", notes = "", kind = "FLOAT", start = null, end = null,
            wall = "FIRM", leadMinutes = 15, partnerAlert = false, durationMinutes = 30, earliestStart = null, deadline = "2026-10-23",
            priority = "AUTO", effort = "QUICK", skipCount = 2, pinnedSlot = null, notBefore = null, recFreq = null, recInterval = 1,
            recDays = "", templateId = null, missPolicy = "REROLL", status = "PENDING", createdAt = "2026-10-19T09:00",
            doneAt = null, activeSince = null,
        )
        val json = Backup.json(
            listOf(item),
            listOf(EventRow(1, 7, null, "PENDING", "2026-10-19T09:00", "auto-skip")),
            listOf(RunRow(1, "call landlord", 25, "2026-10-19T10:00")),
            listOf(AlarmRow(7, null, 1, null, 2)),
            LocalDateTime.of(2026, 10, 20, 3, 0),
        )
        // Through text and back, as it's read on restore.
        val back = JSONObject(json.toString(2))
        assertEquals(1, back.getInt("format"))
        assertEquals("2026-10-20T03:00", back.getString("exportedAt"))
        val row = back.getJSONArray("items").getJSONObject(0)
        assertEquals("call landlord", row.getString("title"))
        assertEquals(2, row.getInt("skipCount"))
        assertEquals("2026-10-23", row.getString("deadline"))
        assertTrue("empty fields stay as null", row.has("start") && row.isNull("start"))
        assertEquals("auto-skip", back.getJSONArray("events").getJSONObject(0).getString("note"))
        assertEquals(25, back.getJSONArray("runs").getJSONObject(0).getInt("minutes"))
        assertEquals(2, back.getJSONArray("alarms").getJSONObject(0).getInt("lastRung"))
    }
}
