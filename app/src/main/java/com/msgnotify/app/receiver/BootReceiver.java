package com.msgnotify.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.msgnotify.app.service.KeepAliveService;

/**
 * 开机 / 应用升级后拉起保活服务
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        KeepAliveService.start(context);
    }
}
