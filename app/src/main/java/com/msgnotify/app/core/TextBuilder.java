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
        if (Prefs.fmtContent(c) && !TextUtils.isEmpty(content)) {
            sb.append(content.trim());
            if (Prefs.fmtContentSuffixOn(c)) sb.append(Prefs.fmtContentSuffix(c));
            sb.append("，");
        }

        String s = sb.toString().replaceAll("[，、\\s]+$", "");
        return clamp(s);
    }

    /** 预览用：固定示例数据 */
    public static String preview(Context c) {
        String s = build(c, "微信", "小华", "你明天有空吗？");
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
