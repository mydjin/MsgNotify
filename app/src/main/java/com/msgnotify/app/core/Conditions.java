package com.msgnotify.app.core;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.text.TextUtils;

import java.util.Calendar;

/**
 * 播报前置条件判断：耳机模式、免打扰时段、关键词过滤
 * 返回 null 表示允许播报；返回非空字符串即不播报的原因
 */
public final class Conditions {

    private Conditions() {
    }

    /** 综合检查，返回拦截原因；null 表示通过 */
    public static String reject(Context c, String text) {
        if (!Prefs.masterEnabled(c)) return "总开关已关闭";
        if (Prefs.getBool(c, Prefs.HEADSET_ONLY, false) && !headsetConnected(c)) {
            return "未连接耳机（耳机模式已开启）";
        }
        if (inDndPeriod(c)) return "处于免打扰时段";
        String kw = keywordReject(c, text);
        if (kw != null) return kw;
        return null;
    }

    /* ------------------ 耳机检测 ------------------ */

    public static boolean headsetConnected(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return false;
        AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        if (devices == null) return false;
        for (AudioDeviceInfo d : devices) {
            int t = d.getType();
            if (t == AudioDeviceInfo.TYPE_WIRED_HEADSET
                    || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                    || t == AudioDeviceInfo.TYPE_USB_HEADSET
                    || t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    || t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                return true;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && t == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                return true;
            }
        }
        return false;
    }

    /* ------------------ 免打扰时段 ------------------ */

    public static boolean inDndPeriod(Context c) {
        if (!Prefs.getBool(c, Prefs.DND_ENABLED, false)) return false;
        int start = Prefs.getInt(c, Prefs.DND_START, 23 * 60);
        int end = Prefs.getInt(c, Prefs.DND_END, 7 * 60);
        if (start == end) return false;

        Calendar cal = Calendar.getInstance();
        int now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);

        if (start < end) {
            // 同日区间，如 09:00 - 18:00
            return now >= start && now < end;
        }
        // 跨午夜，如 23:00 - 07:00
        return now >= start || now < end;
    }

    public static String formatMinutes(int minutes) {
        int h = (minutes / 60) % 24;
        int m = minutes % 60;
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", h, m);
    }

    /* ------------------ 关键词 ------------------ */

    private static String keywordReject(Context c, String text) {
        String t = text == null ? "" : text;

        if (Prefs.getBool(c, Prefs.BLOCK_KEYWORDS_ENABLED, false)) {
            for (String k : split(Prefs.getStr(c, Prefs.BLOCK_KEYWORDS, ""))) {
                if (t.contains(k)) return "命中屏蔽关键字：" + k;
            }
        }
        if (Prefs.getBool(c, Prefs.ONLY_KEYWORDS_ENABLED, false)) {
            String[] ks = split(Prefs.getStr(c, Prefs.ONLY_KEYWORDS, ""));
            if (ks.length > 0) {
                for (String k : ks) {
                    if (t.contains(k)) return null;
                }
                return "未命中只读关键字";
            }
        }
        return null;
    }

    /** 关键词按换行 / 逗号 / 空格分隔 */
    public static String[] split(String raw) {
        if (TextUtils.isEmpty(raw)) return new String[0];
        String[] parts = raw.split("[\\n,，;；\\s]+");
        int n = 0;
        for (String p : parts) if (!p.trim().isEmpty()) n++;
        String[] out = new String[n];
        int i = 0;
        for (String p : parts) {
            if (!p.trim().isEmpty()) out[i++] = p.trim();
        }
        return out;
    }
}
