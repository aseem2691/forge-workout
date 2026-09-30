package com.forge.workout.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forge.workout.SessionState
import com.forge.workout.data.Day
import com.forge.workout.data.Exercise
import com.forge.workout.data.Persisted
import com.forge.workout.data.shouldRecord
import com.forge.workout.data.titleCase

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    day: Day,
    state: SessionState,
    saved: Persisted,
    bpm: Int?,
    onClose: () -> Unit,
    onKeepGoing: () -> Unit,
    onSaveAndFinish: () -> Unit,
    onDiscard: () -> Unit,
    onToggleHow: () -> Unit,
    onToggleRun: () -> Unit,
    onTap: () -> Unit,
    onMode: (String) -> Unit,
    onWeight: (Int) -> Unit,
    onSkip: () -> Unit,
    onFinishSet: () -> Unit,
    onAddRest: () -> Unit,
    onEndRest: () -> Unit,
) {
    val exercises = day.all
    val exercise = exercises.getOrNull(state.exIdx) ?: return
    val weight = saved.weightFor(exercise)
    val isTimed = exercise.isTimed
    val isTap = !isTimed && state.mode == "tap"
    val isTempo = !isTimed && state.mode == "tempo"
    val blockTint = blockColor(exercise)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(C.Bg),
    ) {
        // The design assumes a 412×892 frame. On shorter screens the demo panel and the control
        // scale down instead of squeezing the middle column to nothing.
        val gifHeight = (maxHeight * 0.26f).coerceIn(120.dp, 228.dp)
        val controlSize = (maxHeight * 0.17f).coerceIn(108.dp, 148.dp)

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleButton("✕", onClose)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        when {
                            exercise.isWarmup -> "WARM-UP"
                            exercise.isCooldown -> "COOL-DOWN"
                            exercise.isMobility -> "MOBILITY"
                            exercise.isHiit -> "HIIT · 40 / 20"
                            else -> "STRENGTH BLOCK"
                        },
                        style = arch(9.0, 800, blockTint, track = 0.2, line = 1.0),
                    )
                    Text(
                        if (exercise.isRecovery) {
                            val inBlock = exercises.filter { it.block == exercise.block }
                            val position = exercises.take(state.exIdx).count { it.block == exercise.block } + 1
                            "Move $position / ${inBlock.size}"
                        } else {
                            "Exercise ${state.exIdx - day.warmup.size + 1} / ${day.main.size}"
                        },
                        style = arch(10.5, 600, C.Ghost, line = 1.0),
                    )
                }
                CircleButton("?", onToggleHow)
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 0.dp)
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                exercises.forEachIndexed { index, item ->
                    val done = if (item.isRecovery) blockColor(item) else C.Accent
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                when {
                                    index < state.exIdx -> done
                                    index == state.exIdx -> done.copy(alpha = 0.45f)
                                    else -> Color(0xFF22232A)
                                },
                            ),
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Box(
                    Modifier
                        .padding(horizontal = 18.dp)
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .height(gifHeight)
                        .clip(RoundedCornerShape(20.dp))
                        // Pure white, like the demos' own background, so the square animation
                        // doesn't sit in visible bands. The target muscle is named in the chips
                        // below and lit on the muscle map, so no label covers the figure.
                        .background(Color.White)
                        .border(1.dp, C.Border, RoundedCornerShape(20.dp)),
                ) {
                    ExerciseGif(exercise.gif, Modifier.fillMaxSize())
                    Text(
                        exercise.equipment.uppercase(),
                        style = arch(9.0, 700, Color(0xFFC9C9D1), track = 0.1, line = 1.0),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 12.dp, top = 11.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xDB0D1005))
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                    Text(
                        "© GYM VISUAL",
                        style = arch(7.5, 600, Color(0xFF8D8D95), track = 0.06, line = 1.0),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 11.dp, bottom = 8.dp),
                    )
                    if (bpm != null) {
                        Row(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 12.dp, bottom = 10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xDB0D1005))
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("♥", style = arch(11.0, 700, C.Accent, line = 1.0))
                            Text("$bpm", style = display(15.0, C.Text, line = 1.0))
                            Text("BPM", style = arch(7.5, 700, C.Ghost, track = 0.12, line = 1.0))
                        }
                    }
                }

                Row(
                    Modifier.padding(start = 20.dp, end = 14.dp, top = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(exercise.name.titleCase().uppercase(), style = display(25.0, line = 1.0))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            exercise.muscles.forEach { muscle ->
                                Text(
                                    muscle.titleCase(),
                                    style = arch(9.5, 600, Color(0xFF9A9AA3), line = 1.0),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF17181C))
                                        .border(1.dp, C.BorderSoft, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    if (exercise.bodyPrimary.isNotEmpty()) {
                        BodyMap(
                            exercise.bodyPrimary,
                            exercise.bodySecondary,
                            Modifier
                                .padding(start = 10.dp)
                                .size(width = 70.dp, height = 76.dp),
                        )
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("SET", style = arch(8.5, 700, C.Ghost, track = 0.14, line = 1.0))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val active = state.setIdx.coerceAtLeast(0)
                            repeat(exercise.sets) { index ->
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (index < active) C.Accent else Color.Transparent)
                                        .border(
                                            1.5.dp,
                                            if (index == active) C.Accent else Color(0xFF3A3B42),
                                            RoundedCornerShape(50),
                                        ),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (exercise.hasLoad) {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(C.Panel)
                                .border(1.dp, C.Border, RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            StepperButton("−") { onWeight(-2) }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.widthIn(min = 44.dp),
                            ) {
                                Text("$weight", style = display(17.0, line = 1.0))
                                Text("KG / HAND", style = arch(7.5, 700, C.Ghost, track = 0.12, line = 1.0))
                            }
                            StepperButton("+") { onWeight(2) }
                        }
                    }
                }

                state.progression?.let { note ->
                    Text(
                        "↑ $note",
                        style = arch(10.5, 700, C.OnAccent, line = 1.35),
                        modifier = Modifier
                            .padding(start = 20.dp, end = 20.dp, top = 11.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .background(C.Accent)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                }
                if (state.progression == null) ProgressNote(exercise, saved, weight)

                Spacer(Modifier.height(16.dp))
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                when {
                    isTimed -> {
                        val total = exercise.time.coerceAtLeast(1)
                        RingControl(
                            progress = state.remaining.toFloat() / total,
                            color = C.Accent,
                            size = controlSize,
                            onClick = onToggleRun,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(clock(state.remaining), style = display(44.0, line = 1.0))
                                Text(
                                    if (state.running) "TAP TO PAUSE" else "TAP TO START",
                                    style = arch(8.5, 700, C.Ghost, track = 0.16, line = 1.0),
                                )
                            }
                        }
                    }

                    isTap -> TapControl(state.count, exercise.reps, controlSize, onTap)

                    isTempo -> {
                        val phase = state.tempoSec % 3
                        val countingIn = state.running && state.leadIn > 0
                        RingControl(
                            progress = if (countingIn) state.leadIn / 5f else phase / 3f,
                            color = C.Blue,
                            size = controlSize,
                            onClick = onToggleRun,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                            ) {
                                Text(
                                    if (countingIn) "${state.leadIn}" else "${state.count}",
                                    style = display(46.0, if (countingIn) C.Blue else C.Text, line = 1.0),
                                )
                                Text(
                                    when {
                                        countingIn -> "GET READY"
                                        !state.running -> "OF ${exercise.reps} · PAUSED"
                                        phase == 2 -> "OF ${exercise.reps} · UP"
                                        else -> "OF ${exercise.reps} · DOWN"
                                    },
                                    style = arch(8.5, 700, C.Ghost, track = 0.16, line = 1.0),
                                )
                            }
                        }
                    }
                }

                if (!isTimed) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(C.Panel)
                            .border(1.dp, C.Border, RoundedCornerShape(11.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ModeChip("Tap count", state.mode == "tap") { onMode("tap") }
                        ModeChip("Auto tempo", state.mode == "tempo") { onMode("tempo") }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF17181C))
                            .border(1.dp, C.BorderSoft, RoundedCornerShape(14.dp))
                            .clickable(onClick = onSkip)
                            .padding(horizontal = 18.dp, vertical = 15.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("SKIP", style = arch(11.0, 700, Color(0xFF9A9AA3), track = 0.1, line = 1.0))
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(C.Accent)
                            .clickable(onClick = onFinishSet)
                            .padding(vertical = 15.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (isTimed) "DONE — NEXT" else "LOG SET ${state.setIdx + 1}",
                            style = display(16.0, C.OnAccent, line = 1.0, track = 0.07),
                        )
                    }
                }
            }
        }

        if (state.showHow) {
            HowToSheet(exercise, onToggleHow)
        }

        if (state.resting) {
            val nextIndex = if (state.setIdx == -1) (state.exIdx + 1).coerceAtMost(exercises.size - 1) else state.exIdx
            // During rest exIdx still points at the move just finished.
            RestOverlay(
                label = when {
                    exercise.isRecovery && exercises[nextIndex].block == exercise.block -> "NEXT MOVE"
                    exercise.isWarmup -> "WARM-UP DONE"
                    exercises[nextIndex].isCooldown -> "COOL-DOWN NEXT"
                    else -> "REST"
                },
                tint = when {
                    exercise.isWarmup -> C.Warm
                    exercise.isRecovery || exercises[nextIndex].isCooldown -> C.Calm
                    else -> C.Blue
                },
                left = state.restLeft,
                total = state.restTotal,
                next = exercises[nextIndex],
                nextWeight = saved.weightFor(exercises[nextIndex]),
                onAddRest = onAddRest,
                onEndRest = onEndRest,
                // The overlay swallows taps, so ✕ underneath is out of reach during a rest.
                onEndWorkout = onClose,
            )
        }

        if (state.showEnd) {
            EndSheet(
                logged = shouldRecord(state.mobility, state.setsDone, state.movesDone),
                onSave = onSaveAndFinish,
                onDiscard = onDiscard,
                onKeepGoing = onKeepGoing,
            )
        }
    }
}

@Composable
private fun ProgressNote(exercise: Exercise, saved: Persisted, weight: Int) {
    val logged = saved.lastPerf[exercise.id]
    val tint = if (exercise.isRecovery) blockColor(exercise) else C.Accent
    val note = when {
        exercise.isRecovery -> exercise.cue ?: "Easy pace — breathe and move well."
        logged != null && exercise.hasLoad -> "Last time: $logged — today's working weight is $weight kg."
        logged != null -> "Last time: $logged — match it or beat it."
        exercise.last != null -> "Last time: ${exercise.last} — every rep cleared. Coach says go $weight kg today."
        exercise.isHiit -> "Work hard for 40, breathe for 20. ${exercise.sets} rounds."
        else -> null
    } ?: return

    Text(
        note,
        style = arch(10.5, 600, if (exercise.isRecovery) tint else C.AccentText, line = 1.35),
        modifier = Modifier
            .padding(start = 20.dp, end = 20.dp, top = 11.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(tint.copy(alpha = 0.08f))
            .border(1.dp, tint.copy(alpha = 0.18f), RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}

private fun blockColor(exercise: Exercise): Color = when {
    exercise.isWarmup -> C.Warm
    exercise.isCooldown || exercise.isMobility -> C.Calm
    exercise.isHiit -> C.Blue
    else -> C.Accent
}

/** ✕ / Back: save what's done, throw it away, or carry on. Swallows taps like the rest overlay. */
@Composable
private fun EndSheet(logged: Boolean, onSave: () -> Unit, onDiscard: () -> Unit, onKeepGoing: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xE608080A))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        verticalArrangement = Arrangement.Bottom,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(C.Card)
                .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("END WORKOUT?", style = display(24.0, line = 1.0))
            Text(
                if (logged) "Save what you've done so far, or discard it." else "Nothing is logged yet.",
                style = arch(12.0, 500, C.Muted, line = 1.45),
            )
            if (logged) SheetButton("SAVE & FINISH", C.Accent, C.OnAccent, onSave)
            SheetButton(if (logged) "DISCARD WORKOUT" else "LEAVE", Color(0xFF17181C), Color(0xFFFF8A8A), onDiscard)
            SheetButton("KEEP GOING", Color(0xFF17181C), C.Text, onKeepGoing)
        }
    }
}

@Composable
private fun SheetButton(label: String, background: Color, foreground: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = display(16.0, foreground, line = 1.0, track = 0.07))
    }
}

@Composable
private fun CircleButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF191A1E))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = arch(13.0, 600, Color(0xFFB7B7BF), line = 1.0))
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(C.Chip)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = arch(15.0, 600, Color(0xFFC9C9D1), line = 1.0))
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label.uppercase(),
        style = arch(9.5, 700, if (selected) C.OnAccent else C.Muted, track = 0.1, line = 1.0),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) C.Accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    )
}

@Composable
private fun RingControl(
    progress: Float,
    color: Color,
    size: Dp = 148.dp,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = C.Track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

@Composable
private fun TapControl(count: Int, target: Int, size: Dp, onTap: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulseAlpha",
    )

    Box(
        Modifier.size(size + 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size + 16.dp)
                .clip(RoundedCornerShape(50))
                .border(1.5.dp, C.Accent.copy(alpha = alpha * 0.56f), RoundedCornerShape(50)),
        )
        Column(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(50))
                .background(C.Panel)
                .border(2.dp, Color(0xFF2A2B31), RoundedCornerShape(50))
                .clickable(onClick = onTap),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("$count", style = display(size.value * 0.35, C.Accent, line = 1.0))
            Text("OF $target REPS", style = arch(8.5, 700, C.Ghost, track = 0.16, line = 1.0))
            if (size >= 130.dp) {
                Spacer(Modifier.height(5.dp))
                Text("TAP TO COUNT", style = arch(9.0, 600, Color(0xFF4F5058), line = 1.0))
            }
        }
    }
}

@Composable
private fun RestOverlay(
    label: String,
    tint: Color,
    left: Int,
    total: Int,
    next: Exercise,
    nextWeight: Int,
    onAddRest: () -> Unit,
    onEndRest: () -> Unit,
    onEndWorkout: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xF208080A))
            // Swallow every tap: without this they reached the player underneath, so a tap
            // where LOG SET sits logged an extra set during rest.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = arch(10.0, 800, tint, track = 0.24, line = 1.0))
        Spacer(Modifier.height(18.dp))
        Text("$left", style = display(92.0, line = 1.0))
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .widthIn(max = 280.dp)
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF1E1F24)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(if (total > 0) left.toFloat() / total else 0f)
                    .fillMaxHeight()
                    .background(tint),
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(C.Card)
                .border(1.dp, C.BorderSoft, RoundedCornerShape(15.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExerciseThumb(
                next.thumb,
                Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(C.Light),
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("UP NEXT", style = arch(8.5, 700, C.Ghost, track = 0.16, line = 1.0))
                Text(next.name.titleCase(), style = arch(13.0, 600, C.Text, line = 1.2))
                Text(
                    next.spec + if (next.hasLoad) " · $nextWeight kg" else "",
                    style = arch(10.5, 500, C.Muted, line = 1.0),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color(0xFF17181C))
                    .border(1.dp, C.BorderSoft, RoundedCornerShape(13.dp))
                    .clickable(onClick = onAddRest)
                    .padding(horizontal = 18.dp, vertical = 13.dp),
            ) {
                Text("+15S", style = arch(10.5, 700, Color(0xFFC9C9D1), track = 0.1, line = 1.0))
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .background(C.Light)
                    .clickable(onClick = onEndRest)
                    .padding(horizontal = 24.dp, vertical = 13.dp),
            ) {
                Text("SKIP REST", style = arch(10.5, 700, C.OnAccent, track = 0.1, line = 1.0))
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            "END WORKOUT",
            style = arch(10.0, 700, C.Faint, track = 0.14, line = 1.0),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onEndWorkout)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun HowToSheet(exercise: Exercise, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xE608080A)),
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(onClick = onClose),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(C.Card)
                .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text("HOW TO DO IT", style = arch(9.0, 700, C.Dim, track = 0.18, line = 1.0))
                    Text(exercise.name.titleCase().uppercase(), style = display(22.0, line = 1.05))
                }
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(50))
                        .background(C.Chip)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", style = arch(14.0, 500, Color(0xFFC9C9D1), line = 1.0))
                }
            }

            if (exercise.bodyPrimary.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    BodyMap(
                        exercise.bodyPrimary,
                        exercise.bodySecondary,
                        Modifier.size(width = 118.dp, height = 124.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MapKey(
                            C.Accent,
                            "Worked",
                            if (exercise.target == "cardiovascular system") {
                                // Conditioning moves: the map lights the muscles doing the work.
                                exercise.secondary.take(3).joinToString(", ") { it.titleCase() }
                                    .ifBlank { exercise.target.titleCase() }
                            } else {
                                exercise.target.titleCase()
                            },
                        )
                        if (exercise.bodySecondary.isNotEmpty()) {
                            MapKey(
                                C.Accent.copy(alpha = 0.4f),
                                "Assisting",
                                exercise.secondary.take(3).joinToString(", ") { it.titleCase() },
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                exercise.steps.forEachIndexed { index, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(C.Accent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${index + 1}", style = arch(10.0, 700, C.Accent, line = 1.0))
                        }
                        Text(step, style = arch(12.0, 500, Color(0xFFB9B9C1), line = 1.5))
                    }
                }
            }

            Text(
                exercise.credit.ifBlank { "Instructions from exercises-dataset (MIT) · demo © Gym visual" } +
                    " · Body map: react-body-highlighter (MIT)",
                style = arch(9.5, 500, Color(0xFF4A4A52), line = 1.4),
            )
        }
    }
}

@Composable
private fun MapKey(color: Color, label: String, muscles: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(9.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label.uppercase(), style = arch(8.5, 700, C.Dim, track = 0.14, line = 1.0))
            Text(muscles, style = arch(11.5, 600, Color(0xFFB9B9C1), line = 1.3))
        }
    }
}

private fun clock(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
