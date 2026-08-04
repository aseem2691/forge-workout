package com.forge.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.forge.workout.watch.HcStatus
import com.forge.workout.watch.HrDevice
import com.forge.workout.watch.HrState

@Composable
fun WatchScreen(
    hcStatus: HcStatus,
    hrState: HrState,
    bpm: Int?,
    savedName: String?,
    devices: List<HrDevice>,
    onBack: () -> Unit,
    onLinkHealthConnect: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onPick: (HrDevice) -> Unit,
    onForget: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
            Text("WATCH", style = arch(9.5, 800, C.Dim, track = 0.2, line = 1.0))
        }

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp)) {
            Text("CONNECT\nYOUR WATCH", style = display(34.0, line = 0.95))
            Spacer(Modifier.height(8.dp))
            Text(
                "Forge reads what your Amazfit recorded and attaches it to the session you logged.",
                style = arch(12.0, 500, C.Muted, line = 1.5),
            )
        }

        // ── Health Connect ──────────────────────────────────────────────────────
        Card(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
            SectionHead("Health Connect", statusLabel(hcStatus), statusColor(hcStatus))
            Text(
                when (hcStatus) {
                    HcStatus.Ready ->
                        "Linked. After each workout Forge pulls the watch's session, heart rate and calories, " +
                            "and publishes the Forge session back to Health Connect."
                    HcStatus.NeedsPermission ->
                        "Grant Forge permission to read exercise, heart rate and calories."
                    HcStatus.UpdateRequired ->
                        "Health Connect needs updating before Forge can use it."
                    HcStatus.Unavailable ->
                        "Health Connect isn't available on this device. Install it from the Play Store, " +
                            "then link the Zepp app to it."
                },
                style = arch(11.5, 500, C.Muted, line = 1.5),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                when (hcStatus) {
                    HcStatus.NeedsPermission -> Action("Link Health Connect", true, onLinkHealthConnect)
                    HcStatus.Ready -> Action("Open Health Connect", false, onOpenHealthConnect)
                    else -> Action("Open Health Connect", false, onOpenHealthConnect)
                }
            }
            Spacer(Modifier.height(12.dp))
            Hint("In the Zepp app: Profile → 3rd-party account linking → Health Connect. Zepp only writes to Health Connect, so Forge sessions won't appear back inside Zepp.")
        }

        // ── Live heart rate ─────────────────────────────────────────────────────
        Card(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
            SectionHead("Live heart rate", hrLabel(hrState, bpm), hrColor(hrState))
            Text(
                "Connects to your watch as a standard Bluetooth heart-rate monitor and shows live BPM " +
                    "while you train.",
                style = arch(11.5, 500, C.Muted, line = 1.5),
            )

            if (savedName != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(C.Panel)
                        .border(1.dp, C.Border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(savedName, style = arch(13.0, 600, C.Text, line = 1.2))
                        Text(
                            hrLabel(hrState, bpm),
                            style = arch(10.5, 500, hrColor(hrState), line = 1.3),
                        )
                    }
                    Text(
                        "FORGET",
                        style = arch(10.0, 700, C.Muted, track = 0.1, line = 1.0),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onForget)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                if (hrState == HrState.Scanning) {
                    Action("Stop scan", false, onStopScan)
                } else {
                    Action(if (savedName == null) "Scan for monitors" else "Scan again", savedName == null, onScan)
                }
            }

            if (devices.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    devices.forEach { device ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(11.dp))
                                .background(C.Panel)
                                .border(
                                    1.dp,
                                    if (device.advertisesHeartRate) C.Accent.copy(alpha = 0.4f) else C.Border,
                                    RoundedCornerShape(11.dp),
                                )
                                .clickable { onPick(device) }
                                .padding(horizontal = 13.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(device.name, style = arch(12.5, 600, C.Text, line = 1.2))
                                Text(
                                    if (device.advertisesHeartRate) "Heart rate monitor" else device.address,
                                    style = arch(10.0, 500, if (device.advertisesHeartRate) C.Accent else C.Faint, line = 1.3),
                                )
                            }
                            Text("USE", style = arch(10.0, 700, C.Accent, track = 0.1, line = 1.0))
                        }
                    }
                }
            }

            if (hrState == HrState.NoPermission) {
                Spacer(Modifier.height(10.dp))
                Hint("Bluetooth permission was declined. Grant it in Settings → Apps → Forge → Permissions.")
            }
            if (hrState == HrState.NoBluetooth) {
                Spacer(Modifier.height(10.dp))
                Hint("Bluetooth is off — turn it on to find your watch.")
            }

            Spacer(Modifier.height(12.dp))
            Hint("On the GTR 4: Settings → Heart rate → Heart Rate Push (needs Zepp OS 3.0+). Many watches only broadcast while a workout is running on the watch, so start one there first.")
        }
    }
}

@Composable
private fun Card(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.Card)
            .border(1.dp, C.Border, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun SectionHead(title: String, status: String, statusColor: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title.uppercase(), style = display(15.0, line = 1.0, track = 0.05))
        Text(status.uppercase(), style = arch(9.5, 700, statusColor, track = 0.12, line = 1.0))
    }
}

@Composable
private fun Action(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) C.Accent else Color(0xFF17181C))
            .then(
                if (primary) Modifier else Modifier.border(1.dp, C.BorderSoft, RoundedCornerShape(12.dp)),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            label.uppercase(),
            style = arch(10.5, 700, if (primary) C.OnAccent else Color(0xFFC9C9D1), track = 0.1, line = 1.0),
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = arch(10.5, 500, C.Faint, line = 1.5))
}

private fun statusLabel(status: HcStatus) = when (status) {
    HcStatus.Ready -> "Linked"
    HcStatus.NeedsPermission -> "Not linked"
    HcStatus.UpdateRequired -> "Update needed"
    HcStatus.Unavailable -> "Unavailable"
}

private fun statusColor(status: HcStatus) = when (status) {
    HcStatus.Ready -> C.Accent
    HcStatus.Unavailable -> C.Faint
    else -> C.Blue
}

private fun hrLabel(state: HrState, bpm: Int?) = when (state) {
    HrState.Connected -> bpm?.let { "$it bpm" } ?: "Connected"
    HrState.Connecting -> "Connecting"
    HrState.Scanning -> "Scanning"
    HrState.Disconnected -> "Waiting for watch"
    HrState.NoPermission -> "Needs permission"
    HrState.NoBluetooth -> "Bluetooth off"
    HrState.Idle -> "Not connected"
}

private fun hrColor(state: HrState) = when (state) {
    HrState.Connected -> C.Accent
    HrState.Connecting, HrState.Scanning, HrState.Disconnected -> C.Blue
    else -> C.Faint
}
