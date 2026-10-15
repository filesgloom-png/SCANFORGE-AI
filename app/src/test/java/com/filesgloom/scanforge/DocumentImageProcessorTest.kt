package com.filesgloom.scanforge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.opencv.core.Point

class DocumentImageProcessorTest {

    @Test
    fun orderCornersReturnsTopLeftTopRightBottomRightBottomLeft() {
        val unordered = arrayOf(
            Point(100.0, 100.0),
            Point(0.0, 100.0),
            Point(100.0, 0.0),
            Point(0.0, 0.0)
        )

        val ordered = DocumentImageProcessor.orderCorners(unordered)

        assertPoint(ordered[0], 0.0, 0.0)
        assertPoint(ordered[1], 100.0, 0.0)
        assertPoint(ordered[2], 100.0, 100.0)
        assertPoint(ordered[3], 0.0, 100.0)
    }

    @Test
    fun selectBestQuadrilateralReturnsNullWhenNoDocumentContourExists() {
        assertNull(DocumentImageProcessor.selectBestQuadrilateral(emptyList(), 100_000.0))
    }

    @Test
    fun selectBestQuadrilateralRejectsSmallOrConcaveContours() {
        val tooSmall = arrayOf(
            Point(0.0, 0.0),
            Point(20.0, 0.0),
            Point(20.0, 20.0),
            Point(0.0, 20.0)
        )
        val concave = arrayOf(
            Point(0.0, 0.0),
            Point(200.0, 0.0),
            Point(70.0, 40.0),
            Point(0.0, 200.0)
        )

        assertNull(
            DocumentImageProcessor.selectBestQuadrilateral(
                listOf(tooSmall, concave),
                100_000.0
            )
        )
    }

    @Test
    fun selectBestQuadrilateralChoosesLargestValidDocumentAndOrdersItsCorners() {
        val smaller = rectangle(10.0, 10.0, 150.0, 150.0)
        val larger = arrayOf(
            Point(250.0, 220.0),
            Point(30.0, 20.0),
            Point(240.0, 20.0),
            Point(20.0, 220.0)
        )

        val selected = DocumentImageProcessor.selectBestQuadrilateral(
            listOf(smaller, larger),
            100_000.0
        )

        assertNotNull(selected)
        assertEquals(4, selected!!.size)
        assertPoint(selected[0], 30.0, 20.0)
        assertPoint(selected[1], 240.0, 20.0)
        assertPoint(selected[2], 250.0, 220.0)
        assertPoint(selected[3], 20.0, 220.0)
    }

    private fun rectangle(left: Double, top: Double, right: Double, bottom: Double) = arrayOf(
        Point(left, top),
        Point(right, top),
        Point(right, bottom),
        Point(left, bottom)
    )

    private fun assertPoint(actual: Point, x: Double, y: Double) {
        assertEquals(x, actual.x, 0.001)
        assertEquals(y, actual.y, 0.001)
    }
}
