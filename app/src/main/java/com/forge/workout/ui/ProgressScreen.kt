package com.forge.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.forge.workout.data.Period
import com.forge.workout.data.Progress
import com.forge.workout.data.Range

@Composable
fun ProgressScreen(
    range: Range,
    progress: Progress,
    currentKg: Float,
    targetKg: Float,
    fromScale: Boolean,
    walkKm: Float?,
    walkSteps: Int?,
    onRange: (Range) -> Unit,
    onOpenSessions: () -> Unit,
    onOpenLifts: () -> Unit,
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(C.Bg),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp)) {
                Text("YOUR", style = arch(9.5, 800, C.Dim, track = 0.2, line = 1.0))
                Spacer(Modifier.height(4.dp))
                Text("PROGRESS", style = display(38.0, line = 0.95))
            }
        }

        // One filter row, above everything it scopes — all charts read the same slice.
        item {
            Row(
                Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(C.Panel)
                    .border(1.dp, C.Border, RoundedCornerShape(11.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Range.entries.forEach { option ->
                    val selected = option == range
                    Text(
                        option.label,
                        style = arch(10.0, 700, if (selected) C.OnAccent else C.Muted, track = 0.08, line = 1.0),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) C.Accent else Color.Transparent)
                            .clickable { onRange(option) }
                            .padding(horizontal = 15.dp, vertical = 9.dp),
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Stat(Modifier.weight(1f), "${progress.sessions}", "Sessions")
                    Stat(Modifier.weight(1f), tonnes(progress.volumeKg), "Volume")
                }
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Stat(Modifier.weight(1f), "${progress.minutes}m", "Time trained")
                    Stat(
                        Modifier.weight(1f),
                        progress.avgHr?.let { "$it" } ?: "—",
                        "Avg heart rate",
                    )
                }
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Stat(Modifier.weight(1f), "${progress.reps}", "Reps logged")
                    Stat(Modifier.weight(1f), "${progress.streakWeeks}", "Week streak")
                }
                if (walkKm != null && walkKm > 0f) {
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Stat(Modifier.weight(1f), format1(walkKm), "Km walked")
                        Stat(Modifier.weight(1f), "${walkSteps ?: 0}", "Steps")
                    }
                }
            }
        }

        item {
            ChartCard(
                title = "Body weight",
                subtitle = buildString {
                    append(format1(currentKg))
                    append(" kg now")
                    progress.weightChange?.let { append(" · ${signed(it)} kg over ${range.label}") }
                    if (fromScale) append(" · from your scale")
                },
            ) {
                TrendChart(
                    points = progress.weighIns.map { TrendPoint(it.atMs, it.kg) },
                    target = targetKg,
                    targetLabel = "TARGET ${format1(targetKg)}",
                    emptyMessage = "No weigh-ins in this range",
                    singleMessage = "One weigh-in so far — need two to draw a trend",
                )
            }
        }

        if (progress.bodyFat.isNotEmpty()) {
            item {
                ChartCard(
                    title = "Body fat",
                    subtitle = buildString {
                        append(format1(progress.bodyFat.last().percent))
                        append("% now")
                        progress.bodyFatChange?.let { append(" · ${signed(it)}% over ${range.label}") }
                        append(" · from your scale")
                    },
                ) {
                    TrendChart(
                        points = progress.bodyFat.map { TrendPoint(it.atMs, it.percent) },
                        target = null,
                        targetLabel = null,
                        emptyMessage = "No body-fat readings in this range",
                        singleMessage = "One reading so far — need two to draw a trend",
                        format = { "${format1(it)}%" },
                    )
                }
            }
        }

        item {
            ChartCard(
                title = "Training volume",
                subtitle = "${tonnes(progress.volumeKg)} lifted across ${progress.sessions} " +
                    if (progress.sessions == 1) "session" else "sessions",
            ) {
                VolumeChart(periods = progress.periods)
            }
        }

        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
                NavRow("Your lifts", "Weight per exercise over time", onOpenLifts)
                Spacer(Modifier.height(9.dp))
                NavRow("Your sessions", "Every workout you have finished", onOpenSessions)
            }
        }

        // The table view: every plotted value is readable as text, not colour-only.
        item {
            ProgressTable(progress.periods)
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(C.Card)
            .border(1.dp, C.Border, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = arch(12.5, 600, C.Text, line = 1.2))
            Text(subtitle, style = arch(10.5, 500, C.Muted, line = 1.3))
        }
        Text("›", style = arch(14.0, 700, C.Faint, line = 1.0))
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.Card)
            .border(1.dp, C.Border, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(title.uppercase(), style = display(15.0, line = 1.0, track = 0.05))
        Spacer(Modifier.height(3.dp))
        Text(subtitle, style = arch(10.5, 500, C.Muted, line = 1.4))
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun Stat(modifier: Modifier, value: String, label: String) {
    Column(
        modifier
            .clip(RoundedCornerShape(13.dp))
            .background(C.Card)
            .border(1.dp, C.Border, RoundedCornerShape(13.dp))
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(value, style = display(22.0, line = 1.0))
        Text(label.uppercase(), style = arch(8.5, 700, C.Dim, track = 0.13, line = 1.3))
    }
}

@Composable
private fun ProgressTable(periods: List<Period>) {
    // Newest first, and only rows with something in them — empty buckets belong in the
    // chart (to show the gap) but would be noise as table rows.
    val rows = periods.reversed()
        .filter { it.sessions > 0 || it.weightKg != null || it.bodyFatPct != null }

    Column(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.Card)
            .border(1.dp, C.Border, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text("BREAKDOWN", style = display(15.0, line = 1.0, track = 0.05))
        Spacer(Modifier.height(3.dp))
        Text(
            "Every plotted value, as numbers.",
            style = arch(10.5, 500, C.Muted, line = 1.4),
        )
        Spacer(Modifier.height(12.dp))

        if (rows.isEmpty()) {
            Text("Nothing logged in this range yet.", style = arch(11.0, 500, C.Faint, line = 1.4))
            return@Column
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            HeaderCell("When", 1.1f)
            HeaderCell("Sess", 0.7f)
            HeaderCell("Volume", 1.1f)
            HeaderCell("Min", 0.6f)
            HeaderCell("Kg", 0.75f)
            HeaderCell("Fat", 0.7f)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Border))

        rows.take(40).forEach { period ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BodyCell(period.label, 1.1f, C.Text)
                BodyCell(if (period.sessions > 0) "${period.sessions}" else "—", 0.7f, C.Muted)
                BodyCell(if (period.volumeKg > 0) tonnes(period.volumeKg) else "—", 1.1f, C.Muted)
                BodyCell(if (period.minutes > 0) "${period.minutes}" else "—", 0.6f, C.Muted)
                BodyCell(period.weightKg?.let { format1(it) } ?: "—", 0.75f, C.Muted)
                BodyCell(period.bodyFatPct?.let { format1(it) } ?: "—", 0.7f, C.Muted)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text.uppercase(),
        style = arch(8.5, 700, C.Dim, track = 0.12, line = 1.0),
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BodyCell(text: String, weight: Float, color: Color) {
    Text(
        text,
        style = arch(11.5, 600, color, line = 1.2),
        modifier = Modifier.weight(weight),
    )
}
