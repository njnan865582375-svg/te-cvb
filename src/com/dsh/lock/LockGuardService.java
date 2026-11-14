package com.dsh.lock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public final class LockGuardService extends Service {
    private static final String ACTION_START = "com.dsh.lock.guard.START";
    private static final String ACTION_STOP = "com.dsh.lock.guard.STOP";
    private static final String CHANNEL_GUARD = "tecvb_guard";
    private static final String CHANNEL_SHIZUKU = "tecvb_shizuku";
    private static final int NOTIF_GUARD = 0x7E01;
    private static final int NOTIF_SHIZUKU = 0x7E02;

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
        if (ACTION_STOP.equals(action) || !LockPrefs.isLocked(this)) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            startForeground(NOTIF_GUARD, buildGuardNotification());
            return START_STICKY;
        } catch (Exception exception) {
            stopSelf();
            return START_NOT_STICKY;
        }
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
