<p align="center">
  <img src="docs/droni-logo.svg" alt="Droni — JJRC Elfie Android Flight Deck, by Dr. Vahid Tavakkoli" width="850"/>
</p>

<p align="center">
  <a href="https://github.com/vtavakkoli/Droni/actions/workflows/android-ci.yml"><img alt="Android CI" src="https://github.com/vtavakkoli/Droni/actions/workflows/android-ci.yml/badge.svg?branch=main"/></a>
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Android-7F52FF?logo=kotlin"/>
  <img alt="Android API" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android"/>
  <img alt="License" src="https://img.shields.io/badge/License-MIT-6CE7D0"/>
  <img alt="Version" src="https://img.shields.io/badge/version-0.2.0-47C5F7"/>
</p>

<h1 align="center">Droni</h1>
<p align="center"><strong>Native Android flight deck for the JJRC Elfie Wi‑Fi drone</strong><br/>
<strong>Created by Dr. Vahid Tavakkoli · © 2026</strong></p>

Droni brings hands-on flight control, first-person video, camera recording, a draw-to-preview mission designer, and clear diagnostics into a polished, privacy-friendly Android application. Built from the original <a href="https://github.com/vtavakkoli/jjrc-elfie">JJRC Elfie Python controller</a> and the decompiled legacy Android flight protocol.

## What's inside

| Screen | Capabilities | Status |
| :-- | :-- | :-- |
| **Splash + Home** | Branded splash, refined home with quick-access cards | Implemented |
| **Drone Fly** | Transparent dual-joystick FPV layout, takeoff, land, motor stop, speed, headless, disarmed calibration, reversal | Implemented; physical validation needed |
| **Draw Path** | Finger-drawn route, constrained preview, animated simulation | Implemented |
| **Experimental Path Trial** | Level-calibration request → takeoff request → short bounded screen-direction commands → landing request, with abort | **OPEN LOOP. Not real path following; safety/landing unverified** |
| **Recordings** | MP4 capture, asynchronous video library, thumbnails, play and share | Implemented; verify with real camera |
| **Settings** | Persistent Wi-Fi gateway, default speed 0/1/2, optional auto-calibration before manual takeoff, framing grid | Implemented |
| **About** | Project credits, copyright, source and license | Implemented |

### Why Droni does not claim autonomous path following

**The JJRC Elfie does not supply verified GPS, optical-flow position, heading, IMU, barometer, or touchdown telemetry over this Wi‑Fi protocol.** The older sensor diagnostic did not confirm any inertial measurements. Sending a `calibrate` flag only requests sensor leveling — it cannot establish a world coordinate system or detect where the drone is.

A drawn screen line cannot be translated to meters without pose/velocity feedback. Droni therefore offers (1) **offline path simulation**, and (2) a deliberately explicit, tightly bounded, supervised **open-loop experimental trial**. The latter executes directional *pulses*, not actual waypoint tracking; wind, heading, scale, and inertia will alter the outcome. It requests landing at the end or upon cancellation but **cannot confirm landing**. For initial testing, **remove propellers**. Do not run an experimental trial around people, obstacles, indoors or over property.

## Android stack

- **Kotlin**, **Jetpack Compose**, **Material 3** — native reactive UI, no embedded web views
- **AndroidX Core SplashScreen 1.2.0** — official launch splash on Android 8.0+
- **Android ConnectivityManager** — explicitly selects drone Wi‑Fi even when that network has no internet
- **UDP 8080, 11-byte FF 08** — firmware-compatible original Android control packets, every ~25 ms
- **TCP 8888**, original Elfie handshake, **Android MediaCodec** for H.264 video
- **Android MediaMuxer + MediaStore** — camera MP4 recording to Movies/Droni without a third-party transcoder
- **JUnit + GitHub Actions** — protocol, H.264 parser and path-planner regression tests

No unnecessary trackers, location collection, accounts, or external cloud services.

## Build and install

[![Latest build](https://img.shields.io/badge/Download-debug%20APK-blue)](https://github.com/vtavakkoli/Droni/actions/workflows/android-ci.yml)

1. Open [GitHub Actions → Android CI](https://github.com/vtavakkoli/Droni/actions/workflows/android-ci.yml) and choose a **successful** run.
2. Download **droni-debug-apk** under Artifacts, unzip, and install the APK on Android 8.0+.
3. Or build with Android Studio + JDK 17 and Android SDK 36:

```bash
git clone https://github.com/vtavakkoli/Droni.git
cd Droni
# With Gradle 9.5 installed:
gradle --no-daemon testDebugUnitTest assembleDebug
```

Gradle wrapper properties are provided; its binary bootstrap JAR is not committed. See [build details](docs/BUILD.md).

## Flight preferences

In **Settings**, choose the persistent default speed (0, 1, or 2; initially **1**). You may also enable **Auto-calibrate before takeoff** (off by default). With this enabled, a manual Take Off first sends a calibration request while the drone is disarmed, waits approximately 1.8 seconds, then sends takeoff only if the same flight screen remains active and connected. Calibration is unacknowledged; it does not verify physical orientation or position. Keep the drone stationary and level. Experimental Draw Path missions have their own calibration step.

In **Drone Fly**, **REC MP4** is on the top bar; Android Back returns to the home menu. Recordings remain available from the main menu.

## Connect a drone

1. Connect the phone to the **JJRC Elfie Wi‑Fi** and choose **Stay connected** if Android says it has no internet.
2. Launch **Droni → Drone Fly**. Connect to default `172.16.10.1` (or your Wi‑Fi gateway).
3. Before attaching propellers, test button effects and neutral-stick behavior.
4. In clear, safe conditions, use takeoff, manual sticks and land. The **MOTOR STOP** button stops motors and can cause an uncontrolled fall. Closing Droni does not guarantee a safe landing.
5. While receiving video, tap **REC MP4** to start and **STOP REC** to save. Open **Recordings** to view saved clips. MP4 timestamp and camera format assumptions must be validated on hardware.

### Protocol reference

| Channel | Value |
| :-- | :-- |
| Drone Wi‑Fi gateway (default) | `172.16.10.1` |
| Flight | UDP `8080` · 11-byte `FF 08` + complement checksum |
| Control period | Approx. 25 milliseconds (40 Hz), not a real-time guarantee |
| Camera | TCP `8888` · proprietary 106-byte greeting · H.264 Annex-B |
| Sensor telemetry | **Not verified** |
| Path-tracking accuracy | **Not available without position feedback** |

The control UI's “connected” indicator means sockets are configured and local packets are being sent; **there is no drone acknowledgement or sensor validation**.

## Project map

```text
app/src/main/java/at/droni/
├── MainActivity.kt          # Existing FPV joystick flight deck
├── DroniApp.kt              # Splash destination, menu, settings, path, recordings, about
├── protocol/ElfieProtocol.kt
├── net/DroneController.kt
├── mission/PathPlanner.kt   # Unit-tested bounded path planner, no GPS claims
├── settings/DroniSettings.kt
└── video/
    ├── ElfieVideo.kt        # Original camera handshake + MediaCodec
    ├── AnnexBParser.kt
    └── ElfieMp4Recorder.kt  # Local MP4 remux + MediaStore
```

## Documentation & contribution

- [Safety and experimental mission limitations](docs/SAFETY.md)
- [Architecture and protocol](docs/ARCHITECTURE.md)
- [Development and CI](docs/BUILD.md)
- [Contributing](CONTRIBUTING.md)
- [Security policy](SECURITY.md)

---

**© 2026 Dr. Vahid Tavakkoli.** [MIT License](LICENSE). JJRC Elfie is a third-party product; this is an independent experimental controller, not endorsed by the manufacturer.
