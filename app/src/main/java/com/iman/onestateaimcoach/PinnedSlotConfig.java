package com.iman.onestateaimcoach;

import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Persistent configuration for four numeric display slots.
 *
 * This class stores user-entered values and enabled flags only. It does not
 * resolve process addresses or read/write another application's memory.
 */
public final class PinnedSlotConfig {
    public static final String TARGET_PACKAGE = "com.Chillgaming.oneState";
    public static final String MODULE_NAME = "libil2cpp.so";

    public static final class Slot {
        public final String id;
        public final String label;
        public final float defaultValue;

        private Slot(String id, String label, float defaultValue) {
            this.id = id;
            this.label = label;
            this.defaultValue = defaultValue;
        }

        public String enabledKey() {
            return "slot_" + id + "_enabled";
        }

        public String valueKey() {
            return "slot_" + id + "_value";
        }
    }

    public static final Slot HEALTH = new Slot("health", "Health", 100.0f);
    public static final Slot X = new Slot("x", "Coordinate X", 0.0f);
    public static final Slot Y = new Slot("y", "Coordinate Y", 0.0f);
    public static final Slot Z = new Slot("z", "Coordinate Z", 0.0f);

    public static final List<Slot> ALL = Collections.unmodifiableList(
            Arrays.asList(HEALTH, X, Y, Z));

    private PinnedSlotConfig() {}

    public static boolean isEnabled(SharedPreferences preferences, Slot slot) {
        return preferences.getBoolean(slot.enabledKey(), false);
    }

    public static void setEnabled(
            SharedPreferences preferences, Slot slot, boolean enabled) {
        preferences.edit().putBoolean(slot.enabledKey(), enabled).apply();
    }

    public static float getValue(SharedPreferences preferences, Slot slot) {
        return preferences.getFloat(slot.valueKey(), slot.defaultValue);
    }

    public static boolean setValue(
            SharedPreferences preferences, Slot slot, float value) {
        if (!Float.isFinite(value)) return false;
        preferences.edit().putFloat(slot.valueKey(), value).apply();
        return true;
    }
}
