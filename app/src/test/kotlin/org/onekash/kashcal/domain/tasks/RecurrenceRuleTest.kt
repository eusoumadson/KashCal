package org.onekash.kashcal.domain.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

/**
 * Tests [SimpleRecurrence] and [RecurrenceChoice]: the editor builds only the simple rules and
 * must keep any other rule exactly as Tasks.org holds it, so a KashCal edit never flattens a rule
 * that was set up in Tasks.org.
 */
class RecurrenceRuleTest {

    @Test
    fun `a rule is written without the RRULE prefix, which is the form Tasks_org stores`() {
        assertEquals("FREQ=DAILY", SimpleRecurrence(SimpleRecurrence.Frequency.DAILY).toRrule())
        assertEquals("FREQ=MONTHLY;INTERVAL=2", SimpleRecurrence(SimpleRecurrence.Frequency.MONTHLY, interval = 2).toRrule())
        assertEquals("FREQ=YEARLY;COUNT=3", SimpleRecurrence(SimpleRecurrence.Frequency.YEARLY, count = 3).toRrule())
    }

    @Test
    fun `weekly days are written in week order`() {
        val rule = SimpleRecurrence(
            SimpleRecurrence.Frequency.WEEKLY, interval = 2,
            weekdays = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY), count = 5
        )
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,FR;COUNT=5", rule.toRrule())
    }

    @Test
    fun `weekdays are ignored on a non-weekly rule`() {
        val rule = SimpleRecurrence(SimpleRecurrence.Frequency.DAILY, weekdays = setOf(DayOfWeek.MONDAY))
        assertEquals("FREQ=DAILY", rule.toRrule())
    }

    @Test
    fun `a rule survives a round trip`() {
        listOf(
            "FREQ=DAILY", "FREQ=WEEKLY;INTERVAL=3", "FREQ=WEEKLY;BYDAY=MO,WE,FR",
            "FREQ=MONTHLY;COUNT=10", "FREQ=YEARLY;INTERVAL=2;COUNT=4"
        ).forEach { raw ->
            assertEquals(raw, SimpleRecurrence.parse(raw)!!.toRrule())
        }
    }

    @Test
    fun `parse accepts the RRULE prefix and any case`() {
        assertEquals(SimpleRecurrence(SimpleRecurrence.Frequency.DAILY), SimpleRecurrence.parse("RRULE:FREQ=DAILY"))
        assertEquals(SimpleRecurrence(SimpleRecurrence.Frequency.DAILY), SimpleRecurrence.parse("freq=daily"))
    }

    @Test
    fun `parse rejects what the editor can't build`() {
        listOf(
            "FREQ=MONTHLY;BYDAY=2TU",           // nth weekday
            "FREQ=MONTHLY;BYMONTHDAY=-1",       // last day of the month
            "FREQ=MONTHLY;BYDAY=MO",            // weekday on a non-weekly rule
            "FREQ=DAILY;UNTIL=20261231",        // an end date
            "FREQ=WEEKLY;BYDAY=XX",             // not a weekday
            "FREQ=HOURLY",                      // unsupported frequency
            "FREQ=DAILY;INTERVAL=0",
            "FREQ=DAILY;COUNT=-1",
            "FREQ=DAILY;FREQ=WEEKLY",           // repeated key
            "INTERVAL=2",                       // no frequency
            "every tuesday", ""
        ).forEach { raw -> assertNull("should not parse: $raw", SimpleRecurrence.parse(raw)) }
    }

    @Test
    fun `a task with no rule maps to none`() {
        assertEquals(RecurrenceChoice.None, RecurrenceChoice.of(null))
        assertEquals(RecurrenceChoice.None, RecurrenceChoice.of(""))
        assertEquals(RecurrenceChoice.None, RecurrenceChoice.of("   "))
        assertNull(RecurrenceChoice.None.toRrule())
    }

    @Test
    fun `a simple rule maps to simple and writes back identically`() {
        val choice = RecurrenceChoice.of("FREQ=WEEKLY;BYDAY=TU")
        assertTrue(choice is RecurrenceChoice.Simple)
        assertEquals("FREQ=WEEKLY;BYDAY=TU", choice.toRrule())
    }

    @Test
    fun `a rule set in Tasks_org is kept byte for byte`() {
        val raw = "FREQ=MONTHLY;BYDAY=2TU"
        val choice = RecurrenceChoice.of(raw)
        assertEquals(RecurrenceChoice.Custom(raw), choice)
        assertEquals(raw, choice.toRrule())
    }

    @Test
    fun `normalizeRule makes two spellings of a rule compare equal`() {
        assertEquals(normalizeRule("RRULE:FREQ=DAILY"), normalizeRule("FREQ=DAILY"))
        assertEquals(normalizeRule(" FREQ=DAILY "), normalizeRule("FREQ=DAILY"))
        assertEquals("", normalizeRule(null))
        assertEquals(normalizeRule(null), normalizeRule(""))
    }
}
