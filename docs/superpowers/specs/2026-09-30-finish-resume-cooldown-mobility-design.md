# Forge 1.7 — finish early & resume, cool-down, rest-day mobility

Date: 2026-09-30 · Status: approved in chat, awaiting spec review · Target: v1.7 (versionCode 8)

## Intent

What the user asked for, in their words: "first finish-and-resume, then the cool-down and rest-day
mobility sessions in one release", with the rest-day session being "mobility only".

Success looks like:

- A workout that stops partway is never lost: it can be ended early and saved, and a workout
  interrupted by the app dying can be resumed.
- Every training day ends with a short, hands-free cool-down.
- The plan's static REST row becomes three guided mobility sessions for Wed / Fri / Sun.
- Nothing already stored (history, weights, stats) changes meaning.

## Architecture

Every session the user can run is a `Day`: the existing Day, Player and Done screens render it
unchanged.

- **Training days** gain a `cooldown: List<Exercise>` (block `"cooldown"`), generated like the
  warm-up. `Day.all` becomes `warmup + strength + hiit + cooldown`; `Day.main` stays
  `strength + hiit`, so exercise counts, total sets and muscle chips are unaffected.
- **Mobility flows** are Days made only of timed moves, in a new `Program.mobility: List<Day>`
  (three entries, same every week). A Day gains `kind: String = "training"`; mobility Days use
  `"mobility"` and a `day` field of `Wed` / `Fri` / `Sun`.
- **Session source.** `SessionState` gains `mobility: Boolean`. The ViewModel resolves the running
  Day in one place (`sessionDay()`): `program.mobility[dayIdx]` when `mobility`, otherwise
  `week(weekIdx).days[dayIdx]`. Everything that today reads `plan[dayIdx]` / `list(dayIdx)` goes
  through it.

Rejected: mobility as extra days inside each week (breaks "x/4", ✓ marks and the 4-day layout);
a separate mobility player (duplicates the player).

## 1. Finish early

- The player's ✕ opens an **End workout?** sheet:
  - **Save & finish** (only when the session has something to record — see "What counts as
    logged" below) → `finishSession()` → Done screen.
  - **Discard** → session dropped, back to the Day screen.
  - **Keep going** → sheet closes; the clock was frozen while it was open (same mechanism as the
    how-to sheet).
  - With nothing logged, the choices are **Leave** and **Keep going**.
- The sheet swallows taps like the rest overlay.
- **What counts as logged.** `SessionState` gains `movesDone` (every completed move, including
  warm-up and cool-down). A training session records when `setsDone > 0` (unchanged: a warm-up
  alone records nothing). A mobility session has no sets, so it records when `movesDone > 0` —
  without this rule every mobility session would be silently dropped.
- **Progression rule fix.** `Acc` records the planned set count; at save, an exercise's
  `ExerciseResult.cleared` is `true` only if every set hit target reps **and** the number of
  logged sets ≥ the planned sets. Ending early or skipping mid-exercise therefore never triggers
  the +2 kg step-up.

## 2. Resume after the app dies

- `Persisted` gains `active: ActiveSession? = null`:

  ```kotlin
  @Serializable
  data class ActiveSession(
      val mobility: Boolean, val weekIdx: Int, val dayIdx: Int,
      val exIdx: Int, val setIdx: Int,            // position of the next set to do
      val setsDone: Int, val movesDone: Int, val repsDone: Int, val volume: Int,
      val elapsedSec: Int,                        // active time so far
      val startedAtMs: Long, val lastCheckpointMs: Long,
      val pendingPerf: Map<String, String>,
      val results: List<PendingResult>,           // serializable form of Acc
      val hrSum: Long, val hrCount: Int, val hrMax: Int,
  )
  ```

- **Checkpoints** (one DataStore write each; never per tick): session start, every logged set,
  every move to another exercise (skip, rest end, direct jump), and when the End-workout sheet
  opens. Cleared on save or discard.
- **On launch** with `active != null`, the plan screen shows a **Workout in progress** card above
  the stats: the session's title, the move it stopped on and the sets logged, with **Resume**,
  **Save & finish** and **Discard**.
  - **Resume** reopens the player at the start of the pending set (`exIdx`, `setIdx`), paused.
    If the checkpoint was taken during a rest, the pending set is the one after it. A timed move
    (warm-up, HIIT, cool-down, mobility) restarts from its full time. Elapsed time continues from
    `elapsedSec`; time away is not counted, and heart-rate totals resume from the snapshot.
  - **Save & finish** records the session with `startedAtMs` and `endedAtMs = lastCheckpointMs`
    (the duration is not inflated by the gap), using the session's own start date.
  - If the snapshot no longer resolves to a Day (plan changed in an update), Resume is hidden;
    Save & finish and Discard remain.
- The snapshot is stored under its own DataStore key (`"active"`), separate from the main state
  blob. `Store` falls back to defaults for any blob it cannot decode, so keeping them apart means a
  damaged snapshot reads as "no session in progress" and can never take the history with it.

## 3. Cool-down (training days)

~4½ minutes after the HIIT finisher: five held stretches, 45 s each (60 s with "switch sides
halfway" for one-sided stretches), 10 s switch-overs. Timed and hands-free, styled in a new calm
colour (distinct from warm-up amber and HIIT blue), not counted as sets, with a cue line per move
like the warm-up. Day minutes include
it. The last HIIT move's 20 s rest leads into it.

| Day | Stretches (dataset ids) |
| --- | --- |
| Upper (Mon, Thu) | chest & front of shoulder 1271 · kneeling lat 1346 · overhead triceps 0643 · rear deltoid 0669 · neck side 1403 |
| Lower (Tue) | hamstring 1511 · all-fours quad 1512 · seated piriformis 2567 · calf against wall 1377 · butterfly 1494 |
| Full (Sat) | runners 1585 · seated glute 1424 · chest & front of shoulder 1271 · kneeling lat 1346 · spine 1363 |

## 4. Rest-day mobility

Three ~14-minute flows: 12 moves × 60 s, 10 s switch-overs, all timed and hands-free, same every
week.

- **Wed · Hips & lower back:** circles knee 0257, rocking frog 2571, butterfly 1494, seated
  piriformis 2567, seated glute 1424, hamstring 1511, all-fours quad 1512, iron cross 1419,
  side-lying floor 1358, seated lower back 0690, spine 1363, world's greatest 1604.
- **Fri · Upper back & shoulders:** wrist circles 1428, neck side 1403, side push neck 0716,
  dynamic chest 1167, chest & front of shoulder 1271, back pec 1405, kneeling lat 1346, standing
  lateral 0794, rear deltoid 0669, triceps 0817, upper back 1365, upward dog 1366.
- **Sun · Full-body flow:** inchworm 1471, world's greatest 1604, squat to overhead reach 1685,
  step back and reach 1687, windmill toe touch 3214, runners 1585, seated wide angle 1587, rocking
  frog 2571, kneeling lat 1346, chest & front of shoulder 1271, spine 1363, upward dog 1366.

The plan screen's REST row becomes three cards (Wed / Fri / Sun) with the flow title, length, and
a ✓ when done this week; tapping opens the Day screen for the flow. Any flow can be run on any day.

**Media.** Every stretch GIF is upscaled to 720 px WebP with `tools/upscale_media.py`. Each
animation is checked by eye before shipping; a stretch whose demo doesn't show the move is
replaced from the same 34-move mat-only pool. Instructions that don't match the demo get authored
steps, as in the warm-up. All generated by `tools/genplan.py`.

## 5. Logging and stats

- `SessionRecord` gains `kind: String = "training"` (old records decode as training). Mobility
  sessions save `kind = "mobility"`, `dayIdx` = flow index, zero sets/reps/volume.
- `doneThisWeek()` and the **Sessions done x/4** tile count training records only.
- **Time this week** and the **streak** include mobility.
- **Progress tab:** mobility sessions appear in "Your sessions", labelled Mobility; the Sessions
  stat tile counts training only; volume charts are unaffected (zero volume).
- **Health Connect:** mobility publishes `EXERCISE_TYPE_STRETCHING`; training unchanged
  (HIIT/strength by title).
- **Done screen:** for mobility, shows time and heart rate; sets/volume are hidden.

## 6. Testing

Unit (JVM):

- `ActiveSession` survives encode → decode inside `Persisted`; a missing field decodes to `null`.
- Progression: a result with fewer logged sets than planned is not `cleared`.
- A mobility session with moves done but no sets is recorded; a training session with only
  warm-up moves is not.
- `doneThisWeek()` ignores mobility records; streak counts them.
- `PlanTest`: every training day ends with a 3–6 minute cool-down after the HIIT block; there are
  three mobility flows of 12–16 minutes, all timed; every demo, thumbnail and body-map region they
  name is bundled.

On device:

- Everything that writes data (Save & finish, kill-and-resume, mobility session save) runs on
  the **Forge Preview** build (`-Pforge.preview`, package `com.forge.workout.preview`,
  separate data), so the user's real history is never touched.
- The release build is then installed over the real app for read-only checks: launch, plan screen
  with the three mobility cards, a cool-down visible on a day screen, no crash on cold start.

## Out of scope

Walkpad timing inside rest-day sessions (walks stay tracked via Health Connect steps/distance),
yoga poses (needs a licensed pose source), block rotation for mobility flows, a foreground service
to keep workouts alive.
