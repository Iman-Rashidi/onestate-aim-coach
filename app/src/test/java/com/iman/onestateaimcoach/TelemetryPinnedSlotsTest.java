package com.iman.onestateaimcoach;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TelemetryPinnedSlotsTest {
    @Test
    public void storesPinnedSlotTogglesAndValues() {
        ScreenMotionState.setHealthSlotEnabled(true);
        ScreenMotionState.setCoordinateSlotEnabled(true);
        ScreenMotionState.setHealthValue(87.5f);
        ScreenMotionState.setXValue(-12.25f);
        ScreenMotionState.setYValue(3.5f);
        ScreenMotionState.setZValue(0.75f);

        assertTrue(ScreenMotionState.isHealthSlotEnabled());
        assertTrue(ScreenMotionState.isCoordinateSlotEnabled());
        assertEquals(87.5f, ScreenMotionState.getHealthValue(), 0.0001f);
        assertEquals(-12.25f, ScreenMotionState.getXValue(), 0.0001f);
        assertEquals(3.5f, ScreenMotionState.getYValue(), 0.0001f);
        assertEquals(0.75f, ScreenMotionState.getZValue(), 0.0001f);
    }

    @Test
    public void parsesDecimalAndSignedFloatValues() {
        assertEquals(12.75f, ScreenMotionState.parseFloatOrZero(" 12.75 "), 0.0001f);
        assertEquals(-0.5f, ScreenMotionState.parseFloatOrZero("-0.5"), 0.0001f);
    }

    @Test
    public void emptyInvalidAndNonFiniteInputFallsBackToZero() {
        assertEquals(0f, ScreenMotionState.parseFloatOrZero(null), 0f);
        assertEquals(0f, ScreenMotionState.parseFloatOrZero(""), 0f);
        assertEquals(0f, ScreenMotionState.parseFloatOrZero("   "), 0f);
        assertEquals(0f, ScreenMotionState.parseFloatOrZero("not-a-number"), 0f);
        assertEquals(0f, ScreenMotionState.parseFloatOrZero("NaN"), 0f);
        assertEquals(0f, ScreenMotionState.parseFloatOrZero("Infinity"), 0f);
    }

    @Test
    public void enabledSlotsRemainInertWhenOffsetsAreUnset() {
        ScreenMotionState.configureTelemetryAvailability(false, false);
        ScreenMotionState.setHealthSlotEnabled(true);
        ScreenMotionState.setCoordinateSlotEnabled(true);

        assertTrue(ScreenMotionState.isHealthSlotInert());
        assertTrue(ScreenMotionState.isCoordinateSlotInert());
        assertFalse(OffsetRepo.isMemoryAssistConfigured("", "", 0, 0, 0, 0));
        assertFalse(OffsetRepo.isMemoryAssistConfigured("AABB", "xxxx", 1, 0, 1, 1));
    }

    @Test
    public void configuredOffsetsOnlyMarkLocalSlotsAvailable() {
        ScreenMotionState.configureTelemetryAvailability(true, true);
        ScreenMotionState.setHealthSlotEnabled(true);
        ScreenMotionState.setCoordinateSlotEnabled(true);

        assertFalse(ScreenMotionState.isHealthSlotInert());
        assertFalse(ScreenMotionState.isCoordinateSlotInert());
        assertTrue(OffsetRepo.isMemoryAssistConfigured("AABB", "xxxx", 1, 2, 3, 4));
    }
}
