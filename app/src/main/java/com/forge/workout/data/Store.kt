package com.forge.workout.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** One completed session, kept forever so weekly stats and progression are real. */
@Serializable
data class SessionRecord(
    val epochDay: Long,
    val dayIdx: Int,
    val dayTitle: String,
    val sets: Int,
    val reps: Int,
    val volume: Int,
    val seconds: Int,
    /** Wall-clock bounds, used to match this session against the watch's own recording. */
    val startedAtMs: Long = 0L,
    val endedAtMs: Long = 0L,
    /** From the live BLE monitor during the session, or from the watch via Health Connect. */
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val calories: Int? = null,
    val watchTitle: String? = null,
)

/** A manually entered weigh-in. Scale readings live in Health Connect, not here. */
@Serializable
data class WeightEntry(val atMs: Long, val kg: Float)

/** What one exercise actually produced in one session — the basis of progression. */
@Serializable
data class ExerciseResult(
    val exerciseId: String,
    val name: String,
    val atMs: Long,
    val epochDay: Long,
    val weightKg: Int,
    val sets: Int,
    val reps: Int,
    val targetReps: Int,
    /** True when every set hit its target reps — the trigger for adding load. */
    val cleared: Boolean,
)

@Serializable
data class Persisted(
    /** Working weight per exercise id, as adjusted with the stepper. */
    val weights: Map<String, Int> = emptyMap(),
    /** Last logged performance per exercise id, e.g. "16 kg × 10". */
    val lastPerf: Map<String, String> = emptyMap(),
    val history: List<SessionRecord> = emptyList(),
    val bodyStart: Float = 88.0f,
    val bodyNow: Float = 84.2f,
    val bodyTarget: Float = 75.0f,
    /** Epoch day the program began; 0 until first launch stamps it. */
    val programStart: Long = 0L,
    val mode: String = "tap",
    val initials: String = "AG",
    /** Remembered BLE heart-rate broadcaster (the watch, or a strap). */
    val hrAddress: String? = null,
    val hrName: String? = null,
    /** When bodyNow was last set, so a newer scale reading can supersede a manual entry. */
    val bodyNowMs: Long = 0L,
    /** True when bodyNow came from a scale via Health Connect rather than being typed in. */
    val bodyFromScale: Boolean = false,
    /** Latest body-fat percentage from the scale; null until one is read. */
    val bodyFatPct: Float? = null,
    /** Manually entered weigh-ins, so a trend exists even without a connected scale. */
    val weightLog: List<WeightEntry> = emptyList(),
    /** Per-exercise results, oldest first — drives progression and per-exercise trends. */
    val exerciseHistory: List<ExerciseResult> = emptyList(),
) {
    fun weightFor(e: Exercise): Int = weights[e.id] ?: e.weight

    fun resultsFor(exerciseId: String): List<ExerciseResult> =
        exerciseHistory.filter { it.exerciseId == exerciseId }

    /**
     * The next working weight, when the last outing cleared every set at the weight still set.
     * Dumbbells step in 2 kg and top out at 24.
     */
    fun progressionFor(e: Exercise): Int? {
        if (!e.hasLoad) return null
        val last = resultsFor(e.id).lastOrNull() ?: return null
        val current = weightFor(e)
        if (!last.cleared || last.weightKg != current || current >= 24) return null
        return (current + 2).coerceAtMost(24)
    }

    /** Sessions logged in the current Monday-start week. */
    fun thisWeek(today: LocalDate = LocalDate.now()): List<SessionRecord> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
        return history.filter { it.epochDay >= monday }
    }

    /** Day indices already completed this week — drives the "✓ Done" state on the plan screen. */
    fun doneThisWeek(today: LocalDate = LocalDate.now()): Set<Int> =
        thisWeek(today).map { it.dayIdx }.toSet()

    /** 1-based program week, derived from the stored start date. */
    fun weekNumber(today: LocalDate = LocalDate.now()): Int {
        if (programStart == 0L) return 1
        val start = LocalDate.ofEpochDay(programStart)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (ChronoUnit.WEEKS.between(start, monday) + 1).toInt().coerceAtLeast(1)
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("forge")

class Store(private val context: Context) {
    private val key = stringPreferencesKey("state")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun parse(raw: String?): Persisted =
        raw?.let { runCatching { json.decodeFromString<Persisted>(it) }.getOrNull() } ?: Persisted()

    /**
     * A read failure here — a corrupt file, a disk error — would otherwise kill the collector and
     * leave the app on a blank screen forever, since the UI waits for the first emission. Fall
     * back to defaults instead: losing saved state is bad, never opening again is worse.
     */
    val data: Flow<Persisted> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { parse(it[key]) }

    suspend fun update(transform: (Persisted) -> Persisted) {
        context.dataStore.edit { prefs ->
            prefs[key] = json.encodeToString(transform(parse(prefs[key])))
        }
    }
}
