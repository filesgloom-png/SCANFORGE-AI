package com.filesgloom.scanforge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCornerTransformTest {
    private val corners = listOf(
        0.1f to 0.1f,
        0.9f to 0.1f,
        0.9f to 0.9f,
        0.1f to 0.9f
    )

    @Test
    fun keepsFourCornersOrderedForZeroDegreeRotation() {
        val result = transformDisplayCorners(corners, 0)
        assertEquals(4, result.size)
        assertEquals(0.1f to 0.1f, result[0])
        assertEquals(0.9f to 0.1f, result[1])
        assertEquals(0.9f to 0.9f, result[2])
        assertEquals(0.1f to 0.9f, result[3])
    }

    @Test
    fun rotatesNormalizedCornersAtNinetyDegrees() {
        val result = transformDisplayCorners(corners, 90)
        assertEquals(4, result.size)
        assertEquals(0.9f to 0.1f, result[0])
        assertEquals(0.9f to 0.9f, result[1])
        assertEquals(0.1f to 0.9f, result[2])
        assertEquals(0.1f to 0.1f, result[3])
    }

    @Test
    fun rotatesNormalizedCornersAtOneHundredEightyDegrees() {
        val result = transformDisplayCorners(corners, 180)
        assertEquals(4, result.size)
        assertEquals(0.1f to 0.1f, result[0])
        assertEquals(0.9f to 0.1f, result[1])
        assertEquals(0.9f to 0.9f, result[2])
        assertEquals(0.1f to 0.9f, result[3])
    }

    @Test
    fun rotatesNormalizedCornersAtTwoHundredSeventyDegrees() {
        val result = transformDisplayCorners(corners, 270)
        assertEquals(4, result.size)
        assertTrue(result.all { (x, y) -> x in 0f..1f && y in 0f..1f })
    }

    @Test
    fun rejectsUnexpectedCornerCount() {
        assertTrue(transformDisplayCorners(corners.take(3), 90).isEmpty())
        assertTrue(orderDisplayCorners(emptyList()).isEmpty())
    }
}
