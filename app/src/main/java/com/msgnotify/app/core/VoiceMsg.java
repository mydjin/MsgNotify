package com.msgnotify.app.core;

import android.net.Uri;
import android.text.TextUtils;

import java.util.Locale;

/**
 * 语音消息识别：文本占位 + 通知附件 mime。
 */
public final class VoiceMsg {

    private VoiceMsg() {
    }

    /** 正文是否像语音消息占位（不含真实可播内容） */
    public static boolean isVoiceContent(String content) {
        if (TextUtils.isEmpty(content)) return false;
        String t = content.trim().toLowerCase(Locale.ROOT);
        if (t.contains("[语音]") || t.contains("[voice]") || t.contains("[语音消息]")) {
            return true;
        }
        if (t.equals("语音") || t.equals("语音消息") || t.equals("voice")
                || t.equals("voice message") || t.equals("发来一条语音")) {
            return true;
        }
        return t.contains("语音消息") || t.contains("voice message");
    }

    public static boolean isAudioMime(String mime) {
        if (TextUtils.isEmpty(mime)) return false;
        return mime.toLowerCase(Locale.ROOT).startsWith("audio/");
    }

    public static String uriSummary(Uri uri) {
        if (uri == null) return "";
        String scheme = uri.getScheme() == null ? "" : uri.getScheme();
        String host = uri.getHost() == null ? "" : uri.getHost();
        return scheme + "://" + host;
    }
}
