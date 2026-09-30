package com.forge.workout

import android.app.Application
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forge.workout.data.ActiveSession
import com.forge.workout.data.Day
import com.forge.workout.data.Exercise
import com.forge.workout.data.Persisted
import com.forge.workout.data.PlanLoader
import com.forge.workout.data.Program
import com.forge.workout.data.PendingResult
import com.forge.workout.data.Progress
import com.forge.workout.data.Range
import com.forge.workout.data.SessionRecord
import com.forge.workout.data.Store
import com.forge.workout.data.Week
import com.forge.workout.data.WeightEntry
import com.forge.workout.data.buildProgress
import com.forge.workout.data.nextPosition
import com.forge.workout.data.titleCase
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
    /** Seconds left in the auto-tempo count-in; 0 once counting has started (or when paused). */
    val leadIn: Int = 0,
    /** True while a rest-day mobility flow (Program.mobility[dayIdx]) is open or running. */
    val mobility: Boolean = false,
    /** The End-workout sheet is open; the clock is frozen behind it. */
    val showEnd: Boolean = false,
    /** Every completed set or move, warm-up and cool-down included — what a mobility flow records. */
    val movesDone: Int = 0,
    /** Set when the working weight was stepped up on entering this exercise. */
    val progression: String? = null,
    val setsDone: Int = 0,
    val repsDone: Int = 0,
    val volume: Int = 0,
    val elapsed: Int = 0,
)

/** The plan screen's "Workout in progress" card. */
data class ActiveCard(val title: String, val detail: String, val canResume: Boolean, val canSave: Boolean)

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

    /** Per-exercise tally for the session in progress, in the order exercises were first logged. */
    private val results = linkedMapOf<String, PendingResult>()

    /**
     * Auto tempo starts on its own after this count-in, so a set can begin hands-free. It is
     * modelled as a tempo start time in the future — pausing, the how-to sheet and [tick] all
     * work off `tempoResumeAt` unchanged.
     */
    private val tempoLeadInMs = 5_000L

    private fun autoTempo(e: Exercise, mode: String = _state.value.mode) =
        !e.isTimed && mode == "tempo" && onScreen

    // ── watch ───────────────────────────────────────────────────────────────────

    val heart = HeartRateMonitor(app)
    val health = HealthConnectRepo(app)
    private val alerts = Alerts(app)

    /** Set from the activity lifecycle: alerts only notify when the user can't see the screen. */
    var onScreen: Boolean = true
        set(value) {
            field = value
            // Auto tempo counts — and logs — reps on its own, so it must never run unwatched (a
            // call, the phone locked mid-set): leaving the screen pauses it like a tap on the
            // ring. Timed moves keep running, with the alerts covering them.
            if (!value) pauseTempo()
        }

    private fun pauseTempo() {
        val s = _state.value
        val e = current() ?: return
        if (s.screen != Screen.Player || e.isTimed || !s.running) return
        tempoAccumMs += (SystemClock.elapsedRealtime() - tempoResumeAt).coerceAtLeast(0)
        _state.update { it.copy(running = false, leadIn = 0) }
    }

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

    /** The Day the session screens show: a training day of the pinned week, or a mobility flow. */
    fun sessionDay(s: SessionState = _state.value): Day? = program.day(s.mobility, s.weekIdx, s.dayIdx)

    private fun moves(): List<Exercise> = sessionDay()?.all.orEmpty()
    private fun current(): Exercise? = moves().getOrNull(_state.value.exIdx)
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
        it.copy(screen = Screen.Day, mobility = false, dayIdx = i, weekIdx = currentWeekIdx())
    }

    fun openMobility(i: Int) = _state.update {
        it.copy(screen = Screen.Day, mobility = true, dayIdx = i, weekIdx = currentWeekIdx())
    }

    fun goPlan() {
        sessionActive = false
        _state.update {
            it.copy(
                screen = Screen.Plan, running = false, resting = false, showHow = false, leadIn = 0,
                weekIdx = currentWeekIdx(),
            )
        }
    }

    fun goDay() = _state.update {
        it.copy(screen = Screen.Day, running = false, resting = false, showHow = false, leadIn = 0)
    }

    fun startDay() {
        sessionActive = false
        startEx(0)
    }

    fun startEx(i: Int) {
        val e = moves().getOrNull(i) ?: return
        val now = SystemClock.elapsedRealtime()
        val fresh = !sessionActive
        if (fresh) {
            sessionActive = true
            sessionStartAt = now
            sessionStartWallMs = System.currentTimeMillis()
            pendingPerf.clear()
            results.clear()
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
        val auto = autoTempo(e)
        tempoAccumMs = 0L
        tempoResumeAt = if (auto) now + tempoLeadInMs else now
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
                running = e.isTimed || auto,
                leadIn = if (auto) (tempoLeadInMs / 1000).toInt() else 0,
                remaining = e.time,
                showEnd = false,
                setsDone = if (fresh) 0 else it.setsDone,
                movesDone = if (fresh) 0 else it.movesDone,
                repsDone = if (fresh) 0 else it.repsDone,
                volume = if (fresh) 0 else it.volume,
                elapsed = if (fresh) 0 else it.elapsed,
            )
        }
        checkpoint(i, 0)
    }

    private fun freeze() {
        if (frozenAt == 0L) frozenAt = SystemClock.elapsedRealtime()
    }

    /** Shifts every deadline by the time the clock was held, so nothing ran on behind a sheet. */
    private fun unfreeze() {
        if (frozenAt == 0L) return
        val delta = SystemClock.elapsedRealtime() - frozenAt
        workEndAt += delta
        restEndAt += delta
        tempoResumeAt += delta
        sessionStartAt += delta
        frozenAt = 0L
    }

    /** The how-to sheet holds the clock rather than discarding it. */
    fun toggleHow() {
        val open = !_state.value.showHow
        if (open) freeze() else unfreeze()
        _state.update { it.copy(showHow = open) }
    }

    /** ✕ or Back in the player: ask how to end, with the clock held and the session checkpointed. */
    fun openEnd() {
        if (_state.value.showEnd) return
        freeze()
        checkpoint()
        _state.update { it.copy(showEnd = true, showHow = false) }
    }

    fun keepGoing() {
        unfreeze()
        _state.update { it.copy(showEnd = false) }
    }

    fun saveAndFinish() {
        unfreeze()
        finishSession()
    }

    /** Drops the session entirely — nothing is recorded — and returns to the day screen. */
    fun discardSession() {
        sessionActive = false
        frozenAt = 0L
        alerts.clear()
        viewModelScope.launch { store.update { it.copy(active = null) } }
        _state.update {
            it.copy(screen = Screen.Day, showEnd = false, showHow = false, running = false, resting = false, leadIn = 0)
        }
    }

    // ── controls ────────────────────────────────────────────────────────────────

    fun toggleRun() {
        val e = current() ?: return
        val now = SystemClock.elapsedRealtime()
        val running = _state.value.running
        if (e.isTimed) {
            if (running) workLeftMs = (workEndAt - now).coerceAtLeast(0) else workEndAt = now + workLeftMs
        } else {
            // Pausing during the count-in banks nothing (the start is still in the future), and
            // resuming counts straight away rather than running the count-in again.
            if (running) tempoAccumMs += (now - tempoResumeAt).coerceAtLeast(0) else tempoResumeAt = now
        }
        _state.update { it.copy(running = !running, leadIn = 0) }
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
        val e = current()
        val auto = e != null && autoTempo(e, mode) && !_state.value.resting
        tempoAccumMs = 0L
        tempoResumeAt = if (auto) now + tempoLeadInMs else now
        _state.update {
            it.copy(
                mode = mode, count = 0, tempoSec = 0, running = auto,
                leadIn = if (auto) (tempoLeadInMs / 1000).toInt() else 0,
            )
        }
        viewModelScope.launch { store.update { it.copy(mode = mode) } }
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
        if (s.exIdx + 1 < moves().size) startEx(s.exIdx + 1) else finishSession()
    }

    fun finishSet() {
        val s = _state.value
        val e = current() ?: return
        val list = moves()
        val weight = _saved.value.weightFor(e)
        val reps = if (e.isTimed) 0 else if (s.count > 0) s.count else e.reps

        if (!e.isTimed) {
            pendingPerf[e.id] = if (e.hasLoad) "$weight kg × $reps" else "$reps reps"
            val r = results[e.id] ?: PendingResult(e.id, e.name, targetReps = e.reps, plannedSets = e.sets)
            results[e.id] = r.copy(
                sets = r.sets + 1, reps = r.reps + reps, weightKg = weight,
                allSetsHitTarget = r.allSetsHitTarget && reps >= e.reps,
            )
        }

        // Warm-up, cool-down and mobility moves are not training sets and never count as one.
        val setsDone = s.setsDone + if (e.isRecovery) 0 else 1
        val movesDone = s.movesDone + 1
        val repsDone = s.repsDone + reps
        val volume = s.volume + reps * weight * 2
        val next = nextPosition(s.exIdx, s.setIdx, e.sets, list.size)

        if (next == null) {
            _state.update {
                it.copy(
                    setsDone = setsDone, movesDone = movesDone, repsDone = repsDone, volume = volume,
                    running = false, leadIn = 0,
                )
            }
            finishSession()
            return
        }

        val rest = restFor(e)
        restEndAt = SystemClock.elapsedRealtime() + rest * 1000L
        buzz(40)
        val sameMove = next.first == s.exIdx
        _state.update {
            it.copy(
                setsDone = setsDone, movesDone = movesDone, repsDone = repsDone, volume = volume,
                // setIdx -1 tells endRest() to advance to the next move.
                setIdx = if (sameMove) next.second else -1,
                count = 0, tempoSec = 0, running = false, leadIn = 0,
                resting = true, restLeft = rest, restTotal = rest,
            )
        }
        checkpoint(next.first, next.second)
    }

    /** The next set still to do: during a rest between moves that is the following move. */
    private fun pendingPosition(): Pair<Int, Int> {
        val s = _state.value
        return if (s.resting && s.setIdx == -1) s.exIdx + 1 to 0 else s.exIdx to s.setIdx.coerceAtLeast(0)
    }

    private fun snapshot(exIdx: Int, setIdx: Int): ActiveSession {
        val s = _state.value
        return ActiveSession(
            mobility = s.mobility, weekIdx = s.weekIdx, dayIdx = s.dayIdx, exIdx = exIdx, setIdx = setIdx,
            setsDone = s.setsDone, movesDone = s.movesDone, repsDone = s.repsDone, volume = s.volume,
            elapsedSec = s.elapsed, startedAtMs = sessionStartWallMs,
            lastCheckpointMs = System.currentTimeMillis(),
            pendingPerf = pendingPerf.toMap(), results = results.values.toList(),
            hrSum = hrSum, hrCount = hrCount, hrMax = hrMax,
        )
    }

    /**
     * Persists the session so it survives the process dying. One DataStore write per call —
     * called at session start, each logged set, each move change and when the End sheet opens;
     * never per tick. A finished or discarded session writes nothing more.
     */
    private fun checkpoint(exIdx: Int = pendingPosition().first, setIdx: Int = pendingPosition().second) {
        if (!sessionActive) return
        val snap = snapshot(exIdx, setIdx)
        viewModelScope.launch { store.update { it.copy(active = snap) } }
    }

    /** Writes a session to history (and progression), clears the snapshot, syncs the watch. */
    private fun record(snap: ActiveSession, title: String) {
        val rec = snap.record(title)
        viewModelScope.launch {
            store.update {
                it.copy(
                    history = it.history + rec,
                    lastPerf = it.lastPerf + snap.pendingPerf,
                    exerciseHistory = (it.exerciseHistory + snap.exerciseResults(rec.epochDay)).takeLast(2000),
                    programStart = if (it.programStart == 0L) rec.epochDay else it.programStart,
                    active = null,
                )
            }
            syncWatch(rec)
        }
    }

    fun endRest() {
        val s = _state.value
        val now = SystemClock.elapsedRealtime()
        val advancing = s.setIdx == -1
        val index = if (advancing) s.exIdx + 1 else s.exIdx
        val e = moves().getOrNull(index) ?: return
        workLeftMs = e.time * 1000L
        workEndAt = now + workLeftMs
        val auto = autoTempo(e)
        tempoAccumMs = 0L
        tempoResumeAt = if (auto) now + tempoLeadInMs else now
        buzz(70)
        // With auto tempo the "go" tone marks the end of the count-in instead (see tick()).
        if (!auto) alerts.go()
        if (!onScreen) alerts.restFinished(e.name) else alerts.clear()
        _state.update {
            it.copy(
                resting = false, exIdx = index, setIdx = if (advancing) 0 else it.setIdx,
                count = 0, tempoSec = 0, remaining = e.time, running = e.isTimed || auto,
                leadIn = if (auto) (tempoLeadInMs / 1000).toInt() else 0,
            )
        }
    }

    private fun finishSession() {
        val day = sessionDay() ?: return
        val (exIdx, setIdx) = pendingPosition()
        val snap = snapshot(exIdx, setIdx)
        sessionActive = false
        frozenAt = 0L
        _state.update {
            it.copy(screen = Screen.Done, running = false, resting = false, showHow = false, showEnd = false, leadIn = 0)
        }
        if (!snap.hasWork) {
            // Nothing worth a history row: a warm-up alone, or a flow left before its first move.
            viewModelScope.launch { store.update { it.copy(active = null) } }
            return
        }
        buzz(220)
        record(snap, day.flatTitle)
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
            stretching = record.isMobility,
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

    /** What the plan screen shows for a session the app stopped in the middle of, or null. */
    fun activeCard(p: Persisted): ActiveCard? {
        val a = p.active ?: return null
        val day = program.day(a.mobility, a.weekIdx, a.dayIdx)
        val move = day?.all?.getOrNull(a.exIdx)
        val done = if (a.mobility) "${a.movesDone} moves done" else "${a.setsDone} sets logged"
        return ActiveCard(
            title = day?.flatTitle ?: "Unfinished workout",
            detail = listOfNotNull(move?.let { "Stopped at ${it.name.titleCase()}" }, done).joinToString(" · "),
            canResume = move != null,
            canSave = a.hasWork,
        )
    }

    /** Back into the player at the start of the pending set, paused; time away isn't counted. */
    fun resumeActive() {
        val a = _saved.value.active ?: return
        val day = program.day(a.mobility, a.weekIdx, a.dayIdx) ?: return
        val e = day.all.getOrNull(a.exIdx) ?: return
        val now = SystemClock.elapsedRealtime()
        sessionActive = true
        sessionStartAt = now - a.elapsedSec * 1000L
        sessionStartWallMs = a.startedAtMs
        pendingPerf.clear()
        pendingPerf.putAll(a.pendingPerf)
        results.clear()
        a.results.forEach { results[it.exerciseId] = it }
        hrSum = a.hrSum; hrCount = a.hrCount; hrMax = a.hrMax
        frozenAt = 0L
        workLeftMs = e.time * 1000L
        workEndAt = now + workLeftMs
        tempoAccumMs = 0L
        tempoResumeAt = now
        _watchSummary.value = null
        connectSavedHeartRate()
        _state.update {
            it.copy(
                screen = Screen.Player, mobility = a.mobility, weekIdx = a.weekIdx, dayIdx = a.dayIdx,
                exIdx = a.exIdx, setIdx = a.setIdx, count = 0, tempoSec = 0, leadIn = 0,
                running = false, resting = false, remaining = e.time, showHow = false, showEnd = false,
                progression = null, setsDone = a.setsDone, movesDone = a.movesDone,
                repsDone = a.repsDone, volume = a.volume, elapsed = a.elapsedSec,
            )
        }
    }

    /** Saves a leftover session as it stood at its last checkpoint. */
    fun saveActive() {
        val a = _saved.value.active ?: return
        if (!a.hasWork) return
        val day = program.day(a.mobility, a.weekIdx, a.dayIdx)
        record(a, day?.flatTitle ?: "Workout")
        if (day != null) {
            _state.update {
                it.copy(
                    screen = Screen.Done, mobility = a.mobility, weekIdx = a.weekIdx, dayIdx = a.dayIdx,
                    setsDone = a.setsDone, movesDone = a.movesDone, repsDone = a.repsDone,
                    volume = a.volume, elapsed = a.elapsedSec,
                )
            }
        }
    }

    fun discardActive() {
        viewModelScope.launch { store.update { it.copy(active = null) } }
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
            if (now < tempoResumeAt) {
                // Auto-tempo count-in: tick over the last three seconds, nothing counted yet.
                val lead = ceil((tempoResumeAt - now) / 1000.0).toInt()
                if (lead in 1..3 && lead != s.leadIn) alerts.tick()
                _state.update { it.copy(leadIn = lead, count = 0, tempoSec = 0, elapsed = elapsed) }
                return
            }
            if (s.leadIn > 0) {
                alerts.go()
                buzz(70)
            }
            val tempoMs = tempoAccumMs + (now - tempoResumeAt)
            val seconds = (tempoMs / 1000).toInt()
            val count = seconds / 3
            if (count >= e.reps) {
                _state.update { it.copy(count = e.reps, tempoSec = seconds, leadIn = 0, elapsed = elapsed) }
                finishSet()
            } else {
                _state.update { it.copy(count = count, tempoSec = seconds, leadIn = 0, elapsed = elapsed) }
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
