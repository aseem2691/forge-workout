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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forge.workout.data.Day
import com.forge.workout.data.Persisted
import com.forge.workout.data.Week
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.abs

@Composable
fun PlanScreen(
    week: Week,
    saved: Persisted,
    todayIndex: Int,
    watchLinked: Boolean,
    onOpenDay: (Int) -> Unit,
    onEditBody: () -> Unit,
    onOpenWatch: () -> Unit,
) {
    val plan = week.days
    val done = saved.doneThisWeek()
    val today = LocalDate.now()
    val weekSessions = saved.thisWeek()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(C.Bg),
        contentPadding = PaddingValues(bottom = 26.dp),
    ) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Week ${saved.weekNumber()} · " +
                            today.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.getDefault()),
                        style = arch(9.5, 800, Color(0xFF7E7E88), track = 0.2, line = 1.0),
                    )
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF1E1E23)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(saved.initials, style = arch(11.0, 700, C.Accent, line = 1.0))
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "LET'S\nGET AFTER IT",
                    style = display(40.0, line = 0.92),
                )
            }
        }

        item { BodyWeightCard(saved, onEditBody) }

        item {
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                val volume = weekSessions.sumOf { it.volume }
                val minutes = weekSessions.sumOf { it.seconds } / 60
                StatTile(Modifier.weight(1f), "${done.size}/${plan.size}", "Sessions done")
                StatTile(Modifier.weight(1f), formatTonnes(volume), "Volume this wk")
                StatTile(Modifier.weight(1f), "${minutes}m", "Time this wk")
            }
        }

        item {
            Row(
                Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(C.Card)
                    .border(1.dp, C.Border, RoundedCornerShape(13.dp))
                    .clickable(onClick = onOpenWatch)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("⌚", style = arch(14.0, 500, C.Text, line = 1.0))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (watchLinked) "Watch connected" else "Connect your watch",
                        style = arch(12.5, 600, C.Text, line = 1.2),
                    )
                    Text(
                        if (watchLinked) {
                            "Heart rate and calories sync after each session"
                        } else {
                            "Live heart rate and workout sync from your Amazfit"
                        },
                        style = arch(10.5, 500, C.Muted, line = 1.35),
                    )
                }
                Text(
                    if (watchLinked) "✓" else "›",
                    style = arch(14.0, 700, if (watchLinked) C.Accent else C.Faint, line = 1.0),
                )
            }
        }

        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text("YOUR ${plan.size}-DAY SPLIT", style = display(15.0, line = 1.0, track = 0.06))
                    Text(
                        "BLOCK ${week.label}",
                        style = arch(10.0, 700, C.Accent, track = 0.12, line = 1.0),
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(week.focus, style = arch(11.0, 500, C.Dim, line = 1.4))
            }
        }

        itemsIndexed(plan) { index, day ->
            DayCard(
                day = day,
                isToday = index == todayIndex && index !in done,
                isDone = index in done,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 11.dp),
                onClick = { onOpenDay(index) },
            )
        }

        item {
            Row(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .border(1.dp, Color(0xFF26272C), RoundedCornerShape(15.dp))
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("REST", style = display(15.0, Color(0xFF4D4D55), line = 1.0))
                Text(
                    "Wed / Fri / Sun — 30 min easy walkpad, 15 min mobility on the mat.",
                    style = arch(11.0, 500, Color(0xFF65656E), line = 1.4),
                )
            }
        }

        item {
            Text(
                "Exercise media © Gym visual · exercises-dataset (MIT)",
                style = arch(9.5, 500, Color(0xFF4A4A52), line = 1.4),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, start = 20.dp, end = 20.dp),
            )
        }
    }
}

@Composable
private fun BodyWeightCard(saved: Persisted, onEdit: () -> Unit) {
    val lost = saved.bodyStart - saved.bodyNow
    val toGo = saved.bodyNow - saved.bodyTarget
    val span = (saved.bodyStart - saved.bodyTarget).takeIf { abs(it) > 0.01f } ?: 1f
    val progress = (lost / span).coerceIn(0f, 1f)

    Column(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = 18.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF191A1D), Color(0xFF131316))))
            .border(1.dp, C.BorderSoft, RoundedCornerShape(16.dp))
            .clickable(onClick = onEdit)
            .padding(start = 17.dp, end = 17.dp, top = 16.dp, bottom = 15.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("BODY WEIGHT", style = arch(9.0, 800, Color(0xFF7E7E88), track = 0.18, line = 1.0))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(format1(saved.bodyNow), style = display(32.0, line = 1.0))
                    Text("kg", style = arch(12.0, 600, Color(0xFF7E7E88), line = 1.0))
                    saved.bodyFatPct?.let { fat ->
                        Text(
                            "· ${format1(fat)}% fat",
                            style = arch(11.0, 600, C.Muted, line = 1.0),
                            modifier = Modifier.padding(start = 3.dp, bottom = 1.dp),
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val sign = if (lost >= 0) "−" else "+"
                Text("$sign${format1(abs(lost))} kg", style = arch(12.0, 700, C.Accent, line = 1.0))
                Text(
                    if (toGo > 0) "${format1(toGo)} kg to target" else "target reached",
                    style = arch(10.0, 500, Color(0xFF7E7E88), line = 1.0),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF26272C)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF9BE015), C.Accent))),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("START ${format1(saved.bodyStart)}", style = arch(9.5, 600, Color(0xFF63636C), line = 1.0))
                Text("TARGET ${format1(saved.bodyTarget)}", style = arch(9.5, 600, Color(0xFF63636C), line = 1.0))
            }
        }
    }
}

@Composable
private fun StatTile(modifier: Modifier, value: String, label: String) {
    Column(
        modifier
            .clip(RoundedCornerShape(13.dp))
            .background(C.Card)
            .border(1.dp, C.Border, RoundedCornerShape(13.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(value, style = display(20.0, line = 1.0))
        Text(label.uppercase(), style = arch(8.5, 700, C.Dim, track = 0.13, line = 1.3))
    }
}

@Composable
private fun DayCard(
    day: Day,
    isToday: Boolean,
    isDone: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val background: Modifier = if (isToday) {
        Modifier.background(Brush.linearGradient(listOf(Color(0xFF1F2713), Color(0xFF141519))))
    } else {
        Modifier.background(C.CardAlt)
    }
    val exercises = day.all

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .then(background)
            .border(1.dp, if (isToday) Color(0xFF4A5A24) else C.Border, RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Column(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            isToday -> C.Accent
                            isDone -> Color(0xFF22331A)
                            else -> Color(0xFF1C1D21)
                        },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "DAY",
                    style = arch(
                        7.5, 800,
                        if (isToday) C.OnAccent.copy(alpha = 0.6f) else C.Faint,
                        track = 0.1, line = 1.0,
                    ),
                )
                Text(
                    "${day.num}",
                    style = display(
                        17.0,
                        when {
                            isToday -> C.OnAccent
                            isDone -> C.Accent
                            else -> C.Muted
                        },
                        line = 1.0,
                    ),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(day.flatTitle.uppercase(), style = display(20.0, line = 1.02))
                Text(
                    "${day.strength.size} strength moves · ${day.hiit.size}-move HIIT finisher",
                    style = arch(11.5, 500, Color(0xFF8A8A94), line = 1.35),
                )
            }
            Text(
                when {
                    isDone -> "✓ DONE"
                    isToday -> "TODAY"
                    else -> day.day.uppercase()
                },
                style = arch(
                    10.0, 700,
                    if (isDone || isToday) C.Accent else C.Faint,
                    track = 0.1, line = 1.0,
                ),
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            exercises.take(5).forEach { exercise ->
                ExerciseThumb(
                    exercise.thumb,
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(C.Light),
                )
            }
            if (exercises.size > 5) {
                Text("+${exercises.size - 5}", style = arch(9.5, 700, C.Dim, track = 0.06, line = 1.0))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag("${day.mins} MIN", Color(0x0DFFFFFF), Color(0xFF9A9AA3))
            Tag("40/20 HIIT", C.Blue.copy(alpha = 0.12f), C.Blue)
            Tag(
                if (day.all.any { it.equipment.contains("dumbbell") }) "MAT + BELLS" else "BODY WEIGHT",
                Color(0x0DFFFFFF),
                Color(0xFF9A9AA3),
            )
        }
    }
}

@Composable
private fun Tag(label: String, background: Color, foreground: Color) {
    Text(
        label.uppercase(),
        style = arch(9.0, 700, foreground, track = 0.09, line = 1.0),
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(background)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

fun format1(value: Float): String = String.format(Locale.US, "%.1f", value)

fun formatTonnes(kg: Int): String = String.format(Locale.US, "%.1ft", kg / 1000f)
