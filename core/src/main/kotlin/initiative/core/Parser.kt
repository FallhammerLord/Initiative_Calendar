package initiative.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Result of parsing one quick-add line. [unsureKind] means the parser could not
 * tell anchor from float and the board should ask with a tap chip.
 * The explicit flags say which fields the text set, so repeated items can
 * inherit the rest.
 */
data class Parsed(
    val item: Item,
    val unsureKind: Boolean = false,
    val explicitPriority: Boolean = false,
    val explicitEffort: Boolean = false,
    val explicitDuration: Boolean = false,
)

/**
 * The rule-based baseline parser. A time makes an anchor, no time makes a float.
 * Keywords set the extra fields; whatever is left becomes the title.
 */
object Parser {
    private val DAYS = mapOf(
        "mon" to DayOfWeek.MONDAY, "monday" to DayOfWeek.MONDAY,
        "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "tuesday" to DayOfWeek.TUESDAY,
        "wed" to DayOfWeek.WEDNESDAY, "weds" to DayOfWeek.WEDNESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "thursday" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY, "friday" to DayOfWeek.FRIDAY,
        "sat" to DayOfWeek.SATURDAY, "saturday" to DayOfWeek.SATURDAY,
        "sun" to DayOfWeek.SUNDAY, "sunday" to DayOfWeek.SUNDAY,
    )
    private val TODAY = setOf("today", "tonight")
    private val TOMORROW = setOf("tomorrow", "tmrw", "tmr")
    private val MONTH_NAMES = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december",
    )
    private val DAY_OF_MONTH = Regex("""^(\d{1,2})(st|nd|rd|th)?$""")
    private val SLASH_DATE = Regex("""^(\d{1,2})/(\d{1,2})(?:/(\d{2}|\d{4}))?$""")
    private val TIME = Regex("""^(\d{1,2})(?::(\d{2}))?(am|pm|a|p)?$""")
    private val DURATION = Regex("""^(\d+)(m|min|mins|h|hr|hrs)$""")
    private val MINUTE_WORDS = setOf("m", "min", "mins", "minute", "minutes")
    private val HOUR_WORDS = setOf("h", "hr", "hrs", "hour", "hours")

    /**
     * [on] is the day the line is added to when the text names none, as from a
     * day in the Log; a day in the text still wins.
     */
    fun parse(text: String, now: LocalDateTime, on: LocalDate? = null): Parsed? {
        val raw = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (raw.isEmpty()) return null
        val low = raw.map { it.lowercase().trimEnd(',', '.', ';', '!') }
        val n = raw.size
        val used = BooleanArray(n)
        val today = now.toLocalDate()

        var time: LocalTime? = null
        var date: LocalDate? = null
        var weekday: DayOfWeek? = null
        var deadline: LocalDate? = null
        var wall: WallStrength? = null
        var priority = Priority.AUTO
        var effort: Effort? = null
        var duration: Int? = null
        var freq: Freq? = null
        var interval = 1
        val days = linkedSetOf<DayOfWeek>()
        // "all day" makes a marker: context for the day, not work.
        var allDay = false

        fun takePrev(i: Int, words: Set<String>) {
            if (i > 0 && !used[i - 1] && low[i - 1] in words) used[i - 1] = true
        }

        fun dayAt(i: Int): LocalDate? = when (val l = low[i]) {
            in TODAY -> today
            in TOMORROW -> today.plusDays(1)
            else -> DAYS[l]?.let { nextOn(today, it) }
        }

        // A month's date: "nov 14", "november 14th", "14 nov", or "11/14" (US order),
        // this year, or next once it's passed. Returns the date and how many tokens it used.
        fun monthOf(word: String): Int? =
            if (word.length < 3) null else MONTH_NAMES.indexOfFirst { it.startsWith(word) || word == "sept" && it == "september" }.takeIf { it >= 0 }?.plus(1)

        fun dated(month: Int, day: Int, year: Int?): LocalDate? {
            if (month !in 1..12 || day !in 1..31) return null
            fun on(y: Int) = runCatching { LocalDate.of(y, month, day) }.getOrNull()
            if (year != null) return on(if (year < 100) 2000 + year else year)
            val d = on(today.year) ?: return null
            return if (d.isBefore(today)) on(today.year + 1) else d
        }

        fun monthDateAt(i: Int): Pair<LocalDate, Int>? {
            SLASH_DATE.matchEntire(low[i])?.let { m ->
                val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
                return dated(m.groupValues[1].toInt(), m.groupValues[2].toInt(), y)?.let { it to 1 }
            }
            val month = monthOf(low[i])
            if (month != null && i + 1 < n) {
                DAY_OF_MONTH.matchEntire(low[i + 1])?.let { m -> return dated(month, m.groupValues[1].toInt(), null)?.let { it to 2 } }
            }
            val day = DAY_OF_MONTH.matchEntire(low[i])
            if (day != null && i + 1 < n) {
                monthOf(low[i + 1])?.let { mo -> return dated(mo, day.groupValues[1].toInt(), null)?.let { it to 2 } }
            }
            return null
        }

        // Returns the time and how many tokens it used.
        fun timeAt(i: Int): Pair<LocalTime, Int>? {
            if (low[i] == "noon") return LocalTime.NOON to 1
            val m = TIME.matchEntire(low[i]) ?: return null
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].ifEmpty { "0" }.toInt()
            var suffix = m.groupValues[3]
            var width = 1
            if (suffix.isEmpty() && i + 1 < n && low[i + 1] in setOf("am", "pm")) {
                suffix = low[i + 1]
                width = 2
            }
            if (min > 59) return null
            val hour = when {
                suffix.isNotEmpty() -> {
                    if (h !in 1..12) return null
                    val pm = suffix.startsWith("p")
                    when {
                        h == 12 && !pm -> 0
                        h == 12 -> 12
                        pm -> h + 12
                        else -> h
                    }
                }
                m.groupValues[2].isNotEmpty() && h in 0..23 -> h
                else -> return null
            }
            return LocalTime.of(hour, min) to width
        }

        var i = 0
        while (i < n) {
            val l = low[i]
            if (used[i]) { i++; continue }

            if (l == "every") {
                var j = i + 1
                var other = false
                if (j < n && low[j] == "other") { other = true; j++ }
                val rule: Freq? = when {
                    j >= n -> null
                    low[j] == "day" -> Freq.DAILY
                    low[j] == "weekday" || low[j] == "weekdays" -> Freq.WEEKDAYS
                    low[j] == "week" -> Freq.WEEKLY
                    low[j] == "month" -> Freq.MONTHLY_DATE
                    low[j] == "year" -> Freq.YEARLY
                    low[j] in DAYS -> Freq.WEEKLY
                    else -> null
                }
                if (rule != null) {
                    freq = rule
                    if (other) interval = 2
                    for (k in i..j) used[k] = true
                    if (low[j] in DAYS) {
                        // "every mon and thu"
                        var k = j
                        while (k < n && (low[k] in DAYS || low[k] == "and" || low[k] == "&")) {
                            DAYS[low[k]]?.let { days += it }
                            used[k] = true
                            k++
                        }
                        j = k - 1
                    }
                    i = j + 1
                    continue
                }
            }

            val single: Pair<Freq, Int>? = when (l) {
                "daily" -> Freq.DAILY to 1
                "weekdays" -> Freq.WEEKDAYS to 1
                "weekly" -> Freq.WEEKLY to 1
                "biweekly", "fortnightly" -> Freq.WEEKLY to 2
                "monthly" -> Freq.MONTHLY_DATE to 1
                "bimonthly" -> Freq.MONTHLY_DATE to 2
                "yearly", "annually" -> Freq.YEARLY to 1
                else -> null
            }
            if (single != null) {
                freq = single.first
                interval = single.second
                used[i] = true
                i++
                continue
            }

            if (l == "by" && i + 1 < n && dayAt(i + 1) != null) {
                deadline = dayAt(i + 1)
                used[i] = true
                used[i + 1] = true
                i += 2
                continue
            }
            if (l == "by" && i + 1 < n) {
                val md = monthDateAt(i + 1)
                if (md != null) {
                    deadline = md.first
                    for (k in i..i + md.second) used[k] = true
                    i += 1 + md.second
                    continue
                }
            }

            when (l) {
                "hard" -> { wall = WallStrength.HARD; used[i] = true; i++; continue }
                "firm" -> { wall = WallStrength.FIRM; used[i] = true; i++; continue }
                "soft" -> { wall = WallStrength.SOFT; used[i] = true; i++; continue }
                "high", "urgent" -> { priority = Priority.HIGH; used[i] = true; i++; continue }
                "low" -> { priority = Priority.LOW; used[i] = true; i++; continue }
                "quick" -> { effort = Effort.QUICK; used[i] = true; i++; continue }
                "focus" -> { effort = Effort.FOCUS; used[i] = true; i++; continue }
            }

            val dur = DURATION.matchEntire(l)
            if (dur != null) {
                val v = dur.groupValues[1].toInt()
                duration = if (dur.groupValues[2].startsWith("h")) v * 60 else v
                used[i] = true
                takePrev(i, setOf("for"))
                i++
                continue
            }

            if (l.all { it.isDigit() } && i + 1 < n && (low[i + 1] in MINUTE_WORDS || low[i + 1] in HOUR_WORDS)) {
                val v = l.toInt()
                duration = if (low[i + 1] in HOUR_WORDS) v * 60 else v
                used[i] = true
                used[i + 1] = true
                takePrev(i, setOf("for"))
                i += 2
                continue
            }

            if (l == "allday" || l == "all-day" || (l == "all" && i + 1 < n && low[i + 1] == "day")) {
                allDay = true
                used[i] = true
                if (l == "all") used[i + 1] = true
                i += if (l == "all") 2 else 1
                continue
            }

            val md = monthDateAt(i)
            if (md != null) {
                date = md.first
                weekday = null
                for (k in i until i + md.second) used[k] = true
                takePrev(i, setOf("on"))
                i += md.second
                continue
            }

            val t = timeAt(i)
            if (t != null) {
                time = t.first
                for (k in i until i + t.second) used[k] = true
                takePrev(i, setOf("at", "@"))
                i += t.second
                continue
            }

            val d = dayAt(i)
            if (d != null) {
                date = d
                weekday = DAYS[l]
                used[i] = true
                takePrev(i, setOf("on", "next", "this"))
                i++
                continue
            }
            i++
        }

        val title = raw.filterIndexed { k, _ -> !used[k] }.joinToString(" ")
        if (title.isBlank()) return null

        // Recurring weekly on a named day: "weekly on mon".
        if (freq == Freq.WEEKLY && days.isEmpty() && weekday != null) days += weekday

        var base = date ?: on ?: today
        val clock = time
        if (clock != null && base == today && !clock.isAfter(now.toLocalTime())) {
            base = when {
                date == null || freq != null -> today.plusDays(1)
                weekday != null -> base.plusDays(7)
                else -> base
            }
        }

        val rule = freq?.let { f ->
            Recurrence(f, interval, if (f == Freq.WEEKLY) days.ifEmpty { setOf(base.dayOfWeek) } else emptySet())
        }
        if (rule != null) {
            base = when (rule.freq) {
                Freq.WEEKLY -> firstOn(base) { it.dayOfWeek in rule.days }
                Freq.WEEKDAYS -> firstOn(base) { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
                else -> base
            }
        }

        val explicitDuration = duration != null
        val item = if (allDay) {
            Item(
                title = title,
                kind = Kind.MARK,
                start = base.atStartOfDay(),
                leadMinutes = 0,
                recurrence = rule,
                icon = iconFor(title),
                createdAt = now,
            )
        } else if (clock != null) {
            val start = base.atTime(clock)
            Item(
                title = title,
                kind = Kind.ANCHOR,
                start = start,
                end = duration?.let { start.plusMinutes(it.toLong()) },
                wall = wall ?: WallStrength.FIRM,
                recurrence = rule,
                createdAt = now,
            )
        } else {
            Item(
                title = title,
                kind = Kind.FLOAT,
                durationMinutes = duration ?: Item.defaultMinutes(effort ?: Effort.QUICK),
                earliestStart = if (date != null || rule != null || on != null) base else null,
                deadline = deadline,
                priority = priority,
                effort = effort ?: Effort.QUICK,
                recurrence = rule,
                createdAt = now,
            )
        }
        val unsure = !allDay && clock == null && date != null && rule == null
        return Parsed(
            item = item,
            unsureKind = unsure,
            explicitPriority = priority != Priority.AUTO,
            explicitEffort = effort != null,
            explicitDuration = explicitDuration,
        )
    }

    /** A marker's icon, guessed from its name; Flag when nothing fits. The edit form can change it. */
    fun iconFor(title: String): MarkIcon {
        val words = title.lowercase().split(Regex("[^a-z']+")).toSet()
        return when {
            words.any { it in setOf("birthday", "bday", "birthdays", "anniversary") } -> MarkIcon.CAKE
            words.any { it in setOf("closed", "closure", "off", "shut") } -> MarkIcon.DOOR
            words.any { it in setOf("trip", "travel", "travelling", "traveling", "vacation", "away", "flight", "visit") } -> MarkIcon.PLANE
            words.any { it in setOf("payday", "pay", "rent", "bill", "bills", "paycheck") } -> MarkIcon.COIN
            words.any { it in HOLIDAYS } -> MarkIcon.STAR
            else -> MarkIcon.FLAG
        }
    }

    private val HOLIDAYS = setOf(
        "holiday", "halloween", "christmas", "thanksgiving", "easter", "hanukkah", "diwali", "eid",
        "new", "valentine's", "valentines", "solstice", "equinox", "festival",
    )

    /** Next date on [day], counting today. */
    fun nextOn(today: LocalDate, day: DayOfWeek): LocalDate =
        today.plusDays(((day.value - today.dayOfWeek.value + 7) % 7).toLong())

    private fun firstOn(from: LocalDate, ok: (LocalDate) -> Boolean): LocalDate =
        generateSequence(from) { it.plusDays(1) }.take(14).firstOrNull(ok) ?: from
}
