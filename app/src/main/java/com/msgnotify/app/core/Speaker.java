package com.msgnotify.app.core;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * TTS 播报引擎（进程内单例）
 * - 串行队列，避免并发朗读互相打断
 * - 支持音量 / 音调 / 语速 / 引擎切换
 * - 播报完成后回写记录状态
 */
public final class Speaker {

    public interface InitCallback {
        void onReady(boolean ok);
    }

    private static final class Task {
        final String text;
        final String recordId;
        final long time = System.currentTimeMillis();

        Task(String text, String recordId) {
            this.text = text;
            this.recordId = recordId;
        }
    }

    private static TextToSpeech tts;
    private static boolean ready;
    private static String currentEngine = "";
    private static final ArrayDeque<Task> QUEUE = new ArrayDeque<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Context appCtx;

    /** 消息在队列中的最长等待时间，超过即放弃（避免引擎恢复后突然补播旧通知） */
    private static final long MAX_PENDING_MS = 120_000L;
    /** 引擎初始化超时：超过视为引擎进程被冻结，重建 */
    private static final long INIT_TIMEOUT_MS = 10_000L;
    /** 引擎初始化失败最大重试次数 */
    private static final int MAX_INIT_RETRY = 3;

    private static volatile boolean initializing;
    private static int initRetries;
    private static int generation;
    private static final List<InitCallback> PENDING = new ArrayList<>();
    private static PowerManager.WakeLock wakeLock;

    /** 初始化看门狗：引擎进程被系统冻结时 onInit 长时间不回调，超时重建 */
    private static final Runnable INIT_WATCHDOG = new Runnable() {
        @Override
        public void run() {
            if (ready || !initializing) return;
            if (initRetries >= MAX_INIT_RETRY) {
                initializing = false;
                giveUp();
                return;
            }
            initRetries++;
            createEngine(currentEngine);
        }
    };

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
        shutdown();
        currentEngine = want;
        final int gen = ++generation;
        initializing = true;
        TextToSpeech.OnInitListener listener = new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (gen != generation) return;
                MAIN.removeCallbacks(INIT_WATCHDOG);
                initializing = false;
                ready = status == TextToSpeech.SUCCESS;
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
            VolumeWatcher.markSpeaking(true);
        }

        @Override
        public void onDone(String utteranceId) {
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
     * @param recordId 关联的记录 id，可为空
     */
    public static void enqueue(Context c, String text, String recordId) {
        if (TextUtils.isEmpty(text)) return;
        appCtx = c.getApplicationContext();
        synchronized (QUEUE) {
            QUEUE.add(new Task(TextBuilder.clamp(text), recordId == null ? "" : recordId));
        }
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
        enqueue(c, text, "");
    }

    private static void drain() {
        if (!ready || tts == null) return;
        if (tts.isSpeaking()) return;

        long now = System.currentTimeMillis();
        Task t;
        synchronized (QUEUE) {
            t = QUEUE.poll();
        }
        // 滞留超时的消息直接放弃，避免引擎恢复后突然补播旧通知
        while (t != null && now - t.time > MAX_PENDING_MS) {
            if (appCtx != null && !TextUtils.isEmpty(t.recordId)) {
                RecordStore.updateStatus(appCtx, t.recordId,
                        SpeakRecord.STATUS_SKIPPED, "播报延迟超时，已放弃");
            }
            synchronized (QUEUE) {
                t = QUEUE.poll();
            }
        }
        if (t == null) return;

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
        }
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
        synchronized (QUEUE) {
            QUEUE.clear();
        }
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
        try {
            return tts != null && tts.isSpeaking();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void shutdown() {
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
