package com.iman.onestateaimcoach;

/** Small, deterministic vector helpers for aim-coach calculations. */
public final class AimMath {
    private AimMath() {}

    public static Vec2 aimDelta(
            Vec2 target, Vec2 center, float sens, float deadzone, float maxSwipe) {
        if (target == null || center == null) return null;

        float dx = target.x - center.x;
        float dy = target.y - center.y;
        float distance = (float) Math.hypot(dx, dy);
        if (distance <= Math.max(0f, deadzone)) return null;

        float safeSensitivity = Math.max(0f, sens);
        float scaledX = dx * safeSensitivity;
        float scaledY = dy * safeSensitivity;
        float magnitude = (float) Math.hypot(scaledX, scaledY);
        float limit = Math.max(0f, maxSwipe) * safeSensitivity;

        if (magnitude > limit && magnitude > 0f) {
            float factor = limit / magnitude;
            scaledX *= factor;
            scaledY *= factor;
        }
        return new Vec2(scaledX, scaledY);
    }

    public static boolean inCone(Vec2 target, Vec2 center, float radius) {
        if (target == null || center == null || radius < 0f) return false;
        return Math.hypot(target.x - center.x, target.y - center.y) <= radius;
    }

    public static final class Vec2 {
        public final float x;
        public final float y;

        public Vec2(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
