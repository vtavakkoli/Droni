# Droni interface

Droni uses one native Compose design system across its landscape screens. Palette,
type, shapes and shared components live in `app/src/main/java/at/droni/ui/`.

- **Home:** one primary flight action and four secondary destinations, with a
  compact layout for shorter landscape displays.
- **Flight deck:** full-screen camera, transparent overlays, borderless gateway
  entry, recording in the header, and 48 dp action targets. Android Back returns
  home; leaving after takeoff retains the landing-request confirmation.
- **Settings:** grouped connection, camera and flight preferences. Speed selection
  and switches expose their state and labels to accessibility services.
- **Draw path:** a dedicated canvas, start/finish markers and a separate preview
  panel. Redrawing or clearing cancels the current simulation. Experimental
  aircraft commands keep the existing confirmation and bounded planner.
- **Recordings:** background queries and thumbnails, lazy scrolling, playback,
  sharing, and distinct loading, empty and error states. No new storage permissions.
- **About:** build-derived version, creator attribution, MIT license and source.

## Status language

Wi-Fi socket setup indicates local transport, not an aircraft acknowledgement.
The camera label says **live** only when `ElfieVideo` reports decoded frames.
No interface element invents battery, altitude, position or landing measurements.

## Reviewing the screens

The **Android UI** pull-request workflow runs an offline emulator navigation test.
It checks preference persistence across activity recreation, route drawing and
clearing, and disabled takeoff, landing and motor stop while disconnected. It
does not connect to an aircraft. Its `droni-ui-review` artifact includes six PNGs
and the instrumentation report.

Run the same checks with an attached emulator:

```bash
gradle connectedDebugAndroidTest
adb pull /sdcard/Download/droni-ui
```

For device review, check compact landscape sizes, larger font settings, camera
readability in daylight, playback/share handlers, and physical joystick response.
