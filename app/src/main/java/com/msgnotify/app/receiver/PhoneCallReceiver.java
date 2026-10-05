package com.msgnotify.app.receiver;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.msgnotify.app.core.Prefs;
import com.msgnotify.app.core.SpeakGate;

/**
 * 来电播报
 * Android 9+ 已无法从广播中拿到号码（除非有 READ_CALL_LOG），拿不到就降级播报「有新来电」
 */
public class PhoneCallReceiver extends BroadcastReceiver {

    private static String lastState = "";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Prefs.getBool(context, Prefs.CALL_ENABLED, false)) return;
        if (intent == null) return;

        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (state == null) return;
        if (state.equals(lastState)) return;
        lastState = state;

        if (!TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            return;
        }

        String number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
        String who = displayName(context, number);
        SpeakGate.handle(context, "android.phone", "电话",
                TextUtils.isEmpty(who) ? "" : who,
                TextUtils.isEmpty(who) ? "有新来电" : "来电话了", false);
    }

    private String displayName(Context c, String number) {
        if (TextUtils.isEmpty(number)) return "";
        if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            return number;
        }
        Cursor cur = null;
        try {
            Uri uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            cur = c.getContentResolver().query(uri,
                    new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},
                    null, null, null);
            if (cur != null && cur.moveToFirst()) {
                String name = cur.getString(0);
                if (!TextUtils.isEmpty(name)) return name;
            }
        } catch (Throwable ignored) {
        } finally {
            if (cur != null) {
                try {
                    cur.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return number;
    }
}
