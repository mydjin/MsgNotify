package com.msgnotify.app.ui;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import com.msgnotify.app.R;
import com.msgnotify.app.core.Conditions;
import com.msgnotify.app.core.Prefs;
import com.msgnotify.app.service.KeepAliveService;

/**
 * 播报设置：耳机模式、屏幕常亮、音量减打断、常驻保活、语音消息、长度播报、同人合并、来电短信、免打扰时段、关键词入口
 */
public class SpeakSettingsActivity extends BaseActivity {

    private static final int REQ_CALL = 201;
    private static final int REQ_SMS = 202;

    private TextView tvHeadsetState;
    private TextView tvDndStart;
    private TextView tvDndEnd;
    private TextView tvKeywordDesc;

    private SwitchCompat swCall;
    private SwitchCompat swSms;
    private SwitchCompat swLenLimit;
    private View boxLenLimitFields;
    private EditText etLenLimitChars;
    private EditText etLenLimitMsg;
    private SwitchCompat swVoiceMsg;
    private View boxVoiceMsgFields;
    private EditText etVoiceMsgPlaceholder;
    private SwitchCompat swBurstMerge;
    private View boxBurstMergeFields;
    private EditText etBurstMergeWindow;
    private EditText etBurstMergeCount;
    private EditText etBurstMergeSuffix;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speak_settings);
        setupTitle("播报设置");

        tvHeadsetState = findViewById(R.id.tvHeadsetState);
        tvDndStart = findViewById(R.id.tvDndStart);
        tvDndEnd = findViewById(R.id.tvDndEnd);
        tvKeywordDesc = findViewById(R.id.tvKeywordDesc);

        bind((SwitchCompat) findViewById(R.id.swHeadset), Prefs.HEADSET_ONLY, false);
        bind((SwitchCompat) findViewById(R.id.swKeepScreen), Prefs.KEEP_SCREEN_ON, false);
        bind((SwitchCompat) findViewById(R.id.swVolumeDown), Prefs.VOLUME_DOWN_INTERRUPT, true);

        final SwitchCompat swForeground = findViewById(R.id.swForeground);
        swForeground.setChecked(Prefs.foregroundEnabled(this));
        swForeground.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean checked) {
                Prefs.setBool(SpeakSettingsActivity.this, Prefs.FOREGROUND_ENABLED, checked);
                KeepAliveService.refresh(SpeakSettingsActivity.this);
                if (!checked) {
                    Toast.makeText(SpeakSettingsActivity.this,
                            "已关闭常驻通知，息屏后可能被系统回收", Toast.LENGTH_LONG).show();
                }
            }
        });

        swVoiceMsg = findViewById(R.id.swVoiceMsg);
        boxVoiceMsgFields = findViewById(R.id.boxVoiceMsgFields);
        etVoiceMsgPlaceholder = findViewById(R.id.etVoiceMsgPlaceholder);
        swVoiceMsg.setChecked(Prefs.voiceMsgEnabled(this));
        etVoiceMsgPlaceholder.setText(Prefs.voiceMsgPlaceholder(this));
        applyVoiceMsgEnabled(swVoiceMsg.isChecked());
        swVoiceMsg.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean checked) {
                Prefs.setBool(SpeakSettingsActivity.this, Prefs.VOICE_MSG_ENABLED, checked);
                applyVoiceMsgEnabled(checked);
            }
        });

        swLenLimit = findViewById(R.id.swLenLimit);
        boxLenLimitFields = findViewById(R.id.boxLenLimitFields);
        etLenLimitChars = findViewById(R.id.etLenLimitChars);
        etLenLimitMsg = findViewById(R.id.etLenLimitMsg);
        swLenLimit.setChecked(Prefs.lenLimitEnabled(this));
        etLenLimitChars.setText(String.valueOf(Prefs.lenLimitChars(this)));
        etLenLimitMsg.setText(Prefs.lenLimitMsg(this));
        applyLenLimitEnabled(swLenLimit.isChecked());
        swLenLimit.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean checked) {
                Prefs.setBool(SpeakSettingsActivity.this, Prefs.LEN_LIMIT_ENABLED, checked);
                applyLenLimitEnabled(checked);
            }
        });

        swBurstMerge = findViewById(R.id.swBurstMerge);
        boxBurstMergeFields = findViewById(R.id.boxBurstMergeFields);
        etBurstMergeWindow = findViewById(R.id.etBurstMergeWindow);
        etBurstMergeCount = findViewById(R.id.etBurstMergeCount);
        etBurstMergeSuffix = findViewById(R.id.etBurstMergeSuffix);
        swBurstMerge.setChecked(Prefs.burstMergeEnabled(this));
        etBurstMergeWindow.setText(String.valueOf(Prefs.burstMergeWindowSec(this)));
        etBurstMergeCount.setText(String.valueOf(Prefs.burstMergeCount(this)));
        etBurstMergeSuffix.setText(Prefs.burstMergeSuffix(this));
        applyBurstMergeEnabled(swBurstMerge.isChecked());
        swBurstMerge.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean checked) {
                Prefs.setBool(SpeakSettingsActivity.this, Prefs.BURST_MERGE_ENABLED, checked);
                applyBurstMergeEnabled(checked);
            }
        });

        swCall = findViewById(R.id.swCall);
        swCall.setChecked(Prefs.getBool(this, Prefs.CALL_ENABLED, false));
        swCall.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean checked) {
                if (checked && !hasPermission(Manifest.permission.READ_PHONE_STATE)) {
                    requestPermissions(new String[]{
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.READ_CONTACTS}, REQ_CALL);
                    return;
                }
                Prefs.setBool(SpeakSettingsActivity.this, Prefs.CALL_ENABLED, checked);
            }
        });

        swSms = findViewById(R.id.swSms);
        swSms.setChecked(Prefs.getBool(this, Prefs.SMS_ENABLED, false));
        swSms.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean checked) {
                if (checked && !hasPermission(Manifest.permission.RECEIVE_SMS)) {
                    requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, REQ_SMS);
                    return;
                }
                Prefs.setBool(SpeakSettingsActivity.this, Prefs.SMS_ENABLED, checked);
            }
        });

        bind((SwitchCompat) findViewById(R.id.swDnd), Prefs.DND_ENABLED, false);

        findViewById(R.id.rowDndStart).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickTime(Prefs.DND_START, 23 * 60);
            }
        });
        findViewById(R.id.rowDndEnd).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickTime(Prefs.DND_END, 7 * 60);
            }
        });

        findViewById(R.id.rowKeyword).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SpeakSettingsActivity.this, KeywordActivity.class));
            }
        });
    }

    @Override
    protected void onPause() {
        saveVoiceMsgFields();
        saveLenLimitFields();
        saveBurstMergeFields();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void applyVoiceMsgEnabled(boolean enabled) {
        boxVoiceMsgFields.setAlpha(enabled ? 1f : 0.45f);
        etVoiceMsgPlaceholder.setEnabled(true);
    }

    private void applyLenLimitEnabled(boolean enabled) {
        float alpha = enabled ? 1f : 0.45f;
        boxLenLimitFields.setAlpha(alpha);
        etLenLimitChars.setEnabled(true);
        etLenLimitMsg.setEnabled(true);
    }

    private void applyBurstMergeEnabled(boolean enabled) {
        float alpha = enabled ? 1f : 0.45f;
        boxBurstMergeFields.setAlpha(alpha);
        etBurstMergeWindow.setEnabled(true);
        etBurstMergeCount.setEnabled(true);
        etBurstMergeSuffix.setEnabled(true);
    }

    private void saveVoiceMsgFields() {
        String msg = etVoiceMsgPlaceholder.getText() == null
                ? "" : etVoiceMsgPlaceholder.getText().toString().trim();
        if (TextUtils.isEmpty(msg)) msg = Prefs.VOICE_MSG_PLACEHOLDER_DEFAULT;
        Prefs.setStr(this, Prefs.VOICE_MSG_PLACEHOLDER, msg);
        etVoiceMsgPlaceholder.setText(msg);
    }

    private void saveLenLimitFields() {
        String charsRaw = etLenLimitChars.getText() == null
                ? "" : etLenLimitChars.getText().toString().trim();
        int chars = Prefs.LEN_LIMIT_CHARS_DEFAULT;
        if (!TextUtils.isEmpty(charsRaw)) {
            try {
                int v = Integer.parseInt(charsRaw);
                if (v >= 1) chars = v;
            } catch (NumberFormatException ignored) {
            }
        }
        Prefs.setInt(this, Prefs.LEN_LIMIT_CHARS, chars);
        etLenLimitChars.setText(String.valueOf(chars));

        String msg = etLenLimitMsg.getText() == null
                ? "" : etLenLimitMsg.getText().toString().trim();
        if (TextUtils.isEmpty(msg)) msg = Prefs.LEN_LIMIT_MSG_DEFAULT;
        Prefs.setStr(this, Prefs.LEN_LIMIT_MSG, msg);
        etLenLimitMsg.setText(msg);
    }

    private void saveBurstMergeFields() {
        String winRaw = etBurstMergeWindow.getText() == null
                ? "" : etBurstMergeWindow.getText().toString().trim();
        int win = Prefs.BURST_MERGE_WINDOW_SEC_DEFAULT;
        if (!TextUtils.isEmpty(winRaw)) {
            try {
                int v = Integer.parseInt(winRaw);
                if (v >= 1) win = v;
            } catch (NumberFormatException ignored) {
            }
        }
        Prefs.setInt(this, Prefs.BURST_MERGE_WINDOW_SEC, win);
        etBurstMergeWindow.setText(String.valueOf(win));

        String countRaw = etBurstMergeCount.getText() == null
                ? "" : etBurstMergeCount.getText().toString().trim();
        int count = Prefs.BURST_MERGE_COUNT_DEFAULT;
        if (!TextUtils.isEmpty(countRaw)) {
            try {
                int v = Integer.parseInt(countRaw);
                if (v >= 1) count = v;
            } catch (NumberFormatException ignored) {
            }
        }
        Prefs.setInt(this, Prefs.BURST_MERGE_COUNT, count);
        etBurstMergeCount.setText(String.valueOf(count));

        String suffix = etBurstMergeSuffix.getText() == null
                ? "" : etBurstMergeSuffix.getText().toString().trim();
        if (TextUtils.isEmpty(suffix)) suffix = Prefs.BURST_MERGE_SUFFIX_DEFAULT;
        Prefs.setStr(this, Prefs.BURST_MERGE_SUFFIX, suffix);
        etBurstMergeSuffix.setText(suffix);
    }

    private void bind(final SwitchCompat sw, final String key, boolean def) {
        sw.setChecked(Prefs.getBool(this, key, def));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean checked) {
                Prefs.setBool(SpeakSettingsActivity.this, key, checked);
            }
        });
    }

    private boolean hasPermission(String p) {
        return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED;
    }

    private void pickTime(final String key, int def) {
        int cur = Prefs.getInt(this, key, def);
        new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                Prefs.setInt(SpeakSettingsActivity.this, key, hourOfDay * 60 + minute);
                refresh();
            }
        }, cur / 60, cur % 60, true).show();
    }

    private void refresh() {
        tvHeadsetState.setText(Conditions.headsetConnected(this) ? "已连接" : "未连接");
        tvDndStart.setText(Conditions.formatMinutes(Prefs.getInt(this, Prefs.DND_START, 23 * 60)));
        tvDndEnd.setText(Conditions.formatMinutes(Prefs.getInt(this, Prefs.DND_END, 7 * 60)));

        boolean block = Prefs.getBool(this, Prefs.BLOCK_KEYWORDS_ENABLED, false);
        boolean only = Prefs.getBool(this, Prefs.ONLY_KEYWORDS_ENABLED, false);
        int nb = Conditions.split(Prefs.getStr(this, Prefs.BLOCK_KEYWORDS, "")).length;
        int no = Conditions.split(Prefs.getStr(this, Prefs.ONLY_KEYWORDS, "")).length;
        StringBuilder sb = new StringBuilder();
        if (block && nb > 0) sb.append("屏蔽 ").append(nb).append(" 个");
        if (only && no > 0) {
            if (sb.length() > 0) sb.append("，");
            sb.append("只读 ").append(no).append(" 个");
        }
        tvKeywordDesc.setText(sb.length() == 0 ? "未设置" : sb.toString());

        swCall.setChecked(Prefs.getBool(this, Prefs.CALL_ENABLED, false));
        swSms.setChecked(Prefs.getBool(this, Prefs.SMS_ENABLED, false));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQ_CALL) {
            Prefs.setBool(this, Prefs.CALL_ENABLED, granted);
            if (!granted) Toast.makeText(this, "未授予电话权限，来电播报无法开启", Toast.LENGTH_LONG).show();
        } else if (requestCode == REQ_SMS) {
            Prefs.setBool(this, Prefs.SMS_ENABLED, granted);
            if (!granted) Toast.makeText(this, "未授予短信权限，短信播报无法开启", Toast.LENGTH_LONG).show();
        }
        refresh();
    }
}
