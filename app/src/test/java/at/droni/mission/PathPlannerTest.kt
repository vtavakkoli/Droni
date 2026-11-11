package at.droni.mission

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class PathPlannerTest {
    @Test fun ignoresEmptyAndSinglePoint() {
        assertTrue(PathPlanner.plan(emptyList()).isEmpty())
        assertTrue(PathPlanner.plan(listOf(PathPoint(.1f, .2f))).isEmpty())
    }
    @Test fun limitsLengthAndStrength() {
        val points = (0..200).map { i ->
            PathPoint((i % 2).toFloat(), i.toFloat() / 200f)
        }
        val result = PathPlanner.plan(points)
        assertTrue(result.isNotEmpty())
        assertTrue(result.size <= PathPlanner.MAX_STEPS)
        assertTrue(result.all { hypot(it.roll, it.pitch) <= .7501f })
        assertTrue(result.all { it.durationMs <= 180L })
    }
    @Test fun rightAndUpArePositiveCommands() {
        val right = PathPlanner.plan(listOf(PathPoint(.1f, .5f), PathPoint(.9f, .5f)))
        assertTrue(right.all { abs(it.roll - .75f) < .001f && abs(it.pitch) < .001f })
        val up = PathPlanner.plan(listOf(PathPoint(.5f, .9f), PathPoint(.5f, .1f)))
        assertTrue(up.all { abs(it.pitch - .75f) < .001f && abs(it.roll) < .001f })
    }
    @Test fun diagonalMovementUsesConfiguredMagnitude() {
        val points = listOf(PathPoint(.1f, .9f), PathPoint(.9f, .1f))
        for (percent in listOf(25, 75)) {
            val steps = PathPlanner.plan(points, percent)
            assertTrue(steps.isNotEmpty())
            assertTrue(steps.all { abs(hypot(it.roll, it.pitch) - percent / 100f) < .0001f })
        }
    }
    @Test fun rejectInvalidInputWithoutNaN() {
        val out = PathPlanner.plan(listOf(PathPoint(Float.NaN, .1f),
            PathPoint(.1f, .1f), PathPoint(.8f, .1f)))
        assertTrue(out.all { it.roll.isFinite() && it.pitch.isFinite() })
    }
}
