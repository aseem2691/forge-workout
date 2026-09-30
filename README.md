# Forge — Home Training

A native Android workout app implementing the `Home Workout App.dc.html` Claude Design prototype.
Four-day split, dumbbells 2–24 kg, walkpad and a mat. Everything runs offline: the plan, the
animated exercise demos, the muscle map and the form instructions are bundled in the APK.

## The program

Four rotating blocks, so the training changes week to week and repeats on a 4-week cycle. The
block advances automatically from the stored program start date.

| Block | Focus |
| --- | --- |
| **A** | Dumbbell strength — heaviest loads of the block |
| **B** | Body weight & tempo — no loading, slow reps, high control |
| **C** | Dumbbell volume — lighter loads, more reps, new movement patterns |
| **D** | Hybrid conditioning — deload the loads, raise the heart rate |

Every block keeps the same four sessions — Mon upper, Tue lower/glutes, Thu push·pull, Sat full
body — each opening with a warm-up, then a strength block and a 40/20 HIIT finisher. 66 distinct exercises in total,
selected from the dataset to need nothing but dumbbells, a mat, a chair and a walkpad. Block B is
entirely body-weight apart from two light-dumbbell delt raises.

`plan.json` is generated from the dataset rather than hand-written; the generator lives at
`tools/genplan.py`.

### Warm-up

Every session starts with about 7 minutes of timed moves that run hands-free — a 40 s move, a
10 s switch-over, the next move — ending in a 20 s hand-off to set up the first set:

| Day | Warm-up |
| --- | --- |
| Mon upper, Thu push·pull | walkpad brisk walk 2:00 · step back and reach · scapula push-up · plank to upward dog · inchworm · dynamic chest stretch · world's greatest stretch 1:00 |
| Tue lower | walkpad brisk walk 2:00 · windmill toe touch · squat to overhead reach · glute bridge · forward lunge · world's greatest stretch 1:00 · jumping jacks 0:30 |
| Sat full body | walkpad brisk walk 2:00 · step back and reach · windmill toe touch · inchworm · squat to overhead reach · world's greatest stretch 1:00 · jumping jacks 0:30 |

Warm-up moves are shown in amber, each with a one-line coaching cue, and never count as training
sets. To skip it on a short day, tap the first strength exercise on the day screen.

### Rest

No break runs longer than **60 s** — between sets and between exercises. Rests the program
designed longer (75–90 s on the heavy presses and squats) are capped at a minute; shorter ones
(45 s on calves) are unchanged, and the HIIT finisher keeps its 40/20 interval. **+15 s** on the
rest screen still buys time when a set needs it. The session lengths shown account for both the
shorter rests, the warm-up and the cool-down.

### Cool-down

Every training day ends with about 5 minutes of held stretches for what the day worked, run
hands-free after the HIIT finisher: 60 s for one-sided stretches (switch at 30 s), 45 s for
two-sided ones, 10 s between. Shown in lavender; never counted as training sets.

| Day | Cool-down |
| --- | --- |
| Mon upper, Thu push·pull | towel shoulder opener · kneeling lat stretch · overhead triceps · rear delt · neck side |
| Tue lower | hamstring (on the back) · all-fours quad · seated piriformis (chair) · calf against the wall · butterfly |
| Sat full body | runner's stretch · seated glute · towel shoulder opener · kneeling lat stretch · seated forward fold |

### Rest-day mobility

The REST DAYS cards on the plan screen run three guided ~14-minute flows — 12 moves of 60 s, 10 s
between — the same every week, any day you like:

- **Wed · Hips & lower back** — knee circles, rocking frog, butterfly, piriformis, seated glute,
  hamstring, quad, iron cross, side-lying stretch, seated side reach, forward fold, world's
  greatest stretch.
- **Fri · Upper back & shoulders** — wrist circles, two neck stretches, dynamic chest, towel
  shoulder opener, chair chest stretch, kneeling lat, standing side stretch, rear delt, overhead
  triceps, upper back, plank to upward dog.
- **Sun · Full-body flow** — inchworm, world's greatest stretch, squat to overhead reach, step
  back and reach, windmill, runner's stretch, seated wide-angle, rocking frog, kneeling lat, towel
  shoulder opener, forward fold, plank to upward dog.

A mobility session counts toward **time this week** and the **streak**, and shows in "Your
sessions" labelled Mobility — but not toward **Sessions done x/4** or a training day's ✓. It is
published to Health Connect as *stretching*. The walk on those days is tracked from the watch via
Health Connect, as before.

## Install

The signed APK is at `app/build/outputs/apk/release/app-release.apk` (a copy sits at `Forge.apk`
in the project root).

**Preview builds.** GitHub Actions builds every push with `-Pforge.preview`: package
`com.forge.workout.preview`, labelled **Forge Preview**, signed with the committed
`app/preview.keystore`. Release builds are signed with the local debug key, which CI doesn't
have — a CI build could never update the installed app — so previews install *beside* it,
start with their own empty data and leave the real app's untouched. Successive previews update
each other. Download the `Forge-preview` artifact from the workflow run.

```bash
adb install -r Forge.apk
```

Or rebuild from source:

```bash
./gradlew assembleRelease
```

Requires JDK 17+ and an Android SDK with platform 36. `local.properties` points at the SDK.
Release builds are signed with the debug key so they install straight from the CLI — replace
`signingConfig` in `app/build.gradle.kts` before distributing anywhere.

**minSdk 28** (Android 9). Required for `ImageDecoder`/`AnimatedImageDrawable`, which plays the
exercise GIFs without pulling in an image library.

## Screens

| Screen | What it does |
| --- | --- |
| **Plan** | Program week, body-weight progress, real weekly stats, the 4-day split. Tap the body-weight card to log today's weight. |
| **Day** | Session overview — target muscles, exercise list with demo thumbnails, warm-up, strength block and HIIT finisher. Tap any exercise to jump straight to it. |
| **Player** | Animated demo, muscle map, set dots, weight stepper, rep logging (tap-count or auto-tempo) or a countdown, rest overlay, and `?` for step-by-step form cues. |
| **Done** | Session summary, then back to the week. |

## Architecture

```
tools/genplan.py            regenerates assets/plan.json from the exercises dataset
tools/upscale_media.py      upscales the dataset's GIFs into the 720×720 WebP demos
tools/genbodymap.py         converts the body-map polygons into assets/bodymap.json
app/src/main/
  assets/plan.json          4 rotating blocks × 4 days
  assets/bodymap.json       front/back muscle polygons
  assets/media/             75 animated WebP demos + 180×180 thumbnails
  java/com/forge/workout/
    WorkoutViewModel.kt     session state machine + deadline-based timers
    data/Plan.kt            program models, JSON loading
    data/Store.kt           DataStore persistence, week/streak derivation
    ui/Theme.kt             palette + Anton/Archivo type scale
    ui/Media.kt             animated demo and thumbnail loading from assets
    ui/BodyMap.kt           front/back muscle map drawn on a Canvas
    ui/{Plan,Day,Player,Done}Screen.kt
```

State lives in one `SessionState` in the ViewModel; everything durable goes through `Store`
(DataStore + kotlinx.serialization) — working weights, per-exercise last performance, completed
sessions, body weight, and the program start date.

## Where this deliberately differs from the prototype

The design is a clickable prototype, so several values are hardcoded or stubbed. Those are wired
to real data here:

- **Today's session** comes from the real weekday (the prototype always highlighted day 1). If
  today isn't a training day, the next upcoming one is highlighted.
- **Completed days** are derived from persisted history for the current Monday-start week, so
  the ✓ marks reset weekly instead of living in memory.
- **Weekly stats** — volume and time are computed from history. The prototype's *"Walkpad km 18.6"*
  had no data source behind it, so that tile is now **Time this wk**.
- **Week number** counts from a stored program start date; **body weight** (current / start /
  target) and the avatar initials are editable and persisted rather than fixed at 84.2 / 88 / 75 / "RK".
- **"Last time"** notes use real logged history once you've trained; before that they fall back to
  the seeded values already in `plan.json`.
- **Rep counting**: the prototype's `tap()` completes the set *without* counting the final rep, so
  every tap-logged set recorded one rep short. Here the rep is counted first, then the set completes.
- **Skip** no longer credits a set. In the prototype, skipping the last exercise called `finishSet()`
  and logged work you didn't do. A session with zero logged sets isn't recorded at all.
- **Rest** uses each exercise's own rest, capped at 60 s (45 s on calves, 20 s in HIIT). The
  prototype's prop default collapsed every strength rest to 75 s.
- **Timers are deadline-based** (`elapsedRealtime`), not per-tick decrements, so a backgrounded or
  throttled process cannot silently lose seconds mid-set.

- **Auto tempo starts itself.** In auto-tempo mode every strength set — the first and each one
  after a rest — opens with a 5 s count-in (ticks over the last three seconds, a tone when it
  goes) and then counts reps on its own. Tap the ring to pause; tap again to carry on without a
  second count-in. Leaving the app — a call, the phone locked — pauses it too, so a set can never
  count and log itself unwatched. The prototype started every set paused.
- **Rest can't be tapped through.** The rest overlay swallows taps, so nothing underneath — the
  LOG SET button, the rep counter — fires by accident while resting.

- **End a workout early.** ✕ or Back in the player asks **Save & finish** (logs what's done and
  shows the summary), **Discard** or **Keep going** — the clock is held while it's open. An
  exercise stopped partway never earns the automatic +2 kg: it counts as cleared only if every
  planned set was done at target.
- **Resume after the app dies.** The session in progress is checkpointed at every logged set and
  move — under its own storage key, so a damaged snapshot can never take the history with it. If
  the app is killed, the plan screen shows **Workout in progress** with Resume (back to the start
  of the pending set, paused; time away isn't counted), Save (dated by its start, ended at its
  last checkpoint) or Discard. Starting another workout instead saves the unfinished one first, so
  logged sets are never overwritten; saving the same session twice records it once.

Added for daily use: the screen stays awake in the player, and the phone buzzes when a set ends,
a rest finishes, and a session completes.

## Progressive overload

Forge records what each exercise actually produced — weight, sets, reps, and whether every set hit
its target. When you next reach an exercise you cleared completely at the weight still set, it
steps the working weight up 2 kg (dumbbells top out at 24) and says so on the player. Clear the
new weight and it steps again; miss reps and it holds. The design mocked this with hardcoded coach
text — here it runs off your own logged history.

## Rest alerts

Rest counts down with a tick over the last three seconds and a brighter tone when it ends, so a set
can be run with the phone face-down. If the app isn't on screen when rest finishes, a heads-up
notification names the next exercise. The 40/20 HIIT intervals get the same cues.

## Progress tab

A second tab charting training and body weight over **7D / 30D / 6M / 1Y**. One range filter
scopes everything below it, so every chart and the table always read the same slice.

- **Stat tiles** — sessions, volume, time trained, average heart rate, reps, and a week streak
  (consecutive Monday-start weeks with at least one session; the current week not being logged
  yet doesn't break it).
- **Body weight** — a line over time with the target drawn as a dashed threshold. Points are
  positioned by *time*, not by index, so a fortnight between weigh-ins reads as a gap.
- **Training volume** — one bar per bucket (days for 7D/30D, weeks for 6M, months for 1Y). Empty
  buckets are kept: a missed week should look like a missed week rather than being closed up.
- **Breakdown table** — every plotted value as text, so nothing is encoded by colour alone.
- **Your lifts** — every exercise you've logged, and per-lift working-weight trends over time.
- **Your sessions** — every completed workout with its sets, reps, volume, time and watch data.
- **Km walked / steps** — read from Health Connect for the selected range, most of it the walkpad.

Charts are drawn directly on a Compose `Canvas`; no charting dependency. Single series per chart
(never a dual axis), one colour per series, hairline grid, and labels only on the endpoints and
the peak. The bucketing, streak and range arithmetic are covered by unit tests.

## Body composition from a smart scale

If a scale (e.g. **MovingLife**) writes into Health Connect, Forge reads both **weight** and
**body-fat percentage** from there — the plan card and the charts follow the scale, and nothing
needs typing. Body fat gets its own trend card on the Progress tab and a column in the breakdown
table; it stays hidden until there is a reading, so the tab never shows an empty box. A reading
supersedes what's on the card only if it is *newer* than the last manual entry, so typing a weight
still works and isn't immediately overwritten. Without a scale, manual entries are kept in a local
log so the trend chart still works.

Requires the `READ_WEIGHT` and `READ_BODY_FAT` permissions, granted alongside the others under
Connect your watch. Adding a permission means re-granting after an update — Health Connect only
asks for what the app declared at the time.

## Amazfit / Zepp OS watch

Forge connects to an Amazfit watch two ways, set up from **Connect your watch** on the plan screen.

**Post-workout sync — Health Connect.** The Zepp app writes its data into Health Connect
(Zepp: Profile → 3rd-party account linking → Health Connect). Forge reads the exercise session,
heart rate and calories that overlap the session you just logged and attaches them to it, and
publishes the Forge session back into Health Connect. Zepp only ever *writes* to Health Connect, so
Forge sessions will not appear inside Zepp.

Two details worth knowing: Forge filters out its own records when looking for the watch's, so a
session it just published is never mistaken for the watch's recording; and if the watch logged no
distinct workout, Forge still reports the heart rate it recorded across the session window.

**Live heart rate — Bluetooth.** With **Heart Rate Push** enabled (GTR 4, Zepp OS 3.0+, under
Settings → Heart rate), the watch advertises itself as a standard BLE heart-rate monitor. Forge
connects to it like any chest strap over the Heart Rate Service (`0x180D` / `0x2A37`) and shows live
BPM on the player, then stores the session's average and max. Many watches only broadcast while a
workout is running on the watch, so start one there first.

> Talking to the watch over Zepp's own protocol was deliberately not attempted — it is proprietary
> and encrypted, needs pairing-key extraction, and breaks on firmware updates.

## Exercise demos

Every move — training and warm-up — has the dataset's 3D demo: the working muscles painted red,
the start and end positions held with a dissolve between them. The dataset only publishes these
at **180×180**, which looked soft stretched across the demo panel, so `tools/upscale_media.py`
upscales every frame 4× with Real-ESRGAN (`realesr-general-x4v3`, BSD-3) and re-encodes the
loops as **720×720 animated WebP** with the original timing. Android plays those natively.

Next to the exercise name, and larger in the `?` sheet, a **muscle map** lights the worked muscles
on front and back figures — accent for the target, dimmer for the assisting muscles. Polygons from
[react-body-highlighter](https://github.com/GV79/react-body-highlighter) (MIT), drawn natively on a
Canvas.

What was tried and why it isn't here:

- **MuscleWiki** — the look we'd want, but its videos are its own copyrighted content.
- **Full-motion animation.** The free dataset (and ExerciseDB's free tier, byte-for-byte the same
  files) carries only the two key poses per move. The same Gym visual animations exist in full
  motion under a paid AscendAPI / Gym visual licence. AI frame interpolation (FILM) can't invent
  the in-between poses — for a lateral raise it produced both arm positions faded together.
- **Real-person photos** ([free-exercise-db](https://github.com/yuhonas/free-exercise-db), public
  domain) — sharper, but only start/end stills, and less clear than the 3D demos.
- **wger videos** (CC BY-SA 4.0) — real 1080p video, but 46 exercises, mostly barbell and machine;
  about 8 overlap this plan.

## Tests

```bash
./gradlew test
```

Covers the calendar arithmetic behind the rotating blocks and the Monday-start weekly reset —
logic that can't be exercised from the UI without changing the device clock — and pins the
generated plan: a 5–8 minute warm-up on every day, no rest over 60 s, and every demo, thumbnail
and muscle-map region the plan names actually bundled.

## Attribution

Exercise media © **Gym visual**, redistributed via
[hasaneyldrm/exercises-dataset](https://github.com/hasaneyldrm/exercises-dataset) (MIT) and
upscaled for the app. The dataset's NOTICE requires the Gym visual attribution stay intact — it is
shown on the plan screen, on the player's demo panel, and in the how-to sheet.

Muscle-map polygons from [react-body-highlighter](https://github.com/GV79/react-body-highlighter)
(MIT, © 2020 GV79). Fonts: Anton and Archivo (SIL Open Font License).
