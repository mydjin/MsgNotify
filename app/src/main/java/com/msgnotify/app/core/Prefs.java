package com.msgnotify.app.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 全局设置存储（SharedPreferences 封装）
 */
public final class Prefs {

    private static final String FILE = "msgnotify_settings";

    // 总开关
    public static final String MASTER_ENABLED = "masterEnabled";
    // 已选中的播报应用包名集合
    public static final String SELECTED_PACKAGES = "selectedPackages";

    // 播报格式
    public static final String FMT_APP = "fmtApp";
    public static final String FMT_APP_SUFFIX_ON = "fmtAppSuffixOn";
    public static final String FMT_APP_SUFFIX = "fmtAppSuffix";
    public static final String FMT_TITLE = "fmtTitle";
    public static final String FMT_TITLE_SUFFIX_ON = "fmtTitleSuffixOn";
    public static final String FMT_TITLE_SUFFIX = "fmtTitleSuffix";
    public static final String FMT_CONTENT = "fmtContent";
    public static final String FMT_CONTENT_SUFFIX_ON = "fmtContentSuffixOn";
    public static final String FMT_CONTENT_SUFFIX = "fmtContentSuffix";

    // 音频音色
    public static final String VOLUME = "volume";       // 0.1 - 1.0
    public static final String PITCH = "pitch";         // 0.5 - 2.0
    public static final String RATE = "rate";           // 0.5 - 2.0
    public static final String TTS_ENGINE = "ttsEngine"; // "" = 跟随系统

    // 播报设置
    public static final String HEADSET_ONLY = "headsetOnly";
    public static final String KEEP_SCREEN_ON = "keepScreenOn";
    public static final String VOLUME_DOWN_INTERRUPT = "volumeDownInterrupt";
    public static final String FOREGROUND_ENABLED = "foregroundEnabled";

    // 关键词
    public static final String BLOCK_KEYWORDS_ENABLED = "blockKeywordsEnabled";
    public static final String BLOCK_KEYWORDS = "blockKeywords";
    public static final String ONLY_KEYWORDS_ENABLED = "onlyKeywordsEnabled";
    public static final String ONLY_KEYWORDS = "onlyKeywords";

    // 扩展播报
    public static final String CALL_ENABLED = "callEnabled";
    public static final String SMS_ENABLED = "smsEnabled";

    // 免打扰时段（分钟数，0-1439）
    public static final String DND_ENABLED = "dndEnabled";
    public static final String DND_START = "dndStart";
    public static final String DND_END = "dndEnd";

    // 信息长度播报：正文超长时改播自定义短文案
    public static final String LEN_LIMIT_ENABLED = "lenLimitEnabled";
    public static final String LEN_LIMIT_CHARS = "lenLimitChars";
    public static final String LEN_LIMIT_MSG = "lenLimitMsg";
    public static final int LEN_LIMIT_CHARS_DEFAULT = 30;
    public static final String LEN_LIMIT_MSG_DEFAULT = "具体消息请打开微信";

    // 语音消息：优先原声附件，失败回退占位 TTS
    public static final String VOICE_MSG_ENABLED = "voiceMsgEnabled";
    public static final String VOICE_MSG_PLACEHOLDER = "voiceMsgPlaceholder";
    public static final String VOICE_MSG_PLACEHOLDER_DEFAULT = "发来一条语音";

    // 同人短时多条合并
    public static final String BURST_MERGE_ENABLED = "burstMergeEnabled";
    public static final String BURST_MERGE_WINDOW_SEC = "burstMergeWindowSec";
    public static final String BURST_MERGE_COUNT = "burstMergeCount";
    public static final String BURST_MERGE_SUFFIX = "burstMergeSuffix";
    public static final int BURST_MERGE_WINDOW_SEC_DEFAULT = 5;
    public static final int BURST_MERGE_COUNT_DEFAULT = 3;
    public static final String BURST_MERGE_SUFFIX_DEFAULT = "发来多条消息";

    /** 开发者模式强保活（耗电，仅排障） */
    public static final String DEV_KEEP_ALIVE = "devKeepAlive";

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private Prefs() {
    }

    public static boolean getBool(Context c, String key, boolean def) {
        return sp(c).getBoolean(key, def);
    }

    public static void setBool(Context c, String key, boolean v) {
        sp(c).edit().putBoolean(key, v).apply();
    }

    public static int getInt(Context c, String key, int def) {
        return sp(c).getInt(key, def);
    }

    public static void setInt(Context c, String key, int v) {
        sp(c).edit().putInt(key, v).apply();
    }

    public static float getFloat(Context c, String key, float def) {
        return sp(c).getFloat(key, def);
    }

    public static void setFloat(Context c, String key, float v) {
        sp(c).edit().putFloat(key, v).apply();
    }

    public static String getStr(Context c, String key, String def) {
        String s = sp(c).getString(key, def);
        return s == null ? def : s;
    }

    public static void setStr(Context c, String key, String v) {
        sp(c).edit().putString(key, v == null ? "" : v).apply();
    }

    /* ------------------ 播报应用集合 ------------------ */

    public static Set<String> selectedPackages(Context c) {
        Set<String> s = sp(c).getStringSet(SELECTED_PACKAGES, null);
        if (s == null) return Collections.emptySet();
        return new LinkedHashSet<>(s);
    }

    public static boolean isPackageSelected(Context c, String pkg) {
        return selectedPackages(c).contains(pkg);
    }

    public static void setPackageSelected(Context c, String pkg, boolean selected) {
        Set<String> s = new LinkedHashSet<>(selectedPackages(c));
        if (selected) {
            s.add(pkg);
        } else {
            s.remove(pkg);
        }
        sp(c).edit().putStringSet(SELECTED_PACKAGES, s).apply();
    }

    public static void setSelectedPackages(Context c, Set<String> pkgs) {
        sp(c).edit().putStringSet(SELECTED_PACKAGES, new LinkedHashSet<>(pkgs)).apply();
    }

    /* ------------------ 默认值读取 ------------------ */

    public static boolean masterEnabled(Context c) {
        return getBool(c, MASTER_ENABLED, true);
    }

    public static float volume(Context c) {
        return getFloat(c, VOLUME, 1.0f);
    }

    public static float pitch(Context c) {
        return getFloat(c, PITCH, 1.0f);
    }

    public static float rate(Context c) {
        return getFloat(c, RATE, 1.0f);
    }

    public static String ttsEngine(Context c) {
        return getStr(c, TTS_ENGINE, "");
    }

    public static boolean fmtApp(Context c) {
        return getBool(c, FMT_APP, true);
    }

    public static boolean fmtAppSuffixOn(Context c) {
        return getBool(c, FMT_APP_SUFFIX_ON, true);
    }

    public static String fmtAppSuffix(Context c) {
        return getStr(c, FMT_APP_SUFFIX, "消息");
    }

    public static boolean fmtTitle(Context c) {
        return getBool(c, FMT_TITLE, true);
    }

    public static boolean fmtTitleSuffixOn(Context c) {
        return getBool(c, FMT_TITLE_SUFFIX_ON, true);
    }

    public static String fmtTitleSuffix(Context c) {
        return getStr(c, FMT_TITLE_SUFFIX, "说");
    }

    public static boolean fmtContent(Context c) {
        return getBool(c, FMT_CONTENT, true);
    }

    public static boolean fmtContentSuffixOn(Context c) {
        return getBool(c, FMT_CONTENT_SUFFIX_ON, false);
    }

    public static String fmtContentSuffix(Context c) {
        return getStr(c, FMT_CONTENT_SUFFIX, "");
    }

    public static boolean volumeDownInterrupt(Context c) {
        return getBool(c, VOLUME_DOWN_INTERRUPT, true);
    }

    public static boolean foregroundEnabled(Context c) {
        return getBool(c, FOREGROUND_ENABLED, true);
    }

    public static boolean lenLimitEnabled(Context c) {
        return getBool(c, LEN_LIMIT_ENABLED, false);
    }

    public static int lenLimitChars(Context c) {
        int v = getInt(c, LEN_LIMIT_CHARS, LEN_LIMIT_CHARS_DEFAULT);
        return v < 1 ? LEN_LIMIT_CHARS_DEFAULT : v;
    }

    public static String lenLimitMsg(Context c) {
        String s = getStr(c, LEN_LIMIT_MSG, LEN_LIMIT_MSG_DEFAULT);
        return s == null || s.trim().isEmpty() ? LEN_LIMIT_MSG_DEFAULT : s;
    }

    public static boolean voiceMsgEnabled(Context c) {
        return getBool(c, VOICE_MSG_ENABLED, true);
    }

    public static String voiceMsgPlaceholder(Context c) {
        String s = getStr(c, VOICE_MSG_PLACEHOLDER, VOICE_MSG_PLACEHOLDER_DEFAULT);
        return s == null || s.trim().isEmpty() ? VOICE_MSG_PLACEHOLDER_DEFAULT : s;
    }

    public static boolean burstMergeEnabled(Context c) {
        return getBool(c, BURST_MERGE_ENABLED, true);
    }

    public static int burstMergeWindowSec(Context c) {
        int v = getInt(c, BURST_MERGE_WINDOW_SEC, BURST_MERGE_WINDOW_SEC_DEFAULT);
        return v < 1 ? BURST_MERGE_WINDOW_SEC_DEFAULT : v;
    }

    public static int burstMergeCount(Context c) {
        int v = getInt(c, BURST_MERGE_COUNT, BURST_MERGE_COUNT_DEFAULT);
        return v < 1 ? BURST_MERGE_COUNT_DEFAULT : v;
    }

    public static String burstMergeSuffix(Context c) {
        String s = getStr(c, BURST_MERGE_SUFFIX, BURST_MERGE_SUFFIX_DEFAULT);
        return s == null || s.trim().isEmpty() ? BURST_MERGE_SUFFIX_DEFAULT : s;
    }

    public static boolean devKeepAlive(Context c) {
        return getBool(c, DEV_KEEP_ALIVE, false);
    }
}
