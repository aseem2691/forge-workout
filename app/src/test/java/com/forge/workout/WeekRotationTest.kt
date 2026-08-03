package com.forge.workout

import com.forge.workout.data.Persisted
import com.forge.workout.data.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The rotating block is picked from calendar arithmetic that can't be exercised from the UI
 * without changing the device clock, so it is pinned down here.
 */
class WeekRotationTest {

    private val blocks = 4
    private fun start(date: String) = Persisted(programStart = LocalDate.parse(date).toEpochDay())
    private fun blockOf(saved: Persisted, today: String) =
        (saved.weekNumber(LocalDate.parse(today)) - 1).mod(blocks)

    @Test
    fun `week number counts from the program start monday`() {
        val saved = start("2026-08-03") // a Monday
        assertEquals(1, saved.weekNumber(LocalDate.parse("2026-08-03")))
        assertEquals(1, saved.weekNumber(LocalDate.parse("2026-08-09"))) // same week, Sunday
        assertEquals(2, saved.weekNumber(LocalDate.parse("2026-08-10")))
        assertEquals(5, saved.weekNumber(LocalDate.parse("2026-08-31")))
    }

    @Test
    fun `a program started mid-week still counts that week as week one`() {
        val saved = start("2026-08-06") // Thursday
        assertEquals(1, saved.weekNumber(LocalDate.parse("2026-08-06")))
        assertEquals(2, saved.weekNumber(LocalDate.parse("2026-08-10")))
    }

    @Test
    fun `blocks rotate A B C D and wrap back to A`() {
        val saved = start("2026-08-03")
        assertEquals(0, blockOf(saved, "2026-08-03")) // A
        assertEquals(1, blockOf(saved, "2026-08-10")) // B
        assertEquals(2, blockOf(saved, "2026-08-17")) // C
        assertEquals(3, blockOf(saved, "2026-08-24")) // D
        assertEquals(0, blockOf(saved, "2026-08-31")) // back to A
    }

    @Test
    fun `unset program start is treated as week one`() {
        assertEquals(1, Persisted().weekNumber(LocalDate.parse("2026-08-03")))
        assertEquals(0, blockOf(Persisted(), "2026-08-03"))
    }

    @Test
    fun `completed days only count for the current monday-start week`() {
        val monday = LocalDate.parse("2026-08-10")
        val saved = Persisted(
            history = listOf(
                record(LocalDate.parse("2026-08-09"), 0), // previous week (Sunday)
                record(LocalDate.parse("2026-08-10"), 0),
                record(LocalDate.parse("2026-08-11"), 1),
            ),
        )
        assertEquals(setOf(0, 1), saved.doneThisWeek(monday))
        assertEquals(2, saved.thisWeek(monday).size)
    }

    private fun record(date: LocalDate, dayIdx: Int) = SessionRecord(
        epochDay = date.toEpochDay(), dayIdx = dayIdx, dayTitle = "t",
        sets = 1, reps = 8, volume = 100, seconds = 60,
    )
}
