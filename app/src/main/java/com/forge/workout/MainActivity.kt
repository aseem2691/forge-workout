package com.forge.workout

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import com.forge.workout.ui.WatchScreen
import com.forge.workout.watch.HcStatus
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.forge.workout.ui.ProgressScreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forge.workout.ui.C
import com.forge.workout.ui.DayScreen
import com.forge.workout.ui.DoneScreen
import com.forge.workout.ui.ForgeTheme
import com.forge.workout.ui.PlanScreen
import com.forge.workout.ui.PlayerScreen
import com.forge.workout.ui.arch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ForgeTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(C.Bg)
                        .systemBarsPadding(),
                ) {
                    ForgeApp()
                }
            }
        }
    }
}

@Composable
private fun ForgeApp(vm: WorkoutViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val saved by vm.saved.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val bpm by vm.bpm.collectAsState()
    val hrState by vm.hrState.collectAsState()
    val hrDevices by vm.hrDevices.collectAsState()
    val hcStatus by vm.hcStatus.collectAsState()
    val watchSummary by vm.watchSummary.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val range by vm.range.collectAsState()
    val progress by vm.progress.collectAsState()
    var editingBody by remember { mutableStateOf(false) }
    var showWatch by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(Tab.Train) }
    val context = LocalContext.current

    val healthPermissions = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { vm.refreshHealthConnect() }

    val blePermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted -> if (granted.values.all { it }) vm.scanForHeartRate() }

    if (!loaded) return

    if (showWatch) {
        WatchScreen(
            hcStatus = hcStatus,
            hrState = hrState,
            bpm = bpm,
            savedName = saved.hrName,
            devices = hrDevices,
            onBack = { vm.stopHeartRateScan(); showWatch = false },
            onLinkHealthConnect = { healthPermissions.launch(vm.health.permissions) },
            onOpenHealthConnect = { openHealthConnect(context) },
            onScan = {
                if (vm.heart.hasPermissions()) vm.scanForHeartRate()
                else blePermissions.launch(vm.heart.requiredPermissions())
            },
            onStopScan = vm::stopHeartRateScan,
            onPick = { vm.useHeartRateDevice(it) },
            onForget = vm::forgetHeartRateDevice,
        )
        BackHandler { vm.stopHeartRateScan(); showWatch = false }
        return
    }

    KeepScreenOn(state.screen == Screen.Player)

    BackHandler(enabled = state.screen != Screen.Plan) {
        when (state.screen) {
            Screen.Day -> vm.goPlan()
            Screen.Player -> if (state.showHow) vm.toggleHow() else vm.goDay()
            Screen.Done -> vm.goPlan()
            Screen.Plan -> Unit
        }
    }

    val day = vm.plan.getOrNull(state.dayIdx)

    when (state.screen) {
        Screen.Plan -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.Train -> PlanScreen(
                        week = vm.week(state.weekIdx),
                        saved = saved,
                        todayIndex = vm.todayIndex(),
                        watchLinked = hcStatus == HcStatus.Ready || saved.hrAddress != null,
                        onOpenDay = vm::openDay,
                        onEditBody = { editingBody = true },
                        onOpenWatch = { vm.refreshHealthConnect(); showWatch = true },
                    )

                    Tab.Progress -> ProgressScreen(
                        range = range,
                        progress = progress,
                        currentKg = saved.bodyNow,
                        targetKg = saved.bodyTarget,
                        fromScale = saved.bodyFromScale,
                        onRange = vm::setRange,
                    )
                }
            }
            TabBar(tab) { tab = it }
        }

        Screen.Day -> day?.let {
            DayScreen(
                day = it,
                saved = saved,
                onBack = vm::goPlan,
                onStartDay = vm::startDay,
                onStartExercise = vm::startEx,
            )
        }

        Screen.Player -> day?.let {
            PlayerScreen(
                day = it,
                state = state,
                saved = saved,
                bpm = bpm,
                onClose = vm::goDay,
                onToggleHow = vm::toggleHow,
                onToggleRun = vm::toggleRun,
                onTap = vm::tap,
                onMode = vm::setMode,
                onWeight = vm::adjustWeight,
                onSkip = vm::skip,
                onFinishSet = vm::finishSet,
                onAddRest = vm::addRest,
                onEndRest = vm::endRest,
            )
        }

        Screen.Done -> day?.let {
            val last = saved.history.lastOrNull()
            DoneScreen(
                day = it,
                nextDay = vm.plan[(state.dayIdx + 1) % vm.plan.size],
                state = state,
                watch = watchSummary,
                sessionAvgHr = last?.avgHr,
                sessionMaxHr = last?.maxHr,
                syncing = syncing,
                canSync = hcStatus == HcStatus.Ready,
                onSyncWatch = vm::refreshWatchSync,
                onBackToWeek = vm::goPlan,
            )
        }
    }

    if (editingBody) {
        BodyWeightDialog(
            now = saved.bodyNow,
            start = saved.bodyStart,
            target = saved.bodyTarget,
            initials = saved.initials,
            onDismiss = { editingBody = false },
            onSave = { now, start, target, initials ->
                vm.setBodyWeight(now)
                vm.setBodyTargets(start, target)
                vm.setInitials(initials)
                editingBody = false
            },
        )
    }
}

enum class Tab(val label: String, val glyph: String) {
    Train("Train", "▦"),
    Progress("Progress", "◪"),
}

@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(C.Bg)
            .padding(top = 1.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(C.Line),
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(C.Bg)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Tab.entries.forEach { entry ->
            val active = entry == selected
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) C.Card else Color.Transparent)
                    .clickable { onSelect(entry) }
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    entry.glyph,
                    style = arch(12.0, 700, if (active) C.Accent else C.Faint, line = 1.0),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    entry.label.uppercase(),
                    style = arch(10.5, 700, if (active) C.Text else C.Faint, track = 0.1, line = 1.0),
                )
            }
        }
    }
}

/** Health Connect lives in system settings on Android 14+, and as its own app before that. */
private fun openHealthConnect(context: Context) {
    val intents = listOf(
        // Platform settings screen on Android 14+, the standalone app before that.
        Intent("android.settings.HEALTH_CONNECT_SETTINGS"),
        Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"),
        Intent(Intent.ACTION_VIEW).setData(
            android.net.Uri.parse("market://details?id=com.google.android.apps.healthdata"),
        ),
    )
    for (intent in intents) {
        if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }
                .getOrDefault(false)
        ) {
            return
        }
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = (view.context as? Activity)?.window
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

@Composable
private fun BodyWeightDialog(
    now: Float,
    start: Float,
    target: Float,
    initials: String,
    onDismiss: () -> Unit,
    onSave: (Float, Float, Float, String) -> Unit,
) {
    var nowText by remember { mutableStateOf(now.toString()) }
    var startText by remember { mutableStateOf(start.toString()) }
    var targetText by remember { mutableStateOf(target.toString()) }
    var initialsText by remember { mutableStateOf(initials) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Card,
        title = { Text("Body weight", style = arch(16.0, 700, C.Text)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                WeightField("Today (kg)", nowText) { nowText = it }
                WeightField("Start (kg)", startText) { startText = it }
                WeightField("Target (kg)", targetText) { targetText = it }
                OutlinedTextField(
                    value = initialsText,
                    onValueChange = { initialsText = it.take(3) },
                    label = { Text("Initials", style = arch(11.0, 500, C.Muted)) },
                    singleLine = true,
                    textStyle = TextStyle(color = C.Text),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    nowText.toFloatOrNull() ?: now,
                    startText.toFloatOrNull() ?: start,
                    targetText.toFloatOrNull() ?: target,
                    initialsText,
                )
            }) { Text("Save", style = arch(13.0, 700, C.Accent)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = arch(13.0, 600, C.Muted)) }
        },
    )
}

@Composable
private fun WeightField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        // Keep the field to a single decimal number so a typo cannot be silently discarded on save.
        onValueChange = { input ->
            val cleaned = input.filter { it.isDigit() || it == '.' }
            if (cleaned.count { it == '.' } <= 1 && cleaned.length <= 5) onChange(cleaned)
        },
        label = { Text(label, style = arch(11.0, 500, C.Muted)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = TextStyle(color = C.Text),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}
