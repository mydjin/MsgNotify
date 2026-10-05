package com.msgnotify.app.service;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;

import com.msgnotify.app.core.Prefs;
import com.msgnotify.app.core.SpeakGate;
import com.msgnotify.app.util.Apps;

import java.util.HashMap;
import java.util.Map;

/**
 * 核心：通知监听服务
 */
public class NotifyListener extends NotificationListenerService {

    /** 测试播报通知使用的 tag，用于放行本应用自身通知 */
    public static final String TEST_TAG = "msgnotify_self_test";

    private static final long DEDUP_WINDOW = 3000L;
    private static final Map<String, Long> RECENT = new HashMap<>();

    private static boolean connected = false;

    public static boolean isConnected() {
        return connected;
    }

    /** 是否已授予「读取通知」权限 */
    public static boolean isPermissionGranted(Context c) {
        String enabled = Settings.Secure.getString(
                c.getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(enabled)) return false;
        String self = c.getPackageName();
        for (String part : enabled.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(part);
            if (cn != null && self.equals(cn.getPackageName())) return true;
        }
        return false;
    }

    /** 主动请求系统重新绑定监听服务（权限页「检测并修复」用） */
    public static void rebind(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        try {
            requestRebind(new ComponentName(c, NotifyListener.class));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        connected = true;
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        connected = false;
        // 被系统杀掉后主动请求重新绑定
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                requestRebind(new ComponentName(this, NotifyListener.class));
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            process(sbn);
        } catch (Throwable ignored) {
        }
    }

    private void process(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        if (!Prefs.masterEnabled(this)) return;

        String pkg = sbn.getPackageName();
        if (TextUtils.isEmpty(pkg)) return;

        Notification n = sbn.getNotification();
        boolean selfTest = getPackageName().equals(pkg) && TEST_TAG.equals(sbn.getTag());

        // 过滤：本应用自身的常驻通知（测试通知除外）
        if (getPackageName().equals(pkg) && !selfTest) return;

        // 过滤：分组汇总、持续进行中的通知（音乐、导航、下载进度等）
        if (!selfTest) {
            if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) return;
            // 未被选中的应用不播报
            if (!Prefs.isPackageSelected(this, pkg)) return;
        }

        String title = extractTitle(n);
        String content = extractContent(n);
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(content)) return;

        // 去重：同一通知在短时间内重复 post
        String key = sbn.getKey() + "|" + title + "|" + content;
        long now = System.currentTimeMillis();
        synchronized (RECENT) {
            Long last = RECENT.get(key);
            if (last != null && now - last < DEDUP_WINDOW) return;
            RECENT.put(key, now);
            if (RECENT.size() > 100) {
                java.util.Iterator<Map.Entry<String, Long>> it = RECENT.entrySet().iterator();
                while (it.hasNext()) {
                    if (now - it.next().getValue() > 60000L) it.remove();
                }
            }
        }

        String appName = Apps.appName(this, pkg);
        SpeakGate.handle(this, pkg, appName, title, content, selfTest);
    }

    private String extractTitle(Notification n) {
        Bundle ex = n.extras;
        if (ex == null) return "";
        CharSequence t = ex.getCharSequence(Notification.EXTRA_TITLE);
        if (TextUtils.isEmpty(t)) t = ex.getCharSequence(Notification.EXTRA_TITLE_BIG);
        if (TextUtils.isEmpty(t)) t = ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
        return t == null ? "" : clean(t.toString());
    }

    /**
     * 内容提取，重点处理微信「x 条消息」折叠的情况：
     * MessagingStyle 最新消息 -> BIG_TEXT -> TEXT -> TEXT_LINES 最后一行
     */
    private String extractContent(Notification n) {
        Bundle ex = n.extras;
        if (ex == null) return "";

        // 1) MessagingStyle（微信、QQ 等聊天类通知常用）
        try {
            NotificationCompat.MessagingStyle ms =
                    NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n);
            if (ms != null) {
                java.util.List<NotificationCompat.MessagingStyle.Message> msgs = ms.getMessages();
                if (msgs != null && !msgs.isEmpty()) {
                    NotificationCompat.MessagingStyle.Message last = msgs.get(msgs.size() - 1);
                    CharSequence text = last.getText();
                    if (!TextUtils.isEmpty(text)) return clean(text.toString());
                }
            }
        } catch (Throwable ignored) {
        }

        // 2) 展开后的长文本
        CharSequence big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (!TextUtils.isEmpty(big)) return clean(big.toString());

        // 3) 普通文本
        CharSequence txt = ex.getCharSequence(Notification.EXTRA_TEXT);
        if (!TextUtils.isEmpty(txt)) {
            String s = clean(txt.toString());
            // 「[2条]xxx」之类前缀清理
            s = s.replaceFirst("^\\[\\d+条?\\]\\s*", "");
            if (!isFoldedSummary(s)) return s;
        }

        // 4) 收件箱样式多行，取最后一行（最新）
        CharSequence[] lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null && lines.length > 0) {
            for (int i = lines.length - 1; i >= 0; i--) {
                if (!TextUtils.isEmpty(lines[i])) return clean(lines[i].toString());
            }
        }

        // 5) 实在拿不到就回退到原始 text（即便是折叠提示）
        return txt == null ? "" : clean(txt.toString());
    }

    /** 判断是否是「[3条]」「您有 3 条未读消息」这类折叠汇总文案 */
    private boolean isFoldedSummary(String s) {
        return s.matches("^\\d+条(新)?消息$")
                || s.matches("^你有\\d+条新消息$")
                || s.matches("^\\d+ new messages?$");
    }

    private String clean(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }
}
