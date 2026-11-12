# Karoo Power Curve

An Android extension for Hammerhead Karoo that displays the best rolling-average power over configurable durations during the current ride.

## Features

- Power curve graph with a full active-ride average.
- Tap the data field to cycle through intervals and the ride average.
- Default intervals: 15s, 30s, 60s, 2m, 5m, 8m, 20m, and 60m.
- Custom intervals and optional visual curve smoothing in the Power Curve settings app.
- Changes to intervals recalculate results using the current ride's retained samples.
- Light and dark theme resources.

## How it works

The extension samples power once per second while the ride is recording. Paused time is excluded, and sensor dropout or searching counts as zero watts. An interval's result appears once enough samples have been collected. Smoothing changes the displayed line, not the measured values.

Ride samples are held in memory. Starting a recording from the idle state resets the curve; restarting the extension process loses its sample history. This is a current-ride tool, with no historical ride database or FIT-file export.

## Build requirements

- JDK 17.
- Android SDK with Android API 34 and the build tools required by Android Gradle Plugin 8.5.2.
- Gradle 8.7 for this project. A Gradle wrapper is not currently included, so install Gradle separately and put it on your PATH.
- Access to `io.hammerhead:karoo-ext:1.1.9`, using one of the options below.

The application uses Kotlin 2.0.21 and declares Android API 23 as its minimum. Actual use requires a Karoo device and firmware that support Karoo extensions.

## Resolve the Karoo SDK

Choose one option:

### GitHub Packages

Supply a GitHub username and a token with permission to read the SDK package in your user-level Gradle properties file (`%USERPROFILE%\.gradle\gradle.properties` on Windows):

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_TOKEN
```

Keep credentials out of this repository. The build also accepts `USERNAME` and `TOKEN` environment variables; on Windows, `USERNAME` normally contains your Windows account name, so explicitly setting `gpr.user` is preferable.

### Local Maven repository

Alternatively, clone `hammerheadnav/karoo-ext` from GitHub, check out tag `1.1.9`, and publish its library locally. In that SDK checkout, with `JAVA_HOME` and `ANDROID_HOME` configured, run:

```powershell
.\gradlew.bat :lib:publishToMavenLocal
```

This project's dependency configuration checks Maven Local for the Karoo SDK before using the remote repositories.

## Build and install

From the project root, with JDK 17 and Gradle 8.7 on your PATH:

```powershell
gradle :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Enable developer access and ADB on your Karoo, connect it, and install the APK using Android SDK Platform Tools:

```powershell
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Enable the extension on the device and add **Power Curve Graph** to a ride profile. Open **Power Curve** from the app launcher to change settings. Enter durations separated by commas, such as `15s, 1min, 5min, 1h`, then select **Save and apply**. Durations must be positive and unique.

Debug APKs are suitable for development. A distributable release APK needs your own release signing configuration; keep signing keys and passwords outside version control.

## Validation

```powershell
gradle :app:lintDebug
gradle :app:connectedDebugAndroidTest
```

The instrumentation tests cover calculations, settings, and layout behavior. They require a connected Android device or emulator. Device integration should also be checked on a compatible Karoo.

## Source layout

- `PowerCurve.kt`: rolling averages and current-ride samples.
- `PowerCurveExtension.kt`: Karoo service integration and sampling.
- `PowerCurveDataType.kt`: graph rendering and data-field interaction.
- `PowerCurveConfig.kt`: interval parsing and stored settings.
- `app/src/androidTest`: Android instrumentation tests.

Kotlin source files live in `app/src/main/kotlin/com/kevin/powercurve`.

## Contributing

Include reproduction steps, expected behavior, device/firmware details, and relevant logs when reporting a bug. For changes, describe the resulting behavior and the checks you ran. Never include access tokens, signing keys, or personal ride data in commits or reports.

## License

No license has been selected yet. Public source availability alone does not grant permission to redistribute or modify the project. A license should be selected before inviting broader reuse.
