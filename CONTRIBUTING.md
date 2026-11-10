# Contributing to Droni

Droni is an independent open-source flight-control research project.

1. Open an issue with drone model, firmware (if known), Android version,
   expected behavior, actual behavior, and safe reproduction details.
2. Keep protocol changes backed by captured packet traces or regression tests.
3. Run `gradle testDebugUnitTest assembleDebug` and attach CI results.
4. Do not add unsupported claims of IMU, GPS, barometer, autonomous precision,
   obstacle avoidance or acknowledged landing.
5. Avoid introducing unnecessary permissions, network dependencies, trackers
   or bundled native codecs.
6. Never run newly modified motor/throttle code on a powered drone with
   propellers attached before a propeller-off bench validation.

All contributions should follow the MIT license and preserve attribution:
**Copyright (c) 2026 Dr. Vahid Tavakkoli**.
