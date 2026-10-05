package com.msgnotify.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.msgnotify.app.R;
import com.msgnotify.app.core.Prefs;
import com.msgnotify.app.core.Speaker;
import com.msgnotify.app.core.VolumeWatcher;
import com.msgnotify.app.ui.MainActivity;

/**
 * 前台常驻服务：维持进程存活，保证息屏 / 后台时 TTS 与通知监听仍可工作
 */
public class KeepAliveService extends Service {

    public static final String CHANNEL_ID = "msgnotify_keepalive";
    public static final String CHANNEL_TEST = "msgnotify_test";
    private static final int NOTI_ID = 1001;

    public static void start(Context c) {
        if (!Prefs.foregroundEnabled(c)) return;
        Intent i = new Intent(c, KeepAliveService.class);
        try {
            c.startForegroundService(i);
        } catch (Throwable ignored) {
        }
    }

    public static void stop(Context c) {
        try {
            c.stopService(new Intent(c, KeepAliveService.class));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels(this);
        Speaker.init(this, null);
        VolumeWatcher.start(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTI_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTI_ID, n);
            }
        } catch (Throwable ignored) {
        }
        return START_STICKY;
    }

    private Notification buildNotification() {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("通知语音播报运行中")
                .setContentText(Prefs.masterEnabled(this) ? "播报已开启" : "播报已暂停")
                .setSmallIcon(R.drawable.ic_m_voice)
                .setContentIntent(pi)
                .setOngoing(true);
        return b.build();
    }

    /** 刷新常驻通知文案 */
    public static void refresh(Context c) {
        if (Prefs.foregroundEnabled(c)) {
            start(c);
        } else {
            stop(c);
        }
    }

    public static void createChannels(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel keep = new NotificationChannel(CHANNEL_ID,
                "常驻运行", NotificationManager.IMPORTANCE_MIN);
        keep.setShowBadge(false);
        nm.createNotificationChannel(keep);

        NotificationChannel test = new NotificationChannel(CHANNEL_TEST,
                "播报自检", NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(test);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 若用户仍开启保活，被杀后尝试自启
        if (Prefs.foregroundEnabled(this)) {
            try {
                startForegroundService(new Intent(this, KeepAliveService.class));
            } catch (Throwable ignored) {
            }
        }
    }
}
