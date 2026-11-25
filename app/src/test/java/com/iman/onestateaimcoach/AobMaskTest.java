package com.iman.onestateaimcoach;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AobMaskTest {
    @Test
    public void wildcardBytesAreIgnored() {
        byte[] pattern = {0x11, 0x22, 0x33};
        byte[] mask = {1, 0, 1};
        byte[] data = {0x11, 0x7f, 0x33};
        assertTrue(Aob.matches(pattern, mask, data, 0));
    }

    @Test
    public void strictMismatchFails() {
        byte[] pattern = {0x11, 0x22};
        byte[] mask = {1, 1};
        byte[] data = {0x11, 0x23};
        assertFalse(Aob.matches(pattern, mask, data, 0));
    }

    @Test
    public void findAllReturnsBothOffsets() {
        byte[] pattern = {0x41, 0x00, 0x43};
        byte[] mask = {1, 0, 1};
        byte[] data = {0x41, 0x10, 0x43, 0x00, 0x41, 0x7f, 0x43};
        assertArrayEquals(new int[] {0, 4}, Aob.findAll(pattern, mask, data));
    }
}
