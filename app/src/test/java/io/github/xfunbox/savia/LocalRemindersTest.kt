package io.github.xfunbox.savia

import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalRemindersTest {
    private lateinit var originalTimeZone: TimeZone

    @Before fun useUtc() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test fun billsIncludeOverdueAndNextThreeDaysButExcludePaidAndLaterPayments() {
        val today = LocalDate.of(2026, 11, 6)
        val bills = listOf(
            BillReminder("overdue", "Luz", today.minusDays(30), false),
            BillReminder("today", "Agua", today, false),
            BillReminder("third-day", "Alquiler", today.plusDays(3), false),
            BillReminder("later", "Seguro", today.plusDays(4), false),
            BillReminder("paid", "Teléfono", today, true)
        )
        assertEquals(listOf("overdue", "today", "third-day"), LocalReminders.dueBills(bills, today).map { it.id })
        assertTrue(LocalReminders.dueBills(emptyList(), today).isEmpty())
    }

    @Test fun backupRemindersRequireOptInAndAnExportOrActivationDate() {
        val now = Instant.parse("2026-11-06T12:00:00Z")
        val last = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
        assertFalse(LocalReminders.backupDue(ReminderPreferences(lastExportAt = last), now))
        assertFalse(LocalReminders.backupDue(ReminderPreferences(backupsEnabled = true), now))
        assertTrue(LocalReminders.backupDue(ReminderPreferences(backupsEnabled = true, backupStartedAt = last), now))
    }

    @Test fun eachConfiguredIntervalBecomesDueOnItsLocalCalendarDate() {
        val start = Instant.parse("2026-10-01T18:00:00Z")
        listOf(7, 14, 30).forEach { interval ->
            val prefs = ReminderPreferences(lastExportAt = start.toEpochMilli(), backupsEnabled = true, backupIntervalDays = interval)
            val due = LocalDate.of(2026, 10, 1).plusDays(interval.toLong()).atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
            assertFalse(LocalReminders.backupDue(prefs, due.minusSeconds(1)))
            assertTrue(LocalReminders.backupDue(prefs, due))
        }
    }

    @Test fun successfulExportRestartsIntervalAndSnoozeBlocksUntilItsExactEnd() {
        val now = Instant.parse("2026-11-06T12:00:00Z")
        val old = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
        val prefs = ReminderPreferences(lastExportAt = old, backupsEnabled = true, backupStartedAt = old)
        assertFalse(LocalReminders.backupDue(prefs.copy(lastExportAt = now.toEpochMilli()), now))
        assertFalse(LocalReminders.backupDue(prefs.copy(backupSnoozedUntil = now.plusSeconds(1).toEpochMilli()), now))
        assertTrue(LocalReminders.backupDue(prefs.copy(backupSnoozedUntil = now.toEpochMilli()), now))
    }

    @Test fun backupIntervalUsesLocalDatesAcrossSpringDaylightSavingChange() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid"))
        // 23 March at 00:30 local; seven dates later is 30 March, after the clock change.
        val prefs = ReminderPreferences(lastExportAt = Instant.parse("2026-03-22T23:30:00Z").toEpochMilli(), backupsEnabled = true)
        assertFalse(LocalReminders.backupDue(prefs, Instant.parse("2026-03-29T21:59:59Z")))
        assertTrue(LocalReminders.backupDue(prefs, Instant.parse("2026-03-29T22:00:00Z")))
    }

    @Test fun backupIntervalUsesLocalDatesAcrossAutumnDaylightSavingChange() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        // 31 October at 23:30 local; seven dates later is 7 November, after the clock change.
        val prefs = ReminderPreferences(lastExportAt = Instant.parse("2026-11-01T06:30:00Z").toEpochMilli(), backupsEnabled = true)
        assertFalse(LocalReminders.backupDue(prefs, Instant.parse("2026-11-07T07:59:59Z")))
        assertTrue(LocalReminders.backupDue(prefs, Instant.parse("2026-11-07T08:00:00Z")))
    }
    @Test fun openingAppDoesNotPostponeAnUndeliveredMorningAlarm() {
        val scheduled = Instant.parse("2026-11-06T08:00:00Z").toEpochMilli()
        assertTrue(LocalReminders.keepScheduledCheck(scheduled, Instant.parse("2026-11-06T08:05:00Z")))
        assertTrue(LocalReminders.keepScheduledCheck(scheduled, Instant.parse("2026-11-06T20:00:00Z")))
        assertFalse(LocalReminders.keepScheduledCheck(scheduled, Instant.parse("2026-11-08T20:00:00Z")))
        assertFalse(LocalReminders.keepScheduledCheck(0, Instant.parse("2026-11-06T08:05:00Z")))
    }
}
