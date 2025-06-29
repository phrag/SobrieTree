# BrewLog

A beer consumption tracking Android app built with Kotlin and Rust backend.

## Project Status

✅ **Completed:**
- Rust backend with beer tracking functionality (CRUD operations, goal setting, progress tracking)
- Android app structure with Material Design UI
- Data models and business logic
- RecyclerView adapter for displaying beer entries
- Dialog-based input forms for adding/editing entries

🔄 **Current State:**
- Rust library builds successfully and passes all tests
- Android app structure is complete but needs Java/JDK to build
- Simplified approach without UniFFI (using basic JNI instead)

❌ **Blocked by:**
- Missing Java/JDK installation for Android development
- Android NDK not set up for Rust cross-compilation

## Project Structure

```
brewlog/
├── android/                    # Android app
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/brewlog/android/
│   │   │   │   ├── MainActivity.kt        # Main activity
│   │   │   │   ├── BeerEntryAdapter.kt    # RecyclerView adapter
│   │   │   │   ├── BeerEntry.kt           # Data model
│   │   │   │   └── BrewLog.kt             # Business logic wrapper
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   ├── activity_main.xml
│   │   │   │   │   ├── dialog_add_beer.xml
│   │   │   │   │   └── item_beer_entry.xml
│   │   │   │   ├── values/
│   │   │   │   │   ├── colors.xml
│   │   │   │   │   ├── strings.xml
│   │   │   │   │   └── themes.xml
│   │   │   │   └── mipmap-*/
│   │   │   │       └── ic_launcher*.png
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle
│   ├── build.gradle
│   ├── gradle.properties
│   └── settings.gradle
├── rust/                       # Rust backend
│   ├── src/
│   │   └── lib.rs             # Main library with JNI exports
│   ├── Cargo.toml
│   └── Cargo.lock
├── build.sh                    # Build script for Rust
├── build_android.sh           # Build script for Android
├── setup.sh                   # Setup script
├── README.md
└── PROJECT_OVERVIEW.md
```

## Features

### Core Functionality
- **Add Beer Entries**: Track beer name, alcohol percentage, volume, and notes
- **View Consumption**: Daily and weekly consumption tracking
- **Set Goals**: Configure daily and weekly consumption targets
- **Progress Tracking**: Monitor reduction progress over time
- **Edit/Delete**: Modify or remove existing entries

### Technical Features
- **Rust Backend**: High-performance data processing and storage
- **SQLite Database**: Persistent storage for beer entries and goals
- **Material Design**: Modern, accessible UI components
- **RecyclerView**: Efficient list display with smooth scrolling
- **View Binding**: Type-safe view access

## Getting Started

### Prerequisites

1. **Java Development Kit (JDK)**
   ```bash
   # Install OpenJDK 11 or later
   brew install openjdk@11
   # or download from Oracle/AdoptOpenJDK
   ```

2. **Android Studio** (recommended)
   - Download from https://developer.android.com/studio
   - Install Android SDK and NDK

3. **Rust** (already installed)
   ```bash
   curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
   ```

### Building the Project

1. **Build Rust Library**
   ```bash
   cd rust
   cargo build
   cargo test
   ```

2. **Build Android App**
   ```bash
   cd android
   ./gradlew assembleDebug
   ```

3. **Run on Device/Emulator**
   ```bash
   ./gradlew installDebug
   ```

## Architecture

### Rust Backend
- **BrewLog**: Main business logic class
- **BeerEntry**: Data model for beer entries
- **ConsumptionGoal**: Goal setting and tracking
- **ProgressStats**: Analytics and progress calculation
- **SQLite Integration**: Persistent data storage

### Android Frontend
- **MainActivity**: Main UI controller
- **BrewLog**: JNI wrapper for Rust backend
- **BeerEntryAdapter**: RecyclerView adapter for list display
- **Material Design**: Modern UI components

## Development Roadmap

### Phase 1: Basic App (Current)
- [x] Rust backend with core functionality
- [x] Android UI structure
- [ ] Java/JDK installation
- [ ] Android app compilation
- [ ] Basic testing

### Phase 2: Rust Integration
- [ ] Android NDK setup
- [ ] Cross-compilation for Android architectures
- [ ] JNI integration
- [ ] Performance optimization

### Phase 3: Enhanced Features
- [ ] Data persistence improvements
- [ ] Charts and analytics
- [ ] Export/import functionality
- [ ] Cloud sync (optional)

### Phase 4: Polish
- [ ] UI/UX improvements
- [ ] Accessibility features
- [ ] Performance optimization
- [ ] App store preparation

## Troubleshooting

### Common Issues

1. **Java not found**
   ```bash
   # Install Java
   brew install openjdk@11
   export JAVA_HOME=/opt/homebrew/opt/openjdk@11
   ```

2. **Android NDK not found**
   ```bash
   # Install via Android Studio SDK Manager
   # or download standalone NDK
   ```

3. **Gradle build failures**
   ```bash
   # Clean and rebuild
   ./gradlew clean
   ./gradlew assembleDebug
   ```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Next Steps

To get the app running:

1. **Install Java/JDK** (required for Android development)
2. **Build the Android app** using `./gradlew assembleDebug`
3. **Test on device/emulator**
4. **Set up Android NDK** for Rust integration
5. **Build Rust for Android** using the provided script

The Rust backend is fully functional and tested. The Android app structure is complete and ready to build once Java is installed. 