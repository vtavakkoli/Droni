# Droni architecture

```text
MainActivity (splash / lifecycle shutdown)
  ├─ DroniApp (Home / Settings / Path / Movies / About)
  │    ├─ MainActivity.FlightDeck (existing video + two joysticks + buttons)
  │    └─ PathPlanner (pure Kotlin, bounded open-loop preview and pulse plan)
  └─ DroneController
       ├─ Wi‑Fi-scoped UDP socket to 172.16.10.1:8080
       └─ ElfieVideo (TCP 8888 greeting)
            ├─ AnnexBParser (H.264 NAL extraction)
            ├─ MediaCodec → SurfaceView (preview)
            └─ ElfieMp4Recorder → MediaMuxer → MediaStore / Movies/Droni
```

The Android original `FF 08` flight packet encoder lives in
`protocol/ElfieProtocol.kt` and is regression-tested against the desktop
Python implementation; this is not the unrelated 8-byte H37 0x66 protocol.

Flight UDP and camera TCP explicitly bind to the phone's Wi‑Fi network.
The camera worker is independent of UI touch handling. The command tick uses
`scheduleWithFixedDelay` to avoid a burst after the app is cached.

Navigation is intentionally local (no DI/router SDK needed for five screens).
Settings are stored in Android SharedPreferences. File recording uses the
Android MediaStore on API 29+ and app-scoped Movies on API 26–28.

The mission planner is **not localization**. It produces bounded short commands
from screen polyline direction, without an endpoint guarantee. See
[SAFETY.md](SAFETY.md).
