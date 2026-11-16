# 0030. P5 decisions

- Status: Accepted
- Date: 2026-11-08
- Phase: P5
- Spec: 9, 10, 12.5

## Context

ADR 0023: choices that are cheap to reverse are made by the agent, each with a one-line reason here.
The direction is ADR 0028 and the UI layer is ADR 0029.

## Decision

- **The screenshot matrix is a half fraction of the 16 cells.** Every screen is shot in light and
  dark, English and Turkish, font scale 1.0 and 2.0, phone and tablet width (SPEC 10.6). Each
  screen gets 8 of the 16 combinations: the tablet column is the parity of the other three. Every
  value of every dimension then shows four times and every pair of values twice. Turkish at font
  2.0 is in it on both widths, which is what the acceptance item on fit needs. 16 cells per screen
  would double the goldens, roughly 40 MB more, and show nothing a pair does not.
- **Secondary states are shot in one cell**: dark, English, font 1.0, phone. Dark is checked first
  (SPEC 10.2). A state that only changes content does not need eight copies.
- **Shots render at hdpi**: phone 384 × 832 dp, tablet 840 × 1100 dp, 1500 dp tall for states that
  run past the first screen. hdpi keeps a golden near 70 KB and still shows a 1 dp line.
- **Goldens live next to their module** in `<module>/screenshots/<screen>/<variant>.png`. A
  change to a screen and the change to its pictures land in the same commit.
- **Shots use the owner's clock**: Europe/Istanbul and a 24-hour clock, with one shot on the
  12-hour clock. A run on another machine renders the same text.
- **Espresso is pinned to 3.7.0 in tests.** Robolectric 4.17 needs it: 3.5.1, which comes in
  transitively, calls `InputManager.getInstance()`, removed in API 36.
- **Robolectric runs with two JDK 21 flags**: `--add-exports java.base/jdk.internal.access` and
  `--add-opens java.base/java.io`. Native graphics opens raw file descriptors, and without them
  every shot fails.
- **`:core:design` knows no domain.** It holds the theme, the strip, its axis, stacked strips and
  the shared parts. Strings that features share, such as duration and percentage formats, are its
  own resources. The graph check forbids it any other project.
- **Units are set in 0.7 em quiet ink after the number**, with a narrow no-break space (U+202F)
  before a letter or `%`. The figure stays the thing the eye lands on, which is the specimen raise
  of ADR 0028.
- **A number never breaks from its unit.** Durations put a no-break space between number and unit
  ("6 h 16 min"), and a word joiner follows each "/" in a rate ("%/h"). At font 2.0 a wrapped
  "%0,40/" over "sa" read as two values.
- **A figure that does not fit steps down to 60 % of its size** (`TextAutoSize.StepBased`) and sits
  on the same line bottom as its neighbours. Clipping a unit hides it; wrapping breaks the row. The
  figure row first balances its rows (8 figures on a narrow phone become 4 + 4, not 3 + 3 + 2).
- **A late arrival is recorded at the moment the owner reports it, against the open report's
  session** (SPEC 7.5). The sheet asks only which app, with "Not sure" allowed; the time comes from
  the clock, because the owner reports it while looking at the late notification.
- **Sensor moments are a typed set** (screen off, maintenance, screen on), not strings. The
  snapshot schedule in SPEC 8.1 has exactly those three points.
- **A route owns its loading and missing states** (`ReportRoute`). It takes a small navigation
  interface of plain callbacks, so the feature still holds no navigation types (ADR 0029).
- **A shot is taken of the settled screen.** Roborazzi copies the window straight after `setContent`.
  The scrollable tab row was caught mid-scroll at a different offset on each run, and sheet shots
  came out blank now and then. `shoot` sets the animator scale to 0, so every Compose animation lands
  on its end in the first frame, and moves Robolectric's simulated clock one second before the copy.
  After that, five verify runs in a row matched. `shoot` also fails on a single-colour image, so a
  blank golden cannot be recorded silently.
- **Turkish puts `%` before the number** ("%62") and English after it ("62%"). These are the two
  languages' rules, kept as string resources rather than in code.
- **`Tag` is a shared part of `:core:design`.** Home's "now" marker and the profile slot tags are
  the same thing: an accent fill for the current item, a hairline outline for a quiet label such as
  Recommended or Custom (ADR 0028).
- **An engine action reports one shared outcome** (`ActionOutcome` in `:core:ui`): done,
  unavailable with a reason, or failed with the raw error. Every feature shows a failed apply the
  same way and never claims it worked (SPEC 0.2, honesty).
- **A custom profile's cost is derived from its levers**, in a fixed order: forced idle, allowlist,
  deep-idle window, sensors. Built-ins keep their written sentences (SPEC 6.1).
- **The editor shows each timing in its own unit** (minutes, seconds, hours or a factor) and stores
  milliseconds. A blank field leaves the key to the phone (ADR 0012). A decimal comma is accepted,
  because the owner types in Turkish.
- **A custom profile no slot uses can be deleted**; Delete is disabled while a slot uses it.
  SPEC 9.1 lists clone and edit; deleting is the undo of a clone (SPEC 0.2: every change undoable).
  Leaving the editor with unsaved changes asks first.
- **The strip's key wraps onto a second line** (`FlowRow`) when it does not fit. At font 2.0 the
  Row squeezed the last entry until "No record" broke one letter per line; two Timeline goldens
  held that and were re-recorded.
- **Profile detail on a narrow screen folds the stock and profile columns into one line** ("Stock:
  30 min · Night: 2 min"). From 360 dp × font scale it is a table. A key the profile leaves alone
  reads "unchanged", and an unknown stock value is left out rather than shown as a dash.
- **Plan changes go to the engine at once**, as a profile's slot buttons do. There is no Save step,
  so the plan on screen is always the one running. An edge that would empty the night window cannot
  be confirmed in the time picker.
- **Recommended shows the three strongest reasons** (SPEC 7.4: two or three). Every signal sits in
  a table under them, with its number or the reason it has none.
  - "Choose something else" records the decline (7.4: remembered, no nagging), then opens Profiles.
  - A night offered by hand (ADR 0032) reads "By hand" in the night row, and the why card no longer
    repeats the note.
- **The log shows a joined night as one row** that opens the night report, under the day it began.
  - Filters stay in the view model and are not saved.
  - "Export a range" picks whole days and hands plain dates to the app, which turns them into its
    own zone's times.
- **Hold-offs are listed, not switchable.** They keep calls, alarms, navigation and music working
  (SPEC 1.2 item 4), so Settings explains them and only the keep-awake apps can change.
  - Turning server flag sync off asks first; turning it back on does not.
  - Restore stock and Reset sit apart at the end, outlined, each behind a confirmation. Reset's
    confirm is in the critical colour.
  - Turkish says "Stok ayarlara dön" for Restore stock.
- **The allowlist shows the system's own entries behind Show**, read-only: only the system changes
  them, and Restore stock leaves them alone.
  - A "keep" mark is the app's own record; it writes nothing to the phone.
  - The intro says allowlisted apps may use the network and hold wake locks in deep idle. It does
    not mention alarms, because their ordinary alarms still wait.
- **One app picker** (`AppPickerScreen` in `:core:ui`) serves the allowlist and the keep-awake list:
  search, with system apps behind a toggle.
- **Sensors names no exempt app when the allow package is the app's own.** ADR 0004 passes it as a
  stand-in that holds no sensors, so the screen says motion is cut for every app. Turning all
  sensors off asks first and says that the camera and microphone stop too.
- **Onboarding keeps its step in `SavedStateHandle`**, so the owner returns to the step they left.
  The six steps are stops on a route line.
  - With no worker, the privilege step lists ADR 0011's ways in its order. The forks are named with
    the month they were checked, and none is endorsed.
  - The two ADB commands are shown as raw lines.
- **Shared parts moved down a layer.** The switch row is now in `:core:design`. The tier wording and
  the quick-proof card are in `:core:ui`, for Settings, Home and onboarding.
- **`shoot` collects garbage before each capture.** Robolectric's hardware PixelCopy now and then read
  an empty frame (every pixel transparent black) although the window was composed, on any screen and
  mostly after a dozen captures in one JVM. The miss followed garbage-collection timing: 26 flat in 260
  captures without the collection, none in 120 with it. It is a mitigation, so the flat check stays.
  Software rendering (`robolectric.pixelCopyRenderMode=software`) would avoid the render thread, but
  drops elevation shadows and every baseline would change; it stays the fallback.
- **The app's sources sit on three shared services** (ADR 0029). Engine access looks the host up
  per command, and an engine that is off answers "unavailable" unless the owner's action means
  "turn it on". The warnings are decided once for every screen:
  - levers the plan wants but the tier cannot write mean access lost;
  - below T2 the app is read-only;
  - a lever read back with another value is partly applied;
  - a drift by a known writer in the last day means another manager;
  - a state file that cannot be saved is an error with its raw line.
- **An engine that is off reads as a paused plan on Home**, and Resume starts it. Applying a plan
  from any screen also takes the engine out of a pause: the owner applying a plan means it to run.
- **The quick proof runs in the app's scope**, not the screen's. Leaving Home must not cut it off
  before its undo step (SPEC 0.2, every change undoable).
- **The navigation bar has Home, Log, Profiles and Settings** (the ADR 0028 study). The allowlist and
  sensors open from Settings' Doze section. "Check" in Settings opens onboarding at its privilege
  step and returns to Settings.
- **The app's own record is its preferences file**: onboarding, the answer to Q, applied plans,
  late reports (with the session and the app), retention, keep-awake apps, the declined
  recommendation and the warnings seen. The engine's choices (re-apply on drift, keep marks) stay
  in its state file. While the engine is off, they are written there for it to start from.
- **Retention is the owner's choice**; the recorder reads it instead of a fixed 30 days.
- **Reset removes the app's records only after Restore stock is confirmed by read-back.** A refusal,
  a stock difference or an unverified result stops it.
- **The licence and notice ship as assets**, and a unit test keeps them byte-for-byte equal to the
  repository's.
- **The Compose activity is the launcher.** Upstream's activity keeps only the Quick Settings tile
  preference until the tiles are replaced (SPEC 12.1).
- **The recommendation is built from the app's own sessions for now.** With none recorded, the
  screens say there is not enough history. The day-zero history from the phone's battery history
  (SPEC 7.2) is still to come.
- **Tasker is found by its public package name.** SPEC 11 grants Tasker the automation permission
  by that name, so naming it in code tells nothing about any one phone.
- **Day zero reads the battery history and UsageStats screen events** (SPEC 7.1), in
  `:core:recommend` (`DayZero`), which now depends on `:core:parse` for the history's records.
  - Sessions run from `-screen` to `+screen`. Each carries its plug state, its deep idle
    (`device_idle=full`) and its battery drop. A reboot or lost records drop the stretch they cut.
  - Screen events reach back before the battery history with the screen alone.
  - The event-log buffer is not read: it covers hours, and M falls back to deep idle that never
    came (SPEC 7.2).
- **Day-zero time counts as stock only when nothing says otherwise.**
  - At the read: no constants string, idle not forced, sensors normal.
  - No unplugged session may reach deep idle sooner than `inactive_to` + `idle_after_inactive_to`
    minus a minute; only forced idle does that. Battery Saver's quick doze also trips the check.
  - A wrong "unknown" only leaves D and R unmeasured. A wrong "stock" would mislead them, and the
    owner's own setup (a constants string) is exactly that case.
- **The recommendation's range starts where the records start**, capped at 30 days. Before, a
  fixed 30-day range spread two days of records over thirty: S read low, and a single session had
  a 30-day span for confidence.
- **The durable grants are given through the worker as soon as Shizuku or Sui allows it** (SPEC
  4.1 T1): `DUMP`, `WRITE_SECURE_SETTINGS` (lever A writes from the app, ADR 0012) and usage
  access. `READ_LOGS` stays with the worker, which reads the events buffer anyway, and the exact
  alarm op is only for the optional delay alarm.
- **Tiles and notifications** (SPEC 9.2, 11.2).
  - "Doze plan" pauses and resumes through the engine. With no plan yet, it opens Plan.
  - "Sensors off" sets the all-sensors switch through the worker. Turning it on asks for the same
    confirmation as the Sensors screen. Below T2 the tile is unavailable.
  - Both tiles run in active mode and refresh when the engine status or the switch changes. A long
    press opens Plan or Sensors.
  - The engine notification names the profile in force in one line, at low importance.
  - Night reports have their own low-importance channel. A report is posted once per new night, at
    the first unlock, and opens the night's report.
  - The notification permission is asked once, after onboarding.
  - Upstream's tile classes are unregistered and wait for the upstream code's removal (SPEC 12.1);
    their resources show as unused in lint until then.
- **Server flag sync through two typed worker calls** (contract VERSION 4).
  - The calls are `setFlagSync(mode)` and `getFlagSync()`, verified at android-17.0.0_r1. Shell
    holds `READ_WRITE_SYNC_DISABLED_MODE_CONFIG`, which `setSyncDisabledModeConfig` accepts.
  - The switch sets `persistent`, so the owner's choice survives a reboot, and is read back.
  - The app remembers that it stopped sync. Restore stock (and so Reset) lets sync run again, so
    the change stays undoable.
- **Alarm and messaging suggestions use public calls only**: the next alarm clock's creator, the default SMS app, and social and communication apps by last week's foreground time (usage access), three at most.
- **`impeccable detect` reads web files only.**
  - On a scratch HTML page with gradient text it reported 3 findings. On the same pattern in Compose
    Kotlin, and on every Kotlin and resource directory of the app, it reported nothing: it reads
    nothing there. Its "zero findings" at the P5 gate is therefore met without testing anything.
  - The design checks that do apply are the critique and audit passes over the screenshot set, and
    the P0 and P1 findings fixed. This is recorded so the gate is not read as more than it is.
- **P5 critique and audit (2026-11-16): no P0; five P1s fixed.**
  - Home puts the warnings straight under the plan board. The board says when the plan is only
    partly in force (privilege lost, a key that did not take) or not in force at all (read-only);
    then "now" is drawn unaccented. Quick proof names why it is unavailable without access.
  - A lever a profile does not use is struck through in `quiet`, unboxed. The hairline outline it had
    measured 1.5:1. A lever in use keeps its ink box (`Sizes.mark`, `Sizes.markStroke`).
  - Probe names and the store error moved to `:core:ui`. The goldens now use the strings the app
    shows, so the Turkish shots are Turkish. Probe details are set as raw lines.
  - Captures at font scale 2.0 are twice as tall, so what would scroll is in the picture.
  - The variant matrix stays an orthogonal array of 8 (tablet = dark xor Turkish xor large font).
    Every pair of factors appears, so dark-tr-font200 is shot on a tablet and light-tr-font200 on
    a phone. Turkish at font 2.0 on a phone is covered by the light variant.
- **P5 audit, P2 and P3 fixed in the same pass:**
  - Yellow is kept for the primary action and the item running now (One Signal):
    - the confidence tag, the profile slot tags and the allowlist "keep" chip are quiet now;
    - a kept entry shows a check on a raised chip.
  - Every primary action is the accent-filled tonal button, as on Home. Onboarding and Recommended
    used an ink pill before.
  - A slot a profile already runs in is a quiet tag, not a disabled button. Recommended drops its
    Apply button when the plan is already in use, since the tag above says so.
  - TalkBack hears a notice's tone, and "new" until it is seen.
  - The top bar's title lines up with the reading column on wide screens.
  - "Plugged in" no longer breaks before "in".
- **Built-in profile names stay in English in both languages.** Everyday, Night, Pocket, Strict and
  Stock are names, as in a Tasker action or a log line. Translating them would make one profile read
  differently in the UI, the automation API and the exported logs. Custom profiles keep the owner's
  own names.
- **Lever names keep their capital inside Turkish sentences** ("Bunlar artık yazılamıyor: Zorunlu
  derin uyku, …"). They are labels, the same as in Settings and the report.
- **Recommended shows signal names, not the spec's letters.** S, M, N, D, R and Q are the spec's
  working names; the UI already words each signal, so the letter column went (SPEC 10.4).
- **Profile detail keeps constant keys in monospace.** `inactive_to` and the rest are raw tokens
  from the device's dump, which is what DESIGN.md keeps the monospace face for.
