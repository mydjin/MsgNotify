package com.msgnotify.app.core;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
        shutdown();

        currentEngine = want;
        TextToSpeech.OnInitListener listener = new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                ready = status == TextToSpeech.SUCCESS;
                if (ready) {
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
                if (cb != null) cb.onReady(ready);
                if (ready) drain();
            }
        };

        if (TextUtils.isEmpty(want)) {
            tts = new TextToSpeech(appCtx, listener);
        } else {
            tts = new TextToSpeech(appCtx, listener, want);
        }
    }

    private static final UtteranceProgressListener PROGRESS = new UtteranceProgressListener() {
        @Override
        public void onStart(String utteranceId) {
            VolumeWatcher.markSpeaking(true);
        }

        @Override
        public void onDone(String utteranceId) {
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

        Task t;
        synchronized (QUEUE) {
            t = QUEUE.poll();
        }
        if (t == null) return;

        applyParams();

        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, Prefs.volume(appCtx));
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);

        String id = TextUtils.isEmpty(t.recordId)
                ? "u" + System.currentTimeMillis()
                : t.recordId;
        int r = tts.speak(t.text, TextToSpeech.QUEUE_ADD, params, id);
        if (r != TextToSpeech.SUCCESS) {
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
