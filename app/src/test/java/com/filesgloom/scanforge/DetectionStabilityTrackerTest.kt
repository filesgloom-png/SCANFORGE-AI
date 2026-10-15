package com.filesgloom.scanforge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opencv.core.Point

class DetectionStabilityTrackerTest {
    private fun detection(xOffset: Double = 0.0, confidence: Double = 0.9) = DocumentDetection(
        listOf(
            Point(0.1 + xOffset, 0.1),
            Point(0.9 + xOffset, 0.1),
            Point(0.9 + xOffset, 0.9),
            Point(0.1 + xOffset, 0.9)
        ),
        confidence
    )

    @Test
    fun captureWaitsForFiveStableHighConfidenceFrames() {
        val tracker = DetectionStabilityTracker(requiredFrames = 5)
        repeat(4) { assertFalse(tracker.observe(detection())) }
        assertTrue(tracker.observe(detection()))
        assertFalse(tracker.observe(detection()))
    }

    @Test
    fun lowConfidenceOrMissingDetectionResetsStability() {
        val tracker = DetectionStabilityTracker(requiredFrames = 3)
        assertFalse(tracker.observe(detection()))
        assertFalse(tracker.observe(detection()))
        assertFalse(tracker.observe(detection(confidence = 0.5)))
        assertFalse(tracker.observe(detection()))
        assertFalse(tracker.observe(null))
        assertFalse(tracker.observe(detection()))
        assertFalse(tracker.observe(detection()))
        assertTrue(tracker.observe(detection()))
    }

    @Test
    fun movingCornersRestartTheStableFrameSequence() {
        val tracker = DetectionStabilityTracker(requiredFrames = 3)
        assertFalse(tracker.observe(detection()))
        assertFalse(tracker.observe(detection()))
        assertFalse(tracker.observe(detection(xOffset = 0.1)))
        assertFalse(tracker.observe(detection(xOffset = 0.1)))
        assertTrue(tracker.observe(detection(xOffset = 0.1)))
    }

    @Test
    fun jitterWithinMovementToleranceStillAllowsCapture() {
        val tracker = DetectionStabilityTracker(requiredFrames = 4, maximumCornerMovement = 0.025)
        assertFalse(tracker.observe(detection()))
        assertFalse(tracker.observe(detection(xOffset = 0.005)))
        assertFalse(tracker.observe(detection(xOffset = 0.009)))
        assertTrue(tracker.observe(detection(xOffset = 0.012)))
    }

    @Test
    fun confidenceExactlyAtThresholdCanBecomeStable() {
        val tracker = DetectionStabilityTracker(requiredFrames = 2, minimumConfidence = 0.75)
        assertFalse(tracker.observe(detection(confidence = 0.75)))
        assertTrue(tracker.observe(detection(confidence = 0.75)))
    }

    @Test
    fun captureIsIssuedOnlyOnceUntilResetEvenWithStableFrames() {
        val tracker = DetectionStabilityTracker(requiredFrames = 2)
        assertFalse(tracker.observe(detection()))
        assertTrue(tracker.observe(detection()))
        repeat(10) { assertFalse(tracker.observe(detection())) }
        tracker.reset()
        assertFalse(tracker.observe(detection()))
        assertTrue(tracker.observe(detection()))
    }

    @Test
    fun resetAllowsASecondCaptureSequence() {
        val tracker = DetectionStabilityTracker(requiredFrames = 2)
        assertFalse(tracker.observe(detection()))
        assertTrue(tracker.observe(detection()))
        tracker.reset()
        assertFalse(tracker.observe(detection()))
        assertTrue(tracker.observe(detection()))
    }
}
