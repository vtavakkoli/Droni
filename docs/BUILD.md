# Building and testing Droni

Written by Dr. Vahid Tavakkoli.

## Requirements
- Android Studio with JDK 17 and Android SDK Platform 36
- Gradle 9.5.0 and Android Gradle Plugin 9.3.1
- Kotlin Compose Compiler 2.4.21 and Compose BOM 2026.06.01 (compatible with API 36)
- An Android 8.0+ phone with Wi-Fi

The repo includes Gradle wrapper configuration but does not contain the binary
`gradle-wrapper.jar`. This intentionally avoids shipping an unverified binary.
To generate a standard wrapper locally with installed Gradle 9.5.0:

```bash
gradle wrapper --gradle-version 9.5.0
./gradlew testDebugUnitTest assembleDebug
```

Alternatively, open the project in Android Studio, use its Gradle support to
sync/build, and generate the wrapper with the Gradle task. The first sync requires
internet access to download dependencies; the drone Wi-Fi typically has none.
Build **before** connecting the phone to the drone network.

## Validation
- `testDebugUnitTest` checks exact, known 11-byte Android protocol vectors.
- `assembleDebug` compiles an installable debug APK.
- Physical takeoff, video stream, firmware variants, crash recovery, and wireless
  timing must be validated against the actual JJRC Elfie.
- For a propeller-off bench test, first verify movement packets on a simulator
  or local UDP capture; do not assume the UI's UDP counter is an acknowledgement.
