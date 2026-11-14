package com.dsh.lock;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

public final class AccessibilityShell {
    public interface Callback {
        void onResult(boolean success, String message);
    }

    private static final String SERVICE =
            "com.dsh.lock/com.dsh.lock.LockAccessibilityService";

    /**
     * 只做"补齐"：列表里没有就追加，有了就不动。不会为了强制重连去改写
     * 已经存在的条目，避免把别的无障碍服务一起踢掉。
     */
    private static final String ENABLE_COMMAND =
            "SERVICE=" + SERVICE + "; "
            + "CUR=$(settings get secure enabled_accessibility_services 2>/dev/null); "
            + "[ \"$CUR\" = \"null\" ] && CUR=\"\"; "
            + "case \":$CUR:\" in *\":$SERVICE:\"*) ;; "
            + "*) if [ -z \"$CUR\" ]; then CUR=\"$SERVICE\"; "
            + "else CUR=\"$CUR:$SERVICE\"; fi; "
            + "settings put secure enabled_accessibility_services \"$CUR\" ;; esac; "
            + "EN=$(settings get secure accessibility_enabled 2>/dev/null); "
            + "[ \"$EN\" = \"1\" ] || settings put secure accessibility_enabled 1; "
            + "printf '%s' \"$(settings get secure enabled_accessibility_services)\"";

    /**
     * 强制重连：服务已经在列表里但进程没连上时，单纯重写同一个值系统不会理。
     * 先把它摘出去、等一下、再写回来，系统才会重新 bind。
     * 摘除时保留列表里其他服务不动。
     */
    private static final String REFRESH_COMMAND =
            "SERVICE=" + SERVICE + "; "
            + "CUR=$(settings get secure enabled_accessibility_services 2>/dev/null); "
            + "[ \"$CUR\" = \"null\" ] && CUR=\"\"; "
            + "KEEP=\"\"; OLDIFS=$IFS; IFS=:; "
            + "for s in $CUR; do "
            + "[ -z \"$s\" ] && continue; "
            + "[ \"$s\" = \"$SERVICE\" ] && continue; "
            + "if [ -z \"$KEEP\" ]; then KEEP=\"$s\"; else KEEP=\"$KEEP:$s\"; fi; "
            + "done; IFS=$OLDIFS; "
            + "settings put secure enabled_accessibility_services \"$KEEP\"; "
            + "sleep 1; "
            + "if [ -z \"$KEEP\" ]; then FULL=\"$SERVICE\"; else FULL=\"$KEEP:$SERVICE\"; fi; "
            + "settings put secure enabled_accessibility_services \"$FULL\"; "
            + "settings put secure accessibility_enabled 1; "
            + "printf '%s' \"$(settings get secure enabled_accessibility_services)\"";

    private AccessibilityShell() {
    }

    public static String enableCommand() {
        return ENABLE_COMMAND;
    }

    public static String refreshCommand() {
        return REFRESH_COMMAND;
    }

    public static void waitForService(final Context context, final Callback callback) {
        waitForService(context, callback, System.currentTimeMillis() + 8000, 0);
    }

    private static void waitForService(final Context context, final Callback callback,
                                       final long deadline, final int attempt) {
        if (LockAccessibilityService.isRunning()) {
            callback.onResult(true, "无障碍锁屏已连接");
            return;
        }
        if (System.currentTimeMillis() >= deadline) {
            callback.onResult(false, "无障碍服务没有连接，请在系统设置里手动开启“TE cvb 锁机”");
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                waitForService(context, callback, deadline, attempt + 1);
            }
        }, attempt == 0 ? 250 : 350);
    }

    /**
     * 断线自愈：能拿到 shell 就自己把服务重新绑上，拿不到就如实报告失败。
     * 调用方（守护服务 / 开机广播）负责限速，这里不做节流。
     */
    public static void recover(final Context context, final Callback callback) {
        if (LockAccessibilityService.isRunning()) {
            callback.onResult(true, "无障碍锁屏运行正常");
            return;
        }
        final boolean listed = LockAccessibilityService.isEnabledInSettings(context);
        final boolean root = RootHelper.isRootAvailable();
        final boolean shizuku = !root && ShizukuHelper.isAvailable() && ShizukuHelper.hasPermission();

        if (!root && !shizuku) {
            if (!listed) {
                callback.onResult(false,
                        "无障碍服务被系统关掉了，需要 root 或 Shizuku 才能自动重新打开");
            } else {
                callback.onResult(false,
                        "无障碍服务在设置里是开的，但进程没连上，需要 root 或 Shizuku 才能强制重连");
            }
            return;
        }

        final String command = listed ? REFRESH_COMMAND : ENABLE_COMMAND;
        final Callback afterShell = new Callback() {
            @Override
            public void onResult(final boolean ok, final String message) {
                if (!ok) {
                    callback.onResult(false, message);
                    return;
                }
                waitForService(context, callback);
            }
        };
        if (root) {
            RootHelper.runCommand(command, new Callback() {
                @Override
                public void onResult(boolean ok, String message) {
                    afterShell.onResult(ok, ok ? "" : "root 执行失败");
                }
            });
        } else {
            ShizukuHelper.runCommand(command, new Callback() {
                @Override
                public void onResult(boolean ok, String message) {
                    afterShell.onResult(ok, ok ? "" : "Shizuku 执行失败");
                }
            });
        }
    }
}
