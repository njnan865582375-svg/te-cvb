package com.dsh.lock;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

public final class AccessibilityShell {
    public interface Callback {
        void onResult(boolean success, String message);
    }

    private static final String ENABLE_COMMAND =
            "SERVICE=com.dsh.lock/com.dsh.lock.LockAccessibilityService; "
                    + "CUR=$(settings get secure enabled_accessibility_services 2>/dev/null); "
                    + "if [ \"$CUR\" = \"null\" ]; then CUR=\"\"; fi; "
                    + "case \":$CUR:\" in *\":$SERVICE:\"*) ;; "
                    + "*) if [ -z \"$CUR\" ]; then CUR=\"$SERVICE\"; "
                    + "else CUR=\"$CUR:$SERVICE\"; fi ;; esac; "
                    + "settings put secure enabled_accessibility_services \"$CUR\"; "
                    + "settings put secure accessibility_enabled 1";

    private AccessibilityShell() {
    }

    public static String enableCommand() {
        return ENABLE_COMMAND;
    }

    public static void waitForService(final Context context, final Callback callback) {
        waitForService(context, callback, System.currentTimeMillis() + 3500, 0);
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
}
