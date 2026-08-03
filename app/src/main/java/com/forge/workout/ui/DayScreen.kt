package com.forge.workout.ui

import androidx.compose.foundation.background
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
import com.forge.workout.data.titleCase

@Composable
fun DayScreen(
    day: Day,
    saved: Persisted,
    onBack: () -> Unit,
    onStartDay: () -> Unit,
    onStartExercise: (Int) -> Unit,
) {
    val exercises = day.all

    Column(
        Modifier
            .fillMaxSize()
            .background(C.Bg),
    ) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF1D2413), Color(0xFF101114)),
                                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                end = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY),
                            ),
                        )
                        .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0x14FFFFFF))
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("←", style = arch(15.0, 600, C.Text, line = 1.0))
                        }
                        Text(
                            "DAY ${day.num} OF 4",
                            style = arch(9.5, 800, C.DayEyebrow, track = 0.2, line = 1.0),
                        )
                        Spacer(Modifier.width(34.dp))
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(day.flatTitle.uppercase(), style = display(38.0, line = 0.95))
                        Text(day.coach, style = arch(12.5, 500, C.DayHeader, line = 1.5))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetaTile(Modifier.weight(1f), "${day.mins}", "Minutes")
                        MetaTile(Modifier.weight(1f), "${exercises.size}", "Exercises")
                        MetaTile(Modifier.weight(1f), "${day.totalSets}", "Total sets")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        day.muscles.forEach { muscle ->
                            Text(
                                muscle.uppercase(),
                                style = arch(9.0, 700, C.Accent, track = 0.08, line = 1.0),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(C.Accent.copy(alpha = 0.12f))
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }

            itemsIndexed(exercises) { index, exercise ->
                Column(Modifier.padding(horizontal = 20.dp)) {
                    if (index == 0 || index == day.strength.size) {
                        val strength = index == 0
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            Text(
                                if (strength) "STRENGTH BLOCK" else "HIIT FINISHER — 40 / 20",
                                style = display(13.0, if (strength) C.Text else C.Blue, line = 1.0, track = 0.1),
                            )
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(1.dp)
                                    .background(C.Border),
                            )
                            Text(
                                if (strength) "${day.strength.size} moves"
                                else "${day.hiit.firstOrNull()?.sets ?: 2} rounds",
                                style = arch(10.0, 600, Color(0xFF6A6A73), line = 1.0),
                            )
                        }
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onStartExercise(index) }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ExerciseThumb(
                            exercise.thumb,
                            Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(C.Light),
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(exercise.name.titleCase(), style = arch(13.5, 600, C.Text, line = 1.25))
                            Text(exercise.spec, style = arch(10.5, 500, Color(0xFF7C7C86), line = 1.0))
                            Text(
                                "${exercise.target} · ${exercise.equipment}".uppercase(),
                                style = arch(9.0, 700, Color(0xFF5F6068), track = 0.08, line = 1.0),
                            )
                        }
                        Text(
                            if (exercise.hasLoad) "${saved.weightFor(exercise)} KG" else "BODY",
                            style = arch(10.5, 700, C.Accent, track = 0.06, line = 1.0),
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(C.Line),
                    )
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(C.Bg)
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 18.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(C.Accent)
                    .clickable(onClick = onStartDay)
                    .padding(vertical = 17.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("START WORKOUT", style = display(17.0, C.OnAccent, line = 1.0, track = 0.08))
                    Text("▶", style = arch(13.0, 700, C.OnAccent, line = 1.0))
                }
            }
        }
    }
}

@Composable
private fun MetaTile(modifier: Modifier, value: String, label: String) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x0FFFFFFF))
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = display(18.0, line = 1.0))
        Text(label.uppercase(), style = arch(8.5, 700, Color(0xFF9A9AA2), track = 0.12, line = 1.0))
    }
}
