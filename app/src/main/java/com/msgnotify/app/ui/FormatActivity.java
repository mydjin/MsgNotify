package com.msgnotify.app.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

import com.msgnotify.app.R;
import com.msgnotify.app.core.Prefs;
import com.msgnotify.app.core.Speaker;
import com.msgnotify.app.core.TextBuilder;

import android.widget.CheckBox;

/**
 * 播报格式设置：应用名称 / 通知标题 / 通知内容 三段开关与追加文案
 */
public class FormatActivity extends BaseActivity {

    private TextView tvPreview;

    private SwitchCompat swApp, swTitle, swContent;
    private CheckBox cbApp, cbTitle, cbContent;
    private EditText etApp, etTitle, etContent;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_format);
        setupTitle("播报格式设置");

        tvPreview = findViewById(R.id.tvPreview);

        swApp = findViewById(R.id.swApp);
        swTitle = findViewById(R.id.swTitle);
        swContent = findViewById(R.id.swContent);
        cbApp = findViewById(R.id.cbApp);
        cbTitle = findViewById(R.id.cbTitle);
        cbContent = findViewById(R.id.cbContent);
        etApp = findViewById(R.id.etApp);
        etTitle = findViewById(R.id.etTitle);
        etContent = findViewById(R.id.etContent);

        swApp.setChecked(Prefs.fmtApp(this));
        swTitle.setChecked(Prefs.fmtTitle(this));
        swContent.setChecked(Prefs.fmtContent(this));
        cbApp.setChecked(Prefs.fmtAppSuffixOn(this));
        cbTitle.setChecked(Prefs.fmtTitleSuffixOn(this));
        cbContent.setChecked(Prefs.fmtContentSuffixOn(this));
        etApp.setText(Prefs.fmtAppSuffix(this));
        etTitle.setText(Prefs.fmtTitleSuffix(this));
        etContent.setText(Prefs.fmtContentSuffix(this));

        bindSwitch(swApp, Prefs.FMT_APP);
        bindSwitch(swTitle, Prefs.FMT_TITLE);
        bindSwitch(swContent, Prefs.FMT_CONTENT);
        bindSwitch(cbApp, Prefs.FMT_APP_SUFFIX_ON);
        bindSwitch(cbTitle, Prefs.FMT_TITLE_SUFFIX_ON);
        bindSwitch(cbContent, Prefs.FMT_CONTENT_SUFFIX_ON);
        bindText(etApp, Prefs.FMT_APP_SUFFIX);
        bindText(etTitle, Prefs.FMT_TITLE_SUFFIX);
        bindText(etContent, Prefs.FMT_CONTENT_SUFFIX);

        findViewById(R.id.btnTry).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Speaker.speakNow(FormatActivity.this, TextBuilder.preview(FormatActivity.this));
            }
        });

        refreshPreview();
    }

    private void bindSwitch(CompoundButton btn, final String key) {
        btn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setBool(FormatActivity.this, key, isChecked);
                refreshPreview();
            }
        });
    }

    private void bindText(EditText et, final String key) {
        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                Prefs.setStr(FormatActivity.this, key, s.toString());
                refreshPreview();
            }
        });
    }

    private void refreshPreview() {
        tvPreview.setText(TextBuilder.preview(this));
    }
}
