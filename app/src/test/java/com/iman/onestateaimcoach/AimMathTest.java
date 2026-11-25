package com.iman.onestateaimcoach;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;

public class AimMathTest {
    @Test public void deadzoneReturnsNull() {
        assertNull(AimMath.aimDelta(new AimMath.Vec2(1f, 0f), new AimMath.Vec2(0f, 0f), 1f, 1f, 20f));
    }
    @Test public void scalesBySensitivity() {
        AimMath.Vec2 result = AimMath.aimDelta(new AimMath.Vec2(4f, 2f), new AimMath.Vec2(0f, 0f), 0.5f, 0f, 100f);
        assertEquals(2f, result.x, 0.0001f); assertEquals(1f, result.y, 0.0001f);
    }
    @Test public void clampsMagnitudeToMaximumSwipe() {
        AimMath.Vec2 result = AimMath.aimDelta(new AimMath.Vec2(10f, 0f), new AimMath.Vec2(0f, 0f), 1f, 0f, 3f);
        assertEquals(3f, result.x, 0.0001f); assertEquals(0f, result.y, 0.0001f);
    }
    @Test public void coneBoundaryIsInclusive() {
        assertEquals(true, AimMath.inCone(new AimMath.Vec2(3f, 4f), new AimMath.Vec2(0f, 0f), 5f));
    }
    @Test public void headTargetUsesUpperEighteenPercent() {
        AimMath.Vec2 point = AimMath.computeTargetPoint(0.2f, 0.1f, 0.6f, 0.9f, true);
        assertEquals(0.4f, point.x, 0.0001f); assertEquals(0.244f, point.y, 0.0001f);
    }
    @Test public void bodyTargetUsesVerticalCenter() {
        AimMath.Vec2 point = AimMath.computeTargetPoint(0.2f, 0.1f, 0.6f, 0.9f, false);
        assertEquals(0.4f, point.x, 0.0001f); assertEquals(0.5f, point.y, 0.0001f);
    }
    @Test public void smoothVectorInterpolatesTowardCurrentVector() {
        AimMath.Vec2 result = AimMath.smoothVector(new AimMath.Vec2(1f, 0.5f), new AimMath.Vec2(0f, 0f), 0.25f);
        assertEquals(0.25f, result.x, 0.0001f); assertEquals(0.125f, result.y, 0.0001f);
    }
    @Test public void smoothingAlphaIsClamped() {
        AimMath.Vec2 result = AimMath.smoothVector(new AimMath.Vec2(1f, 1f), new AimMath.Vec2(0f, 0f), 5f);
        assertEquals(1f, result.x, 0.0001f); assertEquals(1f, result.y, 0.0001f);
    }
    @Test public void mapsCropBoxIntoFullScreenCoordinates() {
        float[] b = AimMath.mapCropBoxToFullScreen(0.25f, 0.10f, 0.75f, 0.90f, 0.31f, 0.31f, 0.38f, 0.38f);
        assertEquals(0.405f, b[0], 0.0001f); assertEquals(0.348f, b[1], 0.0001f);
        assertEquals(0.595f, b[2], 0.0001f); assertEquals(0.652f, b[3], 0.0001f);
    }
    @Test public void computesIntersectionOverUnionForNms() {
        assertEquals(1f / 3f, AimMath.computeIoU(0f, 0f, 2f, 2f, 1f, 0f, 3f, 2f), 0.0001f);
    }
    @Test public void nonOverlappingBoxesHaveZeroIou() {
        assertEquals(0f, AimMath.computeIoU(0f, 0f, 1f, 1f, 1.1f, 1.1f, 2f, 2f), 0.0001f);
    }
}
    @Test public void selfCharacterFilterMatchesLowerCenterAvatar() {
        assertEquals(true, AimMath.isSelfCharacterBox(0.30f, 0.40f, 0.50f, 0.90f));
        assertEquals(true, AimMath.isSelfCharacterBox(0.34f, 0.45f, 0.54f, 0.95f));
    }

    @Test public void selfCharacterFilterAllowsDistantUpperAndLateralTargets() {
        assertFalse(AimMath.isSelfCharacterBox(0.35f, 0.10f, 0.48f, 0.40f));
        assertFalse(AimMath.isSelfCharacterBox(0.65f, 0.40f, 0.82f, 0.90f));
        assertFalse(AimMath.isSelfCharacterBox(0.10f, 0.40f, 0.25f, 0.90f));
    }

    @Test public void computesLeadPointAlongVelocity() {
        AimMath.Vec2 lead = AimMath.computeLeadPoint(new AimMath.Vec2(0.50f, 0.50f),
                0.02f, -0.01f, 2f);
        assertEquals(0.54f, lead.x, 0.0001f);
        assertEquals(0.48f, lead.y, 0.0001f);
    }

    @Test public void clampsLeadPointToNormalizedScreenBounds() {
        AimMath.Vec2 lead = AimMath.computeLeadPoint(new AimMath.Vec2(0.98f, 0.02f),
                0.10f, -0.10f, 2f);
        assertEquals(1f, lead.x, 0.0001f);
        assertEquals(0f, lead.y, 0.0001f);
    }
