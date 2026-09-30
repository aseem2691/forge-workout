package com.forge.workout.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min

/** One muscle region of a figure: its name and the polygons (flat x,y lists) that draw it. */
private class Region(val name: String, val polygons: List<FloatArray>)

private class Figures(val width: Float, val height: Float, val front: List<Region>, val back: List<Region>)

/** Parsed once per process — the whole map is ~6 KB of points. */
@Volatile
private var figures: Figures? = null

private fun regions(array: JSONArray): List<Region> = (0 until array.length()).map { i ->
    val part = array.getJSONObject(i)
    val polys = part.getJSONArray("p")
    Region(
        part.getString("m"),
        (0 until polys.length()).map { j ->
            val points = polys.getJSONArray(j)
            FloatArray(points.length()) { k -> points.getDouble(k).toFloat() }
        },
    )
}

private fun loadFigures(context: Context): Figures? = figures ?: runCatching {
    val root = JSONObject(context.assets.open("bodymap.json").bufferedReader().use { it.readText() })
    Figures(
        root.getDouble("width").toFloat(),
        root.getDouble("height").toFloat(),
        regions(root.getJSONArray("front")),
        regions(root.getJSONArray("back")),
    )
}.getOrNull()?.also { figures = it }

private val BodyBase = Color(0xFF2C2D33)

/**
 * Front and back figures with the worked muscles lit — primary in the accent, assisting muscles
 * dimmer. Polygons from react-body-highlighter (MIT), converted by tools/genbodymap.py.
 */
@Composable
fun BodyMap(primary: List<String>, secondary: List<String>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val map by produceState(initialValue = figures, Unit) {
        value = withContext(Dispatchers.IO) { loadFigures(context) }
    }
    val assisting = C.Accent.copy(alpha = 0.4f)

    Canvas(modifier) {
        val data = map ?: return@Canvas
        val gap = data.width * 0.12f
        val scale = min(size.width / (data.width * 2 + gap), size.height / data.height)
        val left = (size.width - (data.width * 2 + gap) * scale) / 2f
        val top = (size.height - data.height * scale) / 2f

        listOf(data.front to left, data.back to left + (data.width + gap) * scale).forEach { (parts, x0) ->
            parts.forEach { region ->
                val color = when (region.name) {
                    in primary -> C.Accent
                    in secondary -> assisting
                    else -> BodyBase
                }
                region.polygons.forEach { points ->
                    if (points.size < 6) return@forEach
                    val path = Path()
                    path.moveTo(x0 + points[0] * scale, top + points[1] * scale)
                    var i = 2
                    while (i + 1 < points.size) {
                        path.lineTo(x0 + points[i] * scale, top + points[i + 1] * scale)
                        i += 2
                    }
                    path.close()
                    drawPath(path, color)
                }
            }
        }
    }
}
