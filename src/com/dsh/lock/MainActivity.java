package com.dsh.lock;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private static final String EXTRA_REMOTE_LOCK = "remote_lock";
    private static final int REQ_NOTIFICATIONS = 33;
    private static final int REQ_MODE = 21;
    private static final int REQ_ACCESSIBILITY = 22;
    private static final int REQ_OVERLAY = 23;

    private LockView lockView;
    private boolean locked;
    private boolean pendingAccessibility;
    private boolean pendingOverlay;
    private int pendingMode = LockModes.UNSET;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Shizuku.OnBinderDeadListener binderDeadListener;

    private final Shizuku.OnRequestPermissionResultListener shizukuListener =
            new Shizuku.OnRequestPermissionResultListener() {
                @Override
                public void onRequestPermissionResult(final int requestCode, final int grantResult) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (requestCode != ShizukuHelper.REQUEST_CODE) {
                                return;
                            }
                            if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                int mode = pendingMode;
                                pendingMode = LockModes.UNSET;
                                if (mode != LockModes.UNSET) {
                                    startShizukuLock(mode);
                                }
                            } else {
                                pendingMode = LockModes.UNSET;
                                showMessage("Shizuku 授权被拒绝");
                            }
                        }
                    });
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.setNavigationBarDividerColor(Color.TRANSPARENT);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }

        if (LockPrefs.isLocked(this) && !LockPrefs.hasPassword(this)) {
            LockPrefs.clearLock(this);
        }
        locked = LockPrefs.isLocked(this);
        int savedMode = locked ? LockPrefs.getLockMode(this) : LockModes.UNSET;

        lockView = new LockView(this, new LockView.Listener() {
            @Override
            public void onStartTest() {
                handleStartTest();
            }

            @Override
            public void onStartLock(int mode) {
                handleStartTest();
            }

            @Override
            public void onOpenSettings() {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }

            @Override
            public void onUnlockRequested() {
                finishUnlock();
            }
        });
        setContentView(lockView);
        requestNotificationPermission();

        if (locked) {
            if (LockModes.usesAccessibility(savedMode)
                    && LockAccessibilityService.isRunning()
                    && LockAccessibilityService.requestLockOverlay(this)) {
                finish();
                return;
            }
            if (savedMode == LockModes.OVERLAY && Settings.canDrawOverlays(this)) {
                OverlayLockService.start(this, OverlayLockService.ACTION_LOCK);
                finish();
                return;
            }
            lockView.showLockedImmediately();
            applyLockedUi();
            if (savedMode != LockModes.OVERLAY) {
                LockGuardService.start(this);
            }
            if ((savedMode == LockModes.KIOSK || savedMode == LockModes.KIOSK_STRONG)
                    && KioskController.isDeviceOwner(this)) {
                KioskController.enter(this, savedMode == LockModes.KIOSK_STRONG);
            }
        } else {
            applyUnlockedUi();
            restoreFloatingButton();
            final int shellMode = LockPrefs.getShellMode(this);
            ShellHardening.restoreSelectedApps(this, shellMode, new Runnable() {
                @Override
                public void run() {
                    if (LockPrefs.isShellHomeActive(MainActivity.this)
                            || LockPrefs.isShellDisplayActive(MainActivity.this)) {
                        ShellHardening.restore(MainActivity.this, shellMode, null);
                    }
                }
            });
        }

        try {
            Shizuku.addRequestPermissionResultListener(shizukuListener);
        } catch (Throwable ignored) {
        }
        try {
            binderDeadListener = new Shizuku.OnBinderDeadListener() {
                @Override
                public void onBinderDead() {
                    if (LockPrefs.isLocked(MainActivity.this)) {
                        LockGuardService.notifyShizukuLost(MainActivity.this);
                    }
                }
            };
            Shizuku.addBinderDeadListener(binderDeadListener);
        } catch (Throwable ignored) {
        }
        lockView.requestApplyInsets();
        handleRemoteLaunch();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleRemoteLaunch();
    }

    private void handleRemoteLaunch() {
        Intent intent = getIntent();
        if (intent == null || !intent.getBooleanExtra(EXTRA_REMOTE_LOCK, false)) {
            return;
        }
        intent.removeExtra(EXTRA_REMOTE_LOCK);
        if (locked) {
            return;
        }
        int mode = LockPrefs.getMode(this);
        if (!LockModes.isValid(mode)) {
            Intent picker = new Intent(this, ModePickerActivity.class);
            picker.putExtra(ModePickerActivity.EXTRA_FIRST_RUN, true);
            startActivityForResult(picker, REQ_MODE);
            return;
        }
        beginLock(mode);
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncLockStateFromPrefs();
        if (pendingAccessibility) {
            if (LockAccessibilityService.isRunning()) {
                pendingAccessibility = false;
                int mode = pendingMode;
                pendingMode = LockModes.UNSET;
                startAccessibilityOverlay(mode);
                return;
            }
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!pendingAccessibility) {
                        return;
                    }
                    if (LockAccessibilityService.isRunning()) {
                        pendingAccessibility = false;
                        int mode = pendingMode;
                        pendingMode = LockModes.UNSET;
                        startAccessibilityOverlay(mode);
                    } else {
                        pendingAccessibility = false;
                        pendingMode = LockModes.UNSET;
                        Toast.makeText(MainActivity.this,
                                "还没有开启无障碍服务", Toast.LENGTH_SHORT).show();
                    }
                }
            }, 1000);
        }
        if (pendingOverlay) {
            pendingOverlay = false;
            if (Settings.canDrawOverlays(this)) {
                int mode = pendingMode;
                pendingMode = LockModes.UNSET;
                startOverlayLock(mode);
                return;
            }
            pendingMode = LockModes.UNSET;
            Toast.makeText(this, "没有获得悬浮窗权限", Toast.LENGTH_SHORT).show();
        }
        if (locked) {
            applyLockedUi();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) {
            return;
        }
        if (locked) {
            applyLockedUi();
        } else {
            applyUnlockedUi();
        }
    }

    @Override
    public void onBackPressed() {
        if (locked) {
            return;
        }
        if (lockView != null && lockView.handleBack()) {
            return;
        }
        super.onBackPressed();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        try {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},
                        REQ_NOTIFICATIONS);
            }
        } catch (Exception ignored) {
        }
    }

    private void handleStartTest() {
        if (RemotePrefs.getRole(this) == RemotePrefs.ROLE_CONTROLLER
                && RemotePrefs.isMasterEnabled(this)) {
            final List<RemoteSsh.Device> targets = new ArrayList<>();
            JSONArray saved = RemotePrefs.getSavedDevices(this);
            for (int i = 0; i < saved.length(); i++) {
                try {
                    JSONObject object = saved.getJSONObject(i);
                    String id = object.optString("id", "");
                    if (id.length() > 0 && RemotePrefs.isSelected(this, id)) {
                        RemoteSsh.Device device = new RemoteSsh.Device();
                        device.id = id;
                        device.name = object.optString("name", "被锁机");
                        targets.add(device);
                    }
                } catch (Exception ignored) {
                }
            }
            if (targets.isEmpty()) {
                AppDialogs.message(this, "远程锁机",
                        "请先在设置 → 远程锁机 → 被锁机列表里打开要锁的设备开关。",
                        "知道了");
                return;
            }
            if (!RemotePrefs.isConfigured(this)) {
                AppDialogs.message(this, "远程锁机",
                        "远程服务器还没有配置，先去设置 → 远程锁机填写 SSH 服务器。",
                        "知道了");
                return;
            }
            Toast.makeText(this, "正在向 " + targets.size() + " 台被锁机发送请求…",
                    Toast.LENGTH_SHORT).show();
            final int total = targets.size();
            final int[] done = {0};
            final int[] approved = {0};
            final int[] rejected = {0};
            final int[] failed = {0};
            for (final RemoteSsh.Device device : targets) {
                RemoteSsh.requestAndWait(this, device.id, device.name,
                        new RemoteSsh.LockCallback() {
                            @Override
                            public void onResult(int state, String message) {
                                done[0]++;
                                if (state == 1) {
                                    approved[0]++;
                                } else if (state == 0) {
                                    rejected[0]++;
                                } else {
                                    failed[0]++;
                                }
                                if (done[0] == total) {
                                    String summary = total == 1
                                            ? message
                                            : ("共 " + total + " 台：同意 " + approved[0]
                                            + " 台，拒绝 " + rejected[0]
                                            + " 台，失败/超时 " + failed[0] + " 台");
                                    AppDialogs.message(MainActivity.this,
                                            "远程锁机", summary, "知道了");
                                }
                            }
                        });
            }
            return;
        }
        int mode = LockPrefs.getMode(this);
        if (!LockModes.isValid(mode)) {
            Intent intent = new Intent(this, ModePickerActivity.class);
            intent.putExtra(ModePickerActivity.EXTRA_FIRST_RUN, true);
            startActivityForResult(intent, REQ_MODE);
            return;
        }
        beginLock(mode);
    }

    private void beginLock(final int mode) {
        if (!LockPrefs.hasPassword(this)) {
            PasswordDialogs.showSet(this, new Runnable() {
                @Override
                public void run() {
                    startLock(mode);
                }
            });
            return;
        }
        startLock(mode);
    }

    private void startLock(int mode) {
        startLockNow(mode);
    }

    private void startLockNow(int mode) {
        if (mode == LockModes.NORMAL) {
            enterScreenPinLock(mode);
        } else if (mode == LockModes.ACCESSIBILITY) {
            if (LockAccessibilityService.isRunning()) {
                startAccessibilityOverlay(mode);
            } else {
                requestAccessibility(mode);
            }
        } else if (mode == LockModes.SHIZUKU) {
            startShizukuLock(mode);
        } else if (mode == LockModes.ROOT) {
            startRootLock(mode);
        } else if (mode == LockModes.OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                startOverlayLock(mode);
            } else {
                requestOverlay(mode);
            }
        } else if (mode == LockModes.KIOSK || mode == LockModes.KIOSK_STRONG) {
            startKioskLock(mode);
        }
    }

    private void startKioskLock(final int mode) {
        final boolean strong = mode == LockModes.KIOSK_STRONG;
        if (!KioskController.isDeviceOwner(this)) {
            AppDialogs.message(this, "设备所有者锁机",
                    "这个模式需要先把 TE cvb 设为设备所有者。\n\n步骤：\n1. 备份并恢复出厂设置\n2. 安装 TE cvb，不要登录账号\n3. 用电脑执行：\n\nadb shell dpm set-device-owner com.dsh.lock/.AdminReceiver\n\n成功后再回来选这个模式。\n\n退出/卸载需要 ADB：\nadb shell dpm remove-active-admin com.dsh.lock/.AdminReceiver",
                    "知道了");
            return;
        }
        String warning = strong
                ? "强力模式会：\n\n• 禁安全模式\n• 禁恢复出厂\n• 禁 ADB 调试\n• 禁未知来源安装\n• 禁 USB 文件传输\n• 再加上普通 kiosk 的全部限制\n\n忘记密码只能靠 ADB/fastboot 处理，主力机不要用。确认开启？"
                : "开启后会：\n\n• 把 TE cvb 设为桌面\n• 禁用状态栏和通知栏\n• 锁定 Home / 最近任务\n• 阻止卸载\n• 只允许 TE cvb 的无障碍服务\n• 禁止截屏/录屏\n• 重启后继续锁\n\n退出只能输入密码；卸载需要 ADB 取消设备所有者。确认开启？";
        AppDialogs.confirm(this, strong ? "设备所有者锁机（强力）" : "设备所有者锁机",
                warning,
                "取消", "开启锁机", new AppDialogs.OnClick() {
                    @Override
                    public void onClick() {
                        if (locked) {
                            return;
                        }
                        locked = true;
                        LockPrefs.setLocked(MainActivity.this, true, mode);
                        LockGuardService.start(MainActivity.this);
                        lockView.enterLock();
                        applyLockedUi();
                        if (!KioskController.enter(MainActivity.this, strong)) {
                            KioskController.exit(MainActivity.this);
                            locked = false;
                            LockPrefs.clearLock(MainActivity.this);
                            lockView.showHome();
                            applyUnlockedUi();
                            showMessage("设备所有者锁机启动失败，请确认 TE cvb 已是设备所有者");
                        }
                    }
                });
    }

    private void enterScreenPinLock(int mode) {
        if (locked) {
            return;
        }
        locked = true;
        LockPrefs.setLocked(this, true, mode);
        LockGuardService.start(this);
        lockView.enterLock();
        applyLockedUi();
        try {
            startLockTask();
        } catch (Exception ignored) {
        }
    }

    private void startAccessibilityOverlay(int mode) {
        if (!LockModes.isValid(mode)) {
            return;
        }
        LockPrefs.setLocked(this, true, mode);
        locked = true;
        LockGuardService.start(this);
        LockAccessibilityService service = LockAccessibilityService.getInstance();
        if (service != null && service.showLockOverlay()) {
            finishAndRemoveTask();
        } else {
            locked = false;
            LockPrefs.clearLock(this);
            showMessage("锁机启动失败，请重试");
        }
    }

    private void requestAccessibility(final int mode) {
        pendingAccessibility = true;
        pendingMode = mode;
        AppDialogs.confirm(this, "开启锁机",
                "需要开启系统无障碍服务。它只会在锁机期间显示全屏覆盖层，阻止 Home / 上滑手势，不会读取屏幕内容。\n\n开启“TE cvb 锁机”后返回，会自动开始锁机。",
                "取消", "去开启", new AppDialogs.OnClick() {
                    @Override
                    public void onClick() {
                        try {
                            Toast.makeText(MainActivity.this,
                                    "请找到“TE cvb 锁机”并开启",
                                    Toast.LENGTH_LONG).show();
                            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        } catch (Exception exception) {
                            pendingAccessibility = false;
                            pendingMode = LockModes.UNSET;
                            showMessage("无法打开无障碍设置");
                        }
                    }
                }, new AppDialogs.OnClick() {
                    @Override
                    public void onClick() {
                        pendingAccessibility = false;
                        pendingMode = LockModes.UNSET;
                    }
                });
    }

    private void startShizukuLock(final int mode) {
        if (!ShizukuHelper.isAvailable()) {
            showShizukuMissing();
            return;
        }
        if (!ShizukuHelper.hasPermission()) {
            pendingMode = mode;
            ShizukuHelper.requestPermission();
            Toast.makeText(this, "请在 Shizuku 里允许 TE cvb", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "正在用 Shizuku 开启无障碍和悬浮窗…", Toast.LENGTH_SHORT).show();
        ShizukuHelper.grantOverlay(this, new AccessibilityShell.Callback() {
            @Override
            public void onResult(boolean overlayGranted, String overlayMessage) {
                ShizukuHelper.enableAccessibility(MainActivity.this,
                        new AccessibilityShell.Callback() {
                            @Override
                            public void onResult(boolean enabled, String text) {
                                if (enabled) {
                                    if (!overlayGranted) {
                                        Toast.makeText(MainActivity.this,
                                                "悬浮窗未授权，已用无障碍锁机",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                    ShellHardening.apply(MainActivity.this, mode,
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    ShellHardening.disableSelectedApps(
                                                            MainActivity.this, mode,
                                                            new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    startAccessibilityOverlay(mode);
                                                                }
                                                            });
                                                }
                                            });
                                } else {
                                    showMessage(text);
                                }
                            }
                        });
            }
        });
    }

    private void startRootLock(final int mode) {
        Toast.makeText(this, "正在用 root 开启无障碍和悬浮窗…", Toast.LENGTH_SHORT).show();
        RootHelper.grantOverlay(this, new AccessibilityShell.Callback() {
            @Override
            public void onResult(boolean overlayGranted, String overlayMessage) {
                RootHelper.enableAccessibility(MainActivity.this,
                        new AccessibilityShell.Callback() {
                            @Override
                            public void onResult(boolean enabled, String text) {
                                if (enabled) {
                                    if (!overlayGranted) {
                                        Toast.makeText(MainActivity.this,
                                                "悬浮窗未授权，已用无障碍锁机",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                    ShellHardening.apply(MainActivity.this, mode,
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    ShellHardening.disableSelectedApps(
                                                            MainActivity.this, mode,
                                                            new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    startAccessibilityOverlay(mode);
                                                                }
                                                            });
                                                }
                                            });
                                } else {
                                    showMessage(text);
                                }
                            }
                        });
            }
        });
    }

    private void startOverlayLock(int mode) {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlay(mode);
            return;
        }
        LockPrefs.setLocked(this, true, mode);
        locked = true;
        OverlayLockService.start(this, OverlayLockService.ACTION_LOCK);
        finishAndRemoveTask();
    }

    private void requestOverlay(final int mode) {
        pendingOverlay = true;
        pendingMode = mode;
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception exception) {
            pendingOverlay = false;
            pendingMode = LockModes.UNSET;
            showMessage("无法打开悬浮窗权限设置");
        }
    }

    private void showShizukuMissing() {
        AppDialogs.confirm(this, "未连接 Shizuku",
                "Shizuku 锁机需要先安装并启动 Shizuku，然后在里面授权 TE cvb。\n\n如果没有安装，可以先用“无障碍锁机”。",
                "知道了", "打开 Shizuku", new AppDialogs.OnClick() {
                    @Override
                    public void onClick() {
                        try {
                            Intent intent = getPackageManager()
                                    .getLaunchIntentForPackage("moe.shizuku.privileged.api");
                            if (intent != null) {
                                startActivity(intent);
                            } else {
                                showMessage("没有找到 Shizuku");
                            }
                        } catch (Exception exception) {
                            showMessage("没有找到 Shizuku");
                        }
                    }
                });
    }

    private void finishUnlock() {
        int mode = LockPrefs.getLockMode(this);
        if (mode == LockModes.KIOSK || mode == LockModes.KIOSK_STRONG) {
            KioskController.exit(this);
        }
        final int shellMode = LockPrefs.getShellMode(this);
        ShellHardening.restoreSelectedApps(this, shellMode, new Runnable() {
            @Override
            public void run() {
                if (LockPrefs.isShellHomeActive(MainActivity.this)
                        || LockPrefs.isShellDisplayActive(MainActivity.this)) {
                    ShellHardening.restore(MainActivity.this, shellMode, null);
                }
            }
        });
        locked = false;
        LockPrefs.clearLock(this);
        LockGuardService.stop(this);
        try {
            stopLockTask();
        } catch (Exception ignored) {
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        applyUnlockedUi();
        finish();
    }

    private void restoreFloatingButton() {
        if (LockPrefs.isFloatingEnabled(this)
                && LockPrefs.hasPassword(this)
                && Settings.canDrawOverlays(this)) {
            try {
                OverlayLockService.start(this, OverlayLockService.ACTION_SHOW_BUTTON);
            } catch (Exception ignored) {
            }
        }
    }

    private void syncLockStateFromPrefs() {
        boolean storedLocked = LockPrefs.isLocked(this);
        if (locked && !storedLocked) {
            locked = false;
            if (lockView != null) {
                lockView.showHome();
            }
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            applyUnlockedUi();
        }
    }

    private void applyLockedUi() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        applySystemBars(true);
    }

    private void applyUnlockedUi() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applySystemBars(false);
    }

    private void applySystemBars(boolean hide) {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                if (hide) {
                    controller.hide(WindowInsets.Type.systemBars());
                } else {
                    controller.show(WindowInsets.Type.systemBars());
                }
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (hide) {
                flags |= View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void showMessage(String message) {
        AppDialogs.message(this, "提示", message, "知道了");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_MODE && resultCode == RESULT_OK && data != null) {
            int mode = data.getIntExtra(ModePickerActivity.EXTRA_MODE, LockModes.UNSET);
            if (LockModes.isValid(mode)) {
                LockPrefs.setMode(this, mode);
                beginLock(mode);
            }
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuListener);
        } catch (Throwable ignored) {
        }
        try {
            if (binderDeadListener != null) {
                Shizuku.removeBinderDeadListener(binderDeadListener);
            }
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }
}
