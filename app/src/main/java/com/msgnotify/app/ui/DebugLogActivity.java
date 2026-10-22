package com.msgnotify.app.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

import com.msgnotify.app.R;
import com.msgnotify.app.core.AppDebugLog;
import com.msgnotify.app.core.Prefs;
import com.msgnotify.app.service.KeepAliveService;
import com.msgnotify.app.util.SysNav;

/**
 * 应用内调试日志：展示 / 刷新 / 清空，以及开发者模式强保活开关。
 */
public class DebugLogActivity extends BaseActivity {

    private TextView tvLog;
    private ScrollView scrollLog;
    private SwitchCompat swDevKeepAlive;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_log);
        setupTitle("调试日志", "清空", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppDebugLog.clear();
                refresh();
                Toast.makeText(DebugLogActivity.this, "日志已清空", Toast.LENGTH_SHORT).show();
            }
        });

        tvLog = findViewById(R.id.tvLog);
        scrollLog = findViewById(R.id.scrollLog);
        swDevKeepAlive = findViewById(R.id.swDevKeepAlive);
        swDevKeepAlive.setChecked(Prefs.devKeepAlive(this));
        swDevKeepAlive.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setBool(DebugLogActivity.this, Prefs.DEV_KEEP_ALIVE, isChecked);
                AppDebugLog.d("Dev", "devKeepAlive=" + isChecked);
                KeepAliveService.applyDevKeepAlive(DebugLogActivity.this);
                if (isChecked) {
                    if (!SysNav.canScheduleExactAlarms(DebugLogActivity.this)) {
                        Toast.makeText(DebugLogActivity.this,
                                "请允许本应用使用精确闹钟，以便心跳保活", Toast.LENGTH_LONG).show();
                        SysNav.openExactAlarmSettings(DebugLogActivity.this);
                    } else {
                        Toast.makeText(DebugLogActivity.this,
                                "已开启强保活（耗电较高）", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(DebugLogActivity.this, "已关闭强保活", Toast.LENGTH_SHORT).show();
                }
            }
        });

        findViewById(R.id.btnRefresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        swDevKeepAlive.setChecked(Prefs.devKeepAlive(this));
        refresh();
    }

    private void refresh() {
        String all = AppDebugLog.readAll();
        if (TextUtils.isEmpty(all)) {
            tvLog.setText("暂无日志");
        } else {
            tvLog.setText(all);
            scrollLog.post(new Runnable() {
                @Override
                public void run() {
                    scrollLog.fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    }
}
