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

import org.json.JSONObject;

public final class RemoteService extends Service {
    private static final String CHANNEL_SERVICE = "te_cvb_remote_service";
    private static final String CHANNEL_REQUEST = "te_cvb_remote_request";
    private static final int NOTIFICATION_SERVICE = 3101;
    private static final int NOTIFICATION_REQUEST = 3102;

    private volatile boolean running;
    private Thread worker;
    private String lastNotified = "";

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_SERVICE, serviceNotification());
        if (RemotePrefs.getRole(this) != RemotePrefs.ROLE_CONTROLLED
                || !RemotePrefs.isConfigured(this)) {
            RemotePrefs.setServiceEnabled(this, false);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) {
            running = true;
            worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    loop();
                }
            }, "te-cvb-remote-poll");
            worker.start();
        }
        return START_NOT_STICKY;
    }

    private void loop() {
        while (running) {
            try {
                RemoteSsh.Request request = RemoteSsh.pollRequest(this);
                if (request != null && !request.id.equals(lastNotified)) {
                    lastNotified = request.id;
                    RemotePrefs.setLastRequestId(this, request.id);
                    JSONObject pending = new JSONObject();
                    pending.put("id", request.id);
                    pending.put("from", request.from);
                    pending.put("time", request.time);
                    RemotePrefs.setPending(this, pending.toString());
                    notifyRequest(request);
                }
            } catch (Exception ignored) {
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException interrupted) {
                return;
            }
        }
    }

    private void notifyRequest(RemoteSsh.Request request) {
        Intent intent = new Intent(this, RemoteApprovalActivity.class);
        intent.putExtra("request_id", request.id);
        intent.putExtra("request_from", request.from);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, request.id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_REQUEST);
        } else {
            builder = new Notification.Builder(this);
        }
        Notification notification = builder
                .setSmallIcon(R.drawable.ic_stat_lock)
                .setContentTitle("远程锁机请求")
                .setContentText("来自 " + request.from + "，点此确认")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .build();
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_REQUEST, notification);
        }
    }

    private Notification serviceNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_SERVICE);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setSmallIcon(R.drawable.ic_stat_lock)
                .setContentTitle("TE cvb 远程被控")
                .setContentText("已在线，等待控制端请求")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_LOW)
                .build();
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) {
                return;
            }
            NotificationChannel service = new NotificationChannel(
                    CHANNEL_SERVICE, "TE cvb 远程被控", NotificationManager.IMPORTANCE_LOW);
            service.setDescription("远程被控在线状态");
            manager.createNotificationChannel(service);
            NotificationChannel request = new NotificationChannel(
                    CHANNEL_REQUEST, "TE cvb 远程锁机请求", NotificationManager.IMPORTANCE_HIGH);
            request.setDescription("控制端请求锁机");
            manager.createNotificationChannel(request);
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
