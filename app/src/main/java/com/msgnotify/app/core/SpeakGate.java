package com.msgnotify.app.core;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 播报总入口：统一做条件判断、写记录、入队
 */
public final class SpeakGate {

    private static final long STALE_NOTIFY_MS = 120_000L;

    private static final class BurstEntry {
        final long time;
        final String recordId;

        BurstEntry(long time, String recordId) {
            this.time = time;
            this.recordId = recordId;
        }
    }

    private static final class BurstWindow {
        final List<BurstEntry> entries = new ArrayList<>();
        boolean merged;
    }

    private static final Map<String, BurstWindow> BURST = new HashMap<>();

    private SpeakGate() {
    }

    public static void handle(Context c, String pkg, String appName,
                              String title, String content, boolean force) {
        handle(c, pkg, appName, title, content, null, force, System.currentTimeMillis());
    }

    public static void handle(Context c, String pkg, String appName,
                              String title, String content, Uri audioUri, boolean force) {
        handle(c, pkg, appName, title, content, audioUri, force, System.currentTimeMillis());
    }

    /**
     * @param pkg        来源包名
     * @param appName    来源应用名
     * @param title      通知标题
     * @param content    通知内容
     * @param audioUri   可选语音附件（MessagingStyle dataUri）
     * @param force      true 表示跳过应用选择/关键词等过滤（用于测试播报）
     * @param notifyTime 通知真实时间（sbn.postTime），用于保鲜与队列双时间戳
     */
    public static void handle(Context c, String pkg, String appName,
                              String title, String content, Uri audioUri,
                              boolean force, long notifyTime) {

        long now = System.currentTimeMillis();
        long nt = notifyTime > 0 ? notifyTime : now;

        boolean hasAudio = audioUri != null && Prefs.voiceMsgEnabled(c);
        boolean voiceLike = Prefs.voiceMsgEnabled(c)
                && (hasAudio || VoiceMsg.isVoiceContent(content));

        String contentForBuild = content;
        if (voiceLike && TextUtils.isEmpty(contentForBuild)) {
            contentForBuild = "[语音]";
        }

        String speakText = TextBuilder.build(c, appName, title, contentForBuild, hasAudio);

        SpeakRecord r = new SpeakRecord();
        r.id = "r" + now + "_" + (int) (Math.random() * 10000);
        r.pkg = pkg;
        r.appName = appName;
        r.title = title;
        r.content = content;
        r.speakText = hasAudio ? ("[原声语音] " + speakText) : speakText;
        r.time = now;

        String reject = null;
        if (TextUtils.isEmpty(speakText) && !hasAudio) {
            reject = "播报内容为空";
        } else if (!force) {
            reject = Conditions.reject(c, title + " " + content);
        } else if (!Prefs.masterEnabled(c)) {
            reject = "总开关已关闭";
        }

        if (reject == null && now - nt > STALE_NOTIFY_MS) {
            reject = "通知延迟超时，已放弃";
            AppDebugLog.d("SpeakGate", "stale by postTime ageMs=" + (now - nt)
                    + " pkg=" + (pkg == null ? "" : pkg));
        }

        if (reject != null) {
            AppDebugLog.d("SpeakGate", "rejected pkg=" + (pkg == null ? "" : pkg)
                    + " force=" + force + " reason=" + reject
                    + " speakTextLen=" + (speakText == null ? 0 : speakText.length()));
            r.status = SpeakRecord.STATUS_SKIPPED;
            r.reason = reject;
            RecordStore.add(c, r);
            return;
        }

        // 同人短时多条合并
        String titleKey = title == null ? "" : title.trim();
        boolean mergeOn = !force && Prefs.burstMergeEnabled(c) && !TextUtils.isEmpty(titleKey);
        if (mergeOn) {
            String key = (pkg == null ? "" : pkg) + "|" + titleKey;
            long windowMs = Prefs.burstMergeWindowSec(c) * 1000L;
            int threshold = Prefs.burstMergeCount(c);
            boolean skipSubsequent = false;
            boolean fireMerge = false;
            List<String> prevIds = new ArrayList<>();
            int windowSize = 0;
            synchronized (BURST) {
                BurstWindow w = BURST.get(key);
                if (w == null) {
                    w = new BurstWindow();
                    BURST.put(key, w);
                }
                pruneBurstLocked(w, now, windowMs);
                if (w.entries.isEmpty()) {
                    w.merged = false;
                }
                w.entries.add(new BurstEntry(now, r.id));
                windowSize = w.entries.size();
                if (w.merged) {
                    skipSubsequent = true;
                } else if (windowSize > threshold) {
                    for (BurstEntry e : w.entries) {
                        if (!r.id.equals(e.recordId)) prevIds.add(e.recordId);
                    }
                    w.merged = true;
                    fireMerge = true;
                }
            }
            if (skipSubsequent) {
                r.status = SpeakRecord.STATUS_SKIPPED;
                r.reason = "已计入合并播报";
                RecordStore.add(c, r);
                AppDebugLog.d("SpeakGate", "burst merge skip subsequent key=" + key
                        + " n=" + windowSize + " id=" + r.id);
                return;
            }
            if (fireMerge) {
                Speaker.cancelPending(prevIds);
                for (String id : prevIds) {
                    RecordStore.updateStatus(c, id,
                            SpeakRecord.STATUS_SKIPPED, "同人短时多条，已合并");
                }
                String mergeText = buildMergeText(c, appName, titleKey);
                r.speakText = mergeText;
                r.status = SpeakRecord.STATUS_PENDING;
                RecordStore.add(c, r);
                AppDebugLog.d("SpeakGate", "burst merge fire key=" + key
                        + " n=" + windowSize + " threshold=" + threshold
                        + " cancelled=" + prevIds.size() + " id=" + r.id
                        + " text=" + mergeText);
                Speaker.enqueue(c, mergeText, r.id, nt);
                return;
            }
        }

        r.status = SpeakRecord.STATUS_PENDING;
        RecordStore.add(c, r);
        AppDebugLog.d("SpeakGate", "accepted pkg=" + (pkg == null ? "" : pkg)
                + " force=" + force + " speakTextLen=" + (speakText == null ? 0 : speakText.length())
                + " hasAudio=" + hasAudio + " voiceLike=" + voiceLike
                + " id=" + r.id);

        if (hasAudio) {
            Speaker.enqueueAudio(c, audioUri, r.id, speakText, nt);
        } else {
            Speaker.enqueue(c, speakText, r.id, nt);
        }
    }

    private static String buildMergeText(Context c, String appName, String title) {
        String suffix = Prefs.burstMergeSuffix(c);
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(appName)) sb.append(appName.trim());
        if (!TextUtils.isEmpty(title)) sb.append(title.trim());
        if (!TextUtils.isEmpty(suffix)) sb.append(suffix.trim());
        return TextBuilder.clamp(sb.toString());
    }

    private static void pruneBurstLocked(BurstWindow w, long now, long windowMs) {
        Iterator<BurstEntry> it = w.entries.iterator();
        while (it.hasNext()) {
            if (now - it.next().time > windowMs) it.remove();
        }
    }

    /** 重播某条记录（记录页绿色播放按钮） */
    public static void replay(Context c, SpeakRecord r) {
        String text = TextUtils.isEmpty(r.speakText)
                ? TextBuilder.build(c, r.appName, r.title, r.content)
                : r.speakText;
        if (text != null && text.startsWith("[原声语音] ")) {
            text = text.substring("[原声语音] ".length());
        }
        Speaker.speakNow(c, text);
    }
}
