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

    private static volatile float fovRadius = 0.35f;
    private static volatile boolean headZoneEnabled = false;
    private static volatile boolean targetLockEnabled = true;
    private static volatile float smoothingAlpha = 0.45f;
    private static volatile float confidenceThreshold = 0.26f;
    private static volatile boolean showFovCircle = true;
    private static volatile boolean longRangeZoomEnabled = true;
    private static volatile boolean detectVehiclesEnabled = true;
    public static volatile float visionFps = 0f;
    public static volatile long inferenceLatencyMs = 0L;
    private static volatile PlayerBox lockedTargetBox;

    public static float getVisionFps() { return visionFps; }
    public static void setVisionFps(float value) {
        if (!Float.isNaN(value) && !Float.isInfinite(value))
            visionFps = Math.max(0f, value);
    }
    public static long getInferenceLatencyMs() { return inferenceLatencyMs; }
    public static void setInferenceLatencyMs(long value) {
        inferenceLatencyMs = Math.max(0L, value);
    }

    public static float getFovRadius() { return fovRadius; }
    public static void setFovRadius(float value) {
        if (!Float.isNaN(value) && !Float.isInfinite(value))
            fovRadius = Math.max(0.10f, Math.min(0.80f, value));
    }
    public static boolean isHeadZoneEnabled() { return headZoneEnabled; }
    public static void setHeadZoneEnabled(boolean value) { headZoneEnabled = value; }
    public static boolean isTargetLockEnabled() { return targetLockEnabled; }
    public static void setTargetLockEnabled(boolean value) { targetLockEnabled = value; }
    public static float getSmoothingAlpha() { return smoothingAlpha; }
    public static void setSmoothingAlpha(float value) {
        if (!Float.isNaN(value) && !Float.isInfinite(value))
            smoothingAlpha = Math.max(0.10f, Math.min(1.0f, value));
    }
    public static float getConfidenceThreshold() { return confidenceThreshold; }
    public static void setConfidenceThreshold(float value) {
        if (!Float.isNaN(value) && !Float.isInfinite(value))
            confidenceThreshold = Math.max(0.15f, Math.min(0.99f, value));
    }
    public static boolean isLongRangeZoomEnabled() { return longRangeZoomEnabled; }
    public static void setLongRangeZoomEnabled(boolean value) { longRangeZoomEnabled = value; }
    public static boolean isDetectVehiclesEnabled() { return detectVehiclesEnabled; }
    public static void setDetectVehiclesEnabled(boolean value) { detectVehiclesEnabled = value; }
    public static boolean isShowFovCircle() { return showFovCircle; }
    public static void setShowFovCircle(boolean value) { showFovCircle = value; }
    public static PlayerBox getLockedTargetBox() { return lockedTargetBox; }
    public static void setLockedTargetBox(PlayerBox value) { lockedTargetBox = value; }

    private static boolean healthSlotEnabled;
    private static boolean coordinateSlotEnabled;
    private static float healthValue;
    private static float xValue;
    private static float yValue;
    private static float zValue;
    private static boolean healthOffsetReady;
    private static boolean coordinateOffsetsReady;
    private static boolean telemetryDefaultsInitialized;

    public static synchronized void configureTelemetryAvailability(
            boolean healthReady, boolean coordinatesReady) {
        healthOffsetReady = healthReady;
        coordinateOffsetsReady = coordinatesReady;
    }

    public static synchronized void initializeTelemetryDefaults(float defaultHealthValue) {
        if (telemetryDefaultsInitialized) return;
        healthValue = sanitize(defaultHealthValue);
        telemetryDefaultsInitialized = true;
    }

    public static synchronized void setHealthSlotEnabled(boolean enabled) {
        healthSlotEnabled = enabled;
    }

    public static synchronized boolean isHealthSlotEnabled() {
        return healthSlotEnabled;
    }

    public static synchronized boolean isHealthSlotInert() {
        return healthSlotEnabled && !healthOffsetReady;
    }

    public static synchronized void setCoordinateSlotEnabled(boolean enabled) {
        coordinateSlotEnabled = enabled;
    }

    public static synchronized boolean isCoordinateSlotEnabled() {
        return coordinateSlotEnabled;
    }

    public static synchronized boolean isCoordinateSlotInert() {
        return coordinateSlotEnabled && !coordinateOffsetsReady;
    }

    public static synchronized void setHealthValue(float value) {
        healthValue = sanitize(value);
    }

    public static synchronized float getHealthValue() {
        return healthValue;
    }

    public static synchronized void setXValue(float value) {
        xValue = sanitize(value);
    }

    public static synchronized float getXValue() {
        return xValue;
    }

    public static synchronized void setYValue(float value) {
        yValue = sanitize(value);
    }

    public static synchronized float getYValue() {
        return yValue;
    }

    public static synchronized void setZValue(float value) {
        zValue = sanitize(value);
    }

    public static synchronized float getZValue() {
        return zValue;
    }

    public static float parseFloatOrZero(String value) {
        if (value == null || value.trim().isEmpty()) return 0f;
        try {
            return sanitize(Float.parseFloat(value.trim()));
        } catch (NumberFormatException ignored) {
            return 0f;
        }
    }

    private static float sanitize(float value) {
        return Float.isNaN(value) || Float.isInfinite(value) ? 0f : value;
    }

    public static final class PlayerBox {
        public final float left, top, right, bottom, confidence;
        public final String category;
        public final boolean isMoving, isLongRangeZoom;

        public PlayerBox(float left, float top, float right, float bottom, float confidence) {
            this(left, top, right, bottom, confidence, "PERSON", false, false);
        }

        public PlayerBox(float left, float top, float right, float bottom, float confidence,
                         String category, boolean isMoving, boolean isLongRangeZoom) {
            this.left = clamp(left); this.top = clamp(top);
            this.right = clamp(right); this.bottom = clamp(bottom);
            this.confidence = clamp(confidence);
            this.category = "VEHICLE".equalsIgnoreCase(category) ? "VEHICLE" : "PERSON";
            this.isMoving = isMoving; this.isLongRangeZoom = isLongRangeZoom;
        }

        private static float clamp(float value) {
            if (Float.isNaN(value) || Float.isInfinite(value)) return 0f;
            return Math.max(0f, Math.min(1f, value));
        }
    }

    private ScreenMotionState() {}
}
