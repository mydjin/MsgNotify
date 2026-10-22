package com.msgnotify.app.core;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.widget.Toast;

import com.msgnotify.app.R;
import com.msgnotify.app.service.KeepAliveService;
import com.msgnotify.app.service.NotifyListener;
import com.msgnotify.app.util.SysNav;

/**
 * 自检播报 / 直接试听 / 监听自愈（首页与权限页共用）
 */
public final class TestSpeakHelper {

    private TestSpeakHelper() {
    }

    /** 权限已开但监听未连接时推进阶梯自愈（首页 onResume） */
    public static void tryAutoHeal(Context c) {
        AppDebugLog.bind(c);
        ListenerHeal.ensure(c);
    }

    /** 发一条带自检标记的通知，走 NotifyListener → SpeakGate（force） */
    public static void sendTestNotification(Context c) {
        AppDebugLog.bind(c);
        boolean connected = NotifyListener.isConnected();
        AppDebugLog.d("Test", "sendTestNotification start"
                + " permission=" + NotifyListener.isPermissionGranted(c)
                + " connected=" + connected
                + " master=" + Prefs.masterEnabled(c));

        if (!NotifyListener.isPermissionGranted(c)) {
            AppDebugLog.d("Test", "sendTestNotification aborted no permission");
            Toast.makeText(c, "请先开启通知读取权限", Toast.LENGTH_LONG).show();
            SysNav.openNotificationListenerSettings(c);
            return;
        }

        if (!connected) {
            AppDebugLog.d("Test", "listener disconnected, ensure heal then post");
            ListenerHeal.ensure(c);
            Toast.makeText(c,
                    "监听未连接，测试通知不会播报，请按修复通知操作或用直接试听",
                    Toast.LENGTH_LONG).show();
        }

        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            AppDebugLog.d("Test", "sendTestNotification aborted nm=null");
            return;
        }
        KeepAliveService.createChannels(c);

        Notification n = new Notification.Builder(c, KeepAliveService.CHANNEL_TEST)
                .setContentTitle("播报自检")
                .setContentText("这是一条测试通知，如果你听到这句话说明播报正常")
                .setSmallIcon(R.drawable.ic_m_voice)
                .setAutoCancel(true)
                .build();
        nm.notify(NotifyListener.TEST_TAG, 2001, n);
        AppDebugLog.d("Test", "test notification posted tag=" + NotifyListener.TEST_TAG);
        if (connected) {
            Toast.makeText(c, "已发出测试通知，请留意播报声音", Toast.LENGTH_SHORT).show();
        }
    }

    /** 绕过通知监听，直接入队 TTS，用于区分「监听挂了」还是「引擎挂了」 */
    public static void directSpeak(Context c) {
        AppDebugLog.bind(c);
        AppDebugLog.d("Test", "directSpeak start"
                + " connected=" + NotifyListener.isConnected()
                + " master=" + Prefs.masterEnabled(c));
        SpeakGate.handle(c, c.getPackageName(), "自检", "直接试听",
                "这是直接试听，不经过通知监听",
                null, true, System.currentTimeMillis());
        Toast.makeText(c, "已发起直接试听", Toast.LENGTH_SHORT).show();
    }
}
