package com.msgnotify.app.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 播报记录存储（JSON 数组存于 SharedPreferences，上限 200 条）
 */
public final class RecordStore {

    private static final String FILE = "msgnotify_records";
    private static final String KEY = "records";
    private static final int MAX = 200;

    private static final Object LOCK = new Object();

    private RecordStore() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** 返回全部记录，最新的在前 */
    public static List<SpeakRecord> all(Context c) {
        List<SpeakRecord> out = new ArrayList<>();
        String raw = sp(c).getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(SpeakRecord.fromJson(o));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void save(Context c, List<SpeakRecord> list) {
        JSONArray arr = new JSONArray();
        int n = Math.min(list.size(), MAX);
        for (int i = 0; i < n; i++) {
            try {
                arr.put(list.get(i).toJson());
            } catch (Exception ignored) {
            }
        }
        sp(c).edit().putString(KEY, arr.toString()).apply();
    }

    /** 新增一条记录（插到最前） */
    public static void add(Context c, SpeakRecord r) {
        synchronized (LOCK) {
            List<SpeakRecord> list = all(c);
            list.add(0, r);
            save(c, list);
        }
    }

    /** 更新某条记录的状态 */
    public static void updateStatus(Context c, String id, String status, String reason) {
        if (id == null || id.isEmpty()) return;
        synchronized (LOCK) {
            List<SpeakRecord> list = all(c);
            for (SpeakRecord r : list) {
                if (id.equals(r.id)) {
                    r.status = status;
                    r.reason = reason == null ? "" : reason;
                    save(c, list);
                    return;
                }
            }
        }
    }

    public static void clear(Context c) {
        synchronized (LOCK) {
            sp(c).edit().putString(KEY, "[]").apply();
        }
    }

    /** 统计今日已播报条数，用于首页展示 */
    public static int countSpokenToday(Context c) {
        long dayStart = todayStart();
        int n = 0;
        for (SpeakRecord r : all(c)) {
            if (r.time >= dayStart && r.spoken()) n++;
        }
        return n;
    }

    private static long todayStart() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
