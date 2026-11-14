package com.dsh.lock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public final class LockGuardService extends Service {
    private static final String ACTION_START = "com.dsh.lock.guard.START";
    private static final String ACTION_STOP = "com.dsh.lock.guard.STOP";
    private static final String CHANNEL_GUARD = "tecvb_guard";
    private static final String CHANNEL_SHIZUKU = "tecvb_shizuku";
    private static final int NOTIF_GUARD = 0x7E01;
    private static final int NOTIF_SHIZUKU = 0x7E02;
    private static final int NOTIF_A11Y_LOST = 0x7E03;

    /** 无障碍健康检查间隔。 */
    private static final long WATCH_INTERVAL = 12_000L;
    /** 两次自动重连之间至少隔这么久，避免和系统自己的重试打架。 */
    private static final long RECOVER_COOLDOWN = 45_000L;

    private final Handler watchHandler = new Handler(Looper.getMainLooper());
    private long lastRecoverAt;
    private boolean recovering;
    private final Runnable watch = new Runnable() {
        @Override
        public void run() {
            checkAccessibility();
            watchHandler.postDelayed(this, WATCH_INTERVAL);
        }
    };

    public static void start(Context context) {
        Intent intent = new Intent(context, LockGuardService.class);
        intent.setAction(ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception ignored) {
        }
    }

    public static void stop(Context context) {
        try {
            context.stopService(new Intent(context, LockGuardService.class));
        } catch (Exception ignored) {
        }
    }

    public static void notifyShizukuLost(Context context) {
        try {
            NotificationManager manager = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_SHIZUKU, "Shizuku 状态",
                        NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("Shizuku 断开提醒");
                manager.createNotificationChannel(channel);
            }
            Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(context, CHANNEL_SHIZUKU)
                    : new Notification.Builder(context);
            builder.setSmallIcon(R.drawable.ic_stat_lock)
                    .setContentTitle("Shizuku 已断开")
                    .setContentText("锁机保护可能失效，点此重新激活")
                    .setContentIntent(contentIntent(context, 2))
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                builder.setPriority(Notification.PRIORITY_HIGH);
            }
            manager.notify(NOTIF_SHIZUKU, builder.build());
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_GUARD, "锁机保护",
                        NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("锁机期间保持保护服务运行");
                channel.setShowBadge(false);
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        try {
            startForeground(NOTIF_GUARD, buildGuardNotification());
        } catch (Exception exception) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(action) || !LockPrefs.isLocked(this)) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        watchHandler.removeCallbacks(watch);
        watchHandler.postDelayed(watch, WATCH_INTERVAL);
        return START_STICKY;
    }


    /**
     * 守护服务的本职：发现无障碍锁屏掉了就把它拉回来。
     * 以前这里只贴张通知，服务断了自己完全不知道。
     */
    private void checkAccessibility() {
        if (!LockPrefs.isLocked(this)
                || !LockModes.usesAccessibility(LockPrefs.getLockMode(this))) {
            return;
        }
        if (LockAccessibilityService.isRunning()) {
            if (LockAccessibilityService.isOverlayMissing()) {
                LockAccessibilityService.requestLockOverlay(this);
            }
            return;
        }
        if (recovering) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRecoverAt < RECOVER_COOLDOWN) {
            return;
        }
        lastRecoverAt = now;
        recovering = true;
        AccessibilityShell.recover(this, new AccessibilityShell.Callback() {
            @Override
            public void onResult(final boolean success, final String message) {
                recovering = false;
                if (success) {
                    cancelLostNotification();
                    LockAccessibilityService.requestLockOverlay(LockGuardService.this);
                } else {
                    notifyAccessibilityLost(message);
                }
            }
        });
    }

    private void notifyAccessibilityLost(String reason) {
        try {
            NotificationManager manager = (NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) {
                return;
            }
            ensureChannel(manager, CHANNEL_SHIZUKU, "锁机保护提醒",
                    NotificationManager.IMPORTANCE_HIGH);
            Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(this, CHANNEL_SHIZUKU)
                    : new Notification.Builder(this);
            builder.setSmallIcon(R.drawable.ic_stat_lock)
                    .setContentTitle("锁机保护已失效")
                    .setContentText(reason == null || reason.length() == 0
                            ? "无障碍服务被系统关掉了，点此恢复"
                            : reason)
                    .setContentIntent(contentIntent(this, 3))
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                builder.setPriority(Notification.PRIORITY_HIGH);
            }
            manager.notify(NOTIF_A11Y_LOST, builder.build());
        } catch (Exception ignored) {
        }
    }

    private void cancelLostNotification() {
        try {
            NotificationManager manager = (NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(NOTIF_A11Y_LOST);
            }
        } catch (Exception ignored) {
        }
    }

    private void ensureChannel(NotificationManager manager, String id, String name, int importance) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(id, name, importance);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        watchHandler.removeCallbacks(watch);
        super.onDestroy();
    }

    private Notification buildGuardNotification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_GUARD)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_stat_lock)
                .setContentTitle("TE cvb 锁机保护中")
                .setContentText("输入密码才能退出")
                .setContentIntent(contentIntent(this, 1))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setShowWhen(false);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setPriority(Notification.PRIORITY_LOW);
        }
        return builder.build();
    }

    private static PendingIntent contentIntent(Context context, int requestCode) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(context, requestCode, intent, flags);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
