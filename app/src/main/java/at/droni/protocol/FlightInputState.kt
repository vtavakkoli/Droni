package at.droni.protocol

import java.util.concurrent.atomic.AtomicReference

/** Atomically keeps each command flag with its deadline, independently of UDP tick timing. */
internal class FlightInputState(initial: ElfieProtocol.Input) {
    companion object { const val COMMAND_PULSE_MS = 1_000L }

    private data class State(
        val input: ElfieProtocol.Input,
        val commandUntilMs: Long = Long.MIN_VALUE,
        val axesUntilMs: Long = Long.MAX_VALUE
    ) {
        fun at(nowMs: Long): State {
            var result = this
            if (nowMs >= commandUntilMs) result = result.copy(
                input = result.input.withoutCommands(), commandUntilMs = Long.MAX_VALUE)
            if (nowMs >= axesUntilMs) result = result.copy(
                input = result.input.neutralAxes(), axesUntilMs = Long.MAX_VALUE)
            return result
        }
    }

    private val state = AtomicReference(State(initial))

    fun read(nowMs: Long): ElfieProtocol.Input = state.updateAndGet { it.at(nowMs) }.input

    fun update(nowMs: Long, transform: (ElfieProtocol.Input) -> ElfieProtocol.Input) {
        state.updateAndGet { old -> old.at(nowMs).let { it.copy(input = transform(it.input)) } }
    }

    fun pulse(nowMs: Long, command: (ElfieProtocol.Input) -> ElfieProtocol.Input) {
        state.updateAndGet { old ->
            State(command(old.input.neutralAxes().withoutCommands()), nowMs + COMMAND_PULSE_MS)
        }
    }

    /** The UDP sender enforces this expiry even if the UI thread cannot resume its delay on time. */
    fun climbPulse(nowMs: Long, ascent: Float, durationMs: Long) {
        require(ascent in 0f..1f && durationMs in 50L..1_000L)
        state.updateAndGet { old ->
            val current = old.at(nowMs)
            check(!current.input.takeoff && !current.input.land && !current.input.emergencyStop)
            current.copy(input = current.input.neutralAxes().copy(throttle = ascent),
                axesUntilMs = nowMs + durationMs)
        }
    }

    fun reset(input: ElfieProtocol.Input) { state.set(State(input)) }
}

private fun ElfieProtocol.Input.withoutCommands() = copy(
    takeoff = false, land = false, emergencyStop = false, calibrate = false
)
