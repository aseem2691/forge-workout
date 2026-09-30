# Forge — Home Training

A native Android workout app implementing the `Home Workout App.dc.html` Claude Design prototype.
Four-day split, dumbbells 2–24 kg, walkpad and a mat. Everything runs offline: the plan, the
exercise demos (real-person photos and 3D animations), the muscle map and the form instructions
are bundled in the APK.

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

`plan.json` is generated from the datasets rather than hand-written; the generator lives at
`tools/genplan.py`.

### Warm-up

Every session starts with about 7 minutes of timed moves that run hands-free — a 40 s move, a
10 s switch-over, the next move — ending in a 20 s hand-off to set up the first set:

| Day | Warm-up |
| --- | --- |
| Mon upper, Thu push·pull | walkpad brisk walk 2:00 · arm circles · scapula push-up · cat-cow · inchworm · dynamic chest stretch · world's greatest stretch 1:00 |
| Tue lower | walkpad brisk walk 2:00 · standing hip circles · bodyweight squat · glute bridge · forward lunge · world's greatest stretch 1:00 · jumping jacks 0:30 |
| Sat full body | walkpad brisk walk 2:00 · arm circles · standing hip circles · inchworm · bodyweight squat · world's greatest stretch 1:00 · jumping jacks 0:30 |

Warm-up moves are shown in amber, each with a one-line coaching cue, and never count as training
sets. To skip it on a short day, tap the first strength exercise on the day screen.

### Rest

No break runs longer than **60 s** — between sets and between exercises. Rests the program
designed longer (75–90 s on the heavy presses and squats) are capped at a minute; shorter ones
(45 s on calves) are unchanged, and the HIIT finisher keeps its 40/20 interval. **+15 s** on the
rest screen still buys time when a set needs it. The session lengths shown account for both the
shorter rests and the warm-up.

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
| **Player** | Real-person demo (tap for the 3D model), muscle map, set dots, weight stepper, rep logging (tap-count or auto-tempo) or a countdown, rest overlay, and `?` for step-by-step form cues. |
| **Done** | Session summary, then back to the week. |

## Architecture

```
tools/genplan.py            regenerates assets/plan.json from both exercise datasets
tools/genbodymap.py         converts the body-map polygons into assets/bodymap.json
app/src/main/
  assets/plan.json          4 rotating blocks × 4 days
  assets/bodymap.json       front/back muscle polygons
  assets/media/             3D GIFs + 180×180 thumbnails, fe_*.jpg photo frames
  java/com/forge/workout/
    WorkoutViewModel.kt     session state machine + deadline-based timers
    data/Plan.kt            program models, JSON loading
    data/Store.kt           DataStore persistence, week/streak derivation
    ui/Theme.kt             palette + Anton/Archivo type scale
    ui/Media.kt             GIF, photo-loop and thumbnail loading from assets
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

MuscleWiki's videos are its own copyrighted content and can't be bundled, so the demos come from
openly licensed sources instead, in the same spirit — a real person doing the move, plus a map of
the muscles it works:

- **Real-person photos** from [free-exercise-db](https://github.com/yuhonas/free-exercise-db)
  (public domain / Unlicense): the start and end position of each movement at 850×567, looped as
  one rep — hold, ease into the end position, hold, ease back. 43 of the 66 training exercises
  and most warm-up moves have a pair, each checked by eye against the 3D demo; `PHOTOS` in
  `tools/genplan.py` is that list. Moves with no faithful match (burpees, skater hops, towel rows,
  curtsey squats…) keep the 3D animation rather than show a different exercise.
- **3D animations** from the exercises-dataset (Gym visual), with the working muscles painted
  red. Tap the demo panel to switch between photo and 3D; the choice is remembered.
- **Muscle map** — front and back figures with the worked muscles lit in the accent and the
  assisting ones dimmer, beside the exercise name and larger in the `?` sheet. Polygons from
  [react-body-highlighter](https://github.com/GV79/react-body-highlighter) (MIT), drawn
  natively on a Canvas.

Everything Forge draws is vector and renders natively at the S25 Ultra's 1440×3120. The 3D GIFs
are published at 180×180 only and look soft upscaled; the photos are 850×567 and hold up.

## Tests

```bash
./gradlew test
```

Covers the calendar arithmetic behind the rotating blocks and the Monday-start weekly reset —
logic that can't be exercised from the UI without changing the device clock — and pins the
generated plan: a 5–8 minute warm-up on every day, no rest over 60 s, and every media file and
muscle-map region the plan names actually bundled.

## Attribution

3D exercise media © **Gym visual**, redistributed via
[hasaneyldrm/exercises-dataset](https://github.com/hasaneyldrm/exercises-dataset) (MIT). The dataset's
NOTICE requires the Gym visual attribution stay intact — it is shown on the plan screen, on the
player's demo panel, and in the how-to sheet.

Exercise photos and some warm-up instructions from
[free-exercise-db](https://github.com/yuhonas/free-exercise-db), dedicated to the public domain
(Unlicense) by its maintainers. The dataset doesn't document where the photos were originally
shot, so check their provenance before any distribution beyond personal use.

Muscle-map polygons from [react-body-highlighter](https://github.com/GV79/react-body-highlighter)
(MIT, © 2020 GV79). Fonts: Anton and Archivo (SIL Open Font License).
