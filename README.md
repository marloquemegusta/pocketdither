# PocketDither

PocketDither is a native Android camera app for live retro dithering. It shows a processed preview in real time, captures the photo with the same visual pipeline, and saves the result directly to the device.

This project was programmed by Marlo with help from OpenAI Codex.

## What it does

- Real-time processed camera preview using CameraX
- Processed photo capture designed to match the preview as closely as possible
- Color dithering plus curated retro presets
- Editable controls for presets, palette, pixel size, detail, contrast, pattern, exposure, zoom, focus, and gallery access
- Saved images in `Pictures/DitherCamera`

## Current presets

- Full Color
- Game Boy DMG
- Game Boy Pocket
- Macintosh
- Virtual Boy
- IBM CGA
- Commodore 64

## Tech stack

- Kotlin
- Jetpack Compose
- CameraX
- MediaStore
- Android SDK 35
- Java 17

## Project structure

- `app/src/main/java/com/arquimea/dithercamera/CameraScreen.kt`: main camera UI and interaction layer
- `app/src/main/java/com/arquimea/dithercamera/camera/DitherProcessor.kt`: preview and capture processing pipeline
- `app/src/main/java/com/arquimea/dithercamera/camera/DitherSettings.kt`: effect settings, presets, palettes, and patterns
- `app/src/main/java/com/arquimea/dithercamera/camera/BitmapStorage.kt`: image saving and last-photo recovery
- `docs/v1-spec.md`: v1 scope and design notes

## Requirements

- Android Studio Ladybug or newer, or a working Android SDK + JDK 17 setup
- Android SDK Platform 35
- Android Build-Tools for API 35
- Platform Tools (`adb`) if you want one-command device installs

If you installed Android Studio, its bundled JDK is enough. On Windows it is usually here:

```powershell
C:\Program Files\Android\Android Studio\jbr
```

## Open the project

1. Clone the repository.
2. Open the root folder in Android Studio.
3. Let Gradle sync and install any missing SDK packages.

## Build from the terminal

PowerShell example on Windows:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:GRADLE_USER_HOME="$PWD\.gradle"
.\gradlew.bat assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Build a release APK

Unsigned release APK:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:GRADLE_USER_HOME="$PWD\.gradle"
.\gradlew.bat assembleRelease
```

Unsigned release output:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

For a signed installable release APK, copy `keystore.properties.example` to `keystore.properties`, fill it with your own values, and build again. The Gradle config will automatically sign `release` if that file is present.

More detail is in [docs/release-signing.md](docs/release-signing.md).

## Install on a device with adb

```powershell
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r .\app\build\outputs\apk\debug\app-debug.apk
& $adb shell am start -n com.arquimea.dithercamera/.MainActivity
```

## GitHub Actions

The repository includes a workflow that builds both debug and release artifacts on every push to `main` and on pull requests. The workflow uploads:

- `app-debug.apk`
- `app-release-unsigned.apk`

If you want signed GitHub release builds later, add your keystore as GitHub Actions secrets and extend the workflow.

## Notes

- Camera behavior varies slightly by device, especially around exposure ranges and lens switching.
- Release builds are unsigned unless a local `keystore.properties` file is provided.
- This repository does not currently include automated UI or image-quality tests.

## Credits

- Product and implementation direction: Marlo
- Development assistance and iteration support: OpenAI Codex
