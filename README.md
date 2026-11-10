# Droni — JJRC Elfie Android Flight Controller

**Written by Dr. Vahid Tavakkoli** · [@vtavakkoli](https://github.com/vtavakkoli)

Droni is an open-source native Android flight controller for the JJRC Elfie Wi‑Fi drone. It ports the original Android **11-byte `FF 08` control protocol** from my verified [Python JJRC Elfie controller](https://github.com/vtavakkoli/jjrc-elfie), informed by the supplied original Android APK decompilation.

## Features
- Native Android interface with two independent touchscreen joysticks, continuous 40 Hz control transmission, real-time link status and built-in video preview
- Takeoff, land, emergency motor stop, trim-neutral flight, speed 0/1/2, headless mode, disarmed calibration
- Wi‑Fi network-scoped sockets: the drone's no-internet Wi‑Fi can be used even when mobile data is enabled
- TCP H.264 camera streaming with the original handshake and hardware-accelerated Android `MediaCodec` decoding
- Zeroed controls on release, activity pause and disconnect; explicit warnings when UDP send fails
- Deterministic packet test vectors matching the Python regression tests

## Connect and fly safely
1. Build with Android Studio (JDK 17+, Android SDK 36) and install on Android 8.0+ (API 26).
2. Connect the phone manually to the **JJRC Elfie drone's Wi‑Fi**, and choose **Stay connected** if Android warns that it has no internet.
3. Launch Droni, keep the default host `172.16.10.1` unless your drone uses a different gateway, then tap **Connect**.
4. Confirm both UDP transmission and video reception status; with **propellers removed**, test axes and action buttons first.
5. In a safe open area, tap **Take off** to request the drone's own altitude-hold takeoff. **Left joystick**: yaw and climb/descent; **right joystick**: roll and pitch.
6. **Land** requests a landing. **Emergency stop** cuts motor power and may cause an uncontrolled fall. Closing the app is *not* a guaranteed safe landing.

The UI's “UDP sending” status describes **local transmission only**: the drone supplies no acknowledged flight-control/altitude/battery telemetry to this app. Never infer physical flight state from it. Do not use indoors near people or obstacles.

## Protocol
Control: UDP host `172.16.10.1:8080`, **11 bytes every 25 ms**. Video: TCP `172.16.10.1:8888`, original 106-byte request, 106-byte server greeting, then raw H.264 NAL stream. Hardware decoder = Android `MediaCodec` (not a generic HTTP/RTSP player).

There are no reliable IMU/barometer readings exposed by this controller. The desktop Python project's experimental camera-assisted visual hold is deliberately **not enabled on Android**: its feedback gains are not validated on real hardware.

## Stack
Kotlin, Jetpack Compose + Material 3 (stable BOM), AndroidX Activity, Android `ConnectivityManager` / `Network`, Java/Android sockets and Android hardware `MediaCodec`. No archived IJKPlayer or native binary dependencies.

## Android application ID
The Kotlin source package, Gradle namespace, and Android application ID are all `at.droni`. This changes the Android application identity, so an installed previous version will remain a separate app until manually uninstalled. The launcher uses an adaptive quadcopter icon.

## Build
```bash
git clone https://github.com/vtavakkoli/Droni.git
cd Droni
./gradlew testDebugUnitTest assembleDebug
```
Install `app/build/outputs/apk/debug/app-debug.apk` after a successful Android SDK build. The Gradle wrapper JAR must be generated locally with Android Studio's bundled Gradle or an installed Gradle distribution if absent; see `docs/BUILD.md`. No APK or physical drone validation is claimed by source publication alone.

## Troubleshooting Android Studio launch after package migration
If Run shows `Activity class {at.tavakkoli.droni/at.droni.MainActivity} does not exist`,
Android Studio is still launching the **old application ID** after the package rename.
The APK's correct identity is `at.droni` and its launcher activity is `at.droni.MainActivity`.

1. Pull the latest `main`, and use **File → Sync Project with Gradle Files**.
2. Open **Run → Edit Configurations**, delete any stale `app` Android App configuration, then create a new **Android App** run configuration with module `Droni.app` and **Launch: Default Activity**.
3. Use **Build → Clean Project**, then **Build → Rebuild Project**, and launch the newly configured `app`.
4. If the IDE still launches the old ID, disconnect the drone, then remove any previous installation with `adb uninstall at.tavakkoli.droni` (only if you no longer need the old installation). Keep `at.droni` installed or reinstall it from the newly built APK.

To inspect the two installed package names, run `adb shell pm list packages | grep droni` (on Windows PowerShell use `findstr droni` instead of `grep`).
Android treats `at.tavakkoli.droni` and `at.droni` as **different apps**: the rename is not an in-place update.

## Project layout
- `app/src/main/java/at/droni/protocol/ElfieProtocol.kt` — tested 11-byte encoder
- `app/src/main/java/at/droni/net/DroneController.kt` — 40 Hz UDP flight link and safety interlocks
- `app/src/main/java/at/droni/video/ElfieVideo.kt` — TCP handshake and H.264 decoding
- `app/src/main/java/at/droni/MainActivity.kt` — native Compose flight-deck interface

© 2026 **Dr. Vahid Tavakkoli**. MIT licensed. This is experimental software and is not a certified flight controller.
