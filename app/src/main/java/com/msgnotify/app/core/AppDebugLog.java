package com.msgnotify.app.core;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * 应用内调试日志：内存环形缓冲 + filesDir 持久化，供调试日志页展示。
 * 不记录通知正文全文，避免隐私与刷屏。
 */
public final class AppDebugLog {

    private static final String TAG = "MsgNotifyDebug";
    private static final String FILE_NAME = "app-debug.log";
    private static final int MAX_LINES = 500;
    private static final long MAX_BYTES = 200 * 1024L;

    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static final Object LOCK = new Object();
    private static final SimpleDateFormat TIME =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA);

    private static volatile Context appCtx;
    private static boolean loaded;

    private AppDebugLog() {
    }

    public static void bind(Context c) {
        if (c == null) return;
        appCtx = c.getApplicationContext();
        ensureLoaded();
    }

    public static void d(String tag, String msg) {
        String t = tag == null ? "-" : tag;
        String m = msg == null ? "" : msg;
        String line;
        synchronized (LOCK) {
            line = TIME.format(new Date()) + " [" + t + "] " + m;
            LINES.addLast(line);
            while (LINES.size() > MAX_LINES) LINES.removeFirst();
            persistLocked();
        }
        Log.d(TAG, "[" + t + "] " + m);
    }

    public static String readAll() {
        ensureLoaded();
        synchronized (LOCK) {
            if (LINES.isEmpty()) return "";
            StringBuilder sb = new StringBuilder(LINES.size() * 64);
            for (String s : LINES) {
                sb.append(s).append('\n');
            }
            return sb.toString();
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            LINES.clear();
            Context c = appCtx;
            if (c != null) {
                try {
                    File f = new File(c.getFilesDir(), FILE_NAME);
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void ensureLoaded() {
        if (loaded) return;
        synchronized (LOCK) {
            if (loaded) return;
            Context c = appCtx;
            if (c == null) {
                loaded = true;
                return;
            }
            File f = new File(c.getFilesDir(), FILE_NAME);
            if (f.exists()) {
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(
                            new FileInputStream(f), StandardCharsets.UTF_8));
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.isEmpty()) continue;
                        LINES.addLast(line);
                        while (LINES.size() > MAX_LINES) LINES.removeFirst();
                    }
                    br.close();
                } catch (Throwable ignored) {
                }
            }
            loaded = true;
        }
    }

    private static void persistLocked() {
        Context c = appCtx;
        if (c == null) return;
        try {
            File f = new File(c.getFilesDir(), FILE_NAME);
            FileOutputStream fos = new FileOutputStream(f, false);
            long bytes = 0;
            for (String s : LINES) {
                byte[] b = (s + "\n").getBytes(StandardCharsets.UTF_8);
                bytes += b.length;
                if (bytes > MAX_BYTES) {
                    // 超限时丢掉更早的行：重写为尾部内容
                    fos.close();
                    trimAndRewrite(f);
                    return;
                }
                fos.write(b);
            }
            fos.close();
        } catch (Throwable ignored) {
        }
    }

    private static void trimAndRewrite(File f) {
        try {
            while (!LINES.isEmpty()) {
                long est = 0;
                for (String s : LINES) est += s.length() + 1;
                if (est <= MAX_BYTES && LINES.size() <= MAX_LINES) break;
                LINES.removeFirst();
            }
            FileOutputStream fos = new FileOutputStream(f, false);
            for (String s : LINES) {
                fos.write((s + "\n").getBytes(StandardCharsets.UTF_8));
            }
            fos.close();
        } catch (Throwable ignored) {
        }
    }
}
