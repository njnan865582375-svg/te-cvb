package com.dsh.lock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public final class OverlayLockService extends Service {
    public static final String ACTION_LOCK = "com.dsh.lock.overflow.LOCK";
    public static final String ACTION_SHOW_BUTTON = "com.dsh.lock.overflow.SHOW_BUTTON";
    public static final String ACTION_HIDE_BUTTON = "com.dsh.lock.overflow.HIDE_BUTTON";
    public static final String ACTION_STOP = "com.dsh.lock.overflow.STOP";

    private static final int NOTIFICATION_ID = 2711;
    private static final String CHANNEL_ID = "te_cvb_overlay";

    private WindowManager windowManager;
    private LockView lockView;
    private TextView floatingButton;
    private WindowManager.LayoutParams buttonParams;
    private boolean lockShowing;
    private boolean foregroundStarted;

    public static void start(Context context, String action) {
        Intent intent = new Intent(context, OverlayLockService.class);
        intent.setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, notification("悬浮窗服务运行中"));
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            shutdown();
            return START_NOT_STICKY;
        }
        if (ACTION_HIDE_BUTTON.equals(action)) {
            LockPrefs.setFloatingEnabled(this, false);
            removeFloatingButton();
            if (!lockShowing) {
                stopForeground(true);
                stopSelf();
            }
            return START_NOT_STICKY;
        }
        if (ACTION_SHOW_BUTTON.equals(action)) {
            LockPrefs.setFloatingEnabled(this, true);
            showFloatingButton();
            updateNotification("悬浮锁机按钮已开启");
            return START_NOT_STICKY;
        }
        if (ACTION_LOCK.equals(action)) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "需要悬浮窗权限", Toast.LENGTH_SHORT).show();
                openApp();
                stopSelf();
                return START_NOT_STICKY;
            }
            if (!LockPrefs.hasPassword(this)) {
                Toast.makeText(this, "请先设置解锁密码", Toast.LENGTH_SHORT).show();
                openApp();
                stopSelf();
                return START_NOT_STICKY;
            }
            showLockOverlay();
            return START_NOT_STICKY;
        }
        if (LockPrefs.isFloatingEnabled(this)) {
            showFloatingButton();
        } else if (!lockShowing) {
            stopForeground(true);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void showLockOverlay() {
        if (lockShowing || lockView != null) {
            return;
        }
        removeFloatingButton();
        LockPrefs.setLocked(this, true, LockModes.OVERLAY);
        final LockView view = new LockView(this, new LockView.Listener() {
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
                unlock();
            }
        });
        view.showLockedImmediately();
        view.setFocusableInTouchMode(true);
        view.requestFocus();

        int windowFlags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                windowFlags,
                PixelFormat.OPAQUE);
        params.gravity = Gravity.TOP | Gravity.START;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        try {
            windowManager.addView(view, params);
            lockView = view;
            lockShowing = true;
            updateNotification("锁机中 · 输入密码退出");
        } catch (Exception exception) {
            LockPrefs.clearLock(this);
            Toast.makeText(this, "悬浮窗锁机启动失败", Toast.LENGTH_SHORT).show();
            stopSelf();
        }
    }

    private void unlock() {
        if (!lockShowing) {
            return;
        }
        hideLockOverlay();
        LockPrefs.clearLock(this);
        if (LockPrefs.isFloatingEnabled(this)) {
            showFloatingButton();
            updateNotification("悬浮锁机按钮已开启");
        } else {
            stopForeground(true);
            stopSelf();
        }
        goHome();
    }

    private void showFloatingButton() {
        if (floatingButton != null || windowManager == null) {
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            LockPrefs.setFloatingEnabled(this, false);
            return;
        }
        final TextView button = new TextView(this);
        button.setText("锁");
        button.setTextColor(Color.WHITE);
        button.setTextSize(18);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(0xF0000000);
        background.setStroke(dp(1.5f), 0xFFFFFFFF);
        button.setBackground(background);
        button.setElevation(dp(8));

        int size = dp(58);
        buttonParams = new WindowManager.LayoutParams(
                size,
                size,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        buttonParams.gravity = Gravity.TOP | Gravity.START;
        buttonParams.x = getResources().getDisplayMetrics().widthPixels - size - dp(14);
        buttonParams.y = (int) (getResources().getDisplayMetrics().heightPixels * 0.55f);

        button.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX;
            private float downRawY;
            private int startX;
            private int startY;
            private boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startX = buttonParams.x;
                        startY = buttonParams.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) {
                            moved = true;
                        }
                        if (moved) {
                            int maxX = getResources().getDisplayMetrics().widthPixels - buttonParams.width;
                            int maxY = getResources().getDisplayMetrics().heightPixels - buttonParams.height;
                            buttonParams.x = Math.max(0, Math.min(maxX, startX + (int) dx));
                            buttonParams.y = Math.max(0, Math.min(maxY, startY + (int) dy));
                            windowManager.updateViewLayout(button, buttonParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            onFloatingButtonClick();
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });

        try {
            windowManager.addView(button, buttonParams);
            floatingButton = button;
        } catch (Exception ignored) {
        }
    }

    private void onFloatingButtonClick() {
        if (!LockPrefs.hasPassword(this)) {
            Toast.makeText(this, "请先设置解锁密码", Toast.LENGTH_SHORT).show();
            openApp();
            return;
        }
        showLockOverlay();
    }

    private void removeFloatingButton() {
        if (floatingButton != null && windowManager != null) {
            try {
                windowManager.removeViewImmediate(floatingButton);
            } catch (Exception ignored) {
            }
        }
        floatingButton = null;
    }

    private void hideLockOverlay() {
        if (lockView != null && windowManager != null) {
            try {
                windowManager.removeViewImmediate(lockView);
            } catch (Exception ignored) {
            }
        }
        lockView = null;
        lockShowing = false;
    }

    private void shutdown() {
        removeFloatingButton();
        hideLockOverlay();
        stopForeground(true);
        stopSelf();
    }

    private void goHome() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
        } catch (Exception ignored) {
        }
    }

    private void openApp() {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID, "TE cvb 悬浮锁机", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("桌面悬浮锁机按钮和锁屏服务的常驻通知");
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification notification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setSmallIcon(R.drawable.ic_stat_lock)
                .setContentTitle("TE cvb")
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification(text));
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        removeFloatingButton();
        hideLockOverlay();
        super.onDestroy();
    }
}
