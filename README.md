# 🌳 TreeTotal

**Drink less. Grow a forest. Tell no one.**

TreeTotal is an Android app for cutting back on alcohol. It keeps a calm, honest record of what you drink, turns the days you skip into a forest you can see growing, and explains what those days are doing for your body — using guidance from the WHO and NHS rather than vibes.

It runs entirely on your phone. No account. No cloud. No analytics. No ads. It doesn't even hold the permission needed to reach the internet.

---

## What it's like to use

### The ring empties, it doesn't fill

Home is a single ring around a young tree. The ring holds **today's allowance**: it starts full and **retracts each time you log a drink**. An untouched day is the fullest ring — so the visual rewards the absence of drinking rather than the act of logging it.

As the ring runs low it warms from green toward soft amber. Go past your goal and it simply empties, leaving one small amber tick. Nothing ever turns red, and nothing ever scolds you.

### Alcohol-free days grow trees you keep

Skip a day and the seedling at the centre grows. In your first month a tree completes **every 7 alcohol-free days** — four quicker wins when motivation is most fragile — and after that each **30 alcohol-free days** grows a larger tree. Finished trees join the forest on your Journey tab and never disappear.

Growth is keyed to your *cumulative* alcohol-free days, so a slip pauses a tree but never shrinks it.

### Streaks that forgive

Every 7 alcohol-free days earns you a **shield**. If you have a shield, a single off day is quietly bridged and your streak survives. One bad evening shouldn't erase three good weeks, so it doesn't.

### Your body's recovery, in plain language

Home shows the **latest recovery stage** you've reached — better sleep, steadier blood pressure, liver repair — pulled from a timeline that runs from your first dry day out to a year. The Journey tab has the full list plus short, cited reads on sleep, hangxiety, calories, cravings, and how to decline a drink without a speech.

Health claims are attributed to real authorities (WHO, IARC, NHS / UK Chief Medical Officers, NIAAA), not to the app.

### Honest numbers, not flattering ones

- **Alcohol-free days this week**, and your **reduction against your own baseline**.
- **Money kept** — you tell TreeTotal roughly what you *used* to spend per week, and it subtracts what you've actually logged spending. Anchoring to a figure you already know beats guessing a per-drink price when a pub round and a beer at home cost wildly different amounts.
- **Calories avoided**, with a rough burger equivalent.

Only **completed** days count toward savings, so numbers never appear out of thin air at midnight.

### Support at the hard hour

Tell TreeTotal when you usually start drinking and it sends one supportive, local-only nudge just before — strongest in your first days, easing as the habit settles. The optional daily check-in notification includes a **"Still alcohol-free 🎉"** button that logs the win and celebrates it without opening the app.

### Logging that stays out of your way

Tap a saved drink to log it in one touch, or log a custom one. Managing your saved drinks is a **separate** screen from logging them, so you can never record a drink by accident while editing your list. There's a calendar for fixing past days, flexible goals (daily *or* weekly), a journey start date you choose, configurable currency, and an end-of-day cut-off so late nights land on the right day.

---

## 🔒 Privacy

Privacy here is structural, not a promise in a settings screen.

- **The app cannot reach the network.** It does not hold the `INTERNET` permission — the manifest explicitly strips it, along with network-state, install-packages, Bluetooth, nearby-devices and location. No request can be made, by TreeTotal or by any library inside it.
- **The only permission it holds is notifications**, and only if you turn reminders on.
- **No account, no sign-in, no cloud, no analytics, no trackers, no ads, no third-party SDKs.**
- **Everything lives in the app's private storage.** Uninstalling erases it.
- **Prevent-screenshots** toggle for extra discretion.
- **Export or import everything as CSV**, or wipe it with Delete All Data.

### Updating

Because the app has no network access, it can't update itself. **Settings → App updates → View latest release** opens the releases page in *your browser*, where you download and install the new build yourself. It's a manual step, deliberately: it's what lets the app hold zero network permissions.

---

## Install

Grab the APK from the [releases page](https://github.com/phrag/BrewLog/releases) — CI publishes a rolling `latest` release with `TreeTotal-latest.apk` (plus a version-stamped copy) on every green build.

You'll need to allow "Install unknown apps" for your browser or file manager.

> TreeTotal is a fresh app identity (`com.treetotal.android`). If you used the earlier BrewLog builds, this installs alongside rather than upgrading them.

---

## Architecture

Native Android — Kotlin, XML views, Material 3 — over a small Rust core.

**UI** — five tabs (Home, Progress, Journey, Calendar, Settings). The ring, trees, and forest are custom `Canvas` views drawn procedurally; there are no image assets to keep in sync.

**Engine** (`com.treetotal.android.engine`) — pure Kotlin, zero Android imports, so it runs under plain JVM unit tests with no device or emulator:

| Component | Responsibility |
|---|---|
| `DayLedger` | Completed vs. in-progress days, tracking window, end-of-day cut-off |
| `StreakEngine` | Streaks, shields, tree and forest growth |
| `SavingsEngine` | Money and calories vs. baseline, completed days only |
| `MetricsEngine` | Daily/weekly ratios, reduction against baseline |
| `BadgeCatalog` · `EncouragementEngine` | Badges, and copy that adapts to your motivations |
| `HealthTimeline` · `EducationLibrary` | Recovery milestones and cited reads |
| `HighRiskSupport` | Craving-window intensity and messaging |

`GamificationManager` composes these into the single state object the UI binds to. Gamification state is derived or kept in `SharedPreferences`; the Rust core owns the entries.

**Native core** (`rust/src/lib.rs`) — SQLite storage exposed to Kotlin over JNI.

---

## Build

Requires JDK 17+, the Android SDK (NDK + CMake), the Rust toolchain, and `cargo-ndk`.

```bash
# 1. Rust core → jniLibs
cd rust
cargo ndk -t arm64-v8a -o ../android/app/src/main/jniLibs build --release

# 2. Android app
cd ../android
./gradlew assembleDebug
```

Output: `android/app/build/outputs/apk/debug/TreeTotal-<version>.apk`.

### Tests

```bash
cd android && ./gradlew testDebugUnitTest   # includes the engine layer
cd rust    && cargo test --all
```

### Signing

No keystore or password lives in this repository. Signing config is read from **either** a gitignored `android/keystore.properties` **or** `SIGNING_*` environment variables (CI secrets):

```properties
storeFile=/absolute/path/to/treetotal-release.jks
storePassword=…
keyAlias=treetotal
keyPassword=…
```

With no key configured the build falls back to the debug key and CI skips publishing, so an unsigned build can never reach the releases page.

### Continuous integration

`.github/workflows/ci.yml` runs on every push: builds the Rust core with `cargo-ndk`, assembles the APK, runs the Android and Rust test suites, checks `cargo fmt` / `clippy` and Android Lint, uploads the APK artifact, and — when signing secrets are present — publishes the rolling `latest` release.

---

## Project structure

```
├── android/app/src/main/java/com/treetotal/android/
│   ├── MainActivity.kt          # Home: ring, tiles, recovery stage, logging
│   ├── ProgressActivity.kt      # Trend chart vs. baseline and goal
│   ├── JourneyActivity.kt       # Forest, recovery timeline, badges, savings, reads
│   ├── CalendarActivity.kt      # Review and edit past days
│   ├── SettingsActivity.kt      # Goals, spend, currency, reminders, privacy
│   ├── GrowthRingView.kt        # The depleting ring
│   ├── TreePainter.kt / ForestView.kt
│   ├── GamificationManager.kt   # Engine state → UI state
│   └── engine/                  # Pure-Kotlin, JVM-tested logic
├── rust/src/lib.rs              # JNI + SQLite core
└── .github/workflows/ci.yml
```

---

## A note on scope

TreeTotal is a tracking and motivation tool, not treatment. It follows the WHO position that no level of alcohol is risk-free and the NHS low-risk guideline of no more than 14 units a week spread over three or more days. If alcohol is causing you harm, or you experience physical withdrawal, please talk to a doctor — stopping suddenly can be dangerous without support.

## License

See the repository for license details.
