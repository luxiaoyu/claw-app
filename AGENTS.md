# Termux App - Agent Documentation

## Project Overview

This is an Android terminal emulator and Linux environment application based on Termux. The project extends Termux with a modern Jetpack Compose-based UI layer (KimiClaw) while maintaining compatibility with the core Termux terminal emulation and package management capabilities.

- **Package Name**: `com.termux`
- **Current Version**: 0.118.0 (versionCode 118)
- **License**: GPLv3 only (with exceptions for libraries)
- **Primary Languages**: Java and Kotlin (with Chinese comments in KimiClaw layer)
- **Build System**: Gradle with Android Gradle Plugin 8.9.1

## Technology Stack

### Primary Technologies
- **Languages**: Java, Kotlin 2.0.21
- **Build System**: Gradle 8.x with Android Gradle Plugin 8.9.1
- **SDK Versions**:
  - `compileSdkVersion`: 36
  - `targetSdkVersion`: 28
  - `minSdkVersion`: 26 (Android 8.0+)
  - `ndkVersion`: 29.0.14206865

### Native Development
- **NDK Build**: Uses `Android.mk` for native builds
- **Native Libraries**: 
  - `libtermux.so` - Terminal emulator JNI (terminal-emulator/src/main/jni/)
  - `libtermux-bootstrap.so` - Bootstrap loader (app/src/main/cpp/)
- **Bootstrap**: Pre-built bootstrap zips containing minimal Linux environment downloaded from termux-packages releases

### Key Dependencies
- **AndroidX libraries**: core-ktx (1.17.0), annotation, drawerlayout, preference-ktx, viewpager
- **Material Design Components**: 1.13.0
- **Jetpack Compose**: BOM 2026.01.01, Material3, Material Icons Extended
- **Google Guava**: 33.5.0-jre
- **Markwon**: 4.6.2 (Markdown rendering)
- **Commons IO**: 2.5 (capped for Android < 8 compatibility)
- **Hidden API Bypass**: 6.1 (for internal Android APIs)
- **termux-am-library**: v2.0.0

## Project Structure

The project follows a multi-module Gradle structure defined in `settings.gradle`:

```
├── app/                          # Main Android application
│   ├── src/main/java/            # Java source code (Termux core)
│   ├── src/main/kotlin/          # Kotlin source code (KimiClaw UI layer)
│   ├── src/main/cpp/             # Native C code (bootstrap loader)
│   ├── src/main/jni/             # JNI native code
│   ├── src/main/res/             # Android resources
│   ├── src/test/java/            # Unit tests
│   └── build.gradle              # App module build config
├── termux-shared/                # Shared library for app and plugins
│   ├── src/main/java/            # Shared Java code
│   └── build.gradle              # Library build config with JitPack publishing
├── terminal-emulator/            # Terminal emulation library
│   ├── src/main/java/            # Terminal emulator core
│   ├── src/main/jni/             # JNI native code (termux.c)
│   ├── src/test/java/            # Unit tests
│   └── build.gradle              # Library build config with JitPack publishing
├── terminal-view/                # Android terminal view widget
│   ├── src/main/java/            # TerminalView implementation
│   └── build.gradle              # Library build config with JitPack publishing
├── boot-strap/                   # Bootstrap zip files
├── docs/                         # Documentation
├── art/                          # Assets and artwork
└── .github/workflows/            # CI/CD configurations
```

### Module Dependencies
```
app -> terminal-view -> terminal-emulator
app -> termux-shared -> terminal-view
termux-shared -> terminal-view -> terminal-emulator
```

### Key Source Directories

#### App Module (`app/src/main/java/com/termux/`)
- `app/TermuxActivity.java` - Main terminal activity
- `app/TermuxService.java` - Background service for terminal sessions
- `app/TermuxApplication.java` - Application class
- `app/TermuxInstaller.java` - Bootstrap installation logic
- `app/RunCommandService.java` - Intent-based command execution service
- `app/activities/` - Settings and Help activities
- `app/fragments/settings/` - Preference fragments
- `app/terminal/` - Terminal UI controllers and clients
- `filepicker/TermuxDocumentsProvider.java` - Storage access framework provider

#### KimiClaw UI Layer (`app/src/main/kotlin/com/moonshot/kimiclaw/`)
- `MainActivity.kt` - Entry point with Jetpack Compose UI (Welcome, Dashboard, Install screens)
- `KimiClawService.kt` - Foreground service for managing shell commands with coroutines
- `TermuxSetup.kt` - Bootstrap installation helper
- `ui/` - Compose screens (WelcomeScreen, DashboardScreen, InstallScreen, LogcatScreen, PhantomProcessDialog)
- `viewmodel/` - ViewModels for UI state management
- `termux/ShellUtils.kt` - Shell execution utilities with Flow support
- `openclaw/OpenClawHelper.kt` - OpenClaw gateway management
- `openclaw/OpenClawInstaller.kt` - OpenClaw installation logic
- `openclaw/OpenClawConfig.kt` - OpenClaw configuration

#### Shared Library (`termux-shared/src/main/java/com/termux/shared/`)
- `termux/TermuxConstants.java` - Central constants definition
- `termux/settings/` - Settings and preferences management
- `termux/shell/` - Shell execution utilities
- `termux/extrakeys/` - Extra keys view implementation
- `file/` - File system utilities
- `shell/command/` - Command execution framework
- `net/socket/local/` - Local socket communication

#### Terminal Emulator (`terminal-emulator/src/main/java/com/termux/terminal/`)
- `TerminalEmulator.java` - Core VT100/ANSI terminal emulation
- `TerminalSession.java` - Terminal session management
- `TerminalBuffer.java` - Screen buffer implementation
- `KeyHandler.java` - Key event processing
- `JNI.java` - JNI bridge to native code

#### Terminal View (`terminal-view/src/main/java/com/termux/view/`)
- `TerminalView.java` - Custom Android View for terminal display
- `TerminalRenderer.java` - Rendering logic
- `GestureAndScaleRecognizer.java` - Touch gesture handling
- `textselection/` - Text selection UI components

## Build System

### Build Commands

```bash
# Build debug APKs for all architectures
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Clean build
./gradlew clean

# Get version name
./gradlew versionName
```

### Build Variants

The app supports two package variants controlled by `TERMUX_PACKAGE_VARIANT` environment variable:
- `apt-android-7` (default) - For Android 7.0+
- `apt-android-5` - For Android 5.0-6.0 (legacy, no package updates)

### APK Outputs

Debug builds produce multiple APKs:
- `termux-app_<version>_<variant>-debug_universal.apk`
- `termux-app_<version>_<variant>-debug_arm64-v8a.apk`
- `termux-app_<version>_<variant>-debug_armeabi-v7a.apk`
- `termux-app_<version>_<variant>-debug_x86_64.apk`
- `termux-app_<version>_<variant>-debug_x86.apk`

### Environment Variables

| Variable | Description |
|----------|-------------|
| `TERMUX_PACKAGE_VARIANT` | Bootstrap variant (`apt-android-7` or `apt-android-5`) |
| `TERMUX_APP_VERSION_NAME` | Override version name |
| `TERMUX_APK_VERSION_TAG` | Custom APK filename tag |
| `TERMUX_SPLIT_APKS_FOR_DEBUG_BUILDS` | Enable split APKs for debug (default: 1) |
| `TERMUX_SPLIT_APKS_FOR_RELEASE_BUILDS` | Enable split APKs for release (default: 0) |
| `JITPACK_NDK_VERSION` | NDK version override for JitPack builds |

### Native Build

The project uses NDK build system with `Android.mk` files:
- `app/src/main/cpp/Android.mk` - Bootstrap loader
- `terminal-emulator/src/main/jni/Android.mk` - Terminal emulator JNI

### Signing

- Debug builds use `app/testkey_untrusted.jks` (shared test key)
- Release builds require proper signing configuration
- **Security Warning**: The test key is publicly shared and should NEVER be used for production releases

## Testing

### Test Structure

```
terminal-emulator/src/test/java/    # Unit tests for terminal emulator
app/src/test/java/                  # Unit tests for app module
```

### Running Tests

```bash
# Run all unit tests
./gradlew test

# Run tests for specific module
./gradlew :terminal-emulator:test
./gradlew :app:test
```

### Test Framework
- JUnit 4.13.2
- Robolectric 4.16.1 (for Android unit tests)

### Key Test Files
- `TerminalTestCase.java` - Base test class for terminal emulator
- `TerminalEmulatorTest.java` - Core emulation tests
- `KeyHandlerTest.java` - Key handling tests
- `ByteQueueTest.java` - Data structure tests

## CI/CD

GitHub Actions workflows (`.github/workflows/`):

### 1. Build Workflow (`debug_build.yml`)
- Triggers on push to `master`, PRs, and `github-releases/**` branches
- Builds both `apt-android-7` and `apt-android-5` variants
- Produces debug APKs for all architectures (universal, arm64-v8a, armeabi-v7a, x86_64, x86)
- Validates semantic versioning
- Generates SHA256 checksums
- Uploads artifacts

### 2. Unit Tests (`run_tests.yml`)
- Runs on push to `master` and `android-10` branches
- Executes `./gradlew test`

### 3. Release Attachment (`attach_debug_apks_to_release.yml`)
- Triggers on GitHub releases (published)
- Builds and attaches APKs to release
- Validates semantic versioning
- Deletes release and tag if build fails

### 4. Library Publishing (`trigger_library_builds_on_jitpack.yml`)
- Triggers JitPack builds for library modules on release
- Waits 3 minutes for tag detection
- Publishes: terminal-emulator, terminal-view, termux-shared

### 5. Gradle Wrapper Validation (`gradle-wrapper-validation.yml`)
- Validates Gradle wrapper checksums

## Code Style and Conventions

### EditorConfig
The project uses `.editorconfig` with the following settings:
- Line endings: LF
- Charset: UTF-8
- Indent style: space
- Indent size: 4 (2 for YAML files)
- Insert final newline: true

### Commit Message Format

**MUST** use [Conventional Commits](https://www.conventionalcommits.org) spec:

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

**Allowed types** (exact case required):
- `Added:` - New features
- `Changed:` - Changes in existing functionality
- `Deprecated:` - Soon-to-be removed features
- `Removed:` - Now removed features
- `Fixed:` - Bug fixes
- `Security:` - Vulnerability fixes

**Format rules**:
- First letter of type and description must be capital
- Use present tense in description
- Space after colon is required
- Breaking changes: add `!` before colon (e.g., `Changed!: Breaking change`)

Examples:
```
Fixed(terminal): Fix cursor positioning bug
Added: Add support for new escape sequences
Changed!: Refactor session management API
```

### Version Naming

Follows [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html):
```
major.minor.patch(-prerelease)(+buildmetadata)
```

Examples: `0.118.0`, `0.118.1-beta`, `0.118.0+build.123`

Version validation is enforced in `app/build.gradle` using regex matching.

### Code Organization

1. **Constants**: Define in `TermuxConstants.java` (termux-shared), never hardcode
2. **Shared Code**: Use `termux-shared` library for code shared between app and plugins
3. **Package Structure**:
   - General utilities: `com.termux.shared.*`
   - Termux-specific: `com.termux.shared.termux.*`
   - KimiClaw UI: `com.moonshot.kimiclaw.*`
4. **Logging**: Use `Logger` class from termux-shared

## Development Guidelines

### Adding New Features

1. **Constants**: Add to appropriate constants class in `termux-shared`
2. **Settings**:
   - Add preference keys to `TermuxPreferenceConstants`
   - Create preference fragment in `app/fragments/settings/`
3. **UI Components**:
   - Jetpack Compose for new UI screens (see KimiClaw UI layer)
   - Consider if reusable; if yes, place in `termux-shared`
4. **Documentation**: Update relevant changelogs

### Working with Native Code

- Native code is in `app/src/main/cpp/` and `terminal-emulator/src/main/jni/`
- Uses NDK build system with `Android.mk`
- Bootstrap zip is downloaded at build time from termux-packages releases
- Checksums are validated for security

### KimiClaw Service Architecture

The KimiClawService is a foreground service that:
- Uses Kotlin coroutines for async operations
- Provides shell command execution with timeout support
- Manages long-running jobs with cancellation support
- Updates notifications to show service status

Key methods:
- `executeCommand()` - Execute single command with callback
- `executeCommandSuspend()` - Suspend function for coroutines
- `executeLongRunningCommand()` - Execute with progress callbacks

## Plugin Development

The `termux-shared`, `terminal-view`, and `terminal-emulator` libraries are published on JitPack for plugin use:

```gradle
implementation 'com.termux:termux-shared:0.118.0'
implementation 'com.termux:terminal-view:0.118.0'
implementation 'com.termux:terminal-emulator:0.118.0'
```

## Security Considerations

### Permissions

The app requires numerous permissions including:
- `INTERNET`, `ACCESS_NETWORK_STATE` - Network access
- `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE` - File system access
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` - Background execution
- `SYSTEM_ALERT_WINDOW` - Overlay windows
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` - Prevent process killing
- `READ_LOGS`, `DUMP` - Debugging capabilities
- `WRITE_SECURE_SETTINGS`, `REQUEST_INSTALL_PACKAGES` - System settings
- `RECEIVE_BOOT_COMPLETED`, `PACKAGE_USAGE_STATS` - System integration
- Custom `RUN_COMMAND` permission - For other apps to execute commands

### Security Best Practices

1. **Never commit production signing keys**
2. **Validate all inputs** from intents and external sources
3. **Use proper file permissions** - Avoid world-readable/writable files
4. **Log level** - Use appropriate log levels; `Verbose` may log sensitive data
5. **Bootstrap checksums** - Always verify bootstrap zip checksums

### Reporting Security Issues

Check https://termux.dev/security for security policies and vulnerability reporting.

## Important Notes

### Android 12+ Compatibility

Termux may be unstable on Android 12+ due to:
- Phantom process killing (limit of 32 background processes)
- Excessive CPU usage process killing

Users may see `[Process completed (signal 9) - press Enter]` errors. The KimiClaw UI includes `PhantomProcessHelper` and `PhantomProcessDialog` to help users manage these issues.

### Forking

To fork Termux:
1. Update `TermuxConstants.java` with new package name
2. Recompile bootstrap zip for new `$PREFIX`
3. Update all plugin constants
4. See `TermuxConstants.java` javadocs for detailed instructions

### Installation Sources

APKs from different sources have different signatures and are NOT compatible:
- F-Droid
- GitHub Releases
- GitHub Build Actions

Users must uninstall ALL Termux apps before switching sources.

## License Summary

- **Main project**: GPLv3 only
- **termux-shared library**: MIT (with some GPLv3 and Apache 2.0 components)
- **terminal-view/emulator**: Apache 2.0 (based on Android Terminal Emulator)

See individual `LICENSE.md` files for details.

## Useful Resources

- **Main Wiki**: https://wiki.termux.com/
- **App Wiki**: https://github.com/termux/termux-app/wiki
- **Packages Wiki**: https://github.com/termux/termux-packages/wiki
- **Community**: https://reddit.com/r/termux
- **Issues**: https://github.com/termux/termux-app/issues
- **Terminal Resources**: XTerm control sequences, vt100.net (see README.md)
