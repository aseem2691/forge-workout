package com.forge.workout

import com.forge.workout.data.ActiveSession
import com.forge.workout.data.Day
import com.forge.workout.data.KIND_MOBILITY
import com.forge.workout.data.KIND_TRAINING
import com.forge.workout.data.PendingResult
import com.forge.workout.data.Persisted
import com.forge.workout.data.Program
import com.forge.workout.data.Range
import com.forge.workout.data.SessionRecord
import com.forge.workout.data.Week
import com.forge.workout.data.activeStartMs
import com.forge.workout.data.bankTempo
import com.forge.workout.data.buildProgress
import com.forge.workout.data.leftoverToSave
import com.forge.workout.data.recorded
import com.forge.workout.data.cleared
import com.forge.workout.data.decodeState
import com.forge.workout.data.encodeState
import com.forge.workout.data.nextPosition
import com.forge.workout.data.shouldRecord
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The in-progress snapshot and the rules that decide what a session records. None of this can be
 * driven through the UI without killing the app mid-workout, so it is pinned here.
 */
class SessionModelTest {

    // The same configuration Store uses.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val utc = ZoneId.of("UTC")
    private val monday = LocalDate.parse("2026-09-28")

    private fun record(dayIdx: Int, kind: String = KIND_TRAINING, date: LocalDate = monday) = SessionRecord(
        epochDay = date.toEpochDay(), dayIdx = dayIdx, dayTitle = "t",
        sets = 0, reps = 0, volume = 0, seconds = 600, kind = kind,
    )

    @Test
    fun `records saved before kinds existed read as training`() {
        val old = """{"epochDay":1,"dayIdx":2,"dayTitle":"t","sets":3,"reps":30,"volume":0,"seconds":60}"""
        assertEquals(KIND_TRAINING, json.decodeFromString<SessionRecord>(old).kind)
    }

    @Test
    fun `mobility sessions never count toward the training week`() {
        val saved = Persisted(history = listOf(record(0), record(1, KIND_MOBILITY), record(2, KIND_MOBILITY)))
        assertEquals(setOf(0), saved.doneThisWeek(monday))
        assertEquals(setOf(1, 2), saved.mobilityDoneThisWeek(monday))
    }

    @Test
    fun `an in-progress session survives a save and reload`() {
        val active = ActiveSession(
            mobility = false, weekIdx = 1, dayIdx = 2, exIdx = 9, setIdx = 1,
            setsDone = 7, movesDone = 14, repsDone = 70, volume = 1200, elapsedSec = 1500,
            startedAtMs = 1_000L, lastCheckpointMs = 2_000L,
            pendingPerf = mapOf("0426" to "14 kg × 8"),
            results = listOf(PendingResult("0426", "press", targetReps = 8, plannedSets = 4, sets = 3, reps = 24, weightKg = 14)),
            hrSum = 900L, hrCount = 9, hrMax = 150,
        )
        val (state, snap) = encodeState(Persisted(active = active))
        assertEquals(active, decodeState(state, snap).active)
    }

    @Test
    fun `stored state from before snapshots existed has no session in progress`() {
        assertNull(decodeState("""{"initials":"AG"}""", active = null).active)
    }

    @Test
    fun `a damaged snapshot is dropped without losing anything else`() {
        val state = json.encodeToString(Persisted(initials = "XY", history = listOf(record(0))))
        val p = decodeState(state, active = """{"exIdx":"not a number"}""")
        assertEquals("XY", p.initials)
        assertEquals(1, p.history.size)
        assertNull(p.active)
    }

    @Test
    fun `the snapshot is stored apart from the rest of the state`() {
        val (state, active) = encodeState(Persisted(initials = "XY", active = ActiveSession(dayIdx = 2)))
        assertNull(json.decodeFromString<Persisted>(state).active)
        assertEquals(2, decodeState(state, active).active?.dayIdx)
        assertNull(encodeState(Persisted()).second)
    }

    @Test
    fun `progress counts training sessions, but minutes and streak include mobility`() {
        val week = listOf(record(0), record(1, KIND_MOBILITY))
        val p = buildProgress(week, emptyList(), emptyList(), Range.Week, today = monday, zone = utc)
        assertEquals(1, p.sessions)
        assertEquals(20, p.minutes)

        // Last week had only a mobility flow; the week before, training. Both keep the streak.
        val streak = listOf(record(0, KIND_MOBILITY, monday.minusWeeks(1)), record(0, date = monday.minusWeeks(2)))
        assertEquals(2, buildProgress(streak, emptyList(), emptyList(), Range.Month, today = monday, zone = utc).streakWeeks)
    }

    @Test
    fun `a warm-up alone is not a training session, one finished move is a mobility session`() {
        assertFalse(shouldRecord(mobility = false, setsDone = 0, movesDone = 7))
        assertTrue(shouldRecord(mobility = false, setsDone = 1, movesDone = 8))
        assertTrue(shouldRecord(mobility = true, setsDone = 0, movesDone = 1))
        assertFalse(shouldRecord(mobility = true, setsDone = 0, movesDone = 0))
    }

    @Test
    fun `stopping partway through an exercise never earns a step-up`() {
        assertFalse(cleared(allSetsHitTarget = true, sets = 2, plannedSets = 4))
        assertFalse(cleared(allSetsHitTarget = false, sets = 4, plannedSets = 4))
        assertTrue(cleared(allSetsHitTarget = true, sets = 4, plannedSets = 4))
    }

    @Test
    fun `after a set the session moves to the next set, then the next move, then ends`() {
        assertEquals(3 to 1, nextPosition(exIdx = 3, setIdx = 0, sets = 4, moves = 10))
        assertEquals(4 to 0, nextPosition(exIdx = 3, setIdx = 3, sets = 4, moves = 10))
        assertNull(nextPosition(exIdx = 9, setIdx = 1, sets = 2, moves = 10))
    }

    @Test
    fun `saving a resumed session dates it by its start and ends it at the last checkpoint`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val start = LocalDate.parse("2026-09-29").atTime(23, 50).atZone(zone).toInstant().toEpochMilli()
        val a = ActiveSession(
            mobility = true, dayIdx = 1, movesDone = 5, elapsedSec = 400,
            startedAtMs = start, lastCheckpointMs = start + 420_000, hrSum = 1000, hrCount = 10, hrMax = 130,
        )
        val r = a.record("Upper Back & Shoulders", zone)
        assertEquals(LocalDate.parse("2026-09-29").toEpochDay(), r.epochDay)
        assertEquals(start + 420_000, r.endedAtMs)
        assertEquals(400, r.seconds)
        assertEquals(KIND_MOBILITY, r.kind)
        assertEquals(100, r.avgHr)
        assertEquals(130, r.maxHr)
    }

    @Test
    fun `exercise results carry the all-planned-sets rule`() {
        val a = ActiveSession(
            lastCheckpointMs = 5L,
            results = listOf(
                PendingResult("a", "A", targetReps = 10, plannedSets = 3, sets = 2, reps = 20, weightKg = 12),
                PendingResult("b", "B", targetReps = 10, plannedSets = 3, sets = 3, reps = 30, weightKg = 12),
            ),
        )
        val (first, second) = a.exerciseResults(epochDay = 7L)
        assertFalse(first.cleared)
        assertTrue(second.cleared)
        assertEquals(7L, second.epochDay)
        assertEquals(5L, second.atMs)
    }

    @Test
    fun `a snapshot pointing at a day that no longer exists resolves to nothing`() {
        val program = Program(
            weeks = listOf(Week("A", "f", days = listOf(Day(num = 1, day = "Mon", title = "T", mins = 40, coach = "c")))),
        )
        assertEquals("Mon", program.day(mobility = false, weekIdx = 0, dayIdx = 0)?.day)
        assertNull(program.day(mobility = false, weekIdx = 0, dayIdx = 3))
        assertNull(program.day(mobility = false, weekIdx = 5, dayIdx = 0))
        assertNull(program.day(mobility = true, weekIdx = 0, dayIdx = 0))
    }

    @Test
    fun `time behind a frozen sheet is never counted as tempo reps`() {
        // Counting since t=1000; End sheet froze the clock at t=5000; app left the screen at t=65000.
        assertEquals(4_000L, bankTempo(accumMs = 0L, resumeAt = 1_000L, now = 65_000L, frozenAt = 5_000L))
        // Not frozen: everything since resuming counts.
        assertEquals(64_000L, bankTempo(accumMs = 0L, resumeAt = 1_000L, now = 65_000L, frozenAt = 0L))
        // Still in the count-in (start in the future): nothing is banked.
        assertEquals(2_000L, bankTempo(accumMs = 2_000L, resumeAt = 70_000L, now = 65_000L, frozenAt = 0L))
    }

    @Test
    fun `the watch window after a resume covers the active time, not the time away`() {
        val resumedNextDay = SessionRecord(
            epochDay = 1, dayIdx = 0, dayTitle = "t", sets = 3, reps = 30, volume = 0, seconds = 1_800,
            startedAtMs = 0L, endedAtMs = 86_400_000L,
        )
        assertEquals(86_400_000L - 1_800_000L, resumedNextDay.activeStartMs)
        val straightThrough = resumedNextDay.copy(startedAtMs = 1_000_000L, endedAtMs = 2_700_000L)
        assertEquals(1_000_000L, straightThrough.activeStartMs)
    }

    @Test
    fun `a leftover with logged work is kept when a new session starts, an empty one is not`() {
        val worked = ActiveSession(setsDone = 2, movesDone = 9)
        assertEquals(worked, leftoverToSave(worked))
        assertNull(leftoverToSave(ActiveSession(setsDone = 0, movesDone = 7)))
        assertNull(leftoverToSave(null))
    }

    @Test
    fun `saving the same session twice records it once`() {
        val snap = ActiveSession(
            setsDone = 1, repsDone = 8, startedAtMs = 1_000L, lastCheckpointMs = 2_000L,
            pendingPerf = mapOf("0426" to "14 kg × 8"),
            results = listOf(PendingResult("0426", "press", targetReps = 8, plannedSets = 4, sets = 1, reps = 8, weightKg = 14)),
        )
        val start = Persisted(active = snap)
        val once = start.recorded(snap, "Upper", utc)
        val twice = once.recorded(snap, "Upper", utc)
        assertEquals(1, twice.history.size)
        assertEquals(1, twice.exerciseHistory.size)
        assertNull(twice.active)
        assertEquals("14 kg × 8", twice.lastPerf["0426"])
    }
}
