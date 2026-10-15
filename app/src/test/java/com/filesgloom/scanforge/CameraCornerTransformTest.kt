package com.filesgloom.scanforge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCornerTransformTest {
    private val corners = listOf(
        0.1f to 0.15f,
        0.8f to 0.1f,
        0.9f to 0.85f,
        0.2f to 0.9f
    )

    @Test
    fun keepsFourCornersOrderedForZeroDegreeRotation() {
        val result = transformDisplayCorners(corners, 0)
        assertEquals(4, result.size)
        assertCorners(result, corners)
    }

    @Test
    fun rotatesNormalizedCornersAtNinetyDegreesWithoutAmbiguousResorting() {
        assertCorners(
            transformDisplayCorners(corners, 90),
            listOf(0.1f to 0.2f, 0.85f to 0.1f, 0.9f to 0.8f, 0.15f to 0.9f)
        )
    }

    @Test
    fun rotatesNormalizedCornersAtOneHundredEightyDegrees() {
        assertCorners(
            transformDisplayCorners(corners, 180),
            listOf(0.1f to 0.15f, 0.8f to 0.1f, 0.9f to 0.85f, 0.2f to 0.9f)
        )
    }

    @Test
    fun rotatesNormalizedCornersAtTwoHundredSeventyDegrees() {
        val result = transformDisplayCorners(corners, 270)
        assertCorners(
            result,
            listOf(0.1f to 0.2f, 0.85f to 0.1f, 0.9f to 0.8f, 0.15f to 0.9f)
        )
        assertTrue(result.all { (x, y) -> x in 0f..1f && y in 0f..1f })
    }

    @Test
    fun rejectsUnexpectedCornerCount() {
        assertTrue(transformDisplayCorners(corners.take(3), 90).isEmpty())
        assertTrue(orderDisplayCorners(emptyList()).isEmpty())
    }

    @Test
    fun fillCenterMappingCompensatesForHorizontalCrop() {
        val points = listOf(
            0.1f to 0.1f, 0.9f to 0.1f, 0.9f to 0.9f, 0.1f to 0.9f
        )
        val mapped = mapCornersToPreview(points, 0, 1920, 1080, 400, 800)
        assertEquals(4, mapped.size)
        assertTrue(mapped.first().first < 0f)
        assertTrue(mapped[1].first > 1f)
        assertCorners(mapped.map { it.second to it.first }, listOf(
            0.1f to mapped[0].first, 0.1f to mapped[1].first,
            0.9f to mapped[2].first, 0.9f to mapped[3].first
        ))
    }

    @Test
    fun invalidPreviewDimensionsReturnNoMappedCorners() {
        assertTrue(mapCornersToPreview(corners, 0, 640, 480, 0, 480).isEmpty())
    }

    @Test
    fun failedCorrectionKeepsOriginalPhoto() {
        val original = java.io.File("original.jpg")
        val corrected = java.io.File("corrected.jpg")
        assertEquals(original, selectPhotoAfterCorrection(original, corrected, false))
        assertEquals(corrected, selectPhotoAfterCorrection(original, corrected, true))
    }

    private fun assertCorners(actual: List<Pair<Float, Float>>, expected: List<Pair<Float, Float>>) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { index ->
            assertEquals(expected[index].first, actual[index].first, 0.0001f)
            assertEquals(expected[index].second, actual[index].second, 0.0001f)
        }
    }
}
