package com.daylight.app

import com.daylight.app.data.*
import com.daylight.app.domain.SchedulePlanner
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class SchedulePlannerTest {
    private val day = LocalDate.of(2026, 10, 5)
    private val zone = ZoneId.of("UTC")
    private val prefs = Preferences()
    private val base = Schedule(id = "schedule-test", title = "My motivation")
    @Test fun fixedTimesAreOrderedAndQuietHoursAreExcluded() {
        val s = base.copy(fixedMinutes = listOf(1260, 480, 60))
        assertEquals(listOf(8, 21), SchedulePlanner.dayTimes(s, day, zone, prefs).map { it.atZone(zone).hour })
    }
    @Test fun intervalIsAnchoredToWindow() {
        val s = base.copy(mode = "Interval", startMinute = 480, endMinute = 780, intervalMinutes = 120)
        assertEquals(listOf(8, 10, 12), SchedulePlanner.dayTimes(s, day, zone, prefs).map { it.atZone(zone).hour })
    }
    @Test fun randomIsDeterministicAndSpacedAcrossManyDays() {
        val s = base.copy(mode = "Random", randomCount = 5, minimumSpacing = 120)
        repeat(366) { offset ->
            val d = day.plusDays(offset.toLong())
            val times = SchedulePlanner.dayTimes(s, d, zone, prefs)
            assertEquals(5, times.size)
            assertEquals(times, SchedulePlanner.dayTimes(s, d, zone, prefs))
            times.zipWithNext().forEach { (a, b) -> assertTrue(Duration.between(a, b).toMinutes() >= 120) }
            assertTrue(times.all { !SchedulePlanner.quiet(it.atZone(zone).hour * 60 + it.atZone(zone).minute, prefs) })
        }
    }
    @Test fun randomRespectsQuietGapWithinWindow() {
        val times = SchedulePlanner.dayTimes(base.copy(mode = "Random", randomCount = 4, minimumSpacing = 120), day, zone, prefs.copy(quietStart = 660, quietEnd = 900))
        assertEquals(4, times.size)
        assertTrue(times.none { it.atZone(zone).hour in 11..14 })
    }
    @Test(expected = IllegalArgumentException::class) fun impossibleRandomWindowRejected() {
        SchedulePlanner.dayTimes(base.copy(mode = "Random", startMinute = 480, endMinute = 600, randomCount = 4, minimumSpacing = 60), day, zone, prefs)
    }
    @Test fun quietHoursCrossMidnightAndBoundaries() {
        assertTrue(SchedulePlanner.quiet(1320, prefs)); assertTrue(SchedulePlanner.quiet(0, prefs))
        assertFalse(SchedulePlanner.quiet(420, prefs)); assertFalse(SchedulePlanner.quiet(1319, prefs))
    }
    @Test fun disabledScheduleDoesNotPlan() {
        assertTrue(SchedulePlanner.plan(listOf(base.copy(enabled = false)), emptyList(), prefs, day.atStartOfDay(zone).toInstant(), zone).isEmpty())
    }
    @Test fun reschedulingAndBootRecoveryPreserveRandomSlots() {
        val s = base.copy(mode = "Random")
        val before = day.atStartOfDay(zone).toInstant()
        val first = SchedulePlanner.plan(listOf(s), emptyList(), prefs, before, zone)
        val restartedAt = Instant.ofEpochMilli(first.first().at).plusSeconds(1)
        val recovered = SchedulePlanner.plan(listOf(s), emptyList(), prefs, restartedAt, zone)
        assertEquals(first.filter { it.at > restartedAt.toEpochMilli() }, recovered)
    }
    @Test fun reviewMovesBeyondQuietHours() {
        val r = FormResponse(formId = "form", formTitle = "Morning", reviewAt = day.atTime(23, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
        val event = SchedulePlanner.plan(emptyList(), listOf(r), prefs, day.atStartOfDay(zone).toInstant(), zone).single()
        assertEquals(day.plusDays(1).atTime(7, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), event.at)
    }
    @Test fun collisionsSuppressed() {
        val events = SchedulePlanner.plan(listOf(base, base.copy(id = "another")), emptyList(), prefs, day.atStartOfDay(zone).toInstant(), zone)
        assertEquals(8, events.size)
    }
    @Test fun quietHoursDeferredReviewSurvivesMidnightRecovery() {
        val r = FormResponse(formId = "form", formTitle = "Morning", reviewAt = day.atTime(23, 0).toInstant(ZoneOffset.UTC).toEpochMilli())
        val recovery = day.plusDays(1).atTime(1, 0).toInstant(ZoneOffset.UTC)
        val planned = SchedulePlanner.plan(emptyList(), listOf(r), prefs, recovery, zone)
        assertEquals(1, planned.size)
        assertEquals(7, Instant.ofEpochMilli(planned.single().at).atZone(zone).hour)
    }
    @Test fun dstSpringGapDoesNotDuplicateTimes() {
        val times = SchedulePlanner.dayTimes(base.copy(fixedMinutes = listOf(150, 210)), LocalDate.of(2026, 3, 8), ZoneId.of("America/New_York"), prefs.copy(quietEnabled = false))
        assertEquals(1, times.size)
    }
    @Test fun dstFallOverlapFiresOnce() {
        val times = SchedulePlanner.dayTimes(base.copy(fixedMinutes = listOf(90)), LocalDate.of(2026, 11, 1), ZoneId.of("America/New_York"), prefs.copy(quietEnabled = false))
        assertEquals(1, times.size)
    }
    @Test fun timezoneChangesPreserveWallClock() {
        val paris = ZoneId.of("Europe/Paris")
        val times = SchedulePlanner.dayTimes(base, day, paris, prefs)
        assertEquals(8, times.single().atZone(paris).hour)
        assertNotEquals(times, SchedulePlanner.dayTimes(base, day, zone, prefs))
    }
}
