package com.dsh.lock;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.provider.Settings;

import java.io.InputStream;

import moe.shizuku.server.IShizukuService;
import moe.shizuku.server.IRemoteProcess;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;

public final class ShizukuHelper {
    public static final int REQUEST_CODE = 4011;

    private ShizukuHelper() {
    }

    public static boolean isAvailable() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable throwable) {
            return false;
        }
    }

    public static boolean hasPermission() {
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable throwable) {
            return false;
        }
    }

    public static void requestPermission() {
        try {
            Shizuku.requestPermission(REQUEST_CODE);
        } catch (Throwable ignored) {
        }
    }

    public static void runCommand(final String command,
                                  final AccessibilityShell.Callback callback) {
        if (!isAvailable()) {
            callback.onResult(false, "没有连接到 Shizuku");
            return;
        }
        if (!hasPermission()) {
            callback.onResult(false, "Shizuku 还没有授权");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                String output = "";
                try {
                    IShizukuService service = IShizukuService.Stub.asInterface(
                            new ShizukuBinderWrapper(Shizuku.getBinder()));
                    IRemoteProcess process = service.newProcess(
                            new String[]{"sh", "-c", command}, null, null);
                    output = read(new ParcelFileDescriptor.AutoCloseInputStream(
                            process.getInputStream()));
                    ok = process.waitForTimeout(20, "SECONDS") && process.exitValue() == 0;
                    if (!ok) {
                        process.destroy();
                    }
                } catch (Throwable throwable) {
                    output = "";
                }
                final boolean success = ok;
                final String result = output;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(success, result);
                    }
                });
            }
        }, "te-cvb-shizuku-command").start();
    }

    private static String read(InputStream inputStream) {
        try {
            byte[] buffer = new byte[512];
            StringBuilder output = new StringBuilder();
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                output.append(new String(buffer, 0, read));
            }
            return output.toString();
        } catch (Exception exception) {
            return "";
        }
    }

    public static void grantOverlay(final Context context,
                                    final AccessibilityShell.Callback callback) {
        if (Settings.canDrawOverlays(context)) {
            callback.onResult(true, "悬浮锁机权限已就绪");
            return;
        }
        if (!isAvailable()) {
            callback.onResult(false, "没有连接到 Shizuku");
            return;
        }
        if (!hasPermission()) {
            callback.onResult(false, "Shizuku 还没有授权");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    IShizukuService service = IShizukuService.Stub.asInterface(
                            new ShizukuBinderWrapper(Shizuku.getBinder()));
                    String pkg = context.getPackageName();
                    String cmd = "cmd appops set " + pkg + " SYSTEM_ALERT_WINDOW allow; "
                            + "cmd appops set " + pkg + " android:system_alert_window allow";
                    IRemoteProcess process = service.newProcess(
                            new String[]{"sh", "-c", cmd}, null, null);
                    boolean finished = process.waitForTimeout(20, "SECONDS");
                    ok = finished && process.exitValue() == 0
                            && Settings.canDrawOverlays(context);
                    if (!finished) {
                        process.destroy();
                    }
                } catch (Throwable ignored) {
                }
                final boolean granted = ok;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (granted) {
                            callback.onResult(true, "Shizuku 已授权悬浮锁机");
                        } else {
                            callback.onResult(false,
                                    "Shizuku 授权悬浮锁机失败，请手动开启悬浮窗权限");
                        }
                    }
                });
            }
        }, "te-cvb-shizuku-overlay").start();
    }

    public static void enableAccessibility(final Context context,
                                           final AccessibilityShell.Callback callback) {
        if (LockAccessibilityService.isRunning()) {
            callback.onResult(true, "无障碍锁屏已经在运行");
            return;
        }
        if (!isAvailable()) {
            callback.onResult(false, "没有连接到 Shizuku");
            return;
        }
        if (!hasPermission()) {
            callback.onResult(false, "Shizuku 还没有授权");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok;
                try {
                    IShizukuService service = IShizukuService.Stub.asInterface(
                            new ShizukuBinderWrapper(Shizuku.getBinder()));
                    IRemoteProcess process = service.newProcess(
                            new String[]{"sh", "-c", AccessibilityShell.enableCommand()},
                            null, null);
                    boolean finished = process.waitForTimeout(25, "SECONDS");
                    ok = finished && process.exitValue() == 0;
                    if (!finished) {
                        process.destroy();
                    }
                } catch (Throwable throwable) {
                    ok = false;
                }
                final boolean granted = ok;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (!granted) {
                            callback.onResult(false, "Shizuku 命令执行失败");
                        } else {
                            AccessibilityShell.waitForService(context, callback);
                        }
                    }
                });
            }
        }, "te-cvb-shizuku").start();
    }
}
