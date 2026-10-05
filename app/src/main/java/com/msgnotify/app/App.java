package com.msgnotify.app;

import android.app.Application;

import com.msgnotify.app.core.Speaker;
import com.msgnotify.app.core.VolumeWatcher;
import com.msgnotify.app.service.KeepAliveService;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        KeepAliveService.createChannels(this);
        Speaker.init(this, null);
        VolumeWatcher.start(this);
        KeepAliveService.start(this);
    }
}
