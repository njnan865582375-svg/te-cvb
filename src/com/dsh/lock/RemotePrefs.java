package com.dsh.lock;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

public final class RemotePrefs {
    public static final int ROLE_CONTROLLER = 0;
    public static final int ROLE_CONTROLLED = 1;
    public static final int AUTH_PASSWORD = 0;
    public static final int AUTH_KEY = 1;

    private static final String FILE = "remote_lock";
    private static final String KEY_ROLE = "role";
    private static final String KEY_NAME = "name";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_USER = "user";
    private static final String KEY_AUTH = "auth";
    private static final String KEY_SECRET = "secret";
    private static final String KEY_KEY = "private_key";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_SERVICE = "service_enabled";
    private static final String KEY_PENDING = "pending_request";
    private static final String KEY_LAST_REQUEST = "last_request_id";
    private static final String KEY_MASTER = "remote_master";
    private static final String KEY_SELECTED_ID = "selected_id";
    private static final String KEY_SELECTED_NAME = "selected_name";
    private static final String KEY_SELECTED_IDS = "selected_ids";
    private static final String KEY_SAVED = "saved_devices";

    private RemotePrefs() {
    }

    private static SharedPreferences p(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static int getRole(Context context) {
        return p(context).getInt(KEY_ROLE, ROLE_CONTROLLER);
    }

    public static void setRole(Context context, int role) {
        p(context).edit().putInt(KEY_ROLE, role).apply();
    }

    public static String getName(Context context) {
        return p(context).getString(KEY_NAME, "我的设备");
    }

    public static String getHost(Context context) {
        return p(context).getString(KEY_HOST, "");
    }

    public static int getPort(Context context) {
        return p(context).getInt(KEY_PORT, 22);
    }

    public static String getUser(Context context) {
        return p(context).getString(KEY_USER, "");
    }

    public static int getAuth(Context context) {
        return p(context).getInt(KEY_AUTH, AUTH_PASSWORD);
    }

    public static String getSecret(Context context) {
        return p(context).getString(KEY_SECRET, "");
    }

    public static String getKey(Context context) {
        return p(context).getString(KEY_KEY, "");
    }

    public static void save(Context context, String name, String host, int port,
                            String user, int auth, String secret, String key, int role) {
        p(context).edit()
                .putString(KEY_NAME, name)
                .putString(KEY_HOST, host)
                .putInt(KEY_PORT, port)
                .putString(KEY_USER, user)
                .putInt(KEY_AUTH, auth)
                .putString(KEY_SECRET, secret)
                .putString(KEY_KEY, key)
                .putInt(KEY_ROLE, role)
                .apply();
    }

    public static String snapshot(Context context) {
        try {
            JSONObject object = new JSONObject();
            object.put("role", getRole(context));
            object.put("name", getName(context));
            object.put("host", getHost(context));
            object.put("port", getPort(context));
            object.put("user", getUser(context));
            object.put("auth", getAuth(context));
            object.put("secret", getSecret(context));
            object.put("key", getKey(context));
            return object.toString();
        } catch (Exception exception) {
            return "{}";
        }
    }

    public static void restore(Context context, String snapshot) {
        if (snapshot == null) {
            return;
        }
        try {
            JSONObject object = new JSONObject(snapshot);
            save(context,
                    object.optString("name", ""),
                    object.optString("host", ""),
                    object.optInt("port", 22),
                    object.optString("user", ""),
                    object.optInt("auth", AUTH_PASSWORD),
                    object.optString("secret", ""),
                    object.optString("key", ""),
                    object.optInt("role", ROLE_CONTROLLER));
        } catch (Exception ignored) {
        }
    }

    public static boolean isConfigured(Context context) {
        return getHost(context).length() > 0
                && getUser(context).length() > 0
                && (getAuth(context) == AUTH_KEY
                ? getKey(context).length() > 0
                : getSecret(context).length() > 0);
    }

    public static String getDeviceId(Context context) {
        String id = p(context).getString(KEY_DEVICE_ID, null);
        if (id == null || id.length() == 0) {
            id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            p(context).edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }

    public static boolean isServiceEnabled(Context context) {
        return p(context).getBoolean(KEY_SERVICE, false);
    }

    public static void setServiceEnabled(Context context, boolean enabled) {
        p(context).edit().putBoolean(KEY_SERVICE, enabled).apply();
    }

    public static String getPending(Context context) {
        return p(context).getString(KEY_PENDING, null);
    }

    public static void setPending(Context context, String pending) {
        p(context).edit().putString(KEY_PENDING, pending).apply();
    }

    public static String getLastRequestId(Context context) {
        return p(context).getString(KEY_LAST_REQUEST, "");
    }

    public static void setLastRequestId(Context context, String id) {
        p(context).edit().putString(KEY_LAST_REQUEST, id).apply();
    }

    public static boolean isMasterEnabled(Context context) {
        return p(context).getBoolean(KEY_MASTER, false);
    }

    public static void setMasterEnabled(Context context, boolean enabled) {
        p(context).edit().putBoolean(KEY_MASTER, enabled).apply();
    }

    public static String getSelectedId(Context context) {
        return p(context).getString(KEY_SELECTED_ID, "");
    }

    public static String getSelectedName(Context context) {
        return p(context).getString(KEY_SELECTED_NAME, "");
    }

    public static void setSelected(Context context, String id, String name) {
        p(context).edit()
                .putString(KEY_SELECTED_ID, id == null ? "" : id)
                .putString(KEY_SELECTED_NAME, name == null ? "" : name)
                .apply();
    }

    public static JSONArray getSelectedIds(Context context) {
        try {
            return new JSONArray(p(context).getString(KEY_SELECTED_IDS, "[]"));
        } catch (Exception exception) {
            return new JSONArray();
        }
    }

    public static boolean isSelected(Context context, String id) {
        JSONArray array = getSelectedIds(context);
        try {
            for (int i = 0; i < array.length(); i++) {
                if (id.equals(array.getString(i))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static void setSelected(Context context, String id, boolean selected) {
        JSONArray array = getSelectedIds(context);
        JSONArray next = new JSONArray();
        try {
            for (int i = 0; i < array.length(); i++) {
                String value = array.getString(i);
                if (!value.equals(id)) {
                    next.put(value);
                }
            }
            if (selected && id != null && id.length() > 0) {
                next.put(id);
            }
            p(context).edit().putString(KEY_SELECTED_IDS, next.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static int selectedCount(Context context) {
        return getSelectedIds(context).length();
    }

    public static JSONArray getSavedDevices(Context context) {
        try {
            return new JSONArray(p(context).getString(KEY_SAVED, "[]"));
        } catch (Exception exception) {
            return new JSONArray();
        }
    }

    public static void saveDevice(Context context, String id, String name) {
        if (id == null || id.length() == 0) {
            return;
        }
        JSONArray array = getSavedDevices(context);
        try {
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                if (id.equals(object.optString("id", ""))) {
                    object.put("name", name);
                    p(context).edit().putString(KEY_SAVED, array.toString()).apply();
                    return;
                }
            }
            JSONObject object = new JSONObject();
            object.put("id", id);
            object.put("name", name == null ? "被锁机" : name);
            array.put(object);
            p(context).edit().putString(KEY_SAVED, array.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static boolean isSaved(Context context, String id) {
        JSONArray array = getSavedDevices(context);
        try {
            for (int i = 0; i < array.length(); i++) {
                if (id.equals(array.getJSONObject(i).optString("id", ""))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
