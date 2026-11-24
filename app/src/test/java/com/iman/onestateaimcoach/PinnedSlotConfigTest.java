package com.iman.onestateaimcoach;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PinnedSlotConfigTest {
    @Test
    public void exposesHealthAndThreeCoordinateSlots() {
        assertEquals(4, PinnedSlotConfig.ALL.size());

        assertEquals("health", PinnedSlotConfig.HEALTH.id);
        assertEquals("x", PinnedSlotConfig.X.id);
        assertEquals("y", PinnedSlotConfig.Y.id);
        assertEquals("z", PinnedSlotConfig.Z.id);

        assertEquals(100.0f, PinnedSlotConfig.HEALTH.defaultValue, 0.0f);
        assertEquals(0.0f, PinnedSlotConfig.X.defaultValue, 0.0f);
        assertEquals(0.0f, PinnedSlotConfig.Y.defaultValue, 0.0f);
        assertEquals(0.0f, PinnedSlotConfig.Z.defaultValue, 0.0f);
    }

    @Test
    public void targetMetadataIsExplicit() {
        assertEquals("com.Chillgaming.oneState", PinnedSlotConfig.TARGET_PACKAGE);
        assertEquals("libil2cpp.so", PinnedSlotConfig.MODULE_NAME);
        assertNotNull(PinnedSlotConfig.ALL);
    }

    @Test
    public void persistsEnabledFlagsAndFloatValues() {
        SharedPreferences preferences = new InMemorySharedPreferences();

        assertFalse(PinnedSlotConfig.isEnabled(preferences, PinnedSlotConfig.HEALTH));
        assertEquals(100.0f, PinnedSlotConfig.getValue(preferences, PinnedSlotConfig.HEALTH), 0.0f);

        PinnedSlotConfig.setEnabled(preferences, PinnedSlotConfig.HEALTH, true);
        assertTrue(PinnedSlotConfig.setValue(preferences, PinnedSlotConfig.HEALTH, 73.25f));

        assertTrue(PinnedSlotConfig.isEnabled(preferences, PinnedSlotConfig.HEALTH));
        assertEquals(73.25f, PinnedSlotConfig.getValue(preferences, PinnedSlotConfig.HEALTH), 0.0f);
    }

    @Test
    public void rejectsNonFiniteFloatsWithoutReplacingLastValidValue() {
        SharedPreferences preferences = new InMemorySharedPreferences();

        assertTrue(PinnedSlotConfig.setValue(preferences, PinnedSlotConfig.X, 12.5f));
        assertFalse(PinnedSlotConfig.setValue(preferences, PinnedSlotConfig.X, Float.NaN));
        assertFalse(PinnedSlotConfig.setValue(preferences, PinnedSlotConfig.X, Float.POSITIVE_INFINITY));
        assertFalse(PinnedSlotConfig.setValue(preferences, PinnedSlotConfig.X, Float.NEGATIVE_INFINITY));

        assertEquals(12.5f, PinnedSlotConfig.getValue(preferences, PinnedSlotConfig.X), 0.0f);
    }

    @Test
    public void slotPreferenceKeysAreStable() {
        assertEquals("slot_health_enabled", PinnedSlotConfig.HEALTH.enabledKey());
        assertEquals("slot_health_value", PinnedSlotConfig.HEALTH.valueKey());
        assertEquals("slot_x_enabled", PinnedSlotConfig.X.enabledKey());
        assertEquals("slot_x_value", PinnedSlotConfig.X.valueKey());
        assertEquals("slot_y_enabled", PinnedSlotConfig.Y.enabledKey());
        assertEquals("slot_y_value", PinnedSlotConfig.Y.valueKey());
        assertEquals("slot_z_enabled", PinnedSlotConfig.Z.enabledKey());
        assertEquals("slot_z_value", PinnedSlotConfig.Z.valueKey());
    }

    private static final class InMemorySharedPreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<>();

        @Override public Map<String, ?> getAll() {
            return Collections.unmodifiableMap(new HashMap<>(values));
        }
        @Override public String getString(String key, String defValue) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : defValue;
        }
        @SuppressWarnings("unchecked")
        @Override public Set<String> getStringSet(String key, Set<String> defValues) {
            Object value = values.get(key);
            return value instanceof Set ? new HashSet<>((Set<String>) value) : defValues;
        }
        @Override public int getInt(String key, int defValue) {
            Object value = values.get(key);
            return value instanceof Integer ? (Integer) value : defValue;
        }
        @Override public long getLong(String key, long defValue) {
            Object value = values.get(key);
            return value instanceof Long ? (Long) value : defValue;
        }
        @Override public float getFloat(String key, float defValue) {
            Object value = values.get(key);
            return value instanceof Float ? (Float) value : defValue;
        }
        @Override public boolean getBoolean(String key, boolean defValue) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : defValue;
        }
        @Override public boolean contains(String key) { return values.containsKey(key); }
        @Override public Editor edit() { return new MemoryEditor(); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

        private final class MemoryEditor implements Editor {
            private final Map<String, Object> updates = new HashMap<>();
            private final Set<String> removals = new HashSet<>();
            private boolean clear;

            @Override public Editor putString(String key, String value) {
                if (value == null) return remove(key);
                updates.put(key, value); removals.remove(key); return this;
            }
            @Override public Editor putStringSet(String key, Set<String> value) {
                if (value == null) return remove(key);
                updates.put(key, new HashSet<>(value)); removals.remove(key); return this;
            }
            @Override public Editor putInt(String key, int value) {
                updates.put(key, value); removals.remove(key); return this;
            }
            @Override public Editor putLong(String key, long value) {
                updates.put(key, value); removals.remove(key); return this;
            }
            @Override public Editor putFloat(String key, float value) {
                updates.put(key, value); removals.remove(key); return this;
            }
            @Override public Editor putBoolean(String key, boolean value) {
                updates.put(key, value); removals.remove(key); return this;
            }
            @Override public Editor remove(String key) {
                updates.remove(key); removals.add(key); return this;
            }
            @Override public Editor clear() { clear = true; return this; }
            @Override public boolean commit() { apply(); return true; }
            @Override public void apply() {
                if (clear) values.clear();
                for (String key : removals) values.remove(key);
                values.putAll(updates);
            }
        }
    }
}
