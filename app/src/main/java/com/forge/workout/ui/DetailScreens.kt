package com.forge.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.forge.workout.data.ExerciseResult
import com.forge.workout.data.SessionRecord
import com.forge.workout.data.titleCase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

private fun dayLabel(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dayFormat)

private fun timeLabel(atMs: Long): String =
    Instant.ofEpochMilli(atMs).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))

@Composable
private fun DetailHeader(eyebrow: String, title: String, onBack: () -> Unit) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF191A1E))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("←", style = arch(15.0, 600, C.Text, line = 1.0))
            }
            Text(eyebrow.uppercase(), style = arch(9.5, 800, C.Dim, track = 0.2, line = 1.0))
        }
        Spacer(Modifier.height(10.dp))
        Text(title.uppercase(), style = display(30.0, line = 0.98))
    }
}

/** Every exercise you have logged, most recently trained first. */
@Composable
fun ExerciseListScreen(
    results: List<ExerciseResult>,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
) {
    val byExercise = results
        .groupBy { it.exerciseId }
        .map { (id, rows) -> Triple(id, rows.last(), rows.size) }
        .sortedByDescending { it.second.atMs }

    LazyColumn(
        Modifier.fillMaxSize().background(C.Bg),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item { DetailHeader("Progress", "Your lifts", onBack) }
        item {
            Text(
                "Tap a lift to see how its working weight has moved.",
                style = arch(11.0, 500, C.Muted, line = 1.5),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
            )
        }
        items(byExercise) { (id, latest, count) ->
            Row(
                Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(C.Card)
                    .border(1.dp, C.Border, RoundedCornerShape(13.dp))
                    .clickable { onPick(id) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(latest.name.titleCase(), style = arch(13.0, 600, C.Text, line = 1.25))
                    Text(
                        "$count ${if (count == 1) "session" else "sessions"} · last ${dayLabel(latest.epochDay)}",
                        style = arch(10.5, 500, C.Muted, line = 1.3),
                    )
                }
                Text(
                    if (latest.weightKg > 0) "${latest.weightKg} KG" else "BODY",
                    style = arch(10.5, 700, C.Accent, track = 0.06, line = 1.0),
                )
            }
        }
        if (byExercise.isEmpty()) {
            item {
                Text(
                    "Nothing logged yet — finish a session and your lifts will appear here.",
                    style = arch(11.0, 500, C.Faint, line = 1.5),
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}

/** One lift's working weight over time, plus every logged outing. */
@Composable
fun ExerciseDetailScreen(
    results: List<ExerciseResult>,
    onBack: () -> Unit,
) {
    val name = results.lastOrNull()?.name?.titleCase() ?: "Exercise"
    val loaded = results.any { it.weightKg > 0 }
    val best = results.maxByOrNull { it.weightKg }

    LazyColumn(
        Modifier.fillMaxSize().background(C.Bg),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item { DetailHeader("Lift", name, onBack) }

        item {
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                MiniStat(Modifier.weight(1f), "${results.size}", "Sessions")
                MiniStat(
                    Modifier.weight(1f),
                    if (loaded) "${best?.weightKg ?: 0}" else "—",
                    "Best weight",
                )
                MiniStat(Modifier.weight(1f), "${results.sumOf { it.reps }}", "Total reps")
            }
        }

        if (loaded) {
            item {
                Column(
                    Modifier
                        .padding(start = 20.dp, end = 20.dp, top = 14.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(C.Card)
                        .border(1.dp, C.Border, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Text("WORKING WEIGHT", style = display(15.0, line = 1.0, track = 0.05))
                    Spacer(Modifier.height(3.dp))
                    Text("Kilograms per hand, per session.", style = arch(10.5, 500, C.Muted, line = 1.4))
                    Spacer(Modifier.height(14.dp))
                    TrendChart(
                        points = results.map { TrendPoint(it.atMs, it.weightKg.toFloat()) },
                        target = null,
                        targetLabel = null,
                        emptyMessage = "No sessions logged",
                        singleMessage = "One session so far — need two to draw a trend",
                        format = { "${it.toInt()}" },
                    )
                }
            }
        }

        item {
            Text(
                "EVERY SESSION",
                style = display(15.0, line = 1.0, track = 0.05),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
            )
        }

        items(results.reversed()) { result ->
            Row(
                Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(C.Card)
                    .border(1.dp, C.Border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(dayLabel(result.epochDay), style = arch(12.5, 600, C.Text, line = 1.2))
                    Text(
                        "${result.sets} × ${if (result.sets > 0) result.reps / result.sets else 0} reps" +
                            if (result.weightKg > 0) " · ${result.weightKg} kg" else "",
                        style = arch(10.5, 500, C.Muted, line = 1.3),
                    )
                }
                if (result.cleared) {
                    Text("✓ CLEARED", style = arch(9.5, 700, C.Accent, track = 0.08, line = 1.0))
                }
            }
        }
    }
}

/** Every completed session, newest first, with what the watch added. */
@Composable
fun SessionListScreen(
    history: List<SessionRecord>,
    onBack: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().background(C.Bg),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item { DetailHeader("Progress", "Your sessions", onBack) }

        items(history.reversed()) { record ->
            Column(
                Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.Card)
                    .border(1.dp, C.Border, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(record.dayTitle.uppercase(), style = display(17.0, line = 1.05))
                    Text(
                        dayLabel(record.epochDay) +
                            if (record.startedAtMs > 0) " · ${timeLabel(record.startedAtMs)}" else "",
                        style = arch(10.0, 600, C.Faint, line = 1.2),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Fact("${record.sets}", "sets")
                    Fact("${record.reps}", "reps")
                    Fact(tonnes(record.volume), "volume")
                    Fact("${record.seconds / 60}m", "time")
                }
                if (record.avgHr != null || record.calories != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        record.avgHr?.let { Fact("$it", "avg bpm") }
                        record.maxHr?.let { Fact("$it", "max bpm") }
                        record.calories?.let { Fact("$it", "kcal") }
                    }
                }
            }
        }

        if (history.isEmpty()) {
            item {
                Text(
                    "No sessions logged yet.",
                    style = arch(11.0, 500, C.Faint, line = 1.5),
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}

@Composable
private fun Fact(value: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = arch(13.0, 700, C.Text, line = 1.0))
        Text(label, style = arch(9.5, 500, C.Faint, line = 1.2))
    }
}

@Composable
private fun MiniStat(modifier: Modifier, value: String, label: String) {
    Column(
        modifier
            .clip(RoundedCornerShape(13.dp))
            .background(C.Card)
            .border(1.dp, C.Border, RoundedCornerShape(13.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(value, style = display(20.0, line = 1.0))
        Text(label.uppercase(), style = arch(8.0, 700, C.Dim, track = 0.12, line = 1.3))
    }
}
