package com.dsh.lock;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.SecureRandom;
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

    /** 被控端出示的配对码；控制端要填同一串，请求才会被标成"已配对"。 */
    private static final String KEY_PAIR_CODE = "pair_code";
    /** 控制端记录的对方配对码，用来给请求签名。 */
    private static final String KEY_TARGET_CODE = "target_code";
    /** 已确认过的主机公钥指纹（TOFU），格式 <算法> <base64 sha256>。 */
    private static final String KEY_HOST_KEY = "host_key";
    /** 最近处理过的请求 ID，用于防重放。 */
    private static final String KEY_SEEN = "seen_requests";

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

    /** 解密后的 SSH 密码；落盘时由 SecretVault 加密。 */
    public static String getSecret(Context context) {
        return SecretVault.open(rawSecret(context));
    }

    /** 落盘的原始（密文）值，只在快照回滚时需要。 */
    private static String rawSecret(Context context) {
        return p(context).getString(KEY_SECRET, "");
    }

    /** 解密后的 SSH 私钥内容。 */
    public static String getKey(Context context) {
        return SecretVault.open(rawKey(context));
    }

    private static String rawKey(Context context) {
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
                .putString(KEY_SECRET, SecretVault.seal(secret))
                .putString(KEY_KEY, SecretVault.seal(key))
                .putInt(KEY_ROLE, role)
                .apply();
    }

    /** 敏感字段是否已经加密落盘（false 表示 Keystore 不可用或还是旧明文）。 */
    public static boolean isSecretEncrypted(Context context) {
        String secret = rawSecret(context);
        String key = rawKey(context);
        boolean anyStored = secret.length() > 0 || key.length() > 0;
        if (!anyStored) {
            return true;
        }
        return !SecretVault.isLegacyPlaintext(secret) && !SecretVault.isLegacyPlaintext(key)
                && SecretVault.isAvailable();
    }

    /**
     * 快照用于"保存失败就回滚"。敏感字段保留的是密文原文，
     * 因此快照本身不含可读密码，且回滚后仍能正常解密。
     */
    public static String snapshot(Context context) {
        try {
            JSONObject object = new JSONObject();
            object.put("role", getRole(context));
            object.put("name", getName(context));
            object.put("host", getHost(context));
            object.put("port", getPort(context));
            object.put("user", getUser(context));
            object.put("auth", getAuth(context));
            object.put("secret_sealed", rawSecret(context));
            object.put("key_sealed", rawKey(context));
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
            SharedPreferences.Editor editor = p(context).edit();
            editor.putString(KEY_NAME, object.optString("name", ""));
            editor.putString(KEY_HOST, object.optString("host", ""));
            editor.putInt(KEY_PORT, object.optInt("port", 22));
            editor.putString(KEY_USER, object.optString("user", ""));
            editor.putInt(KEY_AUTH, object.optInt("auth", AUTH_PASSWORD));
            if (object.has("secret_sealed")) {
                editor.putString(KEY_SECRET, object.optString("secret_sealed", ""));
                editor.putString(KEY_KEY, object.optString("key_sealed", ""));
            } else {
                // 兼容旧快照：里面可能是明文，重新封装后再落盘
                editor.putString(KEY_SECRET, SecretVault.seal(object.optString("secret", "")));
                editor.putString(KEY_KEY, SecretVault.seal(object.optString("key", "")));
            }
            editor.putInt(KEY_ROLE, object.optInt("role", ROLE_CONTROLLER));
            editor.apply();
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

    // ---- 配对码 / 主机指纹 / 防重放 ------------------------------------

    /** 被控端的配对码，第一次取用时生成 6 位数字。 */
    public static String getPairCode(Context context) {
        String code = p(context).getString(KEY_PAIR_CODE, "");
        if (code.length() == 0) {
            code = newPairCode();
            p(context).edit().putString(KEY_PAIR_CODE, code).apply();
        }
        return code;
    }

    public static String regeneratePairCode(Context context) {
        String code = newPairCode();
        p(context).edit().putString(KEY_PAIR_CODE, code).apply();
        return code;
    }

    private static String newPairCode() {
        SecureRandom random = new SecureRandom();
        int value = 100000 + random.nextInt(900000);
        return String.valueOf(value);
    }

    public static String getTargetCode(Context context) {
        return p(context).getString(KEY_TARGET_CODE, "");
    }

    public static void setTargetCode(Context context, String code) {
        p(context).edit().putString(KEY_TARGET_CODE, code == null ? "" : code.trim()).apply();
    }

    public static String getHostKey(Context context) {
        return p(context).getString(KEY_HOST_KEY, "");
    }

    public static void setHostKey(Context context, String fingerprint) {
        p(context).edit().putString(KEY_HOST_KEY, fingerprint == null ? "" : fingerprint).apply();
    }

    public static void clearHostKey(Context context) {
        p(context).edit().remove(KEY_HOST_KEY).apply();
    }

    /** 记一个已处理过的请求 ID，只保留最近 32 个。 */
    public static void rememberRequest(Context context, String id) {
        if (id == null || id.length() == 0) {
            return;
        }
        JSONArray array = seenRequests(context);
        JSONArray next = new JSONArray();
        try {
            next.put(id);
            for (int i = 0; i < array.length() && next.length() < 32; i++) {
                String value = array.getString(i);
                if (!id.equals(value)) {
                    next.put(value);
                }
            }
            p(context).edit().putString(KEY_SEEN, next.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static boolean hasSeenRequest(Context context, String id) {
        JSONArray array = seenRequests(context);
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

    private static JSONArray seenRequests(Context context) {
        try {
            return new JSONArray(p(context).getString(KEY_SEEN, "[]"));
        } catch (Exception exception) {
            return new JSONArray();
        }
    }

    /** 主机公钥的短指纹，给 UI 显示用。 */
    public static String hostKeyShort(Context context) {
        String stored = getHostKey(context);
        if (stored.length() == 0) {
            return "";
        }
        int space = stored.indexOf(' ');
        String hash = space >= 0 ? stored.substring(space + 1) : stored;
        String cleaned = hash.replace("+", "").replace("/", "").replace("=", "");
        return cleaned.length() <= 16 ? cleaned : cleaned.substring(0, 16);
    }

    static String sha256Base64(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return android.util.Base64.encodeToString(digest.digest(data),
                    android.util.Base64.NO_WRAP);
        } catch (Exception exception) {
            return "";
        }
    }
}
