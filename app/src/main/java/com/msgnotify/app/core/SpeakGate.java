package com.msgnotify.app.core;

import android.content.Context;
import android.text.TextUtils;

/**
 * 播报总入口：统一做条件判断、写记录、入队
 */
public final class SpeakGate {

    private SpeakGate() {
    }

    /**
     * @param pkg      来源包名
     * @param appName  来源应用名
     * @param title    通知标题
     * @param content  通知内容
     * @param force    true 表示跳过应用选择/关键词等过滤（用于测试播报）
     */
    public static void handle(Context c, String pkg, String appName,
                              String title, String content, boolean force) {

        String speakText = TextBuilder.build(c, appName, title, content);

        SpeakRecord r = new SpeakRecord();
        r.id = "r" + System.currentTimeMillis() + "_" + (int) (Math.random() * 10000);
        r.pkg = pkg;
        r.appName = appName;
        r.title = title;
        r.content = content;
        r.speakText = speakText;
        r.time = System.currentTimeMillis();

        String reject = null;
        if (TextUtils.isEmpty(speakText)) {
            reject = "播报内容为空";
        } else if (!force) {
            reject = Conditions.reject(c, title + " " + content);
        } else if (!Prefs.masterEnabled(c)) {
            reject = "总开关已关闭";
        }

        if (reject != null) {
            r.status = SpeakRecord.STATUS_SKIPPED;
            r.reason = reject;
            RecordStore.add(c, r);
            return;
        }

        r.status = SpeakRecord.STATUS_PENDING;
        RecordStore.add(c, r);
        Speaker.enqueue(c, speakText, r.id);
    }

    /** 重播某条记录（记录页绿色播放按钮） */
    public static void replay(Context c, SpeakRecord r) {
        String text = TextUtils.isEmpty(r.speakText)
                ? TextBuilder.build(c, r.appName, r.title, r.content)
                : r.speakText;
        Speaker.speakNow(c, text);
    }
}
