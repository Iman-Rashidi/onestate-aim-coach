package com.iman.onestateaimcoach;

/** In-memory metrics only. Captured frames are never saved or transmitted. */
public final class ScreenMotionState {
    public static volatile boolean active = false;
    public static volatile float motionScore = 0f;
    public static volatile String level = "خاموش";

    private ScreenMotionState() {}
}
