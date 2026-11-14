package com.dsh.lock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 两个会让无障碍服务"凭空断掉"的时机：
 *
 * 1. 开机 —— 系统不会自动把无障碍服务绑回来（尤其是开机后第一次解锁前）。
 * 2. 覆盖安装/升级 —— Android 在包被替换时会把它的无障碍服务禁用掉。
 *
 * 这两处以前都没人管，锁机状态下用户会看到锁屏界面消失、保护形同虚设。
 */
public final class GuardReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "android.intent.action.HTC.QUICKBOOT_POWERON".equals(action);
        boolean replaced = Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        if (!boot && !replaced) {
            return;
        }
        if (!LockPrefs.isLocked(context)) {
            // 没锁着就没什么可修的
            return;
        }
        if (!LockModes.usesAccessibility(LockPrefs.getLockMode(context))) {
            LockGuardService.start(context);
            return;
        }
        LockGuardService.start(context);
        AccessibilityShell.recover(context, new AccessibilityShell.Callback() {
            @Override
            public void onResult(boolean success, String message) {
            }
        });
    }
}
