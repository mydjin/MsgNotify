package com.msgnotify.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

import com.msgnotify.app.R;
import com.msgnotify.app.core.Conditions;
import com.msgnotify.app.core.Prefs;

/**
 * 关键词设置：屏蔽关键字（黑名单）与只读关键字（白名单）
 */
public class KeywordActivity extends BaseActivity {

    private EditText etBlock;
    private EditText etOnly;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_keyword);
        setupTitle("关键词设置");

        etBlock = findViewById(R.id.etBlock);
        etOnly = findViewById(R.id.etOnly);
        etBlock.setText(Prefs.getStr(this, Prefs.BLOCK_KEYWORDS, ""));
        etOnly.setText(Prefs.getStr(this, Prefs.ONLY_KEYWORDS, ""));

        bind((SwitchCompat) findViewById(R.id.swBlock), Prefs.BLOCK_KEYWORDS_ENABLED);
        bind((SwitchCompat) findViewById(R.id.swOnly), Prefs.ONLY_KEYWORDS_ENABLED);

        findViewById(R.id.btnSave).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
                Toast.makeText(KeywordActivity.this, "已保存", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bind(final SwitchCompat sw, final String key) {
        sw.setChecked(Prefs.getBool(this, key, false));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean checked) {
                Prefs.setBool(KeywordActivity.this, key, checked);
            }
        });
    }

    private void save() {
        Prefs.setStr(this, Prefs.BLOCK_KEYWORDS, join(etBlock.getText().toString()));
        Prefs.setStr(this, Prefs.ONLY_KEYWORDS, join(etOnly.getText().toString()));
    }

    /** 规范化成每行一个关键字，便于下次编辑查看 */
    private String join(String raw) {
        String[] ks = Conditions.split(raw);
        StringBuilder sb = new StringBuilder();
        for (String k : ks) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(k);
        }
        return sb.toString();
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }
}
