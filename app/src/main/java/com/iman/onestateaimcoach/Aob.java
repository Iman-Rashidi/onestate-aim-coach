package com.iman.onestateaimcoach;

import java.util.ArrayList;
import java.util.List;

/** Byte-pattern matching with zero mask bytes treated as wildcards. */
public final class Aob {
    private Aob() {}

    public static boolean matches(byte[] pattern, byte[] mask, byte[] data, int off) {
        if (pattern == null || mask == null || data == null) return false;
        if (pattern.length != mask.length || off < 0 || off > data.length - pattern.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            if (mask[i] != 0 && pattern[i] != data[off + i]) return false;
        }
        return true;
    }

    public static int[] findAll(byte[] pattern, byte[] mask, byte[] data) {
        if (pattern == null || mask == null || data == null
                || pattern.length != mask.length || pattern.length == 0
                || pattern.length > data.length) {
            return new int[0];
        }
        List<Integer> offsets = new ArrayList<>();
        for (int off = 0; off <= data.length - pattern.length; off++) {
            if (matches(pattern, mask, data, off)) offsets.add(off);
        }
        int[] result = new int[offsets.size()];
        for (int i = 0; i < offsets.size(); i++) result[i] = offsets.get(i);
        return result;
    }
}
