package com.iman.onestateaimcoach;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AimMathTest {
    @Test
    public void deadzoneReturnsNull() {
        AimMath.Vec2 result = AimMath.aimDelta(
                new AimMath.Vec2(1f, 0f),
                new AimMath.Vec2(0f, 0f),
                1f,
                1f,
                20f);
        assertNull(result);
    }

    @Test
    public void scalesBySensitivity() {
        AimMath.Vec2 result = AimMath.aimDelta(
                new AimMath.Vec2(4f, 2f),
                new AimMath.Vec2(0f, 0f),
                0.5f,
                0f,
                100f);
        assertEquals(2f, result.x, 0.0001f);
        assertEquals(1f, result.y, 0.0001f);
    }

    @Test
    public void clampsMagnitudeToMaximumSwipe() {
        AimMath.Vec2 result = AimMath.aimDelta(
                new AimMath.Vec2(10f, 0f),
                new AimMath.Vec2(0f, 0f),
                1f,
                0f,
                3f);
        assertEquals(3f, result.x, 0.0001f);
        assertEquals(0f, result.y, 0.0001f);
    }

    @Test
    public void coneBoundaryIsInclusive() {
        assertEquals(true, AimMath.inCone(
                new AimMath.Vec2(3f, 4f),
                new AimMath.Vec2(0f, 0f),
                5f));
    }
}
