package at.droni.settings

/** Draw Path configuration only. Manual flight keeps its original preferences and controls. */
data class PathFlightSettings(
    val speedMode: Int = 1,
    val autoConnect: Boolean = true,
    val connectionWaitMs: Long = 5_000,
    val autoCalibrate: Boolean = true,
    val calibrationWaitMs: Long = 3_000,
    val takeoffWaitMs: Long = 3_000,
    val climbPercent: Int = 100,
    val climbDurationMs: Long = 300,
    val commandGapMs: Long = 300,
    val movementPercent: Int = 75,
    val landingWaitMs: Long = 5_000
) {
    init {
        require(speedMode in 0..2)
        require(connectionWaitMs in 1_000L..60_000L)
        require(calibrationWaitMs in 1_000L..30_000L)
        require(takeoffWaitMs in 1_000L..30_000L)
        require(climbPercent in 0..100)
        require(climbDurationMs in 50L..1_000L)
        require(commandGapMs in 25L..2_000L)
        require(movementPercent in 1..75)
        require(landingWaitMs in 1_000L..30_000L)
    }

    val summary: String get() = "Speed $speedMode · connect ${connectionWaitMs / 1000} s · " +
        "calibrate ${calibrationWaitMs / 1000} s · takeoff ${takeoffWaitMs / 1000} s · " +
        "up $climbPercent% / $climbDurationMs ms · move $movementPercent% · gap $commandGapMs ms"
}
