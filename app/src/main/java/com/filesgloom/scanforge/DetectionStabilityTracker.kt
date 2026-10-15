package com.filesgloom.scanforge

import org.opencv.core.Point
import kotlin.math.hypot

/**
 * Requires several consecutive high-confidence detections with nearly stationary
 * corners before automatic capture is allowed. Call reset() after a capture.
 */
internal class DetectionStabilityTracker(
    private val requiredFrames: Int = 5,
    private val minimumConfidence: Double = 0.75,
    private val maximumCornerMovement: Double = 0.025
) {
    private var previous: List<Point>? = null
    private var stableFrames = 0
    private var captureIssued = false

    fun observe(detection: DocumentDetection?): Boolean {
        if (captureIssued) return false
        if (detection == null || detection.confidence < minimumConfidence || detection.corners.size != 4) {
            resetSequence()
            return false
        }

        val prior = previous
        val movement = if (prior == null) Double.POSITIVE_INFINITY else prior.indices
            .map { index ->
                hypot(
                    prior[index].x - detection.corners[index].x,
                    prior[index].y - detection.corners[index].y
                )
            }.average()

        stableFrames = if (prior != null && movement <= maximumCornerMovement) stableFrames + 1 else 1
        previous = detection.corners.map { Point(it.x, it.y) }
        if (stableFrames >= requiredFrames) {
            captureIssued = true
            return true
        }
        return false
    }

    fun reset() {
        resetSequence()
        captureIssued = false
    }

    private fun resetSequence() {
        previous = null
        stableFrames = 0
    }
}
