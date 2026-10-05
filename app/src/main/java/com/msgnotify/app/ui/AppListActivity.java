package com.msgnotify.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.msgnotify.app.R;
import com.msgnotify.app.core.Prefs;
import com.msgnotify.app.util.Apps;

import java.util.ArrayList;
import java.util.List;

/**
 * 播报应用设置：勾选哪些应用的通知需要播报
 */
public class AppListActivity extends BaseActivity {

    private final List<Apps.Item> data = new ArrayList<>();
    private Adapter adapter;
    private TextView tvLoading;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_list);
        setupTitle("播报应用设置");

        tvLoading = findViewById(R.id.tvLoading);
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        list.setAdapter(adapter);

        load();
    }

    private void load() {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Apps.Item> items =
                        Apps.launchable(AppListActivity.this, Prefs.selectedPackages(AppListActivity.this));
                // 图标较重，后台线程先加载好
                for (Apps.Item it : items) {
                    it.icon = Apps.icon(AppListActivity.this, it.pkg);
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        data.clear();
                        data.addAll(items);
                        adapter.notifyDataSetChanged();
                        tvLoading.setVisibility(View.GONE);
                    }
                });
            }
        }).start();
    }

    private class Adapter extends RecyclerView.Adapter<VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            final Apps.Item it = data.get(position);
            h.name.setText(it.name);
            h.icon.setImageDrawable(it.icon);
            h.cb.setChecked(Prefs.isPackageSelected(AppListActivity.this, it.pkg));
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean next = !h.cb.isChecked();
                    h.cb.setChecked(next);
                    Prefs.setPackageSelected(AppListActivity.this, it.pkg, next);
                }
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final CheckBox cb;

        VH(View v) {
            super(v);
            icon = v.findViewById(R.id.ivIcon);
            name = v.findViewById(R.id.tvName);
            cb = v.findViewById(R.id.cb);
        }
    }
}
