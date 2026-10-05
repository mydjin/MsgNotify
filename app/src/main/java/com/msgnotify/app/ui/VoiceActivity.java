package com.msgnotify.app.ui;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.msgnotify.app.R;
import com.msgnotify.app.core.Prefs;
import com.msgnotify.app.core.Speaker;
import com.msgnotify.app.util.SysNav;

import java.util.List;
import java.util.Locale;

/**
 * 音频音色设置：引擎切换 + 音量 / 音调 / 语速
 */
public class VoiceActivity extends BaseActivity {

    private TextView tvEngine, tvVolume, tvPitch, tvRate;
    private SeekBar sbVolume, sbPitch, sbRate;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice);
        setupTitle("音频音色设置");

        tvEngine = findViewById(R.id.tvEngine);
        tvVolume = findViewById(R.id.tvVolume);
        tvPitch = findViewById(R.id.tvPitch);
        tvRate = findViewById(R.id.tvRate);
        sbVolume = findViewById(R.id.sbVolume);
        sbPitch = findViewById(R.id.sbPitch);
        sbRate = findViewById(R.id.sbRate);

        tvEngine.setText(Speaker.engineLabel(this, Prefs.ttsEngine(this)));

        sbVolume.setProgress(Math.round(Prefs.volume(this) * 100));
        sbPitch.setProgress(Math.round((Prefs.pitch(this) - 0.5f) * 100));
        sbRate.setProgress(Math.round((Prefs.rate(this) - 0.5f) * 100));
        syncLabels();

        sbVolume.setOnSeekBarChangeListener(new Listener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Prefs.setFloat(VoiceActivity.this, Prefs.VOLUME, volumeValue());
            }
        });
        sbPitch.setOnSeekBarChangeListener(new Listener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Prefs.setFloat(VoiceActivity.this, Prefs.PITCH, pitchValue());
            }
        });
        sbRate.setOnSeekBarChangeListener(new Listener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Prefs.setFloat(VoiceActivity.this, Prefs.RATE, rateValue());
            }
        });

        findViewById(R.id.btnEngine).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickEngine();
            }
        });

        findViewById(R.id.rowTtsSettings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SysNav.openTtsSettings(VoiceActivity.this);
            }
        });

        findViewById(R.id.btnTry).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Speaker.speakNow(VoiceActivity.this, "微信消息，小华说，你明天有空吗？");
            }
        });

        findViewById(R.id.btnReset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setFloat(VoiceActivity.this, Prefs.VOLUME, 1.0f);
                Prefs.setFloat(VoiceActivity.this, Prefs.PITCH, 1.0f);
                Prefs.setFloat(VoiceActivity.this, Prefs.RATE, 1.0f);
                sbVolume.setProgress(100);
                sbPitch.setProgress(50);
                sbRate.setProgress(50);
                syncLabels();
                Toast.makeText(VoiceActivity.this, "已恢复默认", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private float volumeValue() {
        float v = sbVolume.getProgress() / 100f;
        return v < 0.1f ? 0.1f : v;
    }

    private float pitchValue() {
        return 0.5f + sbPitch.getProgress() / 100f;
    }

    private float rateValue() {
        return 0.5f + sbRate.getProgress() / 100f;
    }

    private void syncLabels() {
        tvVolume.setText(String.format(Locale.getDefault(), "%.1fX", volumeValue()));
        tvPitch.setText(String.format(Locale.getDefault(), "%.1fX", pitchValue()));
        tvRate.setText(String.format(Locale.getDefault(), "%.1fX", rateValue()));
    }

    private void pickEngine() {
        final List<Speaker.EngineInfo> list = Speaker.engines(this);
        String[] labels = new String[list.size()];
        int checked = 0;
        String cur = Prefs.ttsEngine(this);
        for (int i = 0; i < list.size(); i++) {
            labels[i] = list.get(i).label;
            if (cur.equals(list.get(i).pkg)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("选择语音引擎")
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Speaker.EngineInfo e = list.get(which);
                        Prefs.setStr(VoiceActivity.this, Prefs.TTS_ENGINE, e.pkg);
                        tvEngine.setText(e.label);
                        Speaker.shutdown();
                        Speaker.init(VoiceActivity.this, null);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 拖动过程中只刷新文案，松手才落盘 */
    private abstract class Listener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            syncLabels();
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }
    }
}
