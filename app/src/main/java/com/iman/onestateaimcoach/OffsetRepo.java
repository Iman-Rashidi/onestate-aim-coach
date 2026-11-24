package com.iman.onestateaimcoach;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Reads the checked-in offset contract from assets. No offsets are inferred at runtime. */
public final class OffsetRepo {
    private static final String TAG = "OffsetRepo";
    private static final String ASSET_NAME = "offsets.json";
    private static volatile JSONObject cachedConfig;
    private static final Set<String> WARNED =
            Collections.synchronizedSet(new HashSet<String>());

    public OffsetRepo(Context context) {
        loadOnce(context == null ? null : context.getApplicationContext());
    }

    /** Pure validation helper; zero offsets or missing signatures keep memory features inert. */
    public static boolean isMemoryAssistConfigured(String pattern, String mask, int... fields) {
        if (pattern == null || pattern.trim().isEmpty()
                || mask == null || mask.trim().isEmpty()
                || pattern.length() != mask.length()
                || fields == null || fields.length == 0) {
            return false;
        }
        for (int field : fields) {
            if (field == 0) return false;
        }
        return true;
    }

    private static void loadOnce(Context context) {
        if (cachedConfig != null) return;
        synchronized (OffsetRepo.class) {
            if (cachedConfig != null) return;
            JSONObject loaded = null;
            if (context != null) {
                try (InputStream input = context.getAssets().open(ASSET_NAME);
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                    loaded = new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
                } catch (Exception error) {
                    warnOnce("asset", "Unable to load " + ASSET_NAME + ": " + error.getMessage());
                }
            } else {
                warnOnce("context", "Context unavailable; offsets remain inert");
            }
            cachedConfig = loaded == null ? new JSONObject() : loaded;
        }
    }

    public String getPattern() {
        String value = stringAt("players", "pattern");
        if (value == null || value.trim().isEmpty()) {
            warnOnce("pattern", "Player pattern is empty; pattern-based features remain inert");
            return null;
        }
        return value;
    }

    public String getMask() {
        String value = stringAt("players", "mask");
        if (value == null || value.trim().isEmpty()) {
            warnOnce("mask", "Player mask is empty; pattern-based features remain inert");
            return null;
        }
        return value;
    }

    public int[] getChain() {
        try {
            JSONArray array = config().getJSONObject("players").optJSONArray("chain");
            if (array == null || array.length() == 0) {
                warnOnce("chain", "Player chain is empty; chain-based features remain inert");
                return null;
            }
            int[] result = new int[array.length()];
            for (int i = 0; i < array.length(); i++) result[i] = array.getInt(i);
            return result;
        } catch (Exception error) {
            warnOnce("chain", "Player chain is unavailable; chain-based features remain inert");
            return null;
        }
    }

    public int field(String name) {
        if (name == null || name.trim().isEmpty()) {
            warnOnce("field:empty", "Empty field name; field remains inert");
            return 0;
        }
        try {
            JSONObject fields = config().getJSONObject("players").getJSONObject("fields");
            int value = fields.optInt(name, 0);
            if (value == 0) {
                warnOnce("field:" + name, "Offset field '" + name + "' is empty or zero; toggle remains inert");
                return 0;
            }
            return value;
        } catch (Exception error) {
            warnOnce("field:" + name, "Offset field '" + name + "' is unavailable; toggle remains inert");
            return 0;
        }
    }

    public double freezeValue(String name) {
        if (name == null || name.trim().isEmpty()) {
            warnOnce("freeze:empty", "Empty freeze value name");
            return 0d;
        }
        try {
            JSONObject freeze = config().getJSONObject("freeze");
            if (!freeze.has(name) || freeze.isNull(name)) {
                warnOnce("freeze:" + name, "Freeze value '" + name + "' is unavailable");
                return 0d;
            }
            double value = freeze.getDouble(name);
            if (value == 0d) {
                warnOnce("freeze:" + name, "Freeze value '" + name + "' is zero; feature remains inert");
                return 0d;
            }
            return value;
        } catch (Exception error) {
            warnOnce("freeze:" + name, "Freeze value '" + name + "' is unavailable");
            return 0d;
        }
    }

    private String stringAt(String objectName, String key) {
        try {
            JSONObject object = config().getJSONObject(objectName);
            return object.isNull(key) ? null : object.optString(key, null);
        } catch (Exception error) {
            return null;
        }
    }

    private static JSONObject config() {
        JSONObject value = cachedConfig;
        return value == null ? new JSONObject() : value;
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) Log.w(TAG, message);
    }
}
