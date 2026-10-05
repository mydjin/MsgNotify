package com.msgnotify.app.ui;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import com.msgnotify.app.R;
import com.msgnotify.app.core.Prefs;
import com.msgnotify.app.core.RecordStore;
import com.msgnotify.app.core.Speaker;
import com.msgnotify.app.service.KeepAliveService;
import com.msgnotify.app.service.NotifyListener;
import com.msgnotify.app.util.SysNav;

/**
 * 首页：总开关 + 权限状态 + 各功能入口 + 发条通知自检播报
 */
public class MainActivity extends BaseActivity {

    private SwitchCompat swMaster;
    private ImageView ivStatus;
    private TextView tvStatus;
    private TextView tvStatusAction;
    private TextView tvTodayCount;
    private TextView tvAppsCount;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupTitle(getString(R.string.app_name));
        hideBack();

        swMaster = findViewById(R.id.swMaster);
        ivStatus = findViewById(R.id.ivStatus);
        tvStatus = findViewById(R.id.tvStatus);
        tvStatusAction = findViewById(R.id.tvStatusAction);
        tvTodayCount = findViewById(R.id.tvTodayCount);
        tvAppsCount = findViewById(R.id.tvAppsCount);

        swMaster.setChecked(Prefs.masterEnabled(this));
        swMaster.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setBool(MainActivity.this, Prefs.MASTER_ENABLED, isChecked);
                if (!isChecked) Speaker.stop();
                KeepAliveService.refresh(MainActivity.this);
                Toast.makeText(MainActivity.this,
                        isChecked ? "播报已开启" : "播报已暂停", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.boxStatus).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SysNav.openNotificationListenerSettings(MainActivity.this);
            }
        });

        go(R.id.rowApps, AppListActivity.class);
        go(R.id.rowFormat, FormatActivity.class);
        go(R.id.rowVoice, VoiceActivity.class);
        go(R.id.rowSpeak, SpeakSettingsActivity.class);
        go(R.id.rowRecord, RecordActivity.class);
        go(R.id.rowPermission, PermissionActivity.class);
        go(R.id.rowFaq, FaqActivity.class);

        findViewById(R.id.btnTest).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendTestNotification();
            }
        });

        requestPostNotificationsIfNeeded();
        KeepAliveService.start(this);
    }

    private void go(int viewId, final Class<?> target) {
        View v = findViewById(viewId);
        if (v == null) return;
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, target));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshState();
        applyKeepScreenOn();
    }

    private void refreshState() {
        boolean granted = NotifyListener.isPermissionGranted(this);
        if (granted) {
            ivStatus.setImageResource(R.drawable.ic_check);
            tvStatus.setText(NotifyListener.isConnected()
                    ? "通知读取权限已开启，服务运行中"
                    : "通知读取权限已开启（服务待唤醒）");
            tvStatusAction.setText("查看");
        } else {
            ivStatus.setImageResource(R.drawable.ic_warn);
            tvStatus.setText("未开启通知读取权限，无法播报");
            tvStatusAction.setText("去开启");
        }
        tvTodayCount.setText("今日已播报 " + RecordStore.countSpokenToday(this) + " 条");

        int n = Prefs.selectedPackages(this).size();
        tvAppsCount.setText(n == 0 ? "未选择" : "已选 " + n + " 个");

        swMaster.setChecked(Prefs.masterEnabled(this));
    }

    /** 屏幕永不休眠（对应「播报设置」页开关） */
    private void applyKeepScreenOn() {
        if (Prefs.getBool(this, Prefs.KEEP_SCREEN_ON, false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 发一条自检通知。该通知带 {@link NotifyListener#TEST_TAG}，
     * 会被监听服务识别为自检消息并强制播报（绕过应用选择与关键词过滤）。
     */
    private void sendTestNotification() {
        if (!NotifyListener.isPermissionGranted(this)) {
            Toast.makeText(this, "请先开启通知读取权限", Toast.LENGTH_LONG).show();
            SysNav.openNotificationListenerSettings(this);
            return;
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        KeepAliveService.createChannels(this);

        Notification n = new Notification.Builder(this, KeepAliveService.CHANNEL_TEST)
                .setContentTitle("播报自检")
                .setContentText("这是一条测试通知，如果你听到这句话说明播报正常")
                .setSmallIcon(R.drawable.ic_m_voice)
                .setAutoCancel(true)
                .build();
        nm.notify(NotifyListener.TEST_TAG, 2001, n);
        Toast.makeText(this, "已发出测试通知，请留意播报声音", Toast.LENGTH_SHORT).show();
    }
}
