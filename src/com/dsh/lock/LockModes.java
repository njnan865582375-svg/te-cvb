package com.dsh.lock;

public final class LockModes {
    public static final int UNSET = -1;
    public static final int NORMAL = 0;
    public static final int ACCESSIBILITY = 1;
    public static final int SHIZUKU = 2;
    public static final int ROOT = 3;
    public static final int OVERLAY = 4;
    public static final int KIOSK = 5;

    private LockModes() {
    }

    public static String name(int mode) {
        switch (mode) {
            case NORMAL:
                return "普通锁机";
            case ACCESSIBILITY:
                return "无障碍锁机";
            case SHIZUKU:
                return "Shizuku 锁机";
            case ROOT:
                return "root 锁机";
            case OVERLAY:
                return "悬浮窗锁机";
            case KIOSK:
                return "设备所有者锁机";
            default:
                return "未选择";
        }
    }

    public static String description(int mode) {
        switch (mode) {
            case NORMAL:
                return "无需权限；Home 手势可能退出，重新打开仍锁定";
            case ACCESSIBILITY:
                return "无障碍全屏覆盖，阻止 Home / 最近任务 / 通知栏；只能密码退出";
            case SHIZUKU:
                return "用 Shizuku 自动开无障碍/悬浮窗、切桌面、冻结选中 App";
            case ROOT:
                return "用 root 自动开无障碍/悬浮窗、切桌面、冻结选中 App";
            case OVERLAY:
                return "用系统悬浮窗覆盖桌面；需要悬浮窗权限";
            case KIOSK:
                return "官方 kiosk：防 Home/最近任务/通知栏/卸载，重启后恢复；需要恢复出厂+ADB 设为设备所有者";
            default:
                return "";
        }
    }

    public static boolean usesAccessibility(int mode) {
        return mode == ACCESSIBILITY || mode == SHIZUKU || mode == ROOT;
    }

    public static boolean isValid(int mode) {
        return mode >= NORMAL && mode <= KIOSK;
    }
}
