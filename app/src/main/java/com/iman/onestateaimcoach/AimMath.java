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

    public static Vec2 computeTargetPoint(
            float left, float top, float right, float bottom, boolean headZone) {
        float centerX = (left + right) * 0.5f;
        float targetY = headZone ? top + (bottom - top) * 0.18f : (top + bottom) * 0.5f;
        return new Vec2(centerX, targetY);
    }

    /** Exponential interpolation from the previous vector toward the current vector. */
    public static Vec2 smoothVector(Vec2 current, Vec2 previous, float smoothingFactor) {
        if (current == null) return previous;
        if (previous == null) return current;
        float alpha = Float.isNaN(smoothingFactor) ? 0.45f : smoothingFactor;
        alpha = Math.max(0.05f, Math.min(1.0f, alpha));
        return new Vec2(previous.x + (current.x - previous.x) * alpha,
                previous.y + (current.y - previous.y) * alpha);
    }

    public static float[] mapCropBoxToFullScreen(float left, float top, float right, float bottom,
            float cropLeft, float cropTop, float cropWidth, float cropHeight) {
        float cl = clamp01(cropLeft), ct = clamp01(cropTop);
        float cw = Math.max(0f, Math.min(1f - cl, cropWidth));
        float ch = Math.max(0f, Math.min(1f - ct, cropHeight));
        return new float[] { clamp01(cl + clamp01(left) * cw), clamp01(ct + clamp01(top) * ch),
                clamp01(cl + clamp01(right) * cw), clamp01(ct + clamp01(bottom) * ch) };
    }

    public static float computeIoU(float leftA, float topA, float rightA, float bottomA,
            float leftB, float topB, float rightB, float bottomB) {
        float iw = Math.max(0f, Math.min(rightA, rightB) - Math.max(leftA, leftB));
        float ih = Math.max(0f, Math.min(bottomA, bottomB) - Math.max(topA, topB));
        float intersection = iw * ih;
        float areaA = Math.max(0f, rightA-leftA) * Math.max(0f, bottomA-topA);
        float areaB = Math.max(0f, rightB-leftB) * Math.max(0f, bottomB-topB);
        float union = areaA + areaB - intersection;
        return union <= 0f ? 0f : intersection / union;
    }

    /** Identifies the typical lower-center avatar silhouette in third-person games. */
    public static boolean isSelfCharacterBox(
            float left, float top, float right, float bottom) {
        if (!isFinite(left) || !isFinite(top) || !isFinite(right) || !isFinite(bottom)) return false;
        float width = right - left;
        float height = bottom - top;
        if (width <= 0f || height <= 0f) return false;
        float centerX = (left + right) * 0.5f;
        float centerY = (top + bottom) * 0.5f;
        return centerX >= 0.28f && centerX <= 0.56f
                && centerY > 0.52f && height > 0.25f;
    }

    /** Projects a target point forward by a configurable number of frame-velocity units. */
    public static Vec2 computeLeadPoint(Vec2 targetPoint, float vx, float vy, float leadFactor) {
        if (targetPoint == null) return null;
        float safeVx = isFinite(vx) ? vx : 0f;
        float safeVy = isFinite(vy) ? vy : 0f;
        float safeFactor = isFinite(leadFactor) ? Math.max(0f, leadFactor) : 0f;
        return new Vec2(clamp01(targetPoint.x + safeVx * safeFactor),
                clamp01(targetPoint.y + safeVy * safeFactor));
    }

    /** Classifies apparent target range from normalized detection-box height. */
    public static String classifyRange(float boxWidth, float boxHeight) {
        if (!isFinite(boxWidth) || !isFinite(boxHeight)
                || boxWidth <= 0f || boxHeight <= 0f) return "FAR";
        if (boxHeight >= 0.24f) return "CLOSE";
        if (boxHeight >= 0.10f) return "MID";
        return "FAR";
    }

    public static final int PERIPHERAL_NONE = 0;
    public static final int PERIPHERAL_LEFT = 1;
    public static final int PERIPHERAL_RIGHT = 1 << 1;
    public static final int PERIPHERAL_TOP = 1 << 2;
    public static final int PERIPHERAL_BOTTOM = 1 << 3;

    /**
     * Returns the dominant screen-edge direction for a target outside a
     * center-origin FOV radius. Coordinates and radius use normalized units.
     */
    public static int computePeripheralDirection(float targetX, float targetY, float fovRadius) {
        if (!isFinite(targetX) || !isFinite(targetY) || !isFinite(fovRadius)
                || fovRadius < 0f) return PERIPHERAL_NONE;
        float dx = targetX - 0.5f;
        float dy = targetY - 0.5f;
        if ((float) Math.hypot(dx, dy) <= fovRadius) return PERIPHERAL_NONE;
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx < 0f ? PERIPHERAL_LEFT : PERIPHERAL_RIGHT;
        }
        return dy < 0f ? PERIPHERAL_TOP : PERIPHERAL_BOTTOM;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
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
