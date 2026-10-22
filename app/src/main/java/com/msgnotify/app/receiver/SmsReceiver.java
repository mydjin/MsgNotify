package com.msgnotify.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsMessage;
import android.text.TextUtils;

import com.msgnotify.app.core.Prefs;
import com.msgnotify.app.core.SpeakGate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 短信播报（长短信自动拼接）
 */
public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Prefs.getBool(context, Prefs.SMS_ENABLED, false)) return;
        if (intent == null) return;
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;

        SmsMessage[] msgs = android.provider.Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (msgs == null || msgs.length == 0) return;

        // 同一发送方的分片合并
        Map<String, StringBuilder> merged = new LinkedHashMap<>();
        for (SmsMessage m : msgs) {
            if (m == null) continue;
            String from = m.getDisplayOriginatingAddress();
            if (from == null) from = "未知号码";
            StringBuilder sb = merged.get(from);
            if (sb == null) {
                sb = new StringBuilder();
                merged.put(from, sb);
            }
            String body = m.getMessageBody();
            if (!TextUtils.isEmpty(body)) sb.append(body);
        }

        for (Map.Entry<String, StringBuilder> e : merged.entrySet()) {
            String body = e.getValue().toString().replaceAll("\\s+", " ").trim();
            if (TextUtils.isEmpty(body)) continue;
            SpeakGate.handle(context, "android.sms", "短信", e.getKey(), body,
                    null, false, System.currentTimeMillis());
        }
    }
}
