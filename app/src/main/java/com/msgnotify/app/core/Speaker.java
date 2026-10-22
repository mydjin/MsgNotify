package com.msgnotify.app.core;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * TTS / 语音附件播报引擎（进程内单例）
 * - 串行队列，避免并发朗读互相打断
 * - 支持音量 / 音调 / 语速 / 引擎切换
 * - 支持 MessagingStyle 语音附件（失败回退 TTS）
 * - 播报完成后回写记录状态
 */
public final class Speaker {

    public interface InitCallback {
        void onReady(boolean ok);
    }

    private static final class Task {
        final String text;
        final String recordId;
        final Uri audioUri;
        final long time;
        final long notifyTime;

        Task(String text, String recordId, Uri audioUri, long notifyTime) {
            this.text = text;
            this.recordId = recordId == null ? "" : recordId;
            this.audioUri = audioUri;
            this.time = System.currentTimeMillis();
            this.notifyTime = notifyTime > 0 ? notifyTime : this.time;
        }
    }

    private static TextToSpeech tts;
    private static boolean ready;
    private static String currentEngine = "";
    private static final ArrayDeque<Task> QUEUE = new ArrayDeque<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Context appCtx;

    private static MediaPlayer mediaPlayer;
    private static volatile boolean mediaPlaying;

    /** 消息在队列中的最长等待时间，超过即放弃（避免引擎恢复后突然补播旧通知） */
    private static final long MAX_PENDING_MS = 120_000L;
    /** 引擎初始化超时：超过视为引擎进程被冻结，重建 */
    private static final long INIT_TIMEOUT_MS = 10_000L;
    /** 引擎初始化失败最大重试次数 */
    private static final int MAX_INIT_RETRY = 3;
    /** 突发入队上限；超限后裁到 KEEP_QUEUE */
    private static final int MAX_QUEUE = 5;
    private static final int KEEP_QUEUE = 3;
    /** MediaPlayer 播报看门狗 */
    private static final long AUDIO_WATCHDOG_MS = 90_000L;

    private static volatile boolean initializing;
    private static int initRetries;
    private static int generation;
    private static final List<InitCallback> PENDING = new ArrayList<>();
    private static PowerManager.WakeLock wakeLock;

    private static String currentUtteranceId = "";

    /** 初始化看门狗：引擎进程被系统冻结时 onInit 长时间不回调，超时重建 */
    private static final Runnable INIT_WATCHDOG = new Runnable() {
        @Override
        public void run() {
            if (ready || !initializing) return;
            AppDebugLog.d("Speaker", "init watchdog fired retries=" + initRetries
                    + " gen=" + generation + " q=" + queueSize()
                    + " engine=" + (currentEngine == null ? "" : currentEngine));
            if (initRetries >= MAX_INIT_RETRY) {
                initializing = false;
                AppDebugLog.d("Speaker", "init giveUp after max retry q=" + queueSize());
                giveUp();
                return;
            }
            initRetries++;
            createEngine(currentEngine);
        }
    };

    /** 播报超时看门狗：TTS/MediaPlayer 卡住时强制收尾并继续队列 */
    private static final Runnable SPEAK_WATCHDOG = new Runnable() {
        @Override
        public void run() {
            String id = currentUtteranceId;
            AppDebugLog.d("Speaker", "speak watchdog fired id=" + (id == null ? "" : id)
                    + " q=" + queueSize());
            currentUtteranceId = "";
            releaseMedia();
            if (tts != null) {
                try {
                    tts.stop();
                } catch (Throwable ignored) {
                }
            }
            afterUtterance();
            VolumeWatcher.markSpeaking(false);
            if (appCtx != null && !TextUtils.isEmpty(id)) {
                RecordStore.updateStatus(appCtx, id,
                        SpeakRecord.STATUS_SKIPPED, "播报超时（引擎可能被冻结）");
            }
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    drain();
                }
            });
        }
    };

    private static int queueSize() {
        synchronized (QUEUE) {
            return QUEUE.size();
        }
    }

    private static boolean isBusy() {
        if (mediaPlaying) return true;
        try {
            return tts != null && tts.isSpeaking();
        } catch (Throwable t) {
            AppDebugLog.d("Speaker", "isSpeaking threw " + t);
            return false;
        }
    }

    /** 引擎彻底不可用：清空队列并回写记录，消息不再无限滞留 */
    private static void giveUp() {
        List<Task> dropped;
        synchronized (QUEUE) {
            dropped = new ArrayList<>(QUEUE);
            QUEUE.clear();
        }
        if (appCtx == null) return;
        for (Task t : dropped) {
            if (!TextUtils.isEmpty(t.recordId)) {
                RecordStore.updateStatus(appCtx, t.recordId,
                        SpeakRecord.STATUS_SKIPPED, "TTS 引擎不可用");
            }
        }
    }

    private Speaker() {
    }

    /** 初始化（可重复调用，引擎设置变更时会自动重建） */
    public static void init(Context c, final InitCallback cb) {
        appCtx = c.getApplicationContext();
        final String want = Prefs.ttsEngine(appCtx);

        if (tts != null && ready && TextUtils.equals(want, currentEngine)) {
            if (cb != null) cb.onReady(true);
            return;
        }
        synchronized (PENDING) {
            if (cb != null) PENDING.add(cb);
        }
        // 已有初始化在途：只挂回调，不重复创建，onInit 后统一处理
        if (initializing) return;
        createEngine(want);
    }

    private static void createEngine(final String want) {
        shutdownTtsOnly();
        currentEngine = want;
        final int gen = ++generation;
        initializing = true;
        TextToSpeech.OnInitListener listener = new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (gen != generation) {
                    AppDebugLog.d("Speaker", "stale onInit ignored gen=" + gen
                            + " current=" + generation + " status=" + status);
                    return;
                }
                MAIN.removeCallbacks(INIT_WATCHDOG);
                initializing = false;
                ready = status == TextToSpeech.SUCCESS;
                AppDebugLog.d("Speaker", "onInit status=" + status + " ready=" + ready
                        + " retries=" + initRetries + " q=" + queueSize()
                        + " engine=" + (currentEngine == null ? "" : currentEngine));
                if (ready) {
                    initRetries = 0;
                    try {
                        tts.setLanguage(java.util.Locale.CHINA);
                    } catch (Throwable ignored) {
                    }
                    tts.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build());
                    tts.setOnUtteranceProgressListener(PROGRESS);
                }
                List<InitCallback> cbs;
                synchronized (PENDING) {
                    cbs = new ArrayList<>(PENDING);
                    PENDING.clear();
                }
                for (InitCallback b : cbs) {
                    try {
                        b.onReady(ready);
                    } catch (Throwable ignored) {
                    }
                }
                if (ready) {
                    drain();
                } else if (initRetries < MAX_INIT_RETRY) {
                    initRetries++;
                    MAIN.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!ready && !initializing) createEngine(Prefs.ttsEngine(appCtx));
                        }
                    }, 2000L);
                } else {
                    giveUp();
                }
            }
        };

        if (TextUtils.isEmpty(want)) {
            tts = new TextToSpeech(appCtx, listener);
        } else {
            tts = new TextToSpeech(appCtx, listener, want);
        }
        MAIN.postDelayed(INIT_WATCHDOG, INIT_TIMEOUT_MS);
    }

    private static final UtteranceProgressListener PROGRESS = new UtteranceProgressListener() {
        @Override
        public void onStart(String utteranceId) {
            AppDebugLog.d("Speaker", "onStart id=" + (utteranceId == null ? "" : utteranceId));
            VolumeWatcher.markSpeaking(true);
        }

        @Override
        public void onDone(String utteranceId) {
            AppDebugLog.d("Speaker", "onDone id=" + (utteranceId == null ? "" : utteranceId)
                    + " q=" + queueSize());
            cancelSpeakWatchdog();
            afterUtterance();
            VolumeWatcher.markSpeaking(false);
            if (appCtx != null && !TextUtils.isEmpty(utteranceId)) {
                RecordStore.updateStatus(appCtx, utteranceId, SpeakRecord.STATUS_SPOKEN, "");
            }
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    drain();
                }
            });
        }

        @Override
        public void onError(String utteranceId) {
            AppDebugLog.d("Speaker", "onError id=" + (utteranceId == null ? "" : utteranceId)
                    + " q=" + queueSize());
            cancelSpeakWatchdog();
            afterUtterance();
            VolumeWatcher.markSpeaking(false);
            if (appCtx != null && !TextUtils.isEmpty(utteranceId)) {
                RecordStore.updateStatus(appCtx, utteranceId, SpeakRecord.STATUS_SKIPPED, "TTS 合成失败");
            }
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    drain();
                }
            });
        }
    };

    /**
     * 加入播报队列
     *
     * @param recordId   关联的记录 id，可为空
     * @param notifyTime 通知真实时间（postTime），用于保鲜
     */
    public static void enqueue(Context c, String text, String recordId, long notifyTime) {
        if (TextUtils.isEmpty(text)) return;
        appCtx = c.getApplicationContext();
        List<Task> trimmed;
        int q;
        synchronized (QUEUE) {
            QUEUE.add(new Task(TextBuilder.clamp(text), recordId, null, notifyTime));
            trimmed = trimBurstLocked();
            q = QUEUE.size();
        }
        markBurstTrimmed(trimmed);
        AppDebugLog.d("Speaker", "enqueue textLen=" + text.length()
                + " q=" + q + " ready=" + ready + " ttsNull=" + (tts == null)
                + " initializing=" + initializing + " isBusy=" + isBusy()
                + " id=" + (recordId == null ? "" : recordId));
        kick();
    }

    /** 加入语音附件任务；播放失败时回退为 fallbackText 的 TTS */
    public static void enqueueAudio(Context c, Uri audioUri, String recordId,
                                    String fallbackText, long notifyTime) {
        if (audioUri == null) {
            enqueue(c, fallbackText, recordId, notifyTime);
            return;
        }
        appCtx = c.getApplicationContext();
        String fb = TextUtils.isEmpty(fallbackText) ? Prefs.voiceMsgPlaceholder(appCtx) : fallbackText;
        List<Task> trimmed;
        int q;
        synchronized (QUEUE) {
            QUEUE.add(new Task(TextBuilder.clamp(fb), recordId, audioUri, notifyTime));
            trimmed = trimBurstLocked();
            q = QUEUE.size();
        }
        markBurstTrimmed(trimmed);
        AppDebugLog.d("Speaker", "enqueueAudio uri=" + VoiceMsg.uriSummary(audioUri)
                + " fallbackLen=" + fb.length() + " q=" + q
                + " id=" + (recordId == null ? "" : recordId));
        // 预热 TTS，便于音频失败时立刻回退
        if (!ready || tts == null) init(appCtx, null);
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                drain();
            }
        });
    }

    /** 从队列移除尚未开播的任务（不打断正在播报的那条） */
    public static int cancelPending(Collection<String> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) return 0;
        Set<String> ids = new HashSet<>();
        for (String id : recordIds) {
            if (!TextUtils.isEmpty(id)) ids.add(id);
        }
        if (ids.isEmpty()) return 0;
        int removed = 0;
        synchronized (QUEUE) {
            Iterator<Task> it = QUEUE.iterator();
            while (it.hasNext()) {
                Task t = it.next();
                if (ids.contains(t.recordId)) {
                    it.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            AppDebugLog.d("Speaker", "cancelPending removed=" + removed + " q=" + queueSize());
        }
        return removed;
    }

    /** 持有 QUEUE 锁：超限时从队头丢掉较旧任务，保留最新 KEEP_QUEUE 条 */
    private static List<Task> trimBurstLocked() {
        List<Task> dropped = new ArrayList<>();
        if (QUEUE.size() <= MAX_QUEUE) return dropped;
        while (QUEUE.size() > KEEP_QUEUE) {
            Task t = QUEUE.pollFirst();
            if (t != null) dropped.add(t);
        }
        return dropped;
    }

    private static void markBurstTrimmed(List<Task> dropped) {
        if (dropped == null || dropped.isEmpty() || appCtx == null) return;
        AppDebugLog.d("Speaker", "burst trim dropped=" + dropped.size() + " q=" + queueSize());
        for (Task t : dropped) {
            if (!TextUtils.isEmpty(t.recordId)) {
                RecordStore.updateStatus(appCtx, t.recordId,
                        SpeakRecord.STATUS_SKIPPED, "突发积压，已放弃");
            }
        }
    }

    private static void kick() {
        if (!ready || tts == null) {
            init(appCtx, null);
        } else {
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    drain();
                }
            });
        }
    }

    /** 立即播报（清空队列，用于试听） */
    public static void speakNow(Context c, String text) {
        synchronized (QUEUE) {
            QUEUE.clear();
        }
        stop();
        enqueue(c, text, "", System.currentTimeMillis());
    }

    private static void drain() {
        if (isBusy()) {
            AppDebugLog.d("Speaker", "drain blocked busy mediaPlaying=" + mediaPlaying
                    + " q=" + queueSize());
            return;
        }

        long now = System.currentTimeMillis();
        Task t;
        synchronized (QUEUE) {
            t = QUEUE.poll();
        }
        // 双时间戳保鲜：通知过旧或入队过久都丢弃
        while (t != null) {
            long ageNotify = now - t.notifyTime;
            long ageQueue = now - t.time;
            if (ageNotify > MAX_PENDING_MS) {
                AppDebugLog.d("Speaker", "dropped stale notify ageMs=" + ageNotify
                        + " id=" + t.recordId + " q=" + queueSize());
                if (appCtx != null && !TextUtils.isEmpty(t.recordId)) {
                    RecordStore.updateStatus(appCtx, t.recordId,
                            SpeakRecord.STATUS_SKIPPED, "通知延迟超时，已放弃");
                }
            } else if (ageQueue > MAX_PENDING_MS) {
                AppDebugLog.d("Speaker", "dropped stale pending ageMs=" + ageQueue
                        + " id=" + t.recordId + " q=" + queueSize());
                if (appCtx != null && !TextUtils.isEmpty(t.recordId)) {
                    RecordStore.updateStatus(appCtx, t.recordId,
                            SpeakRecord.STATUS_SKIPPED, "播报延迟超时，已放弃");
                }
            } else {
                break;
            }
            synchronized (QUEUE) {
                t = QUEUE.poll();
            }
        }
        if (t == null) return;

        if (t.audioUri != null) {
            playAudio(t);
            return;
        }

        if (!ready || tts == null) {
            AppDebugLog.d("Speaker", "drain skipped not ready ready=" + ready
                    + " ttsNull=" + (tts == null) + " initializing=" + initializing
                    + " q=" + queueSize());
            // 放回队首，等 TTS ready
            synchronized (QUEUE) {
                QUEUE.addFirst(t);
            }
            if (!initializing) init(appCtx, null);
            return;
        }

        speakTts(t, now);
    }

    private static void speakTts(Task t, long now) {
        applyParams();
        acquireWakeLock();
        requestAudioFocus();

        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, Prefs.volume(appCtx));
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);

        String id = TextUtils.isEmpty(t.recordId)
                ? "u" + System.currentTimeMillis()
                : t.recordId;
        int r = tts.speak(t.text, TextToSpeech.QUEUE_ADD, params, id);
        AppDebugLog.d("Speaker", "tts.speak result=" + r + " textLen="
                + (t.text == null ? 0 : t.text.length()) + " ageMs=" + (now - t.time)
                + " notifyAgeMs=" + (now - t.notifyTime)
                + " q=" + queueSize() + " id=" + id);
        if (r != TextToSpeech.SUCCESS) {
            afterUtterance();
            if (appCtx != null && !TextUtils.isEmpty(t.recordId)) {
                RecordStore.updateStatus(appCtx, t.recordId,
                        SpeakRecord.STATUS_SKIPPED, "TTS 调用失败");
            }
            MAIN.postDelayed(new Runnable() {
                @Override
                public void run() {
                    drain();
                }
            }, 200);
        } else {
            int len = t.text == null ? 0 : t.text.length();
            long timeout = Math.min(60_000L, Math.max(15_000L, len * 400L));
            armSpeakWatchdog(id, timeout);
        }
    }

    private static void playAudio(final Task t) {
        AppDebugLog.d("Speaker", "audio try uri=" + VoiceMsg.uriSummary(t.audioUri)
                + " id=" + t.recordId);
        releaseMedia();
        acquireWakeLock();
        requestAudioFocus();
        VolumeWatcher.markSpeaking(true);
        String watchId = TextUtils.isEmpty(t.recordId)
                ? "a" + System.currentTimeMillis()
                : t.recordId;
        armSpeakWatchdog(watchId, AUDIO_WATCHDOG_MS);
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            mediaPlayer.setDataSource(appCtx, t.audioUri);
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    try {
                        mediaPlaying = true;
                        mp.start();
                        AppDebugLog.d("Speaker", "audio ok id=" + t.recordId);
                    } catch (Throwable e) {
                        AppDebugLog.d("Speaker", "audio start fail " + e);
                        fallbackAudioToTts(t);
                    }
                }
            });
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    AppDebugLog.d("Speaker", "audio done id=" + t.recordId);
                    cancelSpeakWatchdog();
                    releaseMedia();
                    afterUtterance();
                    VolumeWatcher.markSpeaking(false);
                    if (appCtx != null && !TextUtils.isEmpty(t.recordId)) {
                        RecordStore.updateStatus(appCtx, t.recordId,
                                SpeakRecord.STATUS_SPOKEN, "");
                    }
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            drain();
                        }
                    });
                }
            });
            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    AppDebugLog.d("Speaker", "audio fail what=" + what + " extra=" + extra
                            + " id=" + t.recordId);
                    fallbackAudioToTts(t);
                    return true;
                }
            });
            mediaPlayer.prepareAsync();
        } catch (Throwable e) {
            AppDebugLog.d("Speaker", "audio fail " + e);
            fallbackAudioToTts(t);
        }
    }

    private static void fallbackAudioToTts(Task t) {
        AppDebugLog.d("Speaker", "fallback placeholder id=" + t.recordId
                + " textLen=" + (t.text == null ? 0 : t.text.length()));
        cancelSpeakWatchdog();
        releaseMedia();
        afterUtterance();
        VolumeWatcher.markSpeaking(false);
        Task ttsTask = new Task(t.text, t.recordId, null, t.notifyTime);
        synchronized (QUEUE) {
            QUEUE.addFirst(ttsTask);
        }
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                drain();
            }
        });
    }

    private static void armSpeakWatchdog(String id, long timeoutMs) {
        cancelSpeakWatchdog();
        currentUtteranceId = id == null ? "" : id;
        MAIN.postDelayed(SPEAK_WATCHDOG, timeoutMs);
        AppDebugLog.d("Speaker", "speak watchdog armed ms=" + timeoutMs
                + " id=" + currentUtteranceId);
    }

    private static void cancelSpeakWatchdog() {
        MAIN.removeCallbacks(SPEAK_WATCHDOG);
        currentUtteranceId = "";
    }

    private static void releaseMedia() {
        mediaPlaying = false;
        if (mediaPlayer == null) return;
        try {
            mediaPlayer.setOnCompletionListener(null);
            mediaPlayer.setOnErrorListener(null);
            mediaPlayer.setOnPreparedListener(null);
        } catch (Throwable ignored) {
        }
        try {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
        } catch (Throwable ignored) {
        }
        try {
            mediaPlayer.release();
        } catch (Throwable ignored) {
        }
        mediaPlayer = null;
    }

    /** 息屏播报时持有唤醒锁，防止 CPU 休眠导致合成被推迟 */
    private static void acquireWakeLock() {
        if (appCtx == null) return;
        try {
            PowerManager pm = (PowerManager) appCtx.getSystemService(Context.POWER_SERVICE);
            if (pm == null || pm.isInteractive()) return;
            if (wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "msgnotify:tts");
                wakeLock.setReferenceCounted(false);
            }
            wakeLock.acquire(60_000L);
        } catch (Throwable ignored) {
        }
    }

    /** 请求短暂音频焦点，唤醒音频通路（duck 模式，不打断音乐） */
    private static void requestAudioFocus() {
        if (appCtx == null) return;
        try {
            AudioManager am = (AudioManager) appCtx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 单条播报结束后的统一清理 */
    private static void afterUtterance() {
        if (appCtx == null) return;
        try {
            AudioManager am = (AudioManager) appCtx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.abandonAudioFocus(null);
        } catch (Throwable ignored) {
        }
        releaseWakeLock();
    }

    private static void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Throwable ignored) {
        }
    }

    private static void applyParams() {
        if (tts == null || appCtx == null) return;
        try {
            tts.setPitch(clamp(Prefs.pitch(appCtx), 0.5f, 2.0f));
            tts.setSpeechRate(clamp(Prefs.rate(appCtx), 0.5f, 2.0f));
        } catch (Throwable ignored) {
        }
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    /** 打断当前播报并清空队列（音量减打断） */
    public static void stop() {
        cancelSpeakWatchdog();
        synchronized (QUEUE) {
            QUEUE.clear();
        }
        releaseMedia();
        if (tts != null) {
            try {
                tts.stop();
            } catch (Throwable ignored) {
            }
        }
        afterUtterance();
        VolumeWatcher.markSpeaking(false);
    }

    public static boolean isSpeaking() {
        if (mediaPlaying) return true;
        try {
            return tts != null && tts.isSpeaking();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void shutdownTtsOnly() {
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Throwable ignored) {
            }
        }
        tts = null;
        ready = false;
        MAIN.removeCallbacks(INIT_WATCHDOG);
        cancelSpeakWatchdog();
    }

    public static void shutdown() {
        releaseMedia();
        shutdownTtsOnly();
        releaseWakeLock();
    }

    /** 可用 TTS 引擎列表 */
    public static List<EngineInfo> engines(Context c) {
        List<EngineInfo> out = new ArrayList<>();
        out.add(new EngineInfo("", "跟随系统"));
        TextToSpeech probe = null;
        try {
            probe = new TextToSpeech(c.getApplicationContext(), null);
            for (TextToSpeech.EngineInfo e : probe.getEngines()) {
                out.add(new EngineInfo(e.name, e.label));
            }
        } catch (Throwable ignored) {
        } finally {
            if (probe != null) {
                try {
                    probe.shutdown();
                } catch (Throwable ignored) {
                }
            }
        }
        return out;
    }

    public static String engineLabel(Context c, String pkg) {
        if (TextUtils.isEmpty(pkg)) return "跟随系统";
        for (EngineInfo e : engines(c)) {
            if (pkg.equals(e.pkg)) return e.label;
        }
        return pkg;
    }

    public static class EngineInfo {
        public final String pkg;
        public final String label;

        public EngineInfo(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }
    }
}
