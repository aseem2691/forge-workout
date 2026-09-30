package com.forge.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.forge.workout.watch.WatchSummary
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forge.workout.SessionState
import com.forge.workout.data.Day
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun DoneScreen(
    day: Day,
    nextDay: Day,
    state: SessionState,
    watch: WatchSummary?,
    sessionAvgHr: Int?,
    sessionMaxHr: Int?,
    syncing: Boolean,
    canSync: Boolean,
    onSyncWatch: () -> Unit,
    onBackToWeek: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to Color(0xFF1E2413), 0.55f to C.Bg))
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SESSION COMPLETE", style = arch(10.0, 800, C.Accent, track = 0.24, line = 1.0))
            Text("${day.flatTitle.uppercase()}\nDONE", style = display(42.0, line = 0.94))
            Text(
                if (state.mobility) {
                    "That is ${state.movesDone} ${if (state.movesDone == 1) "move" else "moves"} of mobility. " +
                        "An easy walk on the walkpad later finishes the rest day nicely."
                } else {
                    "That is ${state.setsDone} ${if (state.setsDone == 1) "set" else "sets"} banked. " +
                        "Protein within the hour, and you are one session closer to target."
                },
                style = arch(12.5, 500, C.DayHeader, line = 1.5),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            val minutes = "${max(1, (state.elapsed / 60.0).roundToInt())} min"
            if (state.mobility) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    DoneTile(Modifier.weight(1f), "${state.movesDone}", "Moves done")
                    DoneTile(Modifier.weight(1f), minutes, "Time on task")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    DoneTile(Modifier.weight(1f), "${state.setsDone}", "Sets completed")
                    DoneTile(Modifier.weight(1f), "${state.repsDone}", "Reps logged")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    DoneTile(Modifier.weight(1f), formatTonnes(state.volume), "Volume lifted")
                    DoneTile(Modifier.weight(1f), minutes, "Time on task")
                }
            }
        }

        if (canSync || watch != null || sessionAvgHr != null) {
            WatchBlock(watch, sessionAvgHr, sessionMaxHr, syncing, canSync, onSyncWatch)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(C.Card)
                .border(1.dp, C.Border, RoundedCornerShape(15.dp))
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("NEXT SESSION", style = arch(9.0, 700, C.Dim, track = 0.16, line = 1.0))
            Text(nextDay.flatTitle.uppercase(), style = display(22.0, line = 1.05))
            Text(nextDay.coach, style = arch(11.5, 500, C.Muted, line = 1.45))
        }

        Spacer(Modifier.height(4.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(C.Accent)
                .clickable(onClick = onBackToWeek)
                .padding(vertical = 17.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "BACK TO MY WEEK",
                style = display(17.0, C.OnAccent, line = 1.0, track = 0.08),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun WatchBlock(
    watch: WatchSummary?,
    sessionAvgHr: Int?,
    sessionMaxHr: Int?,
    syncing: Boolean,
    canSync: Boolean,
    onSync: () -> Unit,
) {
    val avg = sessionAvgHr ?: watch?.avgHr
    val max = sessionMaxHr ?: watch?.maxHr

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(C.Card)
            .border(1.dp, C.Border, RoundedCornerShape(15.dp))
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("FROM YOUR WATCH", style = arch(9.0, 700, C.Dim, track = 0.16, line = 1.0))
            if (canSync) {
                Text(
                    if (syncing) "SYNCING…" else "SYNC NOW",
                    style = arch(9.5, 700, if (syncing) C.Faint else C.Accent, track = 0.1, line = 1.0),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !syncing, onClick = onSync)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        if (avg == null && max == null && watch?.calories == null) {
            Text(
                if (syncing) {
                    "Looking for your watch's recording…"
                } else {
                    "Nothing from the watch yet. Open the Zepp app so it uploads this workout, then tap Sync now."
                },
                style = arch(11.5, 500, C.Muted, line = 1.5),
            )
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            if (avg != null) DoneTile(Modifier.weight(1f), "$avg", "Avg heart rate")
            if (max != null) DoneTile(Modifier.weight(1f), "$max", "Max heart rate")
        }
        watch?.calories?.let { kcal ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DoneTile(Modifier.weight(1f), "$kcal", "Calories")
                Spacer(Modifier.weight(1f))
            }
        }
        watch?.let {
            Text(
                "Matched to “${it.title}” on your watch.",
                style = arch(10.5, 500, C.Faint, line = 1.4),
            )
        }
    }
}

@Composable
private fun DoneTile(modifier: Modifier, value: String, label: String) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(C.Card)
            .border(1.dp, C.Border, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(value, style = display(26.0, line = 1.0))
        Text(label.uppercase(), style = arch(8.5, 700, C.Dim, track = 0.13, line = 1.0))
    }
}
