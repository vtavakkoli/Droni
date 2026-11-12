# Flight Deck comparison with the supplied original app

Reference: `classes.dex_Decompiler.com(2).zip` (SHA-256 `f6020db6f91f90a496cb72eab8e2ae1bc187ca82c2c9795b6524113d415ba2e5`). Reviewed on 2026-11-12. This is a source-level comparison, not a physical flight test. The archive supports multiple drone/camera families; its presence does not identify the firmware installed on a particular drone.

The primary references are `com/vison/macrochip/activity/ControlActivity.java` (`SendThread.run`, `onClick`, and `myHandler`), `com/vison/macrochip/MyApplication.java` (`isContraryDirection`), `com/vison/baselibrary/helper/FunctionHelper.java` (`switchRev`), and `com/vison/baselibrary/base/BaseApplication.java` (`writeUDPCmd`). The supplied reference sources are not redistributed in this repository.

## Core flight behavior

| Original behavior | Droni result |
| :-- | :-- |
| 11-byte packet, header `FF 08`, UDP destination 8080 | Same packet format and destination |
| Complement checksum: bytes 1 through 10 sum to `FF` modulo 256 | Same checksum; source-derived reference vectors cover flags, trims and axes |
| Repeated control packets every 25 ms | 25 ms fixed-delay sender; Android scheduling is not a hard real-time guarantee |
| Left stick: yaw and throttle; right stick: roll and pitch | Same axis roles and inversion in the packet formatter |
| Altitude-hold flag: byte 6, bit 7 | Set for manual takeoff; the stick returns to neutral. This is a firmware mode request, not measured position hold |
| Calibration: byte 6, bit 6, asserted for 1000 ms | Same one-second command pulse |
| Takeoff: byte 9, bit 6, asserted for 1000 ms | Same one-second command pulse; altitude hold is selected first |
| Landing: byte 9, bit 7, cleared by its delayed handler | Same one-second command pulse |
| Motor stop: byte 9, bit 5, asserted for 1000 ms | Same stop flag and pulse; cancels pending manual auto-calibration |
| Speed values 0, 1 and 2 in byte 9 | Same encoding and speed selector. Droni's saved default remains a user preference |
| Headless flag: byte 9, bit 4 | Same flag and toggle |
| Manual calibration while level and stationary | Available without queuing takeoff |

Droni's normalized joystick uses its existing rate zones and rounding; it does not reproduce the original widget's pixel-dependent integer quantization. The wire axis roles, neutral values, direction inversion and flag layout are covered by the protocol tests. No separate motor-arm command appears in the reviewed original takeoff handler.

## Requested automatic calibration

The original app offers a calibration button and a takeoff button. Droni adds an optional sequence:

1. Neutralize the sticks and request calibration immediately.
2. Keep the original calibration flag pulse at **1 second**.
3. Wait **3 seconds in total from the calibration request**, then request takeoff.
4. Leave altitude changes to the pilot's stick.

No takeoff request is sent before that wait completes. Cancelling, leaving the deck, disconnecting, or replacing the request prevents a delayed takeoff. With auto-calibration disabled, Take Off retains its existing direct behavior. Draw Path continues to use its own settings and sequence.

## Feature differences still present

| Original feature | Droni status |
| :-- | :-- |
| Yaw, pitch and roll trim widgets | Encoder supports trim values, but the Flight Deck currently keeps them centered at 16; no adjustable trim controls |
| 360-degree flip, byte 9 bit 2 | Not exposed; no automatic flip request is sent |
| Phone tilt steering | Not implemented; this is a phone input mode, not drone IMU telemetry |
| Camera REV / mirror | Original sends one-byte camera commands `02` or `01`; not implemented by Droni's axis reversal button |
| Firmware-specific yaw/roll reversal | The rule for `UDP_720` firmware V2.3.9, V2.4.0 and V2.4.1 matches in `reverseForFirmware`; automatic device-profile selection is not wired into the live controller. Manual axis reversal remains available and is now labeled **AXIS NORM / AXIS REV** |
| Raw throttle mode with altitude hold disabled | Encoder supports it; the Flight Deck keeps the established altitude-hold takeoff behavior |
| Still photo, VR view, gesture/video object tracking | Not included in the manual flight controls; MP4 recording remains available |

These differences mean Droni does not yet reproduce every original-app feature. Camera mirroring and flight-axis reversal are separate operations and must not be conflated. Firmware-specific compatibility, actual calibration completion, lift and touchdown still require hardware validation; incoming packets are not verified flight acknowledgements.

## Checks

- Protocol fixtures are derived from the original sender's packet fields, not inferred from displayed icons.
- Manual takeoff tests verify the three-second wait, cancellation, disconnects, superseded requests and preventing duplicate takeoff requests.
- Draw Path's existing timing and movement tests remain independent of the manual flight sequence.
