# Forge — Home Training

A native Android workout app implementing the `Home Workout App.dc.html` Claude Design prototype.
Four-day split, dumbbells 2–24 kg, walkpad and a mat. Everything runs offline: the plan, all 66
exercise animations and the form instructions are bundled in the APK.

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
body — each with a strength block and a 40/20 HIIT finisher. 66 distinct exercises in total,
selected from the dataset to need nothing but dumbbells, a mat, a chair and a walkpad. Block B is
entirely body-weight apart from two light-dumbbell delt raises.

`plan.json` is generated from the dataset rather than hand-written; the generator lives at
`tools/genplan.py`.

## Install

The signed APK is at `app/build/outputs/apk/release/app-release.apk` (a copy sits at `Forge.apk`
in the project root).

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
| **Day** | Session overview — target muscles, exercise list with demo thumbnails, strength block and HIIT finisher. Tap any exercise to jump straight to it. |
| **Player** | Animated demo, set dots, weight stepper, rep logging (tap-count or auto-tempo) or a 40/20 countdown, rest overlay, and `?` for the dataset's step-by-step form cues. |
| **Done** | Session summary, then back to the week. |

## Architecture

```
tools/genplan.py            regenerates assets/plan.json from the exercises dataset
app/src/main/
  assets/plan.json          4 rotating blocks × 4 days
  assets/media/             66 exercise GIFs + 180×180 thumbnails
  java/com/forge/workout/
    WorkoutViewModel.kt     session state machine + deadline-based timers
    data/Plan.kt            program models, JSON loading
    data/Store.kt           DataStore persistence, week/streak derivation
    ui/Theme.kt             palette + Anton/Archivo type scale
    ui/Media.kt             GIF + thumbnail loading from assets
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
- **Rest** uses each exercise's own rest from the dataset (90 s on heavy presses, 45 s on calves,
  20 s in HIIT). The prototype's prop default collapsed every strength rest to 75 s.
- **Timers are deadline-based** (`elapsedRealtime`), not per-tick decrements, so a backgrounded or
  throttled process cannot silently lose seconds mid-set.

Added for daily use: the screen stays awake in the player, and the phone buzzes when a set ends,
a rest finishes, and a session completes.

## Display and media resolution

Every part of the UI that Forge draws — type, layout, icons, timer rings — is vector and scales to
any density, so it renders natively at the S25 Ultra's 1440×3120. The one raster asset is the
exercise media, and **the dataset only publishes it at 180×180**; there is no higher-resolution
source. Those frames are upscaled to fill the demo panel and look correspondingly soft. Swapping in
sharper media would mean sourcing it elsewhere — nothing in the app caps it.

## Tests

```bash
./gradlew test
```

Covers the calendar arithmetic behind the rotating blocks and the Monday-start weekly reset —
logic that can't be exercised from the UI without changing the device clock.

## Attribution

Exercise media © **Gym visual**, redistributed via
[hasaneyldrm/exercises-dataset](https://github.com/hasaneyldrm/exercises-dataset) (MIT). The dataset's
NOTICE requires the Gym visual attribution stay intact — it is shown on the plan screen, on the
player's demo panel, and in the how-to sheet. Fonts: Anton and Archivo (SIL Open Font License).
