package com.msgnotify.app.core;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 一条播报记录
 */
public class SpeakRecord {

    public static final String STATUS_SPOKEN = "SPOKEN";   // 正常播报
    public static final String STATUS_SKIPPED = "SKIPPED"; // 未播报（被过滤）
    public static final String STATUS_PENDING = "PENDING"; // 等待播报

    public String id = "";
    public String pkg = "";
    public String appName = "";
    public String title = "";
    public String content = "";
    public String speakText = "";
    public String status = STATUS_PENDING;
    public String reason = "";
    public long time = 0L;

    public boolean spoken() {
        return STATUS_SPOKEN.equals(status);
    }

    /** 状态中文描述，对应截图「消息状态：【正常播报】」 */
    public String statusLabel() {
        if (STATUS_SPOKEN.equals(status)) return "【正常播报】";
        if (STATUS_PENDING.equals(status)) return "【等待播报】";
        return "【未播报】";
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("pkg", pkg);
        o.put("appName", appName);
        o.put("title", title);
        o.put("content", content);
        o.put("speakText", speakText);
        o.put("status", status);
        o.put("reason", reason);
        o.put("time", time);
        return o;
    }

    public static SpeakRecord fromJson(JSONObject o) {
        SpeakRecord r = new SpeakRecord();
        r.id = o.optString("id", "");
        r.pkg = o.optString("pkg", "");
        r.appName = o.optString("appName", "");
        r.title = o.optString("title", "");
        r.content = o.optString("content", "");
        r.speakText = o.optString("speakText", "");
        r.status = o.optString("status", STATUS_PENDING);
        r.reason = o.optString("reason", "");
        r.time = o.optLong("time", 0L);
        return r;
    }
}
