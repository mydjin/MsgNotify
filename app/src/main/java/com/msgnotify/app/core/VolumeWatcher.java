package com.msgnotify.app.core;

import android.content.Context;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

/**
 * 「音量减打断播报」实现
 * 监听系统设置变化，比对媒体音量；播报过程中音量变小即判定为用户按下音量减，立即打断。
 */
public final class VolumeWatcher {

    private static ContentObserver observer;
    private static int lastVolume = -1;
    private static volatile boolean speaking = false;
    private static Context appCtx;

    private VolumeWatcher() {
    }

    static void markSpeaking(boolean v) {
        speaking = v;
        if (v && appCtx != null) {
            lastVolume = musicVolume(appCtx);
        }
    }

    private static int musicVolume(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        return am == null ? -1 : am.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    public static void start(Context c) {
        if (observer != null) return;
        appCtx = c.getApplicationContext();
        lastVolume = musicVolume(appCtx);

        observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                super.onChange(selfChange, uri);
                int now = musicVolume(appCtx);
                int prev = lastVolume;
                lastVolume = now;
                if (!Prefs.volumeDownInterrupt(appCtx)) return;
                if (!speaking) return;
                if (prev >= 0 && now >= 0 && now < prev) {
                    Speaker.stop();
                }
            }
        };
        try {
            appCtx.getContentResolver().registerContentObserver(
                    Settings.System.CONTENT_URI, true, observer);
        } catch (Throwable ignored) {
            observer = null;
        }
    }

    public static void stop(Context c) {
        if (observer == null) return;
        try {
            c.getApplicationContext().getContentResolver().unregisterContentObserver(observer);
        } catch (Throwable ignored) {
        }
        observer = null;
    }
}
