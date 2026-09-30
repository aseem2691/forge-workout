package com.forge.workout

import android.app.Application
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forge.workout.data.Day
import com.forge.workout.data.Exercise
import com.forge.workout.data.ExerciseResult
import com.forge.workout.data.Persisted
import com.forge.workout.data.PlanLoader
import com.forge.workout.data.Program
import com.forge.workout.data.Progress
import com.forge.workout.data.Range
import com.forge.workout.data.SessionRecord
import com.forge.workout.data.Store
import com.forge.workout.data.Week
import com.forge.workout.data.WeightEntry
import com.forge.workout.data.buildProgress
import com.forge.workout.watch.Alerts
import com.forge.workout.watch.BodyFatReading
import com.forge.workout.watch.HcStatus
import com.forge.workout.watch.HealthConnectRepo
import com.forge.workout.watch.HeartRateMonitor
import com.forge.workout.watch.HrDevice
import com.forge.workout.watch.HrState
import com.forge.workout.watch.WatchSummary
import com.forge.workout.watch.Walking
import com.forge.workout.watch.WeighIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.ceil

enum class Screen { Plan, Day, Player, Done }

data class SessionState(
    val screen: Screen = Screen.Plan,
    /** Which rotating week the active session belongs to, pinned when the day is opened. */
    val weekIdx: Int = 0,
    val dayIdx: Int = 0,
    val exIdx: Int = 0,
    val setIdx: Int = 0,
    val mode: String = "tap",
    val count: Int = 0,
    val running: Boolean = false,
    val remaining: Int = 0,
    val resting: Boolean = false,
    val restLeft: Int = 0,
    val restTotal: Int = 0,
    val showHow: Boolean = false,
    val tempoSec: Int = 0,
    /** Set when the working weight was stepped up on entering this exercise. */
    val progression: String? = null,
    val setsDone: Int = 0,
    val repsDone: Int = 0,
    val volume: Int = 0,
    val elapsed: Int = 0,
)

class WorkoutViewModel(app: Application) : AndroidViewModel(app) {

    val program: Program = PlanLoader.load(app)
    private val store = Store(app)

    /** Index of the rotating week the calendar says we are in. */
    fun currentWeekIdx(): Int =
        if (program.weeks.isEmpty()) 0 else (_saved.value.weekNumber() - 1).mod(program.weeks.size)

    fun week(index: Int): Week = program.weeks[index.coerceIn(0, program.weeks.lastIndex)]

    /** Days of the week currently being viewed or trained. */
    val plan: List<Day> get() = week(_state.value.weekIdx).days

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _saved = MutableStateFlow(Persisted())
    val saved: StateFlow<Persisted> = _saved.asStateFlow()

    /** False until DataStore's first emission, so the UI never flashes default values on launch. */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    // Timers are deadline-based (elapsedRealtime), never accumulated per tick, so a throttled
    // or backgrounded process cannot silently lose seconds mid-workout.
    private var workEndAt = 0L
    private var workLeftMs = 0L
    private var restEndAt = 0L
    private var tempoAccumMs = 0L
    private var tempoResumeAt = 0L
    private var sessionStartAt = 0L
    private var frozenAt = 0L

    private var sessionActive = false
    private val pendingPerf = mutableMapOf<String, String>()

    /** Per-exercise tally for the session in progress. */
    private class Acc(val name: String, val targetReps: Int) {
        var sets = 0
        var reps = 0
        var weight = 0
        var cleared = true
    }

    private val pendingResults = mutableMapOf<String, Acc>()

    // ── watch ───────────────────────────────────────────────────────────────────

    val heart = HeartRateMonitor(app)
    val health = HealthConnectRepo(app)
    private val alerts = Alerts(app)

    /** Set from the activity lifecycle: alerts only notify when the user can't see the screen. */
    var onScreen: Boolean = true

    val bpm: StateFlow<Int?> = heart.bpm
    val hrState: StateFlow<HrState> = heart.state
    val hrDevices: StateFlow<List<HrDevice>> = heart.found

    private val _hcStatus = MutableStateFlow(HcStatus.Unavailable)
    val hcStatus: StateFlow<HcStatus> = _hcStatus.asStateFlow()

    private val _watchSummary = MutableStateFlow<WatchSummary?>(null)
    val watchSummary: StateFlow<WatchSummary?> = _watchSummary.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private var sessionStartWallMs = 0L
    private var hrSum = 0L
    private var hrCount = 0
    private var hrMax = 0

    fun refreshHealthConnect() {
        viewModelScope.launch {
            // Health Connect is optional: a provider that is missing, mid-update or throwing must
            // never stop the app from opening.
            runCatching {
                _hcStatus.value = health.status()
                if (_hcStatus.value == HcStatus.Ready) {
                    syncWeight()
                    syncWalking()
                }
            }
        }
    }

    // ── body weight & progress ──────────────────────────────────────────────────

    private val _range = MutableStateFlow(Range.Month)
    val range: StateFlow<Range> = _range.asStateFlow()

    private val _weighIns = MutableStateFlow<List<WeighIn>>(emptyList())
    private val _bodyFat = MutableStateFlow<List<BodyFatReading>>(emptyList())
    private val _walking = MutableStateFlow<Walking?>(null)
    val walking: StateFlow<Walking?> = _walking.asStateFlow()

    /** Scale readings from Health Connect, falling back to manual entries. */
    val progress: StateFlow<Progress> =
        combine(_saved, _weighIns, _bodyFat, _range) { saved, scale, fat, range ->
            val manual = saved.weightLog.map { WeighIn(it.atMs, it.kg) }
            val series = (if (scale.isNotEmpty()) scale else manual).sortedBy { it.atMs }
            buildProgress(saved.history, series, fat, range)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            buildProgress(emptyList(), emptyList(), emptyList(), Range.Month),
        )

    fun setRange(value: Range) {
        _range.value = value
        syncWalking()
    }

    /** Steps/distance for the selected range — the walkpad tile. */
    fun syncWalking() {
        viewModelScope.launch {
            if (_hcStatus.value != HcStatus.Ready) return@launch
            runCatching {
                _walking.value = health.walking(
                    Instant.now().minusSeconds(_range.value.days * 86_400),
                    Instant.now(),
                )
            }
        }
    }

    /**
     * Pulls weigh-ins from Health Connect. A newer scale reading supersedes whatever is
     * on the card; a manual entry made more recently is left alone.
     */
    fun syncWeight() {
        viewModelScope.launch {
            runCatching {
            val from = Instant.now().minusSeconds(400L * 86_400)
            val now = Instant.now()

            health.bodyFat(from, now).takeIf { it.isNotEmpty() }?.let { fat ->
                _bodyFat.value = fat
                store.update { it.copy(bodyFatPct = fat.last().percent) }
            }

            val readings = health.weights(from, now)
            if (readings.isNotEmpty()) {
                _weighIns.value = readings
                val latest = readings.last()
                if (latest.atMs > _saved.value.bodyNowMs) {
                    store.update {
                        it.copy(bodyNow = latest.kg, bodyNowMs = latest.atMs, bodyFromScale = true)
                    }
                }
            }
            }
        }
    }

    // ── heart-rate monitor ──────────────────────────────────────────────────────

    fun scanForHeartRate() = heart.startScan()

    fun stopHeartRateScan() = heart.stopScan()

    fun useHeartRateDevice(device: HrDevice) {
        viewModelScope.launch {
            store.update { it.copy(hrAddress = device.address, hrName = device.name) }
        }
        heart.connect(device.address)
    }

    fun forgetHeartRateDevice() {
        heart.disconnect()
        viewModelScope.launch { store.update { it.copy(hrAddress = null, hrName = null) } }
    }

    /** Reconnects to the remembered broadcaster, e.g. when a session starts. */
    fun connectSavedHeartRate() {
        val address = _saved.value.hrAddress ?: return
        if (heart.state.value == HrState.Connected || heart.state.value == HrState.Connecting) return
        heart.connect(address)
    }

    // ── plan helpers ────────────────────────────────────────────────────────────

    fun list(dayIdx: Int): List<Exercise> = plan.getOrNull(dayIdx)?.all.orEmpty()
    private fun current(): Exercise? = list(_state.value.dayIdx).getOrNull(_state.value.exIdx)
    private fun restFor(e: Exercise): Int = e.rest

    /** Today's session: an exact weekday match, otherwise the next training day in the week. */
    fun todayIndex(today: LocalDate = LocalDate.now()): Int {
        val byName = mapOf(
            "Mon" to DayOfWeek.MONDAY, "Tue" to DayOfWeek.TUESDAY, "Wed" to DayOfWeek.WEDNESDAY,
            "Thu" to DayOfWeek.THURSDAY, "Fri" to DayOfWeek.FRIDAY, "Sat" to DayOfWeek.SATURDAY,
            "Sun" to DayOfWeek.SUNDAY,
        )
        var best = 0
        var bestDistance = Int.MAX_VALUE
        plan.forEachIndexed { i, day ->
            val dow = byName[day.day] ?: return@forEachIndexed
            val distance = ((dow.value - today.dayOfWeek.value) + 7) % 7
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return best
    }

    // ── navigation ──────────────────────────────────────────────────────────────

    fun openDay(i: Int) = _state.update {
        it.copy(screen = Screen.Day, dayIdx = i, weekIdx = currentWeekIdx())
    }

    fun goPlan() {
        sessionActive = false
        _state.update {
            it.copy(
                screen = Screen.Plan, running = false, resting = false, showHow = false,
                weekIdx = currentWeekIdx(),
            )
        }
    }

    fun goDay() = _state.update {
        it.copy(screen = Screen.Day, running = false, resting = false, showHow = false)
    }

    fun startDay() {
        sessionActive = false
        startEx(0)
    }

    fun startEx(i: Int) {
        val e = list(_state.value.dayIdx).getOrNull(i) ?: return
        val now = SystemClock.elapsedRealtime()
        val fresh = !sessionActive
        if (fresh) {
            sessionActive = true
            sessionStartAt = now
            sessionStartWallMs = System.currentTimeMillis()
            pendingPerf.clear()
            pendingResults.clear()
            hrSum = 0L; hrCount = 0; hrMax = 0
            _watchSummary.value = null
            connectSavedHeartRate()
        }
        frozenAt = 0L
        // Cleared every set last time at this weight? Step it up before the first set.
        val stepUp = _saved.value.progressionFor(e)
        val note = if (stepUp != null) {
            val previous = _saved.value.weightFor(e)
            viewModelScope.launch { store.update { it.copy(weights = it.weights + (e.id to stepUp)) } }
            "Cleared every set at $previous kg last time — stepped up to $stepUp kg."
        } else {
            null
        }
        workLeftMs = e.time * 1000L
        workEndAt = now + workLeftMs
        tempoAccumMs = 0L
        tempoResumeAt = now
        _state.update {
            it.copy(
                screen = Screen.Player,
                exIdx = i,
                setIdx = 0,
                count = 0,
                tempoSec = 0,
                showHow = false,
                progression = note,
                resting = false,
                running = e.isTimed,
                remaining = e.time,
                setsDone = if (fresh) 0 else it.setsDone,
                repsDone = if (fresh) 0 else it.repsDone,
                volume = if (fresh) 0 else it.volume,
                elapsed = if (fresh) 0 else it.elapsed,
            )
        }
    }

    /** The how-to sheet holds the clock rather than discarding it. */
    fun toggleHow() {
        val open = !_state.value.showHow
        val now = SystemClock.elapsedRealtime()
        if (open) {
            frozenAt = now
        } else if (frozenAt != 0L) {
            val delta = now - frozenAt
            workEndAt += delta
            restEndAt += delta
            tempoResumeAt += delta
            sessionStartAt += delta
            frozenAt = 0L
        }
        _state.update { it.copy(showHow = open) }
    }

    // ── controls ────────────────────────────────────────────────────────────────

    fun toggleRun() {
        val e = current() ?: return
        val now = SystemClock.elapsedRealtime()
        val running = _state.value.running
        if (e.isTimed) {
            if (running) workLeftMs = (workEndAt - now).coerceAtLeast(0) else workEndAt = now + workLeftMs
        } else {
            if (running) tempoAccumMs += now - tempoResumeAt else tempoResumeAt = now
        }
        _state.update { it.copy(running = !running) }
    }

    /**
     * Counts the rep first and only then completes, so the last rep is both shown and logged —
     * the design's version short-circuits and silently drops one rep per set.
     */
    fun tap() {
        val e = current() ?: return
        val next = _state.value.count + 1
        _state.update { it.copy(count = next) }
        if (next >= e.reps) finishSet()
    }

    fun setMode(mode: String) {
        val now = SystemClock.elapsedRealtime()
        tempoAccumMs = 0L
        tempoResumeAt = now
        _state.update { it.copy(mode = mode, count = 0, tempoSec = 0, running = false) }
        viewModelScope.launch { store.update { it.copy(mode = mode) } }
    }

    /** Flips the demo panel between the real-person photos and the 3D model; remembered. */
    fun toggleDemo() {
        viewModelScope.launch {
            store.update { it.copy(demo = if (it.demo == "3d") "photo" else "3d") }
        }
    }

    fun adjustWeight(delta: Int) {
        val e = current() ?: return
        val next = (_saved.value.weightFor(e) + delta).coerceIn(2, 24)
        viewModelScope.launch { store.update { it.copy(weights = it.weights + (e.id to next)) } }
    }

    fun addRest() {
        restEndAt += 15_000
        _state.update { it.copy(restTotal = it.restTotal + 15, restLeft = it.restLeft + 15) }
    }

    /** Moves on without crediting a set — skipping should never inflate the log. */
    fun skip() {
        val s = _state.value
        if (s.exIdx + 1 < list(s.dayIdx).size) startEx(s.exIdx + 1) else finishSession()
    }

    fun finishSet() {
        val s = _state.value
        val e = current() ?: return
        val list = list(s.dayIdx)
        val weight = _saved.value.weightFor(e)
        val reps = if (e.isTimed) 0 else if (s.count > 0) s.count else e.reps

        if (!e.isTimed) {
            pendingPerf[e.id] = if (e.hasLoad) "$weight kg × $reps" else "$reps reps"
            val acc = pendingResults.getOrPut(e.id) { Acc(e.name, e.reps) }
            acc.sets++
            acc.reps += reps
            acc.weight = weight
            if (reps < e.reps) acc.cleared = false
        }

        // Warm-up moves lead into the session; they are not training sets and never count as one.
        val setsDone = s.setsDone + if (e.isWarmup) 0 else 1
        val repsDone = s.repsDone + reps
        val volume = s.volume + reps * weight * 2
        val rest = restFor(e)
        val now = SystemClock.elapsedRealtime()

        when {
            s.setIdx + 1 < e.sets -> {
                restEndAt = now + rest * 1000L
                buzz(40)
                _state.update {
                    it.copy(
                        setsDone = setsDone, repsDone = repsDone, volume = volume,
                        setIdx = it.setIdx + 1, count = 0, tempoSec = 0, running = false,
                        resting = true, restLeft = rest, restTotal = rest,
                    )
                }
            }

            s.exIdx + 1 < list.size -> {
                restEndAt = now + rest * 1000L
                buzz(40)
                _state.update {
                    it.copy(
                        setsDone = setsDone, repsDone = repsDone, volume = volume,
                        setIdx = -1, count = 0, tempoSec = 0, running = false,
                        resting = true, restLeft = rest, restTotal = rest,
                    )
                }
            }

            else -> {
                _state.update {
                    it.copy(setsDone = setsDone, repsDone = repsDone, volume = volume, running = false)
                }
                finishSession()
            }
        }
    }

    fun endRest() {
        val s = _state.value
        val now = SystemClock.elapsedRealtime()
        val advancing = s.setIdx == -1
        val index = if (advancing) s.exIdx + 1 else s.exIdx
        val e = list(s.dayIdx).getOrNull(index) ?: return
        workLeftMs = e.time * 1000L
        workEndAt = now + workLeftMs
        tempoAccumMs = 0L
        tempoResumeAt = now
        buzz(70)
        alerts.go()
        if (!onScreen) alerts.restFinished(e.name) else alerts.clear()
        _state.update {
            it.copy(
                resting = false, exIdx = index, setIdx = if (advancing) 0 else it.setIdx,
                count = 0, tempoSec = 0, remaining = e.time, running = e.isTimed,
            )
        }
    }

    private fun finishSession() {
        val s = _state.value
        val day = plan.getOrNull(s.dayIdx) ?: return
        // Skipping straight through logs nothing and must not mark the day complete.
        if (s.setsDone == 0) {
            sessionActive = false
            _state.update { it.copy(screen = Screen.Done, running = false, resting = false, showHow = false) }
            return
        }
        val endedAtMs = System.currentTimeMillis()
        val record = SessionRecord(
            epochDay = LocalDate.now().toEpochDay(),
            dayIdx = s.dayIdx,
            dayTitle = day.flatTitle,
            sets = s.setsDone,
            reps = s.repsDone,
            volume = s.volume,
            seconds = s.elapsed,
            startedAtMs = sessionStartWallMs,
            endedAtMs = endedAtMs,
            avgHr = if (hrCount > 0) (hrSum / hrCount).toInt() else null,
            maxHr = hrMax.takeIf { it > 0 },
        )
        val perf = pendingPerf.toMap()
        val results = pendingResults.map { (id, acc) ->
            ExerciseResult(
                exerciseId = id, name = acc.name, atMs = endedAtMs,
                epochDay = record.epochDay, weightKg = acc.weight, sets = acc.sets,
                reps = acc.reps, targetReps = acc.targetReps, cleared = acc.cleared,
            )
        }
        viewModelScope.launch {
            store.update {
                it.copy(
                    history = it.history + record,
                    lastPerf = it.lastPerf + perf,
                    exerciseHistory = (it.exerciseHistory + results).takeLast(2000),
                    programStart = if (it.programStart == 0L) record.epochDay else it.programStart,
                )
            }
            syncWatch(record)
        }
        sessionActive = false
        buzz(220)
        _state.update { it.copy(screen = Screen.Done, running = false, resting = false, showHow = false) }
    }

    /**
     * Publishes the session to Health Connect and pulls back whatever the watch recorded for the
     * same window. The watch only reaches Health Connect once the Zepp app syncs, so this is
     * best-effort — [refreshWatchSync] re-runs it on demand.
     */
    private suspend fun syncWatch(record: SessionRecord) {
        if (_hcStatus.value != HcStatus.Ready) return
        _syncing.value = true
        health.publish(
            title = "Forge — ${record.dayTitle}",
            startMs = record.startedAtMs,
            endMs = record.endedAtMs,
            hiit = record.dayTitle.contains("HIIT", ignoreCase = true),
        )
        val summary = health.summaryFor(record.startedAtMs, record.endedAtMs)
        _watchSummary.value = summary
        if (summary != null) {
            store.update { saved ->
                saved.copy(
                    history = saved.history.map {
                        if (it.startedAtMs == record.startedAtMs) {
                            it.copy(
                                avgHr = it.avgHr ?: summary.avgHr,
                                maxHr = it.maxHr ?: summary.maxHr,
                                calories = summary.calories,
                                watchTitle = summary.title,
                            )
                        } else {
                            it
                        }
                    },
                )
            }
        }
        _syncing.value = false
    }

    /** "Sync now" on the done screen — the watch often lands in Health Connect a minute late. */
    fun refreshWatchSync() {
        val record = _saved.value.history.lastOrNull() ?: return
        viewModelScope.launch {
            _hcStatus.value = health.status()
            syncWatch(record)
        }
    }

    override fun onCleared() {
        super.onCleared()
        heart.disconnect()
    }

    /** Manual weigh-in: recorded to the local log so the trend works without a scale. */
    fun setBodyWeight(kg: Float) {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            store.update {
                if (kg == it.bodyNow) {
                    it
                } else {
                    it.copy(
                        bodyNow = kg,
                        bodyNowMs = now,
                        bodyFromScale = false,
                        weightLog = (it.weightLog + WeightEntry(now, kg)).takeLast(400),
                    )
                }
            }
        }
    }

    fun setBodyTargets(start: Float, target: Float) {
        viewModelScope.launch { store.update { it.copy(bodyStart = start, bodyTarget = target) } }
    }

    fun setInitials(value: String) {
        val clean = value.take(3).uppercase().ifBlank { "You" }
        viewModelScope.launch { store.update { it.copy(initials = clean) } }
    }

    // ── clock ───────────────────────────────────────────────────────────────────

    private fun tick() {
        val s = _state.value
        if (s.screen != Screen.Player || s.showHow || frozenAt != 0L) return
        val e = current() ?: return
        val now = SystemClock.elapsedRealtime()
        val elapsed = ((now - sessionStartAt) / 1000).toInt().coerceAtLeast(0)

        if (s.resting) {
            val left = ceil((restEndAt - now) / 1000.0).toInt()
            if (left <= 0) {
                endRest()
            } else {
                if (left in 1..3 && left != s.restLeft) alerts.tick()
                _state.update { it.copy(restLeft = left, elapsed = elapsed) }
            }
            return
        }
        if (!s.running) {
            if (elapsed != s.elapsed) _state.update { it.copy(elapsed = elapsed) }
            return
        }

        if (e.isTimed) {
            val left = ceil((workEndAt - now) / 1000.0).toInt()
            if (left <= 0) {
                alerts.go()
                _state.update { it.copy(remaining = 0, elapsed = elapsed) }
                finishSet()
            } else {
                if (left in 1..3 && left != s.remaining) alerts.tick()
                _state.update { it.copy(remaining = left, elapsed = elapsed) }
            }
        } else {
            val tempoMs = tempoAccumMs + (now - tempoResumeAt)
            val seconds = (tempoMs / 1000).toInt()
            val count = seconds / 3
            if (count >= e.reps) {
                _state.update { it.copy(count = e.reps, tempoSec = seconds, elapsed = elapsed) }
                finishSet()
            } else {
                _state.update { it.copy(count = count, tempoSec = seconds, elapsed = elapsed) }
            }
        }
    }

    private fun buzz(ms: Long) {
        val context = getApplication<Application>()
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(VibratorManager::class.java))?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        runCatching {
            vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    // Declared last deliberately. viewModelScope uses Dispatchers.Main.immediate, so a launch
    // here runs synchronously until it genuinely suspends — with this block above the progress
    // flows, a Health Connect status that resolved without suspending reached them before they
    // were constructed, killing the app on every launch after permissions had been granted.
    init {
        viewModelScope.launch {
            store.data.collect { p ->
                _saved.value = p
                if (!_loaded.value) {
                    _loaded.value = true
                    _state.value = _state.value.copy(mode = p.mode, weekIdx = currentWeekIdx())
                    if (p.programStart == 0L || p.initials == "RK") {
                        store.update {
                            it.copy(
                                programStart = if (it.programStart == 0L) {
                                    LocalDate.now().toEpochDay()
                                } else {
                                    it.programStart
                                },
                                // "RK" was the design mock's placeholder, never a real value.
                                initials = if (it.initials == "RK") "AG" else it.initials,
                            )
                        }
                    }
                } else if (!sessionActive && _state.value.screen == Screen.Plan) {
                    _state.value = _state.value.copy(mode = p.mode)
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(200)
                tick()
            }
        }
        // Accumulate heart rate for the session average as samples arrive (~1 Hz).
        viewModelScope.launch {
            heart.bpm.collect { value ->
                if (value != null && sessionActive) {
                    hrSum += value
                    hrCount++
                    if (value > hrMax) hrMax = value
                }
            }
        }
        refreshHealthConnect()
    }
}

private inline fun MutableStateFlow<SessionState>.update(block: (SessionState) -> SessionState) {
    value = block(value)
}
