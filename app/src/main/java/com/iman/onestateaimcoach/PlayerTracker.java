package com.iman.onestateaimcoach;

import java.util.ArrayList;
import java.util.List;

/**
 * Assigns short-lived IDs to visible person candidates across adjacent frames.
 * This only stabilizes on-screen annotations; it never controls game input.
 */
public final class PlayerTracker {
    private static final float MAX_MATCH_DISTANCE_SQUARED = 0.0196f; // 0.14 normalized frame units
    private static final float OLD_POSITION_WEIGHT = 0.32f;

    private int nextTrackId = 1;
    private ScreenMotionState.PlayerBox[] previous = new ScreenMotionState.PlayerBox[0];

    public synchronized ScreenMotionState.PlayerBox[] update(
            List<ScreenMotionState.PlayerBox> detections) {
        if (detections == null || detections.isEmpty()) {
            previous = new ScreenMotionState.PlayerBox[0];
            return previous;
        }

        boolean[] previousUsed = new boolean[previous.length];
        ArrayList<ScreenMotionState.PlayerBox> current =
                new ArrayList<>(detections.size());

        for (ScreenMotionState.PlayerBox detection : detections) {
            int bestIndex = -1;
            float bestDistance = MAX_MATCH_DISTANCE_SQUARED;
            float centerX = centerX(detection);
            float centerY = centerY(detection);

            for (int i = 0; i < previous.length; i++) {
                if (previousUsed[i]) continue;
                ScreenMotionState.PlayerBox old = previous[i];
                float dx = centerX - centerX(old);
                float dy = centerY - centerY(old);
                float distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestIndex = i;
                }
            }

            if (bestIndex >= 0) {
                previousUsed[bestIndex] = true;
                ScreenMotionState.PlayerBox old = previous[bestIndex];
                current.add(new ScreenMotionState.PlayerBox(
                        smooth(old.left, detection.left),
                        smooth(old.top, detection.top),
                        smooth(old.right, detection.right),
                        smooth(old.bottom, detection.bottom),
                        detection.confidence,
                        old.trackId));
            } else {
                current.add(new ScreenMotionState.PlayerBox(
                        detection.left,
                        detection.top,
                        detection.right,
                        detection.bottom,
                        detection.confidence,
                        nextTrackId++));
            }
        }

        previous = current.toArray(new ScreenMotionState.PlayerBox[0]);
        return previous;
    }

    public synchronized void reset() {
        previous = new ScreenMotionState.PlayerBox[0];
        nextTrackId = 1;
    }

    private float smooth(float oldValue, float newValue) {
        return oldValue * OLD_POSITION_WEIGHT + newValue * (1f - OLD_POSITION_WEIGHT);
    }

    private float centerX(ScreenMotionState.PlayerBox box) {
        return (box.left + box.right) * 0.5f;
    }

    private float centerY(ScreenMotionState.PlayerBox box) {
        return (box.top + box.bottom) * 0.5f;
    }
}
