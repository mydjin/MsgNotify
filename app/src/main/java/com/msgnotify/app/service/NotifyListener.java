package com.msgnotify.app.service;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;

import com.msgnotify.app.core.AppDebugLog;
import com.msgnotify.app.core.ListenerHeal;
import com.msgnotify.app.core.Prefs;
import com.msgnotify.app.core.SpeakGate;
import com.msgnotify.app.core.VoiceMsg;
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

    /** 从通知提取的正文与可选语音附件 */
    private static final class Extracted {
        final String content;
        final Uri audioUri;
        final String audioMime;

        Extracted(String content, Uri audioUri, String audioMime) {
            this.content = content == null ? "" : content;
            this.audioUri = audioUri;
            this.audioMime = audioMime == null ? "" : audioMime;
        }
    }

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

    /** 主动请求系统重新绑定监听服务 */
    public static void rebind(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            AppDebugLog.d("Notify", "rebind skipped sdk=" + Build.VERSION.SDK_INT);
            return;
        }
        try {
            AppDebugLog.d("Notify", "rebind requested connected=" + connected);
            requestRebind(new ComponentName(c, NotifyListener.class));
        } catch (Throwable t) {
            AppDebugLog.d("Notify", "rebind failed " + t);
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        connected = true;
        AppDebugLog.bind(this);
        AppDebugLog.d("Notify", "onListenerConnected");
        ListenerHeal.onConnected(this);
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        connected = false;
        AppDebugLog.bind(this);
        boolean rebound = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                requestRebind(new ComponentName(this, NotifyListener.class));
                rebound = true;
            } catch (Throwable t) {
                AppDebugLog.d("Notify", "onListenerDisconnected rebind failed " + t);
            }
        }
        AppDebugLog.d("Notify", "onListenerDisconnected rebindRequested=" + rebound);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            process(sbn);
        } catch (Throwable t) {
            AppDebugLog.d("Notify", "onNotificationPosted error "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private void process(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) {
            AppDebugLog.d("Notify", "skip null sbn/notification");
            return;
        }

        String pkg = sbn.getPackageName();
        String tag = sbn.getTag();
        boolean selfTest = getPackageName().equals(pkg) && TEST_TAG.equals(tag);
        AppDebugLog.d("Notify", "posted pkg=" + (pkg == null ? "" : pkg)
                + " tag=" + (tag == null ? "" : tag)
                + " selfTest=" + selfTest
                + " connected=" + connected);

        // 自检通知不受总开关拦截（强制播报）
        if (!selfTest && !Prefs.masterEnabled(this)) {
            AppDebugLog.d("Notify", "skip master off pkg=" + (pkg == null ? "" : pkg));
            return;
        }

        if (TextUtils.isEmpty(pkg)) {
            AppDebugLog.d("Notify", "skip empty pkg");
            return;
        }

        Notification n = sbn.getNotification();

        // 过滤：本应用自身的常驻通知（测试通知除外）
        if (getPackageName().equals(pkg) && !selfTest) {
            AppDebugLog.d("Notify", "skip self notify filtered");
            return;
        }

        // 过滤：分组汇总、持续进行中的通知（音乐、导航、下载进度等）
        if (!selfTest) {
            if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
                AppDebugLog.d("Notify", "skip group summary pkg=" + pkg);
                return;
            }
            if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) {
                AppDebugLog.d("Notify", "skip ongoing pkg=" + pkg);
                return;
            }
            if (!Prefs.isPackageSelected(this, pkg)) {
                AppDebugLog.d("Notify", "skip pkg not selected pkg=" + pkg);
                return;
            }
        }

        String title = extractTitle(n);
        Extracted extracted = extract(n);
        String content = extracted.content;
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(content) && extracted.audioUri == null) {
            AppDebugLog.d("Notify", "skip empty payload pkg=" + pkg);
            return;
        }

        // 去重：同一通知在短时间内重复 post
        String key = sbn.getKey() + "|" + title + "|" + content
                + "|" + VoiceMsg.uriSummary(extracted.audioUri);
        long now = System.currentTimeMillis();
        synchronized (RECENT) {
            Long last = RECENT.get(key);
            if (last != null && now - last < DEDUP_WINDOW) {
                AppDebugLog.d("Notify", "skip dedup pkg=" + pkg
                        + " ageMs=" + (now - last));
                return;
            }
            RECENT.put(key, now);
            if (RECENT.size() > 100) {
                java.util.Iterator<Map.Entry<String, Long>> it = RECENT.entrySet().iterator();
                while (it.hasNext()) {
                    if (now - it.next().getValue() > 60000L) it.remove();
                }
            }
        }

        boolean voicePlaceholder = VoiceMsg.isVoiceContent(content);
        boolean hasAudio = extracted.audioUri != null && VoiceMsg.isAudioMime(extracted.audioMime);
        AppDebugLog.d("Notify", "voice detect pkg=" + pkg
                + " voicePlaceholder=" + voicePlaceholder
                + " hasAudioUri=" + hasAudio
                + " mime=" + extracted.audioMime
                + " uri=" + VoiceMsg.uriSummary(extracted.audioUri)
                + " contentLen=" + content.length());

        String appName = Apps.appName(this, pkg);
        long notifyTime = sbn.getPostTime();
        if (notifyTime <= 0L) notifyTime = now;
        AppDebugLog.d("Notify", "forward pkg=" + pkg + " selfTest=" + selfTest
                + " titleLen=" + (title == null ? 0 : title.length())
                + " contentLen=" + content.length()
                + " postAgeMs=" + (now - notifyTime)
                + " connected=" + connected);
        SpeakGate.handle(this, pkg, appName, title, content,
                hasAudio ? extracted.audioUri : null, selfTest, notifyTime);
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
     * 同时尝试读取 MessagingStyle 附件中的 audio URI
     */
    private Extracted extract(Notification n) {
        Bundle ex = n.extras;
        if (ex == null) return new Extracted("", null, "");

        Uri audioUri = null;
        String audioMime = "";
        String content = "";

        try {
            NotificationCompat.MessagingStyle ms =
                    NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n);
            if (ms != null) {
                java.util.List<NotificationCompat.MessagingStyle.Message> msgs = ms.getMessages();
                if (msgs != null && !msgs.isEmpty()) {
                    NotificationCompat.MessagingStyle.Message last = msgs.get(msgs.size() - 1);
                    CharSequence text = last.getText();
                    if (!TextUtils.isEmpty(text)) content = clean(text.toString());
                    Uri data = last.getDataUri();
                    String mime = last.getDataMimeType();
                    if (data != null && VoiceMsg.isAudioMime(mime)) {
                        audioUri = data;
                        audioMime = mime;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        if (!TextUtils.isEmpty(content)) {
            return new Extracted(content, audioUri, audioMime);
        }

        CharSequence big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (!TextUtils.isEmpty(big)) {
            return new Extracted(clean(big.toString()), audioUri, audioMime);
        }

        CharSequence txt = ex.getCharSequence(Notification.EXTRA_TEXT);
        if (!TextUtils.isEmpty(txt)) {
            String s = clean(txt.toString());
            s = s.replaceFirst("^\\[\\d+条?\\]\\s*", "");
            if (!isFoldedSummary(s)) {
                return new Extracted(s, audioUri, audioMime);
            }
        }

        CharSequence[] lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null && lines.length > 0) {
            for (int i = lines.length - 1; i >= 0; i--) {
                if (!TextUtils.isEmpty(lines[i])) {
                    return new Extracted(clean(lines[i].toString()), audioUri, audioMime);
                }
            }
        }

        String fallback = txt == null ? "" : clean(txt.toString());
        return new Extracted(fallback, audioUri, audioMime);
    }

    private boolean isFoldedSummary(String s) {
        return s.matches("^\\d+条(新)?消息$")
                || s.matches("^你有\\d+条新消息$")
                || s.matches("^\\d+ new messages?$");
    }

    private String clean(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }
}
