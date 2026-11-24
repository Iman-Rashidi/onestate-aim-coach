package com.iman.onestateaimcoach;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class PinnedSlotConfigTest {
    @Test
    public void exposesHealthAndThreeCoordinateSlots() {
        assertEquals(4, PinnedSlotConfig.ALL.size());

        assertEquals("health", PinnedSlotConfig.HEALTH.id);
        assertEquals("x", PinnedSlotConfig.X.id);
        assertEquals("y", PinnedSlotConfig.Y.id);
        assertEquals("z", PinnedSlotConfig.Z.id);

        assertEquals(100.0f, PinnedSlotConfig.HEALTH.defaultValue, 0.0f);
        assertEquals(0.0f, PinnedSlotConfig.X.defaultValue, 0.0f);
        assertEquals(0.0f, PinnedSlotConfig.Y.defaultValue, 0.0f);
        assertEquals(0.0f, PinnedSlotConfig.Z.defaultValue, 0.0f);
    }

    @Test
    public void targetMetadataIsExplicit() {
        assertEquals("com.Chillgaming.oneState", PinnedSlotConfig.TARGET_PACKAGE);
        assertEquals("libil2cpp.so", PinnedSlotConfig.MODULE_NAME);
        assertNotNull(PinnedSlotConfig.ALL);
    }
}
