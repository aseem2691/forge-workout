package com.forge.workout.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val target: String = "",
    val secondary: List<String> = emptyList(),
    val equipment: String = "",
    val category: String = "",
    val steps: List<String> = emptyList(),
    /** Animated 3D demo: the dataset's GIF, upscaled to a 720×720 animated WebP. */
    val gif: String = "",
    val thumb: String = "",
    val sets: Int = 1,
    val reps: Int = 0,
    val time: Int = 0,
    val weight: Int = 0,
    val rest: Int = 60,
    val last: String? = null,
    val type: String = "reps",
    val block: String? = null,
    /** Body-map regions (assets/bodymap.json) the move works hardest, then the ones assisting. */
    val bodyPrimary: List<String> = emptyList(),
    val bodySecondary: List<String> = emptyList(),
    /** Coaching line for a warm-up move, shown on the player. */
    val cue: String? = null,
    /** Where the instructions and demo media come from, for the how-to sheet. */
    val credit: String = "",
) {
    val isTimed: Boolean get() = type == "time"
    val isHiit: Boolean get() = block == "hiit"
    val isWarmup: Boolean get() = block == "warmup"
    val hasLoad: Boolean get() = weight > 0

    /** "3 sets × 12 reps" / "2 × 40s work / 20s rest", matching the design's spec() helper. */
    val spec: String
        get() = when {
            isWarmup -> duration(time)
            isTimed -> "$sets × ${time}s work / ${rest}s rest"
            else -> "$sets sets × $reps reps"
        }

    /** Up to four muscles: target first, then secondaries, deduped. */
    val muscles: List<String>
        get() = (listOf(target) + secondary).filter { it.isNotBlank() }.distinct().take(4)
}

@Serializable
data class Day(
    val num: Int,
    val day: String,
    val title: String,
    val mins: Int,
    val coach: String,
    val warmup: List<Exercise> = emptyList(),
    val strength: List<Exercise> = emptyList(),
    val hiit: List<Exercise> = emptyList(),
) {
    /** The session in running order: warm-up, strength block, then the HIIT finisher. */
    val all: List<Exercise> get() = warmup + strength + hiit

    /** The training itself — what the exercise count, sets and muscle chips describe. */
    val main: List<Exercise> get() = strength + hiit
    val flatTitle: String get() = title.replace("\n", " ")
    val totalSets: Int get() = main.sumOf { it.sets }

    /** Warm-up length in minutes: the moves and the switch-overs between them. */
    val warmupMins: Int
        get() = ((warmup.sumOf { it.time + it.rest } - (warmup.lastOrNull()?.rest ?: 0)) / 60.0).roundToInt()

    /** Distinct target muscles across the session, for the day-header chips. */
    val muscles: List<String> get() = main.map { it.target }.filter { it.isNotBlank() }.distinct().take(5)
}

/** One week of the training block. Weeks rotate so the program varies week to week. */
@Serializable
data class Week(
    val label: String,
    val focus: String,
    val days: List<Day> = emptyList(),
)

@Serializable
data class Program(val weeks: List<Week> = emptyList())

private val json = Json { ignoreUnknownKeys = true }

object PlanLoader {
    fun load(context: Context): Program =
        context.assets.open("plan.json").bufferedReader().use { json.decodeFromString(it.readText()) }
}

/** "40 sec" / "2 min". */
fun duration(seconds: Int): String = if (seconds % 60 == 0) "${seconds / 60} min" else "$seconds sec"

/** Title-cases dataset strings like "upper back" → "Upper Back" (the design's cap()). */
fun String.titleCase(): String = split(" ").joinToString(" ") { word ->
    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
