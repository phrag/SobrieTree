# BrewLog Project Overview

## 🍺 Project Description

BrewLog is an Android application designed to help users track and reduce their beer consumption. The app provides a comprehensive solution for monitoring daily and weekly beer intake, setting consumption goals, and tracking progress over time.

## 🏗️ Architecture

### Technology Stack
- **Frontend**: Android (Kotlin) with Material Design 3
- **Backend**: Rust with SQLite database
- **Integration**: JNI (Java Native Interface)
- **Build System**: Gradle (Android) + Cargo (Rust)

### Project Structure
```
brewlog/
├── android/                    # Android application
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
├── rust/                       # Rust backend library
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

## 📱 Features

### Core Functionality
1. **Beer Entry Management**
   - Add new beer entries with name, alcohol percentage, volume, and notes
   - Edit existing entries
   - Delete entries with confirmation
   - View entry history

2. **Consumption Tracking**
   - Daily consumption totals
   - Weekly consumption summaries
   - Historical data viewing
   - Real-time statistics

3. **Goal Setting**
   - Set daily consumption targets
   - Set weekly consumption targets
   - Track progress against goals
   - Visual progress indicators

4. **Baseline & Progress**
   - Calculate consumption baseline from historical data
   - Monitor reduction progress
   - Progress statistics and trends

### User Interface
- **Modern Material Design**: Clean, intuitive interface
- **Responsive Layout**: Works on various screen sizes
- **Dark/Light Theme Support**: Automatic theme switching
- **Accessibility**: Screen reader support and proper contrast

## 🛠️ Technical Implementation

### Rust Backend Structure
```
rust/
├── src/
│   ├── lib.rs          # Main library implementation
│   └── lib.udl         # UniFFI interface definition
├── Cargo.toml          # Rust dependencies
├── uniffi.toml         # UniFFI configuration
└── build.rs           # Build script
```

### Android Frontend Structure
```
android/
├── app/
│   ├── src/main/
│   │   ├── java/com/brewlog/android/
│   │   │   ├── MainActivity.kt
│   │   │   └── BeerEntryAdapter.kt
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   ├── values/
│   │   │   └── drawable/
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
└── settings.gradle
```

## 🚀 Getting Started

### Prerequisites
- Rust (latest stable)
- Android Studio
- Android SDK
- Java 11 or later

### Quick Start
1. **Setup Environment**:
   ```bash
   ./setup.sh
   ```

2. **Build the App**:
   ```bash
   ./build.sh
   ```

3. **Install on Device**:
   ```bash
   adb install android/app/build/outputs/apk/debug/app-debug.apk
   ```

### Development Commands
- **Full Build**: `./build.sh`
- **Rust Only**: `cd rust && cargo build`
- **Android Only**: `cd android && ./gradlew assembleDebug`
- **Run Tests**: `cd rust && cargo test`
- **Install on Device**: `cd android && ./gradlew installDebug`

## 📊 Data Models

### BeerEntry
```rust
pub struct BeerEntry {
    pub id: String,
    pub name: String,
    pub alcohol_percentage: f64,
    pub volume_ml: f64,
    pub date: String,
    pub notes: String,
}
```

### ConsumptionGoal
```rust
pub struct ConsumptionGoal {
    pub id: String,
    pub daily_target: f64,
    pub weekly_target: f64,
    pub start_date: String,
    pub end_date: String,
}
```

### Baseline
```rust
pub struct Baseline {
    pub average_daily_consumption: f64,
    pub average_weekly_consumption: f64,
    pub calculated_date: String,
}
```

## 🔧 Configuration

### UniFFI Configuration
The app uses UniFFI to generate Kotlin bindings from the Rust backend:
- **Package**: `com.brewlog.core`
- **Library**: `brewlog_core`
- **Supported Languages**: Kotlin, Swift, Python

### Android Configuration
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Theme**: Material Design 3
- **Architecture**: MVVM with ViewBinding

## 🧪 Testing

### Rust Tests
The backend includes comprehensive unit tests:
- Beer tracker creation
- Entry validation
- CRUD operations
- Goal management
- Consumption calculations

Run tests with:
```bash
cd rust && cargo test
```

### Android Tests
- Unit tests for Kotlin components
- UI tests for user interactions
- Integration tests for Rust bindings

## 🔮 Future Enhancements

### Planned Features
1. **Data Persistence**: File-based storage for production
2. **Cloud Sync**: Backup and sync across devices
3. **Charts & Analytics**: Visual progress tracking
4. **Notifications**: Goal reminders and achievements
5. **Export**: Data export to CSV/PDF
6. **Social Features**: Share progress with friends

### Technical Improvements
1. **Cross-compilation**: Proper ARM/ARM64/x86 support
2. **Performance**: Optimized database queries
3. **Security**: Data encryption
4. **Accessibility**: Enhanced screen reader support

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## 📞 Support

For questions or issues:
1. Check the documentation
2. Search existing issues
3. Create a new issue with detailed information

---

**Note**: This app is designed to help users make informed decisions about their alcohol consumption. It should be used responsibly and in conjunction with professional health advice when needed. 