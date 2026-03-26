# BrewLog

A private companion app for reducing or stopping alcohol consumption.

**Your journey, your pace.** BrewLog helps you understand and reduce your alcohol intake with evidence-based guidance and honest progress tracking.

## Features

- **Goal modes:** Choose to reduce drinking or stop completely
- **Progress visualization:** See your remaining daily allowance at a glance
- **Honest tracking:** Real data only, no fabricated charts or misleading visuals
- **Evidence-based info:** Health information from WHO, NHS, and CDC (bundled, offline)
- **Calendar view:** Track, review, and edit past days
- **Bar charts:** Clear weekly/monthly trends with goal and baseline markers
- **One-tap logging:** Quick Add chips for common drinks
- **Export/import:** Back up your data as CSV; delete all data from settings
- Native Rust + SQLite backend via JNI for on-device storage
- Works fully offline. No account, no cloud, no analytics, no ads

### Privacy and Security

BrewLog is privacy-focused by design:
- No account or sign-in
- No dangerous/runtime permissions requested (no contacts, location, camera, mic)
- Network-free by default: the app does not make any internet requests
- All data stays on your device; nothing is uploaded or shared
- No analytics, trackers, ads, or third-party SDKs
- Works completely offline; no cloud services are required

Your data, your control:
- Entries live in the app's private storage; uninstalling the app or clearing its data removes everything
- Open-source codebase - audit how data is handled
- Battery-friendly: no background sync, polling, or push connections

## Quick Start

1) Install the app
- Download `BrewLog-0.0.3.apk` and install on your phone
- You may need to enable "Install unknown apps" in Android settings

2) First-time setup
- Choose your goal: reduce drinking or stop completely
- Set your daily/weekly limits and your current baseline
- Add your typical drinks for quick logging

3) Track your intake
- Tap a Quick Add chip to log a drink
- The glass shows your remaining daily allowance

4) View Progress
- Bottom navigation -> Progress
- See your weekly bar chart with goal and baseline markers

5) Learn more
- Settings -> About Alcohol and Health
- Evidence-based information from WHO, NHS, and CDC

### Screenshots

![Home](screenshots/home.png)

---

## Developer Guide

### Requirements
- JDK 17+
- Android Studio (SDK + NDK + CMake)
- Rust toolchain
- cargo-ndk (`cargo install cargo-ndk`)

### One-command build
From the repository root:
```bash
./build.sh              # builds Rust with cargo-ndk, builds the APK, copies it to ./BrewLog-0.0.3.apk
PRUNE=1 ./build.sh      # optional: also cargo clean + prune stale jniLibs
```
Notes:
- The script prefers cargo-ndk; if absent it falls back to plain cargo (requires NDK toolchains on PATH)
- Output APK: `BrewLog-0.0.3.apk` at the repo root

### Manual build (if you prefer)
```bash
# macOS defaults
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/26.1.10909125"
# add toolchains bin to PATH (use aarch64 or x86_64 prebuilt dir, depending on host)
if [ -d "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-aarch64/bin" ]; then
  export PATH="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-aarch64/bin:$PATH"
else
  export PATH="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin:$PATH"
fi

# Build Rust core into jniLibs
cd rust
cargo ndk -t arm64-v8a -o ../android/app/src/main/jniLibs build --release

# Build Android app
cd ../android
./gradlew assembleDebug
```

### Release build
```bash
cd android
./gradlew assembleRelease
open app/build/outputs/apk/release
```
(The output name is configured in the module's `build.gradle`.)

### Project Structure
```
brewlog/
├── android/                    # Android app (Kotlin)
│   ├── app/src/main/java/com/brewlog/android/
│   │   ├── MainActivity.kt     # Home + progress glass + Quick Add
│   │   ├── ProgressActivity.kt # Bar charts and progress metrics
│   │   ├── AboutHealthActivity.kt # WHO/NHS/CDC health information
│   │   └── BrewLog.kt          # In-memory model and metrics
│   └── app/src/main/jniLibs/   # Native libs (arm64-v8a)
├── rust/                       # Rust core
│   ├── src/lib.rs              # JNI-ready core API
│   └── Cargo.toml
├── build.sh                    # Unified build (Rust + Android), copies APK to repo root
└── README.md
```

### Troubleshooting
- aarch64-linux-android-clang not found
  - Ensure the NDK is installed and `ANDROID_NDK_HOME` is set; add toolchain bin to `PATH` (see Manual build)
- Lint/AGP warnings
  - Project uses Android Gradle Plugin 8.5.x and Gradle 8.7
- Missing SDK dir warning
  - Ensure `android/local.properties` points to your SDK or export `ANDROID_SDK_ROOT`

### License
MIT - see `LICENSE`.
