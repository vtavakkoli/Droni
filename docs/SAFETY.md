# Flight safety, sensor evidence and experimental missions

> Droni is experimental software. It is not a certified flight controller.

## Evidence

The `FF 08` 11-byte command format was verified against original Android/Python
packet tests. UDP sends are **unacknowledged**: a green network indicator is not
confirmation that the drone is airborne, stationary, or landed.

The previous sensor probe did not verify remotely readable accelerometer,
gyroscope, barometric altitude or heading telemetry. The phone's accelerometer
is not the drone's IMU. Sending the calibration flag is *not* feedback.

## Path screen

The user's drawing is a normalized screen-space polyline; it has no mapping
to real-world meters, local compass coordinates, obstacle distance, or GPS.

- **SIMULATE**: no control packets, safe offline demonstration.
- **EXPERIMENTAL TRIAL**: if disconnected, auto-connect and wait 10 seconds;
  apply speed 1, request calibration and wait 5 seconds; request takeoff and wait
  5 seconds; 100% upward stick for 200 ms, then neutral. The same configurable
  launch settings are used in Drone Fly. Then at most 24 roll/pitch pulses,
  16% of the joystick range, 180 ms each with 200 ms neutral gaps, followed by
  neutral axes, a landing request and a 5-second response check.
- **Command timing**: command flags expire before new axes are accepted. New
  command pulses clear previous flags. The UDP sender also expires the timed
  climb input, without depending on the UI coroutine to resume on time.
- **Responses**: received packets on the control socket or local UDP 8081 are
  counted and recorded by phase. There is no verified flight-ACK decoder. A
  packet or device identity never confirms calibration, takeoff or touchdown.
- **Abort**: cancels sending future commands, sends neutral axes and LAND.
- **Lifecycle**: leaving the screen requests LAND if a trial is active. Leaving
  the app terminates the link, but a neutral packet is *not* a guaranteed landing.

The command durations and stick percentages cannot predict distance. The
aircraft may drift, overshoot, crash or fail to land. Do not fly such
experimental trajectories near people, buildings, obstacles or property.
Propeller-off bench testing is mandatory before even considering a field test.
Use only where flight is legal and keep line of sight.

## Path following requirements for a real implementation

You would need verified position/velocity/heading estimation (e.g. calibrated
visual-inertial odometry or optical flow + rangefinder), time synchronization,
robust Wi‑Fi link monitoring, failsafe behavior, obstacle avoidance and an
independent emergency override. IMU-only integration drifts. The firmware's
altitude-hold mode is not 3D position hold.

## Camera

The MP4 recorder uses the received H.264 elementary stream. Actual frame
boundaries, codec dimensions and timestamps depend on firmware and must be
tested with the physical aircraft. Recording must be stopped normally to
finalize the file; forced power loss may corrupt an MP4.

**Copyright (c) 2026 Dr. Vahid Tavakkoli.**
