package com.msgnotify.app;

import android.app.Application;

import com.msgnotify.app.core.AppDebugLog;
import com.msgnotify.app.core.ListenerHeal;
import com.msgnotify.app.core.Prefs;
import com.msgnotify.app.core.Speaker;
import com.msgnotify.app.core.VolumeWatcher;
import com.msgnotify.app.service.KeepAliveService;
import com.msgnotify.app.service.NotifyListener;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppDebugLog.bind(this);
        AppDebugLog.d("App", "onCreate"
                + " master=" + Prefs.masterEnabled(this)
                + " foreground=" + Prefs.foregroundEnabled(this)
                + " devKeepAlive=" + Prefs.devKeepAlive(this)
                + " nlsPermission=" + NotifyListener.isPermissionGranted(this)
                + " nlsConnected=" + NotifyListener.isConnected());
        KeepAliveService.createChannels(this);
        Speaker.init(this, null);
        VolumeWatcher.start(this);
        KeepAliveService.start(this);
        ListenerHeal.onAppStart(this);
    }
}
