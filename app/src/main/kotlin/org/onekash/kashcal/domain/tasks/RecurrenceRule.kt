package org.onekash.kashcal.domain.tasks

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek

/**
 * A repeat rule the task editor can build: every [interval] days, weeks, months or years,
 * optionally on given [weekdays] (weekly only) and for a total of [count] times.
 *
 * Anything richer (monthly on the 2nd Tuesday, an end date, a specific day of the month) is kept
 * as a [RecurrenceChoice.Custom] rule and written back untouched, so editing a task in KashCal
 * never flattens a rule that was set up in Tasks.org.
 */
@Immutable
data class SimpleRecurrence(
    val frequency: Frequency,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val count: Int? = null
) {
    enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

    /** The RFC 5545 `RRULE` value, without the `RRULE:` prefix (the form Tasks.org stores). */
    fun toRrule(): String = buildList {
        add("FREQ=${frequency.name}")
        if (interval > 1) add("INTERVAL=$interval")
        if (frequency == Frequency.WEEKLY && weekdays.isNotEmpty()) {
            add("BYDAY=" + weekdays.sortedBy { it.value }.joinToString(",") { it.code() })
        }
        count?.let { add("COUNT=$it") }
    }.joinToString(";")

    companion object {
        /**
         * Parses [rrule] into a [SimpleRecurrence], or null when it uses anything the editor
         * can't build (any key beyond FREQ, INTERVAL, COUNT and a plain weekly BYDAY).
         */
        fun parse(rrule: String): SimpleRecurrence? {
            val parts = rrule.trim().removePrefix("RRULE:").split(";").filter { it.isNotBlank() }
            if (parts.isEmpty()) return null
            val map = HashMap<String, String>()
            for (part in parts) {
                val i = part.indexOf('=')
                if (i <= 0) return null
                if (map.put(part.substring(0, i).uppercase(), part.substring(i + 1)) != null) return null
            }
            if (!map.keys.all { it in SUPPORTED_KEYS }) return null

            val frequency = map["FREQ"]?.let { name -> Frequency.entries.firstOrNull { it.name == name.uppercase() } }
                ?: return null
            val interval = map["INTERVAL"]?.let { it.toIntOrNull()?.takeIf { n -> n >= 1 } ?: return null } ?: 1
            val count = map["COUNT"]?.let { it.toIntOrNull()?.takeIf { n -> n >= 1 } ?: return null }
            val weekdays = map["BYDAY"]?.let { raw ->
                if (frequency != Frequency.WEEKLY) return null
                raw.split(",").map { code -> dayOfWeekOf(code.trim()) ?: return null }.toSet()
            } ?: emptySet()
            return SimpleRecurrence(frequency, interval, weekdays, count)
        }

        private val SUPPORTED_KEYS = setOf("FREQ", "INTERVAL", "COUNT", "BYDAY")

        private fun dayOfWeekOf(code: String): DayOfWeek? = DayOfWeek.entries.firstOrNull { it.code() == code.uppercase() }
    }
}

private fun DayOfWeek.code(): String = when (this) {
    DayOfWeek.MONDAY -> "MO"
    DayOfWeek.TUESDAY -> "TU"
    DayOfWeek.WEDNESDAY -> "WE"
    DayOfWeek.THURSDAY -> "TH"
    DayOfWeek.FRIDAY -> "FR"
    DayOfWeek.SATURDAY -> "SA"
    DayOfWeek.SUNDAY -> "SU"
}

/** How a task repeats in the editor. */
@Immutable
sealed interface RecurrenceChoice {
    /** The task does not repeat. */
    data object None : RecurrenceChoice

    /** A rule the editor builds and can edit. */
    data class Simple(val rule: SimpleRecurrence) : RecurrenceChoice

    /** A rule set elsewhere that the editor shows but doesn't edit; written back as it is. */
    data class Custom(val raw: String) : RecurrenceChoice

    /** The value to write to Tasks.org, or null when the task doesn't repeat. */
    fun toRrule(): String? = when (this) {
        None -> null
        is Simple -> rule.toRrule()
        is Custom -> raw
    }

    companion object {
        /** Reads the stored rule of a task into the editor's model. */
        fun of(rrule: String?): RecurrenceChoice {
            if (rrule.isNullOrBlank()) return None
            return SimpleRecurrence.parse(rrule)?.let(::Simple) ?: Custom(rrule)
        }
    }
}
