package com.msgnotify.app.ui;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import com.msgnotify.app.R;
import com.msgnotify.app.service.KeepAliveService;
import com.msgnotify.app.service.NotifyListener;
import com.msgnotify.app.util.SysNav;

/**
 * 检查权限：必要权限自检 + 后台运行权限引导 + 一键检测修复 + 发通知自检播报
 */
public class PermissionActivity extends BaseActivity {

    private ImageView ivNotifyAccess;
    private TextView tvNotifyAccess;
    private ImageView ivPostNotify;
    private TextView tvPostNotify;
    private ImageView ivBattery;
    private TextView tvBattery;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission);
        setupTitle("检查权限");

        ivNotifyAccess = findViewById(R.id.ivNotifyAccess);
        tvNotifyAccess = findViewById(R.id.tvNotifyAccess);
        ivPostNotify = findViewById(R.id.ivPostNotify);
        tvPostNotify = findViewById(R.id.tvPostNotify);
        ivBattery = findViewById(R.id.ivBattery);
        tvBattery = findViewById(R.id.tvBattery);

        findViewById(R.id.rowNotifyAccess).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SysNav.openNotificationListenerSettings(PermissionActivity.this);
            }
        });
        findViewById(R.id.rowPostNotify).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SysNav.openAppNotificationSettings(PermissionActivity.this);
            }
        });
        findViewById(R.id.rowAutoStart).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SysNav.openAutoStartSettings(PermissionActivity.this);
            }
        });
        findViewById(R.id.rowBattery).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SysNav.requestIgnoreBattery(PermissionActivity.this);
            }
        });

        findViewById(R.id.btnFix).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fix();
            }
        });
        findViewById(R.id.btnTest).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendTestNotification();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean listener = NotifyListener.isPermissionGranted(this);
        mark(ivNotifyAccess, tvNotifyAccess, listener,
                listener ? (NotifyListener.isConnected() ? "已开启，服务运行中" : "已开启（服务待唤醒）")
                        : "未开启，点击前往「通知使用权」授权");

        boolean post = NotificationManagerCompat.from(this).areNotificationsEnabled();
        mark(ivPostNotify, tvPostNotify, post,
                post ? "已开启" : "未开启，常驻通知与自检通知将无法显示");

        boolean battery = SysNav.isIgnoringBattery(this);
        mark(ivBattery, tvBattery, battery,
                battery ? "已加入白名单" : "未加入，息屏后可能被系统限制");
    }

    private void mark(ImageView iv, TextView tv, boolean ok, String desc) {
        iv.setImageResource(ok ? R.drawable.ic_check : R.drawable.ic_warn);
        tv.setText(desc);
    }

    /**
     * 一键修复：重启监听服务 + 重建通知渠道 + 重启常驻前台服务，
     * 再把仍缺失的权限按优先级引导用户开启。
     */
    private void fix() {
        NotifyListener.rebind(this);
        KeepAliveService.createChannels(this);
        KeepAliveService.refresh(this);
        refresh();

        if (!NotifyListener.isPermissionGranted(this)) {
            Toast.makeText(this, "请先开启「读取通知内容」权限", Toast.LENGTH_LONG).show();
            SysNav.openNotificationListenerSettings(this);
            return;
        }
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            Toast.makeText(this, "请开启本应用的通知显示权限", Toast.LENGTH_LONG).show();
            SysNav.openAppNotificationSettings(this);
            return;
        }
        if (!SysNav.isIgnoringBattery(this)) {
            Toast.makeText(this, "建议将本应用加入电池优化白名单", Toast.LENGTH_LONG).show();
            SysNav.requestIgnoreBattery(this);
            return;
        }
        Toast.makeText(this, "已重启播报服务，权限检测通过", Toast.LENGTH_SHORT).show();
    }

    /** 与首页一致：发一条带自检标记的通知，强制播报 */
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
        nm.notify(NotifyListener.TEST_TAG, 2002, n);
        Toast.makeText(this, "已发出测试通知，请留意播报声音", Toast.LENGTH_SHORT).show();
    }
}
