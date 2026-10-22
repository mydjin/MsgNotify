package com.msgnotify.app.core;

import android.content.Context;
import android.text.TextUtils;

/**
 * 播报文案拼接，对应「播报格式设置」页
 * 默认效果：微信消息，小华说，你明天有空吗？
 */
public final class TextBuilder {

    private TextBuilder() {
    }

    public static String build(Context c, String appName, String title, String content) {
        return build(c, appName, title, content, false);
    }

    /**
     * @param forceVoicePlaceholder true 时即便正文不像语音占位也替换（有 audio 附件时）
     */
    public static String build(Context c, String appName, String title, String content,
                               boolean forceVoicePlaceholder) {
        String body = content == null ? "" : content.trim();

        if (Prefs.voiceMsgEnabled(c)
                && (forceVoicePlaceholder || VoiceMsg.isVoiceContent(body))) {
            body = Prefs.voiceMsgPlaceholder(c);
            AppDebugLog.d("TextBuilder", "voice placeholder applied"
                    + " force=" + forceVoicePlaceholder
                    + " origLen=" + (content == null ? 0 : content.trim().length()));
        }

        if (Prefs.lenLimitEnabled(c) && body.length() > Prefs.lenLimitChars(c)) {
            int origLen = body.length();
            body = Prefs.lenLimitMsg(c);
            AppDebugLog.d("TextBuilder", "content truncated to custom msg"
                    + " limit=" + Prefs.lenLimitChars(c)
                    + " origLen=" + origLen);
        }

        StringBuilder sb = new StringBuilder();

        if (Prefs.fmtApp(c) && !TextUtils.isEmpty(appName)) {
            sb.append(appName.trim());
            if (Prefs.fmtAppSuffixOn(c)) sb.append(Prefs.fmtAppSuffix(c));
            sb.append("，");
        }
        if (Prefs.fmtTitle(c) && !TextUtils.isEmpty(title)) {
            sb.append(title.trim());
            if (Prefs.fmtTitleSuffixOn(c)) sb.append(Prefs.fmtTitleSuffix(c));
            sb.append("，");
        }
        if (Prefs.fmtContent(c) && !TextUtils.isEmpty(body)) {
            sb.append(body);
            if (Prefs.fmtContentSuffixOn(c)) sb.append(Prefs.fmtContentSuffix(c));
            sb.append("，");
        }

        String s = sb.toString().replaceAll("[，、\\s]+$", "");
        return clamp(s);
    }

    /** 预览用：固定示例数据（超长正文，便于验证长度播报） */
    public static String preview(Context c) {
        String longSample = "这是一条用于预览信息长度播报的超长示例正文，"
                + "超过默认三十字阈值时应被替换为自定义短文案。";
        String s = build(c, "微信", "小华", longSample);
        return TextUtils.isEmpty(s) ? "（当前设置无任何可播报内容）" : s;
    }

    /** TTS 单次输入长度限制保护 */
    public static String clamp(String s) {
        if (s == null) return "";
        int max = 1000;
        try {
            max = Math.min(max, android.speech.tts.TextToSpeech.getMaxSpeechInputLength());
        } catch (Throwable ignored) {
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
