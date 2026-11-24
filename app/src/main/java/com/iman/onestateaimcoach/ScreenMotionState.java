package com.iman.onestateaimcoach;

/** Volatile in-memory state. Raw screenshots are not stored or uploaded. */
public final class ScreenMotionState {
    public static volatile boolean active = false;
    public static volatile float motionScore = 0f;
    public static volatile String level = "خاموش";
    public static volatile PlayerBox[] detectedPlayers = new PlayerBox[0];
    public static volatile boolean offsetContractReady = false;
    public static volatile boolean aimGuideActive = false;
    public static volatile float aimTargetX = 0.5f;
    public static volatile float aimTargetY = 0.5f;
    public static volatile float aimDeltaX = 0f;
    public static volatile float aimDeltaY = 0f;

    public static final class PlayerBox {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;
        public final float confidence;

        public PlayerBox(float left, float top, float right, float bottom, float confidence) {
            this.left = clamp(left);
            this.top = clamp(top);
            this.right = clamp(right);
            this.bottom = clamp(bottom);
            this.confidence = clamp(confidence);
        }

        private static float clamp(float value) {
            if (Float.isNaN(value) || Float.isInfinite(value)) return 0f;
            return Math.max(0f, Math.min(1f, value));
        }
    }

    private ScreenMotionState() {}
}
