package com.dsh.lock;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public final class LockAccessibilityService extends AccessibilityService {

    private static LockAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private LockView lockView;
    private boolean overlayShowing;
    private long lastGuardAt;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        SharedPreferences prefs = LockPrefs.prefs(this);
        if (prefs.getBoolean("locked", false)
                && LockModes.usesAccessibility(LockPrefs.getLockMode(this))) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    showLockOverlay();
                }
            }, 250);
        }
    }

    public static LockAccessibilityService getInstance() {
        return instance;
    }

    public static boolean isRunning() {
        return instance != null;
    }

    public static boolean isEnabledInSettings(Context context) {
        try {
            String enabled = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) {
                return false;
            }
            String component = context.getPackageName() + "/"
                    + LockAccessibilityService.class.getName();
            String[] parts = enabled.split(":");
            for (String part : parts) {
                if (part.equalsIgnoreCase(component)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static boolean requestLockOverlay(Context context) {
        LockAccessibilityService service = instance;
        if (service == null) {
            return false;
        }
        return service.showLockOverlay();
    }

    public boolean showLockOverlay() {
        if (overlayShowing && lockView != null) {
            return true;
        }
        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
        if (windowManager == null) {
            return false;
        }

        if (!LockPrefs.hasPassword(this)) {
            return false;
        }
        try {
            LockView view = new LockView(this, new LockView.Listener() {
                @Override
                public void onStartTest() {
                }

                @Override
                public void onStartLock(int mode) {
                }

                @Override
                public void onOpenSettings() {
                }

                @Override
                public void onUnlockRequested() {
                    unlockAndExit();
                }
            });
            view.showLockedImmediately();
            view.setFocusableInTouchMode(true);
            view.requestFocus();

            int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    | WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                    | WindowManager.LayoutParams.FLAG_SECURE;

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    flags,
                    PixelFormat.OPAQUE);
            params.gravity = Gravity.TOP | Gravity.START;
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
            params.setTitle("TE cvb lock");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }

            windowManager.addView(view, params);
            lockView = view;
            overlayShowing = true;
            LockGuardService.start(this);

            int storedMode = LockPrefs.getLockMode(this);
            if (!LockModes.usesAccessibility(storedMode)) {
                storedMode = LockModes.ACCESSIBILITY;
            }
            LockPrefs.setLocked(this, true, storedMode);
            return true;
        } catch (Exception exception) {
            overlayShowing = false;
            lockView = null;
            return false;
        }
    }

    private void unlockAndExit() {
        if (!overlayShowing) {
            return;
        }
        final int mode = LockPrefs.getLockMode(this);
        final int shellMode = LockPrefs.getShellMode(this) == -1
                ? mode : LockPrefs.getShellMode(this);
        LockPrefs.clearLock(this);
        LockGuardService.stop(this);
        final Runnable finish = new Runnable() {
            @Override
            public void run() {
                hideLockOverlay();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        performGlobalAction(GLOBAL_ACTION_HOME);
                    }
                }, 120);
            }
        };
        ShellHardening.restoreSelectedApps(this, shellMode, new Runnable() {
            @Override
            public void run() {
                if (LockPrefs.isShellHomeActive(LockAccessibilityService.this)
                        || LockPrefs.isShellDisplayActive(LockAccessibilityService.this)) {
                    ShellHardening.restore(LockAccessibilityService.this, shellMode, finish);
                } else {
                    finish.run();
                }
            }
        });
    }

    private void hideLockOverlay() {
        if (lockView != null && windowManager != null) {
            try {
                windowManager.removeViewImmediate(lockView);
            } catch (Exception ignored) {
            }
        }
        lockView = null;
        overlayShowing = false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!overlayShowing || event == null) {
            return;
        }
        int eventType = event.getEventType();
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return;
        }
        CharSequence packageName = event.getPackageName();
        if (packageName == null) {
            return;
        }
        if (getPackageName().contentEquals(packageName)) {
            return;
        }

        KeyguardManager keyguardManager =
                (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager != null && keyguardManager.isKeyguardLocked()) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        if (now - lastGuardAt < 550) {
            return;
        }
        lastGuardAt = now;
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        hideLockOverlay();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
