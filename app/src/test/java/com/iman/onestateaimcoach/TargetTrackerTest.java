package com.iman.onestateaimcoach;

import org.junit.Test;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;

public class TargetTrackerTest {
    private ScreenMotionState.PlayerBox box(float l, float t, float r, float b) {
        return new ScreenMotionState.PlayerBox(l, t, r, b, 0.9f);
    }

    @Test public void retainsMovingTargetWhenAnotherMovesCloserToCenter() {
        TargetTracker tracker = new TargetTracker(3);
        ScreenMotionState.PlayerBox first = box(0.36f, 0.30f, 0.46f, 0.60f);
        assertSame(first, tracker.update(new ScreenMotionState.PlayerBox[] { first }, 0.8f));
        ScreenMotionState.PlayerBox moving = box(0.38f, 0.30f, 0.48f, 0.60f);
        ScreenMotionState.PlayerBox decoy = box(0.47f, 0.32f, 0.57f, 0.62f);
        assertSame(moving, tracker.update(new ScreenMotionState.PlayerBox[] { decoy, moving }, 0.8f));
    }

    @Test public void switchesAfterMaxLostFramesExpire() {
        TargetTracker tracker = new TargetTracker(2);
        ScreenMotionState.PlayerBox first = box(0.25f, 0.25f, 0.35f, 0.55f);
        tracker.update(new ScreenMotionState.PlayerBox[] { first }, 0.8f);
        assertNull(tracker.update(new ScreenMotionState.PlayerBox[0], 0.8f));
        assertNull(tracker.update(new ScreenMotionState.PlayerBox[0], 0.8f));
        ScreenMotionState.PlayerBox replacement = box(0.46f, 0.30f, 0.56f, 0.60f);
        assertSame(replacement, tracker.update(new ScreenMotionState.PlayerBox[] { replacement }, 0.8f));
    }

    @Test public void doesNotAcquireOutsideFov() {
        TargetTracker tracker = new TargetTracker(3);
        ScreenMotionState.PlayerBox outside = box(0.80f, 0.40f, 0.90f, 0.70f);
        assertNull(tracker.update(new ScreenMotionState.PlayerBox[] { outside }, 0.2f));
    }

    @Test public void computesNormalizedVelocityAcrossConsecutiveFrames() {
        TargetTracker tracker = new TargetTracker(3);
        ScreenMotionState.PlayerBox first = box(0.35f, 0.30f, 0.45f, 0.60f);
        tracker.update(new ScreenMotionState.PlayerBox[] { first }, 0.8f);
        ScreenMotionState.PlayerBox next = box(0.37f, 0.32f, 0.47f, 0.62f);
        assertSame(next, tracker.update(new ScreenMotionState.PlayerBox[] { next }, 0.8f));
        assertEquals(0.02f, tracker.getVelocityX(), 0.0001f);
        assertEquals(0.02f, tracker.getVelocityY(), 0.0001f);
    }

    @Test public void acceptsTargetOnFovBoundary() {
        TargetTracker tracker = new TargetTracker(3);
        ScreenMotionState.PlayerBox boundary = box(0.60f, 0.40f, 0.70f, 0.60f);
        assertSame(boundary, tracker.update(new ScreenMotionState.PlayerBox[] { boundary }, 0.2f));
    }
}