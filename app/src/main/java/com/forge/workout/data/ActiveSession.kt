package com.forge.workout.data

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

const val KIND_TRAINING = "training"
const val KIND_MOBILITY = "mobility"

/** Running tally for one exercise in the session in progress; persisted inside [ActiveSession]. */
@Serializable
data class PendingResult(
    val exerciseId: String,
    val name: String,
    val targetReps: Int,
    val plannedSets: Int,
    val sets: Int = 0,
    val reps: Int = 0,
    val weightKg: Int = 0,
    /** False as soon as one logged set fell short of the target reps. */
    val allSetsHitTarget: Boolean = true,
)

/**
 * The workout in progress, written at every checkpoint (session start, each logged set, each move
 * to another exercise, opening the End-workout sheet) so it survives the process dying.
 * [exIdx]/[setIdx] point at the next set still to do.
 */
@Serializable
data class ActiveSession(
    val mobility: Boolean = false,
    val weekIdx: Int = 0,
    val dayIdx: Int = 0,
    val exIdx: Int = 0,
    val setIdx: Int = 0,
    val setsDone: Int = 0,
    val movesDone: Int = 0,
    val repsDone: Int = 0,
    val volume: Int = 0,
    val elapsedSec: Int = 0,
    val startedAtMs: Long = 0L,
    val lastCheckpointMs: Long = 0L,
    val pendingPerf: Map<String, String> = emptyMap(),
    val results: List<PendingResult> = emptyList(),
    val hrSum: Long = 0L,
    val hrCount: Int = 0,
    val hrMax: Int = 0,
) {
    val kind: String get() = if (mobility) KIND_MOBILITY else KIND_TRAINING

    /** Whether saving would record anything — see [shouldRecord]. */
    val hasWork: Boolean get() = shouldRecord(mobility, setsDone, movesDone)

    /**
     * The history row this session becomes: dated by when it started and ended at its last
     * checkpoint, so saving it later never inflates the duration or moves it to another day.
     */
    fun record(dayTitle: String, zone: ZoneId = ZoneId.systemDefault()) = SessionRecord(
        epochDay = Instant.ofEpochMilli(startedAtMs).atZone(zone).toLocalDate().toEpochDay(),
        dayIdx = dayIdx,
        dayTitle = dayTitle,
        sets = setsDone,
        reps = repsDone,
        volume = volume,
        seconds = elapsedSec,
        startedAtMs = startedAtMs,
        endedAtMs = lastCheckpointMs,
        avgHr = if (hrCount > 0) (hrSum / hrCount).toInt() else null,
        maxHr = hrMax.takeIf { it > 0 },
        kind = kind,
    )

    /** Per-exercise results for progression, stamped with the record's [epochDay]. */
    fun exerciseResults(epochDay: Long): List<ExerciseResult> = results.map {
        ExerciseResult(
            exerciseId = it.exerciseId, name = it.name, atMs = lastCheckpointMs, epochDay = epochDay,
            weightKg = it.weightKg, sets = it.sets, reps = it.reps, targetReps = it.targetReps,
            cleared = cleared(it.allSetsHitTarget, it.sets, it.plannedSets),
        )
    }
}

/** A training session records once a set is logged; a mobility flow (no sets) once a move is done. */
fun shouldRecord(mobility: Boolean, setsDone: Int, movesDone: Int): Boolean =
    if (mobility) movesDone > 0 else setsDone > 0

/** Cleared — and so due a step-up — only if every planned set was done and every one hit target. */
fun cleared(allSetsHitTarget: Boolean, sets: Int, plannedSets: Int): Boolean =
    allSetsHitTarget && sets >= plannedSets

/** Where the session goes after set [setIdx] of move [exIdx]; null when that was the last one. */
fun nextPosition(exIdx: Int, setIdx: Int, sets: Int, moves: Int): Pair<Int, Int>? = when {
    setIdx + 1 < sets -> exIdx to setIdx + 1
    exIdx + 1 < moves -> exIdx + 1 to 0
    else -> null
}

/**
 * Tempo time to bank when counting stops. Time behind a frozen sheet never counts (the clock was
 * held), and neither does a count-in still to come — otherwise leaving the app with the End sheet
 * open would credit reps nobody did.
 */
fun bankTempo(accumMs: Long, resumeAt: Long, now: Long, frozenAt: Long): Long =
    accumMs + ((if (frozenAt != 0L) frozenAt else now) - resumeAt).coerceAtLeast(0)

/** A snapshot the app stopped in the middle of, worth saving before a new session replaces it. */
fun leftoverToSave(active: ActiveSession?): ActiveSession? = active?.takeIf { it.hasWork }

/**
 * Where the session's active time begins, for matching the watch and publishing to Health
 * Connect: a resumed session ends at its last checkpoint, but its time away is left out.
 */
val SessionRecord.activeStartMs: Long get() = maxOf(startedAtMs, endedAtMs - seconds * 1000L)

/**
 * This state with [snap] saved into history and the snapshot cleared. Saving the same session
 * again changes nothing, so a double tap can never record it twice.
 */
fun Persisted.recorded(snap: ActiveSession, title: String, zone: ZoneId = ZoneId.systemDefault()): Persisted {
    val rec = snap.record(title, zone)
    if (rec.startedAtMs != 0L && history.any { it.startedAtMs == rec.startedAtMs }) return copy(active = null)
    return copy(
        history = history + rec,
        lastPerf = lastPerf + snap.pendingPerf,
        exerciseHistory = (exerciseHistory + snap.exerciseResults(rec.epochDay)).takeLast(2000),
        programStart = if (programStart == 0L) rec.epochDay else programStart,
        active = null,
    )
}
