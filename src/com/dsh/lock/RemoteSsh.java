package com.dsh.lock;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public final class RemoteSsh {
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
            String content = read(sftp, base + "/requests/" + RemotePrefs.getDeviceId(context) + ".json");
            if (content == null) {
                return null;
            }
            JSONObject object = new JSONObject(content);
            if (!"pending".equals(object.optString("state", "pending"))) {
                return null;
            }
            String id = object.optString("id", "");
            if (id.length() == 0 || id.equals(RemotePrefs.getLastRequestId(context))) {
                return null;
            }
            Request request = new Request();
            request.id = id;
            request.from = object.optString("from", "控制端");
            request.fromId = object.optString("from_id", "");
            request.time = object.optLong("time", System.currentTimeMillis());
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
            session = openSession(context);
            sftp = openSftp(session);
            String base = ensureDirs(sftp);
            JSONObject object = new JSONObject();
            object.put("id", requestId);
            object.put("from", RemotePrefs.getName(context));
            object.put("from_id", RemotePrefs.getDeviceId(context));
            object.put("time", System.currentTimeMillis());
            object.put("state", "pending");
            write(sftp, base + "/requests/" + deviceId + ".json", object.toString());
        } finally {
            close(sftp, session);
        }
    }

    public static void writeResponse(Context context, String deviceId, String requestId,
                                     boolean approved) throws Exception {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            session = openSession(context);
            sftp = openSftp(session);
            String base = ensureDirs(sftp);
            JSONObject object = new JSONObject();
            object.put("request_id", requestId);
            object.put("device_id", RemotePrefs.getDeviceId(context));
            object.put("approved", approved);
            object.put("time", System.currentTimeMillis());
            write(sftp, base + "/responses/" + deviceId + ".json", object.toString());
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
            return read(sftp, base + "/responses/" + deviceId + ".json");
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
                if (!name.endsWith(".json")) {
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
                    String requestId = java.util.UUID.randomUUID()
                            .toString().replace("-", "").substring(0, 12);
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

    private static Session openSession(Context context) throws Exception {
        if (!RemotePrefs.isConfigured(context)) {
            throw new IllegalStateException("远程服务器还没有配置完整");
        }
        JSch jsch = new JSch();
        if (RemotePrefs.getAuth(context) == RemotePrefs.AUTH_KEY) {
            byte[] key = RemotePrefs.getKey(context).getBytes(StandardCharsets.UTF_8);
            jsch.addIdentity("te-cvb-key", key, null, null);
        }
        Session session = jsch.getSession(
                RemotePrefs.getUser(context),
                RemotePrefs.getHost(context),
                RemotePrefs.getPort(context));
        if (RemotePrefs.getAuth(context) == RemotePrefs.AUTH_PASSWORD) {
            session.setPassword(RemotePrefs.getSecret(context));
        }
        session.setConfig("StrictHostKeyChecking", "no");
        session.setServerAliveInterval(10_000);
        session.setTimeout(15_000);
        session.connect(15_000);
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
}
