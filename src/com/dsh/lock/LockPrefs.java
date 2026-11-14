package com.dsh.lock;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

public final class LockPrefs {
    private static final String FILE = "lock_state";
    private static final String KEY_LOCKED = "locked";
    private static final String KEY_MODE = "mode";
    private static final String KEY_LOCK_MODE = "lock_mode";
    private static final String KEY_PASSWORD_HASH = "pw_hash";
    private static final String KEY_PASSWORD_SALT = "pw_salt";
    private static final String KEY_PASSWORD_LENGTH = "pw_len";
    private static final String KEY_FLOATING = "floating_enabled";
    private static final String KEY_SHELL_HOME = "shell_home";
    private static final String KEY_SHELL_HOME_ACTIVE = "shell_home_active";
    private static final String KEY_SHELL_MODE = "shell_mode";
    private static final String KEY_SHELL_POLICY = "shell_policy";
    private static final String KEY_SHELL_ROTATION = "shell_rotation";
    private static final String KEY_SHELL_DISPLAY_ACTIVE = "shell_display_active";
    private static final String KEY_STRONG_ADB = "strong_adb";

    private LockPrefs() {
    }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static int getMode(Context context) {
        return prefs(context).getInt(KEY_MODE, LockModes.UNSET);
    }

    public static void setMode(Context context, int mode) {
        prefs(context).edit().putInt(KEY_MODE, mode).apply();
    }

    public static int getLockMode(Context context) {
        return prefs(context).getInt(KEY_LOCK_MODE, LockModes.UNSET);
    }

    public static boolean isLocked(Context context) {
        return prefs(context).getBoolean(KEY_LOCKED, false);
    }

    public static void setLocked(Context context, boolean locked, int mode) {
        SharedPreferences.Editor editor = prefs(context).edit().putBoolean(KEY_LOCKED, locked);
        if (locked) {
            editor.putInt(KEY_LOCK_MODE, mode);
        } else {
            editor.remove(KEY_LOCK_MODE);
        }
        editor.apply();
    }

    public static boolean hasPassword(Context context) {
        String hash = prefs(context).getString(KEY_PASSWORD_HASH, null);
        String salt = prefs(context).getString(KEY_PASSWORD_SALT, null);
        int length = prefs(context).getInt(KEY_PASSWORD_LENGTH, 0);
        return hash != null && !hash.isEmpty() && salt != null && !salt.isEmpty()
                && length >= 4 && length <= 8;
    }

    public static int getPasswordLength(Context context) {
        int length = prefs(context).getInt(KEY_PASSWORD_LENGTH, 0);
        if (length < 4 || length > 8) {
            return 6;
        }
        return length;
    }

    public static boolean setPassword(Context context, String password) {
        if (!isValidPassword(password)) {
            return false;
        }
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String saltHex = toHex(salt);
        String hash = hash(saltHex, password);
        prefs(context).edit()
                .putString(KEY_PASSWORD_SALT, saltHex)
                .putString(KEY_PASSWORD_HASH, hash)
                .putInt(KEY_PASSWORD_LENGTH, password.length())
                .apply();
        return true;
    }

    public static boolean checkPassword(Context context, String password) {
        if (password == null || !hasPassword(context)) {
            return false;
        }
        String salt = prefs(context).getString(KEY_PASSWORD_SALT, "");
        String expected = prefs(context).getString(KEY_PASSWORD_HASH, "");
        String actual = hash(salt, password);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    public static boolean isValidPassword(String password) {
        if (password == null || password.length() < 4 || password.length() > 8) {
            return false;
        }
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    public static boolean isFloatingEnabled(Context context) {
        return prefs(context).getBoolean(KEY_FLOATING, false);
    }

    public static void setFloatingEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_FLOATING, enabled).apply();
    }

    public static int getStrongAdb(Context context) {
        return prefs(context).getInt(KEY_STRONG_ADB, -1);
    }

    public static void setStrongAdb(Context context, int value) {
        prefs(context).edit().putInt(KEY_STRONG_ADB, value).apply();
    }

    public static void clearStrongAdb(Context context) {
        prefs(context).edit().remove(KEY_STRONG_ADB).apply();
    }

    public static String getShellHome(Context context) {
        return prefs(context).getString(KEY_SHELL_HOME, "");
    }

    public static void setShellHome(Context context, String component) {
        prefs(context).edit().putString(KEY_SHELL_HOME, component).apply();
    }

    public static int getShellMode(Context context) {
        return prefs(context).getInt(KEY_SHELL_MODE, -1);
    }

    public static void setShellMode(Context context, int mode) {
        prefs(context).edit().putInt(KEY_SHELL_MODE, mode).apply();
    }

    public static String getShellPolicy(Context context) {
        return prefs(context).getString(KEY_SHELL_POLICY, "null");
    }

    public static void setShellPolicy(Context context, String policy) {
        prefs(context).edit().putString(KEY_SHELL_POLICY, policy).apply();
    }

    public static String getShellRotation(Context context) {
        return prefs(context).getString(KEY_SHELL_ROTATION, "1");
    }

    public static void setShellRotation(Context context, String rotation) {
        prefs(context).edit().putString(KEY_SHELL_ROTATION, rotation).apply();
    }

    public static boolean isShellDisplayActive(Context context) {
        return prefs(context).getBoolean(KEY_SHELL_DISPLAY_ACTIVE, false);
    }

    public static void setShellDisplayActive(Context context, boolean active) {
        prefs(context).edit().putBoolean(KEY_SHELL_DISPLAY_ACTIVE, active).apply();
    }

    public static void clearShellDisplay(Context context) {
        prefs(context).edit()
                .remove(KEY_SHELL_POLICY)
                .remove(KEY_SHELL_ROTATION)
                .putBoolean(KEY_SHELL_DISPLAY_ACTIVE, false)
                .apply();
    }

    public static boolean isShellHomeActive(Context context) {
        return prefs(context).getBoolean(KEY_SHELL_HOME_ACTIVE, false);
    }

    public static void setShellHomeActive(Context context, boolean active) {
        prefs(context).edit().putBoolean(KEY_SHELL_HOME_ACTIVE, active).apply();
    }

    public static void clearShellHome(Context context) {
        prefs(context).edit()
                .remove(KEY_SHELL_HOME)
                .remove(KEY_SHELL_MODE)
                .putBoolean(KEY_SHELL_HOME_ACTIVE, false)
                .apply();
    }

    public static void clearLock(Context context) {
        prefs(context).edit()
                .putBoolean(KEY_LOCKED, false)
                .remove(KEY_LOCK_MODE)
                .apply();
    }

    private static String hash(String saltHex, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(saltHex.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update(password.getBytes(StandardCharsets.UTF_8));
            return toHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            out[i * 2] = digits[value >>> 4];
            out[i * 2 + 1] = digits[value & 0x0F];
        }
        return new String(out);
    }
}
