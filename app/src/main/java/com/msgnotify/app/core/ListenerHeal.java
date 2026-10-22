package com.msgnotify.app.core;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import com.msgnotify.app.R;
import com.msgnotify.app.service.KeepAliveService;
import com.msgnotify.app.service.NotifyListener;

/**
 * 通知监听阶梯自愈：L1 rebind → L2 组件 toggle → L3 引导用户拨权限。
 */
public final class ListenerHeal {

    public static final int FIX_NOTI_ID = 1002;

    private static final long L1_THROTTLE_MS = 10_000L;
    private static final long L2_AFTER_L1_MS = 12_000L;
    private static final long L2_TOGGLE_GAP_MS = 500L;
    private static final long L2_THROTTLE_MS = 3 * 60_000L;
    private static final long L3_AFTER_L2_MS = 20_000L;
    private static final long L3_THROTTLE_MS = 10 * 60_000L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static long lastL1At;
    private static long lastToggleAt;
    private static long lastL3At;
    private static boolean ladderRunning;

    private static final Runnable CHECK_AFTER_L1 = new Runnable() {
        @Override
        public void run() {
            Context c = appCtx;
            if (c == null) return;
            if (NotifyListener.isConnected()) {
                onConnected(c);
                return;
            }
            if (!NotifyListener.isPermissionGranted(c)) {
                AppDebugLog.d("Heal", "after L1 abort no permission");
                ladderRunning = false;
                return;
            }
            maybeToggle(c);
            MAIN.postDelayed(CHECK_AFTER_L2, L3_AFTER_L2_MS);
        }
    };

    private static final Runnable CHECK_AFTER_L2 = new Runnable() {
        @Override
        public void run() {
            Context c = appCtx;
            if (c == null) return;
            if (NotifyListener.isConnected()) {
                onConnected(c);
                return;
            }
            if (!NotifyListener.isPermissionGranted(c)) {
                AppDebugLog.d("Heal", "after L2 abort no permission");
                ladderRunning = false;
                return;
            }
            postFixNotification(c);
            ladderRunning = false;
        }
    };

    private static Context appCtx;

    private ListenerHeal() {
    }

    /** 进程启动时调用 */
    public static void onAppStart(Context c) {
        bind(c);
        AppDebugLog.d("Heal", "onAppStart permission=" + NotifyListener.isPermissionGranted(c)
                + " connected=" + NotifyListener.isConnected());
        ensure(c);
    }

    /** 权限已开但未连接时推进阶梯自愈 */
    public static void ensure(Context c) {
        bind(c);
        if (!NotifyListener.isPermissionGranted(c)) {
            AppDebugLog.d("Heal", "ensure skip no permission");
            return;
        }
        if (NotifyListener.isConnected()) {
            cancelFixNotification(c);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastL1At < L1_THROTTLE_MS) {
            AppDebugLog.d("Heal", "L1 throttled ladderRunning=" + ladderRunning);
            return;
        }

        lastL1At = now;
        ladderRunning = true;
        MAIN.removeCallbacks(CHECK_AFTER_L1);
        MAIN.removeCallbacks(CHECK_AFTER_L2);

        AppDebugLog.d("Heal", "L1 rebind");
        NotifyListener.rebind(c);
        MAIN.postDelayed(CHECK_AFTER_L1, L2_AFTER_L1_MS);
    }

    /** 监听已连接：清通知、停阶梯 */
    public static void onConnected(Context c) {
        bind(c);
        AppDebugLog.d("Heal", "onConnected clear ladder");
        ladderRunning = false;
        MAIN.removeCallbacks(CHECK_AFTER_L1);
        MAIN.removeCallbacks(CHECK_AFTER_L2);
        cancelFixNotification(c);
        KeepAliveService.refresh(c);
    }

    private static void bind(Context c) {
        if (c == null) return;
        appCtx = c.getApplicationContext();
        AppDebugLog.bind(appCtx);
    }

    private static void maybeToggle(Context c) {
        long now = System.currentTimeMillis();
        if (now - lastToggleAt < L2_THROTTLE_MS) {
            AppDebugLog.d("Heal", "L2 toggle throttled remainMs="
                    + (L2_THROTTLE_MS - (now - lastToggleAt)));
            return;
        }
        lastToggleAt = now;
        final Context app = c.getApplicationContext();
        final ComponentName cn = new ComponentName(app, NotifyListener.class);
        final PackageManager pm = app.getPackageManager();
        try {
            AppDebugLog.d("Heal", "L2 toggle disable");
            pm.setComponentEnabledSetting(cn,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (Throwable t) {
            AppDebugLog.d("Heal", "L2 disable failed " + t);
            return;
        }
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    pm.setComponentEnabledSetting(cn,
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP);
                    AppDebugLog.d("Heal", "L2 toggle enable ok");
                } catch (Throwable t) {
                    AppDebugLog.d("Heal", "L2 enable failed " + t);
                }
                NotifyListener.rebind(app);
                AppDebugLog.d("Heal", "L2 rebind after toggle connected="
                        + NotifyListener.isConnected());
            }
        }, L2_TOGGLE_GAP_MS);
    }

    private static void postFixNotification(Context c) {
        long now = System.currentTimeMillis();
        if (now - lastL3At < L3_THROTTLE_MS) {
            AppDebugLog.d("Heal", "L3 notify throttled");
            return;
        }
        lastL3At = now;
        KeepAliveService.createChannels(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            AppDebugLog.d("Heal", "L3 notify aborted nm=null");
            return;
        }

        PendingIntent pi = PendingIntent.getActivity(c, FIX_NOTI_ID,
                listenerSettingsIntent(c),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(c, KeepAliveService.CHANNEL_LISTENER_FIX)
                .setContentTitle("通知监听已断开")
                .setContentText("请点此关闭再打开读取通知权限")
                .setSmallIcon(R.drawable.ic_m_voice)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build();
        nm.notify(FIX_NOTI_ID, n);
        AppDebugLog.d("Heal", "L3 notify posted");
    }

    private static void cancelFixNotification(Context c) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(FIX_NOTI_ID);
        } catch (Throwable ignored) {
        }
    }

    private static Intent listenerSettingsIntent(Context c) {
        Intent i = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (i.resolveActivity(c.getPackageManager()) == null) {
                i = new Intent(Settings.ACTION_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
        } catch (Throwable ignored) {
        }
        return i;
    }
}
