package com.iman.onestateaimcoach;

/** Pure-Java target association using bounding-box overlap and center proximity. */
public final class TargetTracker {
    private final int maxLostFrames;
    private ScreenMotionState.PlayerBox locked;
    private int lostFrames;

    public TargetTracker() { this(3); }
    public TargetTracker(int maxLostFrames) { this.maxLostFrames = Math.max(0, maxLostFrames); }

    /** Returns a current-frame match, or null while a missing lock is in its grace period. */
    public synchronized ScreenMotionState.PlayerBox update(
            ScreenMotionState.PlayerBox[] detections, float fovRadius) {
        float radius = sanitizeRadius(fovRadius);
        if (locked != null) {
            ScreenMotionState.PlayerBox match = findMatch(detections, locked, radius);
            if (match != null) {
                locked = match;
                lostFrames = 0;
                return match;
            }
            lostFrames++;
            if (lostFrames <= maxLostFrames) return null;
            locked = null;
            lostFrames = 0;
        }
        locked = findClosestInsideFov(detections, radius);
        return locked;
    }

    public synchronized ScreenMotionState.PlayerBox getLockedTarget() { return locked; }
    public synchronized int getLostFrames() { return lostFrames; }
    public synchronized void reset() { locked = null; lostFrames = 0; }

    private ScreenMotionState.PlayerBox findMatch(
            ScreenMotionState.PlayerBox[] detections,
            ScreenMotionState.PlayerBox previous, float radius) {
        if (detections == null) return null;
        ScreenMotionState.PlayerBox best = null;
        float bestScore = Float.MAX_VALUE;
        float px = centerX(previous), py = centerY(previous);
        for (ScreenMotionState.PlayerBox candidate : detections) {
            if (candidate == null || !insideFov(candidate, radius)) continue;
            float distance = distance(px, py, centerX(candidate), centerY(candidate));
            float overlap = iou(previous, candidate);
            float associationLimit = Math.max(0.10f, Math.min(0.18f, radius * 0.5f));
            if (distance > associationLimit && overlap < 0.01f) continue;
            float score = distance * 0.65f + (1f - overlap) * 0.35f;
            if (score < bestScore) { bestScore = score; best = candidate; }
        }
        return best;
    }

    private ScreenMotionState.PlayerBox findClosestInsideFov(
            ScreenMotionState.PlayerBox[] detections, float radius) {
        if (detections == null) return null;
        ScreenMotionState.PlayerBox best = null;
        float bestDistance = Float.MAX_VALUE;
        for (ScreenMotionState.PlayerBox candidate : detections) {
            if (candidate == null || !insideFov(candidate, radius)) continue;
            float d = distance(0.5f, 0.5f, centerX(candidate), centerY(candidate));
            if (d < bestDistance || (d == bestDistance && best != null
                    && candidate.confidence > best.confidence)) {
                best = candidate;
                bestDistance = d;
            }
        }
        return best;
    }

    private boolean insideFov(ScreenMotionState.PlayerBox box, float radius) {
        return distance(0.5f, 0.5f, centerX(box), centerY(box)) <= radius;
    }
    private static float centerX(ScreenMotionState.PlayerBox b) { return (b.left + b.right) * 0.5f; }
    private static float centerY(ScreenMotionState.PlayerBox b) { return (b.top + b.bottom) * 0.5f; }
    private static float distance(float ax, float ay, float bx, float by) {
        return (float) Math.hypot(ax - bx, ay - by);
    }
    private static float iou(ScreenMotionState.PlayerBox a, ScreenMotionState.PlayerBox b) {
        float left = Math.max(a.left, b.left), top = Math.max(a.top, b.top);
        float right = Math.min(a.right, b.right), bottom = Math.min(a.bottom, b.bottom);
        float intersection = Math.max(0f, right - left) * Math.max(0f, bottom - top);
        float areaA = Math.max(0f, a.right - a.left) * Math.max(0f, a.bottom - a.top);
        float areaB = Math.max(0f, b.right - b.left) * Math.max(0f, b.bottom - b.top);
        float union = areaA + areaB - intersection;
        return union <= 0f ? 0f : intersection / union;
    }
    private static float sanitizeRadius(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 0.35f;
        return Math.max(0.10f, Math.min(0.80f, value));
    }
}
