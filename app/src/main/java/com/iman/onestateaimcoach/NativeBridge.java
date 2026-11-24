package com.iman.onestateaimcoach;

/** Optional JNI bridge for bounded byte-array pattern matching. */
public final class NativeBridge {
    private static final boolean AVAILABLE;

    static {
        boolean loaded;
        try {
            System.loadLibrary("onestate_native");
            loaded = true;
        } catch (UnsatisfiedLinkError | SecurityException error) {
            loaded = false;
        }
        AVAILABLE = loaded;
    }

    private NativeBridge() {}

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static native boolean nativeMatchMask(
            byte[] pattern, byte[] mask, byte[] data, int offset);

    public static native int[] nativeFindAll(
            byte[] pattern, byte[] mask, byte[] data);
}
