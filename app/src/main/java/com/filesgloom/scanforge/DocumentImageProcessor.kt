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
 * Returns null when no convincing four-corner contour is found.
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

        try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 55.0, 165.0)
            Imgproc.dilate(edges, edges, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)))

            Imgproc.findContours(
                edges,
                contours,
                hierarchy,
                Imgproc.RETR_LIST,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            contours.forEach { contour ->
                val curve = MatOfPoint2f(*contour.toArray())
                val perimeter = Imgproc.arcLength(curve, true)
                val approximation = MatOfPoint2f()
                Imgproc.approxPolyDP(curve, approximation, perimeter * 0.02, true)
                val points = approximation.toArray()
                if (points.size == 4) {
                    quadrilaterals.add(points)
                }
                curve.release()
                approximation.release()
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
            val transform = Imgproc.getPerspectiveTransform(sourcePoints, destinationPoints)
            val corrected = Mat()
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
                Utils.matToBitmap(corrected, output)
                return output
            } finally {
                sourcePoints.release()
                destinationPoints.release()
                transform.release()
                corrected.release()
            }
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
            .map { points -> points to polygonArea(points) }
            .filter { (_, area) -> area > minimumArea }
            .maxByOrNull { it.second }
            ?.first ?: return null
        return orderCorners(best)
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
