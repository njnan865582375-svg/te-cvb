package com.dsh.lock;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class RemoteSsh {
    /** 设备 ID / 请求 ID 只允许这套字符，避免拼进 SFTP 路径时被穿越。 */
    private static final String SAFE_ID = "[A-Za-z0-9_-]{1,48}";

    public static final class Device {
        public String id;
        public String name;
        public String model;
        public long lastSeen;
    }

    public static final class Request {
        public String id;
        public String from;
        public String fromId;
        public long time;
        /** 对方是否用配对码给这次请求签过名。 */
        public boolean verified;
    }

    public interface LockCallback {
        void onResult(int state, String message);
    }

    private RemoteSsh() {
    }

    public static void test(Context context) throws Exception {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            session = openSession(context);
            sftp = openSftp(session);
            ensureDirs(sftp);
        } finally {
            close(sftp, session);
        }
    }

    public static void writePresence(Context context, String status) throws Exception {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            session = openSession(context);
            sftp = openSftp(session);
            String base = ensureDirs(sftp);
            JSONObject object = new JSONObject();
            object.put("id", RemotePrefs.getDeviceId(context));
            object.put("name", RemotePrefs.getName(context));
            object.put("model", Build.MODEL);
            object.put("status", status);
            object.put("last", System.currentTimeMillis());
            write(sftp, base + "/devices/" + RemotePrefs.getDeviceId(context) + ".json",
                    object.toString());
        } finally {
            close(sftp, session);
        }
    }

    public static Request pollRequest(Context context) throws Exception {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            session = openSession(context);
            sftp = openSftp(session);
            String base = ensureDirs(sftp);
            JSONObject presence = new JSONObject();
            presence.put("id", RemotePrefs.getDeviceId(context));
            presence.put("name", RemotePrefs.getName(context));
            presence.put("model", Build.MODEL);
            presence.put("status", "online");
            presence.put("last", System.currentTimeMillis());
            write(sftp, base + "/devices/" + RemotePrefs.getDeviceId(context) + ".json",
                    presence.toString());
            String path = base + "/requests/" + safeId(RemotePrefs.getDeviceId(context)) + ".json";
            String content = read(sftp, path);
            if (content == null) {
                return null;
            }
            JSONObject object = new JSONObject(content);
            if (!"pending".equals(object.optString("state", "pending"))) {
                return null;
            }
            String id = object.optString("id", "");
            if (id.length() == 0 || !id.matches(SAFE_ID)) {
                return null;
            }
            if (id.equals(RemotePrefs.getLastRequestId(context))
                    || RemotePrefs.hasSeenRequest(context, id)) {
                return null;
            }
            Request request = new Request();
            request.id = id;
            request.from = object.optString("from", "控制端");
            request.fromId = object.optString("from_id", "");
            request.time = object.optLong("time", System.currentTimeMillis());
            request.verified = verifySignature(RemotePrefs.getPairCode(context),
                    request.id, request.fromId, request.time, object.optString("sig", ""));
            if (!request.verified) {
                // 没签名或签名不对：来源只保留可读片段，交给用户判断
                if (request.fromId.length() > 0 && !request.fromId.matches(SAFE_ID)) {
                    request.fromId = "";
                }
                request.from = request.from + "（未配对）";
            }
            return request;
        } finally {
            close(sftp, session);
        }
    }

    public static void writeRequest(Context context, String deviceId, String requestId)
            throws Exception {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            String target = safeId(deviceId);
            if (!requestId.matches(SAFE_ID)) {
                throw new IllegalArgumentException("请求 ID 不合法");
            }
            session = openSession(context);
            sftp = openSftp(session);
            String base = ensureDirs(sftp);
            long now = System.currentTimeMillis();
            String fromId = RemotePrefs.getDeviceId(context);
            JSONObject object = new JSONObject();
            object.put("id", requestId);
            object.put("from", RemotePrefs.getName(context));
            object.put("from_id", fromId);
            object.put("time", now);
            object.put("state", "pending");
            String sig = sign(RemotePrefs.getTargetCode(context), requestId, fromId, now);
            if (sig.length() > 0) {
                object.put("sig", sig);
            }
            write(sftp, base + "/requests/" + target + ".json", object.toString());
        } finally {
            close(sftp, session);
        }
    }

    public static void writeResponse(Context context, String deviceId, String requestId,
                                     boolean approved) throws Exception {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            String target = safeId(deviceId);
            session = openSession(context);
            sftp = openSftp(session);
            String base = ensureDirs(sftp);
            long now = System.currentTimeMillis();
            JSONObject object = new JSONObject();
            object.put("request_id", requestId);
            object.put("device_id", RemotePrefs.getDeviceId(context));
            object.put("approved", approved);
            object.put("time", now);
            String sig = sign(RemotePrefs.getPairCode(context), requestId,
                    RemotePrefs.getDeviceId(context), now);
            if (sig.length() > 0) {
                object.put("sig", sig);
            }
            write(sftp, base + "/responses/" + target + ".json", object.toString());
        } finally {
            close(sftp, session);
        }
    }

    public static String readResponse(Context context, String deviceId) throws Exception {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            session = openSession(context);
            sftp = openSftp(session);
            String base = ensureDirs(sftp);
            return read(sftp, base + "/responses/" + safeId(deviceId) + ".json");
        } finally {
            close(sftp, session);
        }
    }

    public static List<Device> listDevices(Context context) throws Exception {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            session = openSession(context);
            sftp = openSftp(session);
            String base = ensureDirs(sftp);
            List<Device> result = new ArrayList<>();
            Vector<ChannelSftp.LsEntry> entries = sftp.ls(base + "/devices");
            long nowSeconds = System.currentTimeMillis() / 1000L;
            for (ChannelSftp.LsEntry entry : entries) {
                String name = entry.getFilename();
                if (!name.endsWith(".json") || !name.matches("[A-Za-z0-9_-]{1,48}\\.json")) {
                    continue;
                }
                long modified = 0;
                try {
                    modified = entry.getAttrs().getMTime();
                } catch (Exception ignored) {
                }
                if (modified > 0 && nowSeconds - modified > 20L) {
                    continue;
                }
                String content = read(sftp, base + "/devices/" + name);
                if (content == null) {
                    continue;
                }
                JSONObject object = new JSONObject(content);
                Device device = new Device();
                device.id = object.optString("id", name.substring(0, name.length() - 5));
                if (!device.id.matches(SAFE_ID)) {
                    continue;
                }
                device.name = object.optString("name", "未命名设备");
                device.model = object.optString("model", "");
                device.lastSeen = modified * 1000L;
                result.add(device);
            }
            return result;
        } finally {
            close(sftp, session);
        }
    }

    public static void requestAndWait(final Context context, final String deviceId,
                                      final String deviceName, final LockCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int state = -1;
                String message = "";
                try {
                    String requestId = newRequestId();
                    writeRequest(context, deviceId, requestId);
                    for (int i = 0; i < 30; i++) {
                        Thread.sleep(2000);
                        String response = readResponse(context, deviceId);
                        if (response == null) {
                            continue;
                        }
                        JSONObject object = new JSONObject(response);
                        if (!requestId.equals(object.optString("request_id", ""))) {
                            continue;
                        }
                        if (object.optBoolean("approved", false)) {
                            String sig = object.optString("sig", "");
                            String from = object.optString("device_id", "");
                            long when = object.optLong("time", 0L);
                            if (!verifySignature(RemotePrefs.getTargetCode(context),
                                    requestId, from, when, sig)) {
                                state = 0;
                                message = "对方回复没有通过配对码校验，已忽略";
                                break;
                            }
                            state = 1;
                            message = "对方已同意，锁机已开始";
                        } else {
                            state = 0;
                            message = "对方拒绝了锁机请求";
                        }
                        break;
                    }
                    if (state == -1 && message.length() == 0) {
                        message = "等待超时，对方没有同意";
                    }
                } catch (Exception exception) {
                    message = exception.getMessage();
                    if (message == null || message.length() == 0) {
                        message = "远程请求失败";
                    }
                }
                final int finalState = state;
                final String finalMessage = message;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(finalState, finalMessage);
                    }
                });
            }
        }, "te-cvb-remote-request").start();
    }

    /** 32 位十六进制随机串，够长到暴力猜不动。 */
    public static String newRequestId() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        StringBuilder out = new StringBuilder();
        for (byte value : bytes) {
            out.append(String.format("%02x", value));
        }
        return out.toString();
    }

    // ---- 内部实现 ------------------------------------------------------

    private static Session openSession(Context context) throws Exception {
        if (!RemotePrefs.isConfigured(context)) {
            throw new IllegalStateException("远程服务器还没有配置完整");
        }
        JSch jsch = new JSch();
        if (RemotePrefs.getAuth(context) == RemotePrefs.AUTH_KEY) {
            byte[] key = RemotePrefs.getKey(context).getBytes(StandardCharsets.UTF_8);
            jsch.addIdentity("te-cvb-key", key, null, null);
        }
        String host = RemotePrefs.getHost(context);
        Session session = jsch.getSession(
                RemotePrefs.getUser(context),
                host,
                RemotePrefs.getPort(context));
        if (RemotePrefs.getAuth(context) == RemotePrefs.AUTH_PASSWORD) {
            session.setPassword(RemotePrefs.getSecret(context));
        }
        // TOFU：第一次见到的公钥记下来，之后必须完全一致
        jsch.setHostKeyRepository(new TofuHostKeys(context));
        session.setConfig("StrictHostKeyChecking", "yes");
        session.setConfig("PreferredAuthentications",
                RemotePrefs.getAuth(context) == RemotePrefs.AUTH_KEY
                        ? "publickey"
                        : "password,keyboard-interactive");
        session.setUserInfo(new SilentUserInfo());
        session.setServerAliveInterval(10_000);
        session.setTimeout(15_000);
        try {
            session.connect(15_000);
        } catch (Exception exception) {
            String text = String.valueOf(exception.getMessage());
            if (text.contains("HostKey has been changed")
                    || text.contains("changed")
                    || text.contains("UnknownHostKey")) {
                throw new IllegalStateException(
                        "服务器主机密钥与之前记录的不一致，可能是中间人。"
                                + "确认服务器换过密钥后，在远程设置里点“清除主机指纹”再连。");
            }
            throw exception;
        }
        return session;
    }

    private static ChannelSftp openSftp(Session session) throws Exception {
        ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect(15_000);
        return sftp;
    }

    private static String ensureDirs(ChannelSftp sftp) throws Exception {
        String home = sftp.getHome();
        String base = home.endsWith("/") ? home + ".tecvb" : home + "/.tecvb";
        mkdir(sftp, base);
        mkdir(sftp, base + "/devices");
        mkdir(sftp, base + "/requests");
        mkdir(sftp, base + "/responses");
        return base;
    }

    private static void mkdir(ChannelSftp sftp, String path) {
        try {
            sftp.mkdir(path);
        } catch (Exception ignored) {
        }
    }

    private static void write(ChannelSftp sftp, String path, String content) throws Exception {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        sftp.put(new ByteArrayInputStream(bytes), path);
    }

    private static String read(ChannelSftp sftp, String path) {
        try {
            InputStream input = sftp.get(path);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            input.close();
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return null;
        }
    }

    private static void close(ChannelSftp sftp, Session session) {
        if (sftp != null) {
            try {
                sftp.disconnect();
            } catch (Exception ignored) {
            }
        }
        if (session != null) {
            try {
                session.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    private static String safeId(String id) {
        if (id == null || !id.matches(SAFE_ID)) {
            throw new IllegalArgumentException("设备 ID 不合法");
        }
        return id;
    }

    /** 配对码为空时返回空串，表示这次交互没有签名。 */
    private static String sign(String code, String requestId, String fromId, long time) {
        if (code == null || code.length() == 0) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(code.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal((requestId + "|" + fromId + "|" + time)
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : out) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString().substring(0, 32);
        } catch (Exception exception) {
            return "";
        }
    }

    private static boolean verifySignature(String code, String requestId, String fromId,
                                           long time, String signature) {
        String expected = sign(code, requestId, fromId, time);
        if (expected.length() == 0 || signature == null || signature.length() == 0) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 主机公钥 TOFU 仓库：第一次见到某个主机就记住它的公钥指纹，
     * 以后连接时公钥变了就拒绝，而不是无脑信任。
     */
    private static final class TofuHostKeys implements HostKeyRepository {
        private final Context context;

        TofuHostKeys(Context context) {
            this.context = context;
        }

        @Override
        public int check(String host, byte[] key) {
            String known = RemotePrefs.getHostKey(context);
            String current = RemotePrefs.sha256Base64(key);
            if (known.length() == 0) {
                RemotePrefs.setHostKey(context, "sha256 " + current);
                return OK;
            }
            int space = known.indexOf(' ');
            String stored = space >= 0 ? known.substring(space + 1) : known;
            if (stored.equals(current)) {
                return OK;
            }
            return CHANGED;
        }

        @Override
        public void add(HostKey hostKey, UserInfo userInfo) {
            if (hostKey != null && hostKey.getKey() != null) {
                RemotePrefs.setHostKey(context, hostKey.getType() + " "
                        + RemotePrefs.sha256Base64(
                        hostKey.getKey().getBytes(StandardCharsets.UTF_8)));
            }
        }

        @Override
        public void remove(String host, String type) {
            RemotePrefs.clearHostKey(context);
        }

        @Override
        public void remove(String host, String type, byte[] key) {
            RemotePrefs.clearHostKey(context);
        }

        @Override
        public String getKnownHostsRepositoryID() {
            return "te-cvb-tofu";
        }

        @Override
        public HostKey[] getHostKey() {
            return new HostKey[0];
        }

        @Override
        public HostKey[] getHostKey(String host, String type) {
            return new HostKey[0];
        }
    }

    private static final class SilentUserInfo implements UserInfo {
        @Override
        public String getPassphrase() {
            return null;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public boolean promptPassword(String message) {
            return false;
        }

        @Override
        public boolean promptPassphrase(String message) {
            return false;
        }

        @Override
        public boolean promptYesNo(String message) {
            return false;
        }

        @Override
        public void showMessage(String message) {
        }
    }
}
