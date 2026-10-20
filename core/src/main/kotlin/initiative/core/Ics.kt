package initiative.core

import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Anchors as an iCalendar file, written beside the nightly backup so any
 * calendar app can read the day's walls. Times are written as floating local
 * times, matching how the app stores them.
 */
object Ics {
    private const val LINE_LIMIT = 75

    fun calendar(items: List<Item>, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): String {
        val lines = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Initiative//Initiative//EN", "CALSCALE:GREGORIAN")
        val stamp = Formats.icsUtc(WallClock.instant(now, zone))
        items
            .filter { it.isAnchor && !it.isTemplate && it.start != null && it.status != Status.DROPPED }
            .sortedBy { it.start }
            .forEach { a ->
                lines += "BEGIN:VEVENT"
                lines += "UID:initiative-${a.id}"
                lines += "DTSTAMP:$stamp"
                lines += "DTSTART:${Formats.icsLocal(a.start!!)}"
                lines += "DTEND:${Formats.icsLocal(a.anchorEnd!!)}"
                lines += "SUMMARY:${escape(a.title)}"
                if (a.notes.isNotBlank()) lines += "DESCRIPTION:${escape(a.notes)}"
                lines += "CATEGORIES:${Formats.wall(a.wall)}"
                lines += "END:VEVENT"
            }
        lines += "END:VCALENDAR"
        return lines.joinToString("") { fold(it) + "\r\n" }
    }

    /** Text values escape backslashes, commas, semicolons, and line breaks. */
    internal fun escape(text: String): String =
        text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n")

    /** Lines longer than 75 bytes continue on the next line, which starts with a space. */
    internal fun fold(line: String): String {
        if (line.toByteArray().size <= LINE_LIMIT) return line
        val out = StringBuilder()
        var count = 0
        var limit = LINE_LIMIT
        var i = 0
        while (i < line.length) {
            // Never split a character in two: step by whole code points.
            val cp = line.codePointAt(i)
            val chars = Character.charCount(cp)
            val bytes = String(Character.toChars(cp)).toByteArray().size
            if (count + bytes > limit) {
                out.append("\r\n ")
                count = 0
                limit = LINE_LIMIT - 1
            }
            out.append(line, i, i + chars)
            count += bytes
            i += chars
        }
        return out.toString()
    }
}
