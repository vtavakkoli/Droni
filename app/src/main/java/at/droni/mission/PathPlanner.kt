package at.droni.mission

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min

/** A line on the touchscreen, not an inertial/GPS trajectory.
 * Written by Dr. Vahid Tavakkoli (c) 2026.
 */
data class PathPoint(val x: Float, val y: Float) {
    fun bounded() = PathPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}
data class MissionStep(val roll: Float, val pitch: Float, val durationMs: Long = 180L)

object PathPlanner {
    const val MAX_STEPS = 24
    const val MAX_DEFLECTION = 0.16f
    const val REST_MS = 200L

    /**
     * Translate drawn screen directions to bounded, open-loop roll/pitch pulses.
     * Positions on the drawing DO NOT correspond to real-world meters.
     * No telemetry exists to close the loop; it will not track the drawn coordinates.
     */
    fun plan(input: List<PathPoint>): List<MissionStep> {
        val clean = input.filter { it.x.isFinite() && it.y.isFinite() }
            .map { it.bounded() }
            .fold(mutableListOf<PathPoint>()) { out, p ->
                if (out.isEmpty() || distance(out.last(), p) > 0.004f) out.add(p)
                out
            }
        if (clean.size < 2) return emptyList()
        val lengths = mutableListOf(0f)
        for (i in 1 until clean.size) {
            lengths += lengths.last() + distance(clean[i - 1], clean[i])
        }
        val total = lengths.last()
        if (total < 0.025f) return emptyList()
        val count = min(MAX_STEPS, ceil(total / 0.07f).toInt().coerceAtLeast(1))
        val steps = mutableListOf<MissionStep>()
        for (i in 0 until count) {
            val start = interpolate(clean, lengths, total * i / count)
            val end = interpolate(clean, lengths, total * (i + 1) / count)
            val dx = end.x - start.x
            val dy = end.y - start.y
            val len = hypot(dx, dy)
            if (len < 1e-5f) continue
            steps += MissionStep(
                roll = (dx / len) * MAX_DEFLECTION,
                pitch = (-dy / len) * MAX_DEFLECTION,
            )
        }
        return steps
    }

    private fun distance(a: PathPoint, b: PathPoint) =
        hypot(a.x - b.x, a.y - b.y)

    private fun interpolate(points: List<PathPoint>, cumulative: List<Float>, at: Float): PathPoint {
        for (i in 1 until points.size) {
            if (at <= cumulative[i]) {
                val span = cumulative[i] - cumulative[i - 1]
                val fraction = if (span < 1e-6f) 0f else
                    ((at - cumulative[i - 1]) / span).coerceIn(0f, 1f)
                return PathPoint(
                    points[i - 1].x + (points[i].x - points[i - 1].x) * fraction,
                    points[i - 1].y + (points[i].y - points[i - 1].y) * fraction,
                )
            }
        }
        return points.last()
    }
}
