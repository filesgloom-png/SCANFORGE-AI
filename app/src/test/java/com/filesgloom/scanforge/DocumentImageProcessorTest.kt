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
    fun selectBestQuadrilateralRejectsNarrowInternalTableInsteadOfCroppingIt() {
        val narrowTable = arrayOf(
            Point(100.0, 0.0),
            Point(150.0, 0.0),
            Point(150.0, 400.0),
            Point(100.0, 400.0)
        )

        assertNull(
            DocumentImageProcessor.selectBestQuadrilateral(
                listOf(narrowTable),
                100_000.0
            )
        )
    }

    @Test
    fun selectBestQuadrilateralChoosesLargestValidDocumentAndOrdersItsCorners() {
        val smaller = rectangle(10.0, 10.0, 150.0, 150.0)
        val larger = arrayOf(
            Point(250.0, 220.0),
            Point(20.0, 220.0),
            Point(30.0, 20.0),
            Point(240.0, 20.0)
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


    @Test
    fun selectBestQuadrilateralAcceptsPortraitPage() {
        val portrait = rectangle(40.0, 10.0, 260.0, 390.0)

        val selected = DocumentImageProcessor.selectBestQuadrilateral(
            listOf(portrait),
            400.0 * 400.0
        )

        assertNotNull(selected)
        assertEquals(4, selected!!.size)
        assertPoint(selected[0], 40.0, 10.0)
        assertPoint(selected[1], 260.0, 10.0)
        assertPoint(selected[2], 260.0, 390.0)
        assertPoint(selected[3], 40.0, 390.0)
    }

    @Test
    fun selectBestQuadrilateralAcceptsLandscapePage() {
        val landscape = rectangle(10.0, 60.0, 390.0, 280.0)

        val selected = DocumentImageProcessor.selectBestQuadrilateral(
            listOf(landscape),
            400.0 * 320.0
        )

        assertNotNull(selected)
        assertEquals(4, selected!!.size)
        assertPoint(selected[0], 10.0, 60.0)
        assertPoint(selected[1], 390.0, 60.0)
        assertPoint(selected[2], 390.0, 280.0)
        assertPoint(selected[3], 10.0, 280.0)
    }

    @Test
    fun selectBestQuadrilateralReturnsNullWhenNoVisiblePageBoundaryExists() {
        // Text lines or an empty image do not provide a trustworthy page contour.
        assertNull(
            DocumentImageProcessor.selectBestQuadrilateral(
                emptyList(),
                400.0 * 600.0
            )
        )
    }

    @Test
    fun selectBestQuadrilateralPrefersWholePageOverInternalTable() {
        val internalTable = rectangle(140.0, 30.0, 260.0, 370.0)
        val wholePortraitPage = rectangle(40.0, 10.0, 360.0, 390.0)

        val selected = DocumentImageProcessor.selectBestQuadrilateral(
            listOf(internalTable, wholePortraitPage),
            400.0 * 400.0
        )

        assertNotNull(selected)
        assertPoint(selected!![0], 40.0, 10.0)
        assertPoint(selected[1], 360.0, 10.0)
        assertPoint(selected[2], 360.0, 390.0)
        assertPoint(selected[3], 40.0, 390.0)
    }

    @Test
    fun geometryConfidenceRewardsAWellShapedPage() {
        val page = rectangle(20.0, 20.0, 380.0, 380.0)
        val confidence = DocumentImageProcessor.scoreQuadrilateralConfidence(page, 400.0 * 400.0)
        assertEquals(true, confidence >= 0.80)
    }

    @Test
    fun geometryConfidencePenalizesSeverelySkewedPage() {
        val skewed = arrayOf(
            Point(20.0, 20.0),
            Point(370.0, 35.0),
            Point(390.0, 380.0),
            Point(100.0, 300.0)
        )
        val confidence = DocumentImageProcessor.scoreQuadrilateralConfidence(skewed, 400.0 * 400.0)
        assertEquals(true, confidence < 0.75)
    }

    @Test
    fun geometryConfidenceRejectsLowAreaAndInvalidGeometry() {
        val tiny = rectangle(10.0, 10.0, 100.0, 100.0)
        val concave = arrayOf(
            Point(10.0, 10.0), Point(390.0, 10.0),
            Point(80.0, 80.0), Point(10.0, 390.0)
        )
        assertEquals(0.0, DocumentImageProcessor.scoreQuadrilateralConfidence(tiny, 400.0 * 400.0), 0.0001)
        assertEquals(0.0, DocumentImageProcessor.scoreQuadrilateralConfidence(concave, 400.0 * 400.0), 0.0001)
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
