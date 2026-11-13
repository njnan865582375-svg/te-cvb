package com.dsh.lock;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public final class RootHelper {
    private RootHelper() {
    }

    public static boolean isRootAvailable() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            boolean finished = process.waitFor(8, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                return false;
            }
            if (process.exitValue() != 0) {
                return false;
            }
            String output = read(process.getInputStream());
            return output.contains("uid=0");
        } catch (Exception exception) {
            return false;
        }
    }

    public static void runCommand(final String command,
                                  final AccessibilityShell.Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                String output = "";
                try {
                    Process process = Runtime.getRuntime().exec(
                            new String[]{"su", "-c", command});
                    output = read(process.getInputStream());
                    ok = process.waitFor(20, TimeUnit.SECONDS)
                            && process.exitValue() == 0;
                    if (!ok) {
                        process.destroy();
                    }
                } catch (Exception exception) {
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
        }, "te-cvb-root-command").start();
    }

    public static void grantOverlay(final Context context,
                                    final AccessibilityShell.Callback callback) {
        if (Settings.canDrawOverlays(context)) {
            callback.onResult(true, "悬浮锁机权限已就绪");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    String pkg = context.getPackageName();
                    String cmd = "cmd appops set " + pkg + " SYSTEM_ALERT_WINDOW allow; "
                            + "cmd appops set " + pkg + " android:system_alert_window allow";
                    Process process = Runtime.getRuntime().exec(
                            new String[]{"su", "-c", cmd});
                    boolean finished = process.waitFor(20, TimeUnit.SECONDS);
                    ok = finished && process.exitValue() == 0
                            && Settings.canDrawOverlays(context);
                    if (!finished) {
                        process.destroy();
                    }
                } catch (Exception ignored) {
                }
                final boolean granted = ok;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (granted) {
                            callback.onResult(true, "root 已授权悬浮锁机");
                        } else {
                            callback.onResult(false,
                                    "root 授权悬浮锁机失败，请手动开启悬浮窗权限");
                        }
                    }
                });
            }
        }, "te-cvb-root-overlay").start();
    }

    public static void enableAccessibility(final Context context,
                                           final AccessibilityShell.Callback callback) {
        if (LockAccessibilityService.isRunning()) {
            callback.onResult(true, "无障碍锁屏已经在运行");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok;
                try {
                    Process process = Runtime.getRuntime().exec(
                            new String[]{"su", "-c", AccessibilityShell.enableCommand()});
                    ok = process.waitFor(25, TimeUnit.SECONDS) && process.exitValue() == 0;
                    if (!ok) {
                        process.destroy();
                    }
                } catch (Exception exception) {
                    ok = false;
                }
                final boolean granted = ok;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (!granted) {
                            callback.onResult(false, "root 授权失败，或者设备没有 root");
                        } else {
                            AccessibilityShell.waitForService(context, callback);
                        }
                    }
                });
            }
        }, "te-cvb-root").start();
    }

    private static String read(InputStream inputStream) {
        try {
            byte[] buffer = new byte[256];
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
}
