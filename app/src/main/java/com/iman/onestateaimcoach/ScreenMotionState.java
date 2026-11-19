package com.iman.onestateaimcoach;

/** Volatile in-memory state. Raw screenshots are not stored or uploaded. */
public final class ScreenMotionState {
    public static volatile boolean active = false;
    public static volatile float motionScore = 0f;
    public static volatile String level = "خاموش";
    public static volatile PlayerBox[] detectedPlayers = new PlayerBox[0];

    public static final class PlayerBox {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;
        public final float confidence;
        public final int trackId;

        public PlayerBox(float left, float top, float right, float bottom, float confidence) {
            this(left, top, right, bottom, confidence, 0);
        }

        public PlayerBox(
                float left, float top, float right, float bottom,
                float confidence, int trackId) {
            this.left = clamp(left);
            this.top = clamp(top);
            this.right = clamp(right);
            this.bottom = clamp(bottom);
            this.confidence = clamp(confidence);
            this.trackId = Math.max(0, trackId);
        }

        private static float clamp(float value) {
            return Math.max(0f, Math.min(1f, value));
        }
    }

    private ScreenMotionState() {}
}
