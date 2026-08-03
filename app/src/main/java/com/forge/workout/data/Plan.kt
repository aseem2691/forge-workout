package com.forge.workout.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val target: String = "",
    val secondary: List<String> = emptyList(),
    val equipment: String = "",
    val category: String = "",
    val steps: List<String> = emptyList(),
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
) {
    val isTimed: Boolean get() = type == "time"
    val isHiit: Boolean get() = block == "hiit"
    val hasLoad: Boolean get() = weight > 0

    /** "3 sets × 12 reps" / "2 × 40s work / 20s rest", matching the design's spec() helper. */
    val spec: String
        get() = if (isTimed) "$sets × ${time}s work / ${rest}s rest" else "$sets sets × $reps reps"

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
    val strength: List<Exercise> = emptyList(),
    val hiit: List<Exercise> = emptyList(),
) {
    /** The session in running order: strength block, then the HIIT finisher. */
    val all: List<Exercise> get() = strength + hiit
    val flatTitle: String get() = title.replace("\n", " ")
    val totalSets: Int get() = all.sumOf { it.sets }

    /** Distinct target muscles across the session, for the day-header chips. */
    val muscles: List<String> get() = all.map { it.target }.filter { it.isNotBlank() }.distinct().take(5)
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

/** Title-cases dataset strings like "upper back" → "Upper Back" (the design's cap()). */
fun String.titleCase(): String = split(" ").joinToString(" ") { word ->
    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
