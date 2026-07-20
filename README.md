# BrewLog

A private, offline alcohol-reduction companion for Android. Drink less, notice more, and watch something grow as you go.

BrewLog turns cutting back into a calm, encouraging daily practice instead of a scoreboard of failures. It tracks what you drink, compares it against your own goal and baseline, and rewards the days you skip — never punishing the days you don't. Everything runs on-device: no account, no cloud, no analytics, no ads.

## What it does

### A home screen that grows with you
- **Growth Ring** — today's drinks shown as a soft ring. On an alcohol-free day the ring fills and a seedling at its centre keeps growing.
- **A forest you collect** — the seedling matures into trees you keep. In your first month you earn a tree every week (four smaller trees), then a larger tree for each alcohol-free month after that. Your forest is a running picture of how far you've come.
- **Stat tiles** — money saved, calories avoided, and your next badge, always a tap away.

### Support when you need it
- **Craving-time nudges** — tell BrewLog when you usually start drinking and it sends a supportive, local-only nudge just before, strongest in your first days and easing as new habits settle.
- **Shame-free encouragement** — a quiet evening is celebrated; a heavy day gets a plan, not a scolding. Copy adapts to your reasons for cutting back.

### Gamification that only ever moves forward
- **Cumulative alcohol-free days** that never regress.
- **Forgiving streaks** — an earned shield bridges a single lapse so one slip doesn't erase weeks of progress.
- **Badges** for alcohol-free milestones, streak lengths, and money saved.

### Your journey, explained
- **Recovery timeline** — evidence-based milestones showing what changes in your body from your first alcohol-free day out to a year.
- **Money & calorie savings** — set a per-drink cost (or a default price) and BrewLog totals what you haven't spent and haven't consumed, counting only completed days so the numbers are honest.
- **Short reads** — bite-sized, evidence-based notes on the benefits of drinking less.

### Day-to-day tracking
- **Quick Add chips** and a one-tap favorite drink, plus a **Drinks manager** for presets (size, strength, and cost).
- **Calendar** view to review and edit past days.
- **Progress charts** of your real pattern versus baseline and goal (7 days / 4 weeks / 3 months).
- **Flexible goals** — enter a daily amount, or a weekly amount if you don't drink every day.
- **Journey start date** you can set yourself.
- **Configurable currency** and an end-of-day cut-off (default 3 AM) so late nights land on the right day.

### Private by design
- No account or sign-in; no contacts, location, camera, or microphone permissions.
- Network-free: the app makes no internet requests. All data stays in the app's private storage.
- No analytics, trackers, ads, or third-party SDKs.
- Optional **prevent-screenshots** toggle for extra discretion.
- Export/import your data as CSV, or wipe everything with **Delete All Data**.
- Optional local-only daily check-in reminder (off by default).

## Architecture

BrewLog is native Android (Kotlin + XML Views + Material 3) on top of a Rust core.

- **UI** — Activities with a five-tab bottom navigation (Home, Progress, Journey, Calendar, Settings). Custom canvas views render the growth ring, trees, and forest.
- **Engine** (`com.brewlog.android.engine`) — a pure-Kotlin, JVM-testable layer with no Android dependencies. All gamification is derived here or stored in `SharedPreferences`:
  - `DayLedger` — completed vs. in-progress days and the tracking window.
  - `StreakEngine` — streaks, shields, and tree/forest growth.
  - `SavingsEngine` — money and calories not consumed vs. baseline (completed days only).
  - `MetricsEngine`, `BadgeCatalog`, `EncouragementEngine`, `HealthTimeline`, `EducationLibrary`, `HighRiskSupport`.
  - `GamificationManager` composes these into the state the UI binds to.
- **Native core** (`rust/src/lib.rs`) — the data store, exposed to Kotlin over JNI and backed by SQLite on-device.

The engine layer takes plain data in and returns plain results, so its behaviour is covered by fast JVM unit tests that need no device or emulator.

## Build

Requirements: JDK 17+, the Android SDK (with NDK + CMake), the Rust toolchain, and `cargo-ndk` (`cargo install cargo-ndk`).

```bash
# 1. Build the Rust core into jniLibs
cd rust
cargo ndk -t arm64-v8a -o ../android/app/src/main/jniLibs build --release

# 2. Build the Android app
cd ../android
./gradlew assembleDebug
```

The debug APK is written to `android/app/build/outputs/apk/debug/BrewLog-<version>.apk`. Debug builds are signed with the release key so they install as an upgrade over release builds.

### Tests

```bash
# Android unit tests (includes the engine layer)
cd android && ./gradlew testDebugUnitTest

# Rust core tests
cd rust && cargo test --all
```

### Continuous integration

Every push runs `.github/workflows/ci.yml` on GitHub Actions, which:
1. builds the Rust core with `cargo-ndk`,
2. assembles the debug APK,
3. runs the Android unit tests and the Rust tests,
4. checks Rust formatting/clippy and runs Android Lint, and
5. uploads the APK as the `BrewLog-apk` artifact.

## Project structure

```
brewlog/
├── android/
│   └── app/src/main/java/com/brewlog/android/
│       ├── MainActivity.kt            # Home: growth ring, quick add, recent entries
│       ├── ProgressActivity.kt        # Progress charts
│       ├── JourneyActivity.kt         # Timeline, badges, savings, reads
│       ├── CalendarActivity.kt        # Review and edit past days
│       ├── SettingsActivity.kt        # Goals, currency, reminders, privacy
│       ├── GamificationManager.kt     # Composes engine state for the UI
│       └── engine/                    # Pure-Kotlin, JVM-tested logic
├── rust/
│   ├── src/lib.rs                     # JNI core API over SQLite
│   └── Cargo.toml
├── .github/workflows/ci.yml           # Build + test + lint + APK artifact
└── README.md
```

## License

See the repository for license details.
