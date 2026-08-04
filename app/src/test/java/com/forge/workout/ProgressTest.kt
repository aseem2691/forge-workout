package com.forge.workout

import com.forge.workout.data.Bucket
import com.forge.workout.data.Range
import com.forge.workout.data.SessionRecord
import com.forge.workout.data.buildProgress
import com.forge.workout.watch.WeighIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ProgressTest {

    private val today = LocalDate.parse("2026-08-04")
    private val utc = ZoneId.of("UTC")

    private fun session(date: String, volume: Int = 1000, seconds: Int = 1800, hr: Int? = null) =
        SessionRecord(
            epochDay = LocalDate.parse(date).toEpochDay(),
            dayIdx = 0, dayTitle = "t", sets = 4, reps = 40,
            volume = volume, seconds = seconds, avgHr = hr,
        )

    private fun weighIn(date: String, kg: Float) =
        WeighIn(LocalDate.parse(date).atStartOfDay(utc).toInstant().toEpochMilli(), kg)

    @Test
    fun `week range produces seven daily buckets ending today`() {
        val p = buildProgress(emptyList(), emptyList(), Range.Week, today, utc)
        assertEquals(Bucket.Day, Range.Week.bucket)
        assertEquals(7, p.periods.size)
        assertEquals(LocalDate.parse("2026-07-29"), p.periods.first().start)
        assertEquals(today, p.periods.last().start)
    }

    @Test
    fun `sessions land in their own bucket and empty buckets are kept`() {
        val history = listOf(session("2026-08-04", volume = 2000), session("2026-08-02", volume = 500))
        val p = buildProgress(history, emptyList(), Range.Week, today, utc)

        assertEquals(7, p.periods.size)
        assertEquals(2000, p.periods.last().volumeKg)
        assertEquals(500, p.periods.first { it.start == LocalDate.parse("2026-08-02") }.volumeKg)
        // The untrained days are still present — a gap must read as a gap.
        assertEquals(5, p.periods.count { it.sessions == 0 })
        assertEquals(2, p.sessions)
        assertEquals(2500, p.volumeKg)
    }

    @Test
    fun `sessions outside the range are excluded`() {
        val history = listOf(session("2026-08-04"), session("2026-06-01"))
        val week = buildProgress(history, emptyList(), Range.Week, today, utc)
        assertEquals(1, week.sessions)

        val year = buildProgress(history, emptyList(), Range.Year, today, utc)
        assertEquals(2, year.sessions)
    }

    @Test
    fun `year range buckets by month`() {
        val p = buildProgress(emptyList(), emptyList(), Range.Year, today, utc)
        assertEquals(Bucket.Month, Range.Year.bucket)
        assertEquals(13, p.periods.size) // Aug 2025 .. Aug 2026 inclusive
        assertTrue(p.periods.all { it.start.dayOfMonth == 1 })
    }

    @Test
    fun `half year range buckets by monday-start weeks`() {
        val p = buildProgress(emptyList(), emptyList(), Range.HalfYear, today, utc)
        assertEquals(Bucket.Week, Range.HalfYear.bucket)
        assertTrue(p.periods.all { it.start.dayOfWeek.value == 1 })
    }

    @Test
    fun `weight change is measured across the range, not against target`() {
        val weighIns = listOf(weighIn("2026-07-30", 85f), weighIn("2026-08-03", 83.5f))
        val p = buildProgress(emptyList(), weighIns, Range.Week, today, utc)
        assertEquals(-1.5f, p.weightChange!!, 0.001f)
        // Bucket carries the last reading of that day.
        assertEquals(83.5f, p.periods.first { it.start == LocalDate.parse("2026-08-03") }.weightKg!!, 0.001f)
    }

    @Test
    fun `a single weigh-in yields no change`() {
        val p = buildProgress(emptyList(), listOf(weighIn("2026-08-01", 84f)), Range.Week, today, utc)
        assertNull(p.weightChange)
    }

    @Test
    fun `average heart rate ignores sessions that recorded none`() {
        val history = listOf(
            session("2026-08-04", hr = 140),
            session("2026-08-03", hr = 160),
            session("2026-08-02", hr = null),
        )
        val p = buildProgress(history, emptyList(), Range.Week, today, utc)
        assertEquals(150, p.avgHr)
    }

    @Test
    fun `streak counts consecutive trained weeks and survives an unlogged current week`() {
        // Trained the previous three weeks, nothing yet this week.
        val history = listOf(
            session("2026-07-28"), session("2026-07-21"), session("2026-07-14"),
        )
        assertEquals(3, buildProgress(history, emptyList(), Range.Year, today, utc).streakWeeks)

        // A missed week breaks it.
        val gapped = listOf(session("2026-07-28"), session("2026-07-14"))
        assertEquals(1, buildProgress(gapped, emptyList(), Range.Year, today, utc).streakWeeks)

        assertEquals(0, buildProgress(emptyList(), emptyList(), Range.Year, today, utc).streakWeeks)
    }
}
