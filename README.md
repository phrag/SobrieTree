# BrewLog

**Drink less. Grow something. Keep it private.**

BrewLog is an offline-first Android companion for drinking less. It turns cutting back into a calm daily practice — you log what you drink, watch alcohol-free days grow a tree (and real health gains), and see honest progress against your own goal. No account, no cloud, no ads, and — unless you explicitly opt in to update checks — no internet at all.

Three ideas run through the whole app:

- **Private** — your drinking data never leaves your phone.
- **Growing** — every alcohol-free day grows a tree, a streak, and your body's recovery.
- **Drinking less** — measured gently against your own baseline, and never with shame.

---

## 🌱 Growing, not grading

BrewLog rewards the days you skip and never punishes the days you don't.

- **Growth Ring home screen** — today's drinks form a soft ring around a seedling. On an alcohol-free day the ring fills and the plant grows.
- **A forest you collect** — the seedling matures into trees you keep. In your first month you earn a tree every week (four smaller trees); after that, a larger tree for each alcohol-free month. Your forest is a picture of how far you've come.
- **Latest recovery stage** — the Home screen shows the most recent milestone your body has reached (better sleep, steadier blood pressure, liver recovery…), drawn from an evidence-based recovery timeline that runs from your first dry day out to a year.
- **Forgiving streaks** — you earn a shield every seven alcohol-free days, and a shield quietly bridges a single slip so one off day never erases weeks of progress.
- **Badges** for alcohol-free totals, streak lengths, and money saved. Cumulative counts only ever move forward.

## 📉 Drinking less, measured honestly

- **At-a-glance tiles on Home** — alcohol-free days this week, your reduction versus your usual, and the money and calories you haven't spent (completed days only, so numbers never appear out of thin air).
- **One-tap logging** — tap a saved drink to log it instantly, or log a custom one. Logging and *managing* your saved drinks are kept firmly separate, so you never log a drink by accident while editing.
- **Flexible goals** — set a daily amount, or a weekly amount if you don't drink every day.
- **Progress charts** of your real pattern against baseline and goal (7 days / 4 weeks / 3 months).
- **Calendar** to review and edit past days, and a **journey start date** you choose.

## 🤝 Support when it's hard

- **Craving-time nudges** — tell BrewLog when you usually start drinking and it sends a supportive, local-only notification just before, strongest in your first days and easing as new habits settle.
- **Shame-free encouragement** — a quiet evening is celebrated; a heavy day gets a plan, not a scolding. The tone adapts to *your* reasons for cutting back.
- **Optional daily check-in** reminder (off by default).

## 🔒 Private by design

- **No account, no sign-in, no cloud.** All data lives in the app's private storage; uninstalling wipes it.
- **No analytics, trackers, ads, or third-party SDKs.**
- **No location, contacts, camera, or microphone** permissions.
- **Offline by default.** The *only* feature that can touch the network is the optional update check below — it is off unless you turn it on, and when on it talks to nobody but GitHub over HTTPS.
- **Prevent-screenshots** toggle for extra discretion.
- **Your data, your call** — export or import everything as CSV, or wipe it with **Delete All Data**.

## 🔄 Optional auto-updates (opt-in)

Because BrewLog installs outside app stores, Settings has an **opt-in** updater:

- **Off by default.** Turn it on in **Settings → App updates**.
- **Two channels** — **Stable releases** or **Latest builds** (the newest CI build).
- BrewLog checks the public GitHub Releases for a newer version, at most once a day, and offers to download and install it. **You always confirm the install** in the system dialog — nothing installs silently.

This is the one feature that requires the `INTERNET` permission; with updates off, the app makes no network requests whatsoever.

---

## Install

The latest build is published publicly on every green CI run — a direct APK download, not a login-gated artifact:

**→ [`BrewLog-latest.apk`](https://github.com/phrag/BrewLog/releases/download/latest/BrewLog-latest.apk)** &nbsp;·&nbsp; [all releases](https://github.com/phrag/BrewLog/releases)

You may need to allow "Install unknown apps" for your browser or file manager. Once installed, you can let the app keep itself updated via the opt-in updater above.

## Architecture

BrewLog is native Android (Kotlin + XML Views + Material 3) on top of a small Rust core.

- **UI** — Activities with a five-tab bottom navigation (Home, Progress, Journey, Calendar, Settings). Custom canvas views render the growth ring, trees, and forest.
- **Engine** (`com.brewlog.android.engine`) — a pure-Kotlin, JVM-testable layer with no Android dependencies; all gamification is derived here or stored in `SharedPreferences`:
  - `DayLedger` (completed vs. in-progress days), `StreakEngine` (streaks, shields, tree/forest growth), `SavingsEngine` (money & calories vs. baseline, completed days only), `MetricsEngine`, `BadgeCatalog`, `EncouragementEngine`, `HealthTimeline`, `EducationLibrary`, `HighRiskSupport`.
  - `GamificationManager` composes these into the state the UI binds to.
- **Native core** (`rust/src/lib.rs`) — the data store, exposed to Kotlin over JNI and backed by SQLite on-device.

The engine takes plain data in and returns plain results, so its behaviour is covered by fast JVM unit tests that need no device or emulator.

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

The debug APK lands at `android/app/build/outputs/apk/debug/BrewLog-<version>.apk`. Debug builds are signed with the release key so they install as an upgrade over release builds.

### Tests

```bash
cd android && ./gradlew testDebugUnitTest   # Android unit tests (incl. the engine layer)
cd rust    && cargo test --all              # Rust core tests
```

### Continuous integration

Every push runs `.github/workflows/ci.yml` on GitHub Actions, which builds the Rust core with `cargo-ndk`, assembles the debug APK, runs the Android and Rust tests, checks Rust formatting/clippy and Android Lint, uploads the `BrewLog-apk` artifact, and publishes the APK to the rolling **`latest`** public release.

## Project structure

```
brewlog/
├── android/
│   └── app/src/main/java/com/brewlog/android/
│       ├── MainActivity.kt          # Home: growth ring, tiles, recovery stage, logging
│       ├── ProgressActivity.kt      # Progress trend chart
│       ├── JourneyActivity.kt       # Recovery timeline, badges, savings, reads
│       ├── CalendarActivity.kt      # Review and edit past days
│       ├── SettingsActivity.kt      # Goals, currency, reminders, privacy, updates
│       ├── UpdateChecker.kt         # Opt-in GitHub release check (the only network code)
│       ├── UpdateInstaller.kt       # Downloads and hands the APK to the system installer
│       ├── GamificationManager.kt   # Composes engine state for the UI
│       └── engine/                  # Pure-Kotlin, JVM-tested logic
├── rust/
│   ├── src/lib.rs                   # JNI core API over SQLite
│   └── Cargo.toml
├── .github/workflows/ci.yml         # Build + test + lint + APK artifact + latest release
└── README.md
```

## License

See the repository for license details.
