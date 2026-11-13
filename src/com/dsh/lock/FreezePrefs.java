package com.dsh.lock;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

public final class FreezePrefs {
    private static final String NAME = "tecvb_freeze";
    private static final String KEY_PACKAGES = "packages";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_FREEZE_ALL = "freeze_all";
    private static final String KEY_ACTIVE_PACKAGES = "active_packages";

    private FreezePrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static JSONArray getPackages(Context context) {
        try {
            return new JSONArray(prefs(context).getString(KEY_PACKAGES, "[]"));
        } catch (Exception exception) {
            return new JSONArray();
        }
    }

    public static void setPackages(Context context, JSONArray packages) {
        prefs(context).edit().putString(KEY_PACKAGES, packages.toString()).apply();
    }

    public static boolean isFreezeAll(Context context) {
        return prefs(context).getBoolean(KEY_FREEZE_ALL, false);
    }

    public static void setFreezeAll(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_FREEZE_ALL, enabled).apply();
    }

    public static JSONArray getActivePackages(Context context) {
        try {
            return new JSONArray(prefs(context).getString(KEY_ACTIVE_PACKAGES, "[]"));
        } catch (Exception exception) {
            return new JSONArray();
        }
    }

    public static void setActivePackages(Context context, JSONArray packages) {
        prefs(context).edit().putString(KEY_ACTIVE_PACKAGES, packages.toString()).apply();
    }

    public static boolean isActive(Context context) {
        return prefs(context).getBoolean(KEY_ACTIVE, false);
    }

    public static void setActive(Context context, boolean active) {
        prefs(context).edit().putBoolean(KEY_ACTIVE, active).apply();
    }

    public static int count(Context context) {
        return getPackages(context).length();
    }
}
