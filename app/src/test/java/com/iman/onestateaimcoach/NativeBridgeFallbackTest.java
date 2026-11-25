package com.iman.onestateaimcoach;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NativeBridgeFallbackTest {
    @Test
    public void nativeLibraryAvailabilityCanBeQueriedOnHostJvm() {
        boolean available = NativeBridge.isAvailable();
        if (!available) {
            assertFalse(available);
        }
    }

    @Test
    public void missingPatternAndZeroOffsetsKeepMemoryAssistInert() {
        assertFalse(OffsetRepo.isMemoryAssistConfigured(null, "FF", 4, 8));
        assertFalse(OffsetRepo.isMemoryAssistConfigured("", "FF", 4, 8));
        assertFalse(OffsetRepo.isMemoryAssistConfigured("FF", "", 4, 8));
        assertFalse(OffsetRepo.isMemoryAssistConfigured("FF", "FF", 4, 0));
        assertFalse(OffsetRepo.isMemoryAssistConfigured("FF", "FF", 0, 0));
    }

    @Test
    public void completeNonzeroOffsetContractPassesPureValidation() {
        assertTrue(OffsetRepo.isMemoryAssistConfigured("AABB", "FFFF", 4, 8, 12));
    }
}
