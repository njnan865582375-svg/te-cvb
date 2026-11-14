package com.dsh.lock;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.UserManager;
import android.provider.Settings;

import java.util.Collections;

public final class KioskController {
    private KioskController() {
    }

    public static ComponentName admin(Context context) {
        return new ComponentName(context, AdminReceiver.class);
    }

    public static boolean isDeviceOwner(Context context) {
        try {
            DevicePolicyManager manager = (DevicePolicyManager) context.getSystemService(
                    Context.DEVICE_POLICY_SERVICE);
            return manager != null && manager.isDeviceOwnerApp(context.getPackageName());
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean enter(Activity activity) {
        return enter(activity, false);
    }

    public static boolean enter(Activity activity, boolean strong) {
        if (!isDeviceOwner(activity)) {
            return false;
        }
        try {
            DevicePolicyManager manager = (DevicePolicyManager) activity.getSystemService(
                    Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = admin(activity);
            String pkg = activity.getPackageName();
            manager.setLockTaskPackages(admin, new String[]{pkg});
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.setLockTaskFeatures(admin,
                        DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
            }
            manager.setStatusBarDisabled(admin, true);
            manager.setUninstallBlocked(admin, pkg, true);
            try {
                manager.setPermittedAccessibilityServices(admin,
                        Collections.singletonList(pkg));
            } catch (Throwable ignored) {
            }
            try {
                manager.setScreenCaptureDisabled(admin, true);
            } catch (Throwable ignored) {
            }
            if (strong) {
                applyStrong(activity, manager, admin);
            }

            IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
            filter.addCategory(Intent.CATEGORY_HOME);
            filter.addCategory(Intent.CATEGORY_DEFAULT);
            manager.addPersistentPreferredActivity(admin, filter,
                    new ComponentName(activity, MainActivity.class));

            activity.startLockTask();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void exit(Activity activity) {
        try {
            activity.stopLockTask();
        } catch (Throwable ignored) {
        }
        if (!isDeviceOwner(activity)) {
            return;
        }
        try {
            DevicePolicyManager manager = (DevicePolicyManager) activity.getSystemService(
                    Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = admin(activity);
            String pkg = activity.getPackageName();
            manager.setStatusBarDisabled(admin, false);
            manager.clearPackagePersistentPreferredActivities(admin, pkg);
            manager.setLockTaskPackages(admin, new String[]{});
            manager.setUninstallBlocked(admin, pkg, false);
            try {
                manager.setPermittedAccessibilityServices(admin, null);
            } catch (Throwable ignored) {
            }
            try {
                manager.setScreenCaptureDisabled(admin, false);
            } catch (Throwable ignored) {
            }
            removeStrong(activity, manager, admin);
        } catch (Throwable ignored) {
        }
    }

    private static void applyStrong(Activity activity, DevicePolicyManager manager,
                                    ComponentName admin) {
        try {
            manager.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET);
            manager.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
            manager.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT);
                manager.addUserRestriction(admin, UserManager.DISALLOW_USB_FILE_TRANSFER);
            }
        } catch (Throwable ignored) {
        }
        try {
            int adb = Settings.Global.getInt(activity.getContentResolver(),
                    Settings.Global.ADB_ENABLED, 0);
            LockPrefs.setStrongAdb(activity, adb);
            manager.setGlobalSetting(admin, Settings.Global.ADB_ENABLED, "0");
        } catch (Throwable ignored) {
        }
    }

    private static void removeStrong(Activity activity, DevicePolicyManager manager,
                                     ComponentName admin) {
        try {
            manager.clearUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET);
            manager.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
            manager.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT);
                manager.clearUserRestriction(admin, UserManager.DISALLOW_USB_FILE_TRANSFER);
            }
        } catch (Throwable ignored) {
        }
        try {
            int adb = LockPrefs.getStrongAdb(activity);
            if (adb >= 0) {
                manager.setGlobalSetting(admin, Settings.Global.ADB_ENABLED,
                        String.valueOf(adb));
                LockPrefs.clearStrongAdb(activity);
            }
        } catch (Throwable ignored) {
        }
    }
}
