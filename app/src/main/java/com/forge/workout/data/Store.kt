package com.forge.workout.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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
    val initials: String = "RK",
    /** Remembered BLE heart-rate broadcaster (the watch, or a strap). */
    val hrAddress: String? = null,
    val hrName: String? = null,
) {
    fun weightFor(e: Exercise): Int = weights[e.id] ?: e.weight

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

    val data: Flow<Persisted> = context.dataStore.data.map { parse(it[key]) }

    suspend fun update(transform: (Persisted) -> Persisted) {
        context.dataStore.edit { prefs ->
            prefs[key] = json.encodeToString(transform(parse(prefs[key])))
        }
    }
}
