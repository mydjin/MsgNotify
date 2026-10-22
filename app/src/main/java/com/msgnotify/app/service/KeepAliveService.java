package com.msgnotify.app.service;

import android.app.AlarmManager;
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
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import com.msgnotify.app.R;
import com.msgnotify.app.core.AppDebugLog;
import com.msgnotify.app.core.ListenerHeal;
import com.msgnotify.app.core.Prefs;
import com.msgnotify.app.core.Speaker;
import com.msgnotify.app.core.VolumeWatcher;
import com.msgnotify.app.ui.MainActivity;

/**
 * 前台常驻服务：维持进程存活；开发者模式下叠加 WakeLock + 精确闹钟心跳。
 */
public class KeepAliveService extends Service {

    public static final String CHANNEL_ID = "msgnotify_keepalive";
    public static final String CHANNEL_ID_DEV = "msgnotify_keepalive_dev";
    public static final String CHANNEL_TEST = "msgnotify_test";
    public static final String CHANNEL_LISTENER_FIX = "msgnotify_listener_fix";
    public static final String ACTION_HEARTBEAT = "com.msgnotify.app.action.HEARTBEAT";
    public static final String ACTION_APPLY_DEV = "com.msgnotify.app.action.APPLY_DEV";

    private static final int NOTI_ID = 1001;
    private static final int ALARM_REQ = 3001;
    private static final long HEARTBEAT_MS = 5 * 60 * 1000L;

    private PowerManager.WakeLock devWakeLock;

    public static void start(Context c) {
        if (!Prefs.foregroundEnabled(c) && !Prefs.devKeepAlive(c)) {
            AppDebugLog.d("KeepAlive", "start skipped foreground=false dev=false");
            return;
        }
        Intent i = new Intent(c, KeepAliveService.class);
        try {
            AppDebugLog.d("KeepAlive", "startForegroundService"
                    + " foreground=" + Prefs.foregroundEnabled(c)
                    + " dev=" + Prefs.devKeepAlive(c));
            c.startForegroundService(i);
        } catch (Throwable t) {
            AppDebugLog.d("KeepAlive", "start failed " + t);
        }
    }

    public static void stop(Context c) {
        AppDebugLog.d("KeepAlive", "stop requested");
        cancelHeartbeatAlarm(c);
        try {
            c.stopService(new Intent(c, KeepAliveService.class));
        } catch (Throwable t) {
            AppDebugLog.d("KeepAlive", "stop failed " + t);
        }
    }

    /** 开发者模式开关变更后调用：启停服务并刷新强保活状态 */
    public static void applyDevKeepAlive(Context c) {
        AppDebugLog.d("KeepAlive", "applyDevKeepAlive enabled=" + Prefs.devKeepAlive(c));
        if (Prefs.devKeepAlive(c) || Prefs.foregroundEnabled(c)) {
            Intent i = new Intent(c, KeepAliveService.class);
            i.setAction(ACTION_APPLY_DEV);
            try {
                c.startForegroundService(i);
            } catch (Throwable t) {
                AppDebugLog.d("KeepAlive", "applyDev start failed " + t);
            }
        } else {
            stop(c);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AppDebugLog.bind(this);
        AppDebugLog.d("KeepAlive", "onCreate");
        createChannels(this);
        Speaker.init(this, null);
        VolumeWatcher.start(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        AppDebugLog.d("KeepAlive", "onStartCommand action="
                + (action == null ? "" : action)
                + " flags=" + flags
                + " connected=" + NotifyListener.isConnected()
                + " dev=" + Prefs.devKeepAlive(this));

        if (ACTION_HEARTBEAT.equals(action)) {
            onHeartbeat();
        }

        Notification n = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTI_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTI_ID, n);
            }
            AppDebugLog.d("KeepAlive", "startForeground ok");
        } catch (Throwable t) {
            AppDebugLog.d("KeepAlive", "startForeground failed " + t);
        }

        applyDevState();
        return START_STICKY;
    }

    private void onHeartbeat() {
        AppDebugLog.d("KeepAlive", "heartbeat"
                + " connected=" + NotifyListener.isConnected()
                + " master=" + Prefs.masterEnabled(this)
                + " speaking=" + Speaker.isSpeaking());
        if (NotifyListener.isPermissionGranted(this) && !NotifyListener.isConnected()) {
            ListenerHeal.ensure(this);
        }
        Speaker.init(this, null);
    }

    private void applyDevState() {
        if (Prefs.devKeepAlive(this)) {
            acquireDevWakeLock();
            scheduleHeartbeatAlarm(this);
        } else {
            releaseDevWakeLock();
            cancelHeartbeatAlarm(this);
        }
        // 刷新通知文案 / 渠道
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTI_ID, buildNotification());
        } catch (Throwable ignored) {
        }
    }

    private void acquireDevWakeLock() {
        try {
            if (devWakeLock != null && devWakeLock.isHeld()) return;
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm == null) return;
            if (devWakeLock == null) {
                devWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "msgnotify:devKeepAlive");
                devWakeLock.setReferenceCounted(false);
            }
            devWakeLock.acquire();
            AppDebugLog.d("KeepAlive", "dev WakeLock acquired");
        } catch (Throwable t) {
            AppDebugLog.d("KeepAlive", "dev WakeLock acquire failed " + t);
        }
    }

    private void releaseDevWakeLock() {
        try {
            if (devWakeLock != null && devWakeLock.isHeld()) {
                devWakeLock.release();
                AppDebugLog.d("KeepAlive", "dev WakeLock released");
            }
        } catch (Throwable t) {
            AppDebugLog.d("KeepAlive", "dev WakeLock release failed " + t);
        }
    }

    private Notification buildNotification() {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        boolean dev = Prefs.devKeepAlive(this);
        String channel = dev ? CHANNEL_ID_DEV : CHANNEL_ID;
        String title = dev ? "强保活运行中（开发者模式）" : "通知语音播报运行中";
        String text;
        if (NotifyListener.isPermissionGranted(this) && !NotifyListener.isConnected()) {
            text = "监听未连接，正在自愈…";
        } else if (dev) {
            text = "每 5 分钟心跳 + WakeLock，耗电较高";
        } else {
            text = Prefs.masterEnabled(this) ? "播报已开启" : "播报已暂停";
        }

        Notification.Builder b = new Notification.Builder(this, channel)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_m_voice)
                .setContentIntent(pi)
                .setOngoing(true);
        return b.build();
    }

    /** 刷新常驻通知 / 启停服务 */
    public static void refresh(Context c) {
        AppDebugLog.d("KeepAlive", "refresh foreground=" + Prefs.foregroundEnabled(c)
                + " dev=" + Prefs.devKeepAlive(c));
        if (Prefs.foregroundEnabled(c) || Prefs.devKeepAlive(c)) {
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

        NotificationChannel keepDev = new NotificationChannel(CHANNEL_ID_DEV,
                "开发者模式强保活", NotificationManager.IMPORTANCE_LOW);
        keepDev.setShowBadge(false);
        nm.createNotificationChannel(keepDev);

        NotificationChannel test = new NotificationChannel(CHANNEL_TEST,
                "播报自检", NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(test);

        NotificationChannel fix = new NotificationChannel(CHANNEL_LISTENER_FIX,
                "监听断开修复", NotificationManager.IMPORTANCE_HIGH);
        fix.setShowBadge(true);
        nm.createNotificationChannel(fix);
    }

    private static PendingIntent heartbeatPi(Context c) {
        Intent i = new Intent(c, KeepAliveService.class);
        i.setAction(ACTION_HEARTBEAT);
        return PendingIntent.getService(c, ALARM_REQ, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void scheduleHeartbeatAlarm(Context c) {
        if (!Prefs.devKeepAlive(c)) return;
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            PendingIntent pi = heartbeatPi(c);
            long trigger = SystemClock.elapsedRealtime() + HEARTBEAT_MS;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
            } else {
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
            }
            AppDebugLog.d("KeepAlive", "heartbeat alarm scheduled inMs=" + HEARTBEAT_MS);
        } catch (Throwable t) {
            AppDebugLog.d("KeepAlive", "schedule heartbeat failed " + t);
        }
    }

    public static void cancelHeartbeatAlarm(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            am.cancel(heartbeatPi(c));
            AppDebugLog.d("KeepAlive", "heartbeat alarm cancelled");
        } catch (Throwable t) {
            AppDebugLog.d("KeepAlive", "cancel heartbeat failed " + t);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        AppDebugLog.d("KeepAlive", "onDestroy"
                + " foreground=" + Prefs.foregroundEnabled(this)
                + " dev=" + Prefs.devKeepAlive(this));
        releaseDevWakeLock();
        cancelHeartbeatAlarm(this);
        super.onDestroy();
        if (Prefs.foregroundEnabled(this) || Prefs.devKeepAlive(this)) {
            try {
                AppDebugLog.d("KeepAlive", "onDestroy restart sticky");
                startForegroundService(new Intent(this, KeepAliveService.class));
            } catch (Throwable t) {
                AppDebugLog.d("KeepAlive", "onDestroy restart failed " + t);
            }
        }
    }
}
