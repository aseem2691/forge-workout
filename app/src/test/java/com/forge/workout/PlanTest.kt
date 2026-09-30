package com.forge.workout

import com.forge.workout.data.Program
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * plan.json is generated, so these pin the generator's promises: every session opens with a
 * 5–8 minute warm-up, no rest runs past a minute, and every demo, thumbnail and body-map region
 * the plan names actually ships in the APK.
 */
class PlanTest {

    // Unit tests run from the module directory.
    private val assets = File("src/main/assets")
    private val program: Program =
        Json { ignoreUnknownKeys = true }.decodeFromString(File(assets, "plan.json").readText())
    private val days = program.weeks.flatMap { it.days }
    private val flows = program.mobility
    private val exercises = (days + flows).flatMap { it.all }

    @Test
    fun `every session opens with a five to eight minute warm-up`() {
        assertEquals(16, days.size)
        days.forEach { day ->
            assertTrue("${day.flatTitle}: ${day.warmupMins} min", day.warmupMins in 5..8)
            assertEquals(day.warmup, day.all.take(day.warmup.size))
            day.warmup.forEach {
                assertTrue(it.name, it.isWarmup && it.isTimed && it.sets == 1 && !it.hasLoad)
                assertTrue(it.name, it.cue != null)
            }
        }
    }

    @Test
    fun `no break between sets or exercises runs past a minute`() {
        exercises.forEach { assertTrue("${it.name} rests ${it.rest}s", it.rest <= 60) }
        // The 40/20 finisher keeps its interval.
        days.flatMap { it.hiit }.forEach { assertEquals(it.name, 20, it.rest) }
    }

    @Test
    fun `the warm-up is not counted as training`() {
        days.forEach { day ->
            assertEquals(day.strength.sumOf { it.sets } + day.hiit.sumOf { it.sets }, day.totalSets)
            assertTrue(day.muscles.all { muscle -> day.main.any { it.target == muscle } })
        }
    }

    @Test
    fun `every move has an animated demo and a thumbnail bundled`() {
        val media = File(assets, "media")
        exercises.forEach { e ->
            assertTrue("${e.name}: demo ${e.gif} is not an upscaled WebP", e.gif.endsWith(".webp"))
            listOf(e.gif, e.thumb).forEach { ref ->
                assertTrue("${e.name}: missing $ref", File(media, ref.substringAfterLast('/')).isFile)
            }
        }
    }

    @Test
    fun `body-map regions all exist in the map`() {
        val map = Json.parseToJsonElement(File(assets, "bodymap.json").readText()).jsonObject
        val regions = listOf("front", "back").flatMap { side ->
            map.getValue(side).jsonArray.map { it.jsonObject.getValue("m").jsonPrimitive.content }
        }.toSet()
        exercises.forEach { e ->
            assertTrue("${e.name} lights no muscle", e.bodyPrimary.isNotEmpty())
            (e.bodyPrimary + e.bodySecondary).forEach { assertTrue("${e.name}: $it", it in regions) }
        }
    }

    @Test
    fun `every training day ends with a three to six minute cool-down`() {
        days.forEach { day ->
            assertTrue("${day.flatTitle}: ${day.cooldownMins} min", day.cooldownMins in 3..6)
            assertEquals(day.cooldown, day.all.takeLast(day.cooldown.size))
            day.cooldown.forEach {
                assertTrue(it.name, it.isCooldown && it.isTimed && it.sets == 1 && !it.hasLoad)
                assertTrue(it.name, it.cue != null)
            }
        }
    }

    @Test
    fun `three rest-day mobility flows of twelve to sixteen minutes`() {
        assertEquals(listOf("Wed", "Fri", "Sun"), flows.map { it.day })
        flows.forEach { flow ->
            assertTrue(flow.flatTitle, flow.isMobility)
            assertTrue(flow.flatTitle, flow.warmup.isEmpty() && flow.main.isEmpty() && flow.cooldown.isEmpty())
            val minutes = (flow.flow.sumOf { it.time + it.rest } - flow.flow.last().rest) / 60.0
            assertTrue("${flow.flatTitle}: $minutes min", minutes in 12.0..16.0)
            flow.flow.forEach {
                assertTrue(it.name, it.isMobility && it.isTimed && it.sets == 1 && !it.hasLoad)
                assertTrue(it.name, it.cue != null)
            }
        }
    }

    @Test
    fun `cool-down and mobility moves are not counted as training`() {
        days.forEach { day ->
            assertEquals(day.strength.sumOf { it.sets } + day.hiit.sumOf { it.sets }, day.totalSets)
        }
        flows.forEach { assertEquals(0, it.totalSets) }
    }
}
