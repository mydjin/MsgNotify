package com.msgnotify.app.ui;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
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
 * 播报设置：耳机模式、屏幕常亮、音量减打断、常驻保活、来电短信、免打扰时段、关键词入口
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
    protected void onResume() {
        super.onResume();
        refresh();
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
