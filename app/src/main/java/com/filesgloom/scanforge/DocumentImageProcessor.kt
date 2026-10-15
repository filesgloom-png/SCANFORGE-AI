package com.filesgloom.scanforge

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot

/**
 * On-device document boundary detection and perspective correction.
 * Returns null when no convincing page-like four-corner contour is found;
 * callers should then keep the original photo rather than crop an internal region.
 */
object DocumentImageProcessor {
    fun initialize(): Boolean = OpenCVLoader.initLocal()

    fun autoCorrect(source: Bitmap): Bitmap? {
        if (source.width < 32 || source.height < 32 || !OpenCVLoader.initLocal()) return null

        val rgba = Mat()
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()
        val quadrilaterals = mutableListOf<Array<Point>>()

        return try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 55.0, 165.0)
            val dilationKernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                Size(3.0, 3.0)
            )
            try {
                Imgproc.dilate(edges, edges, dilationKernel)
            } finally {
                dilationKernel.release()
            }

            // Internal table/grid lines must not outrank the paper boundary.
            Imgproc.findContours(
                edges,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            contours.forEach { contour ->
                val curve = MatOfPoint2f(*contour.toArray())
                val approximation = MatOfPoint2f()
                try {
                    val perimeter = Imgproc.arcLength(curve, true)
                    Imgproc.approxPolyDP(curve, approximation, perimeter * 0.02, true)
                    val points = approximation.toArray()
                    if (points.size == 4) quadrilaterals.add(points)
                } finally {
                    curve.release()
                    approximation.release()
                }
            }

            val corners = selectBestQuadrilateral(
                quadrilaterals,
                source.width.toDouble() * source.height
            )?.toTypedArray() ?: return null

            val width = maxOf(distance(corners[0], corners[1]), distance(corners[3], corners[2])).toInt()
            val height = maxOf(distance(corners[0], corners[3]), distance(corners[1], corners[2])).toInt()
            if (width < 32 || height < 32) return null

            val sourcePoints = MatOfPoint2f(*corners)
            val destinationPoints = MatOfPoint2f(
                Point(0.0, 0.0),
                Point((width - 1).toDouble(), 0.0),
                Point((width - 1).toDouble(), (height - 1).toDouble()),
                Point(0.0, (height - 1).toDouble())
            )
            val corrected = Mat()
            try {
                val transform = Imgproc.getPerspectiveTransform(sourcePoints, destinationPoints)
                try {
                    Imgproc.warpPerspective(
                        rgba,
                        corrected,
                        transform,
                        Size(width.toDouble(), height.toDouble()),
                        Imgproc.INTER_CUBIC,
                        Core.BORDER_REPLICATE
                    )
                    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        Utils.matToBitmap(corrected, output)
                        return output
                    } catch (error: Exception) {
                        output.recycle()
                        throw error
                    }
                } finally {
                    transform.release()
                }
            } finally {
                sourcePoints.release()
                destinationPoints.release()
                corrected.release()
            }
        } catch (_: Exception) {
            null
        } finally {
            contours.forEach { it.release() }
            rgba.release()
            gray.release()
            blurred.release()
            edges.release()
            hierarchy.release()
        }
    }

    /**
     * Detects the page in a camera frame without creating a corrected bitmap.
     * Coordinates are normalized to the supplied bitmap; null means the frame
     * does not contain a sufficiently large, page-like external contour.
     */
    fun detectDocumentCorners(source: Bitmap): DocumentDetection? {
        if (source.width < 32 || source.height < 32 || !OpenCVLoader.initLocal()) return null

        val rgba = Mat()
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()
        val candidates = mutableListOf<Array<Point>>()
        return try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 55.0, 165.0)
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            try {
                Imgproc.dilate(edges, edges, kernel)
            } finally {
                kernel.release()
            }
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            contours.forEach { contour ->
                val curve = MatOfPoint2f(*contour.toArray())
                val approximation = MatOfPoint2f()
                try {
                    Imgproc.approxPolyDP(curve, approximation, Imgproc.arcLength(curve, true) * 0.02, true)
                    val points = approximation.toArray()
                    if (points.size == 4) candidates.add(points)
                } finally {
                    curve.release()
                    approximation.release()
                }
            }
            val ordered = selectBestQuadrilateral(candidates, source.width.toDouble() * source.height)
                ?: return null
            val areaRatio = polygonArea(ordered.toTypedArray()) / (source.width.toDouble() * source.height)
            if (areaRatio < 0.12 || areaRatio > 0.98) return null
            val normalized = ordered.map { Point(it.x / source.width, it.y / source.height) }
            val confidence = (0.65 + (areaRatio.coerceIn(0.12, 0.85) - 0.12) * 0.25).coerceIn(0.0, 0.95)
            DocumentDetection(normalized, confidence)
        } catch (_: Exception) {
            null
        } finally {
            contours.forEach { it.release() }
            rgba.release()
            gray.release()
            blurred.release()
            edges.release()
            hierarchy.release()
        }
    }

    internal fun selectBestQuadrilateral(
        candidates: List<Array<Point>>,
        imageArea: Double
    ): List<Point>? {
        if (imageArea <= 0.0) return null
        val minimumArea = imageArea * 0.12
        val best = candidates.asSequence()
            .filter { it.size == 4 && isConvexQuadrilateral(it) }
            .filter { hasPlausibleDocumentAspect(it) }
            .map { points -> points to polygonArea(points) }
            .filter { (_, area) -> area > minimumArea }
            .maxByOrNull { it.second }
            ?.first ?: return null
        return orderCorners(best)
    }

    /**
     * A very narrow internal table/column is not a safe substitute for a page.
     * Reject it so the caller preserves the full camera image instead of
     * silently producing a partial scan. The limits allow portrait and landscape
     * pages with moderate perspective distortion.
     */
    private fun hasPlausibleDocumentAspect(points: Array<Point>): Boolean {
        if (points.size != 4) return false
        val width = (distance(points[0], points[1]) + distance(points[2], points[3])) / 2.0
        val height = (distance(points[1], points[2]) + distance(points[3], points[0])) / 2.0
        if (width <= 0.0 || height <= 0.0) return false
        val ratio = width / height
        return ratio in 0.38..2.65
    }

    private fun polygonArea(points: Array<Point>): Double {
        var twiceArea = 0.0
        for (index in points.indices) {
            val current = points[index]
            val next = points[(index + 1) % points.size]
            twiceArea += current.x * next.y - next.x * current.y
        }
        return kotlin.math.abs(twiceArea) / 2.0
    }

    private fun isConvexQuadrilateral(points: Array<Point>): Boolean {
        if (points.size != 4) return false
        var direction = 0
        for (index in points.indices) {
            val a = points[index]
            val b = points[(index + 1) % points.size]
            val c = points[(index + 2) % points.size]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (kotlin.math.abs(cross) < 1e-6) return false
            val currentDirection = if (cross > 0.0) 1 else -1
            if (direction != 0 && currentDirection != direction) return false
            direction = currentDirection
        }
        return true
    }

    internal fun orderCorners(points: Array<Point>): List<Point> {
        require(points.size == 4) { "Exactly four corners are required" }
        val topLeft = points.minBy { it.x + it.y }
        val bottomRight = points.maxBy { it.x + it.y }
        val topRight = points.minBy { it.y - it.x }
        val bottomLeft = points.maxBy { it.y - it.x }
        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun distance(first: Point, second: Point): Double =
        hypot(first.x - second.x, first.y - second.y)
}
