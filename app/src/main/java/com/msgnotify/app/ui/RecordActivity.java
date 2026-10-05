package com.msgnotify.app.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.msgnotify.app.R;
import com.msgnotify.app.core.RecordStore;
import com.msgnotify.app.core.SpeakGate;
import com.msgnotify.app.core.SpeakRecord;
import com.msgnotify.app.util.Apps;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 语音播报记录：全部 / 已播报 / 未播报，支持重播与清空
 */
public class RecordActivity extends BaseActivity {

    private static final int TAB_ALL = 0;
    private static final int TAB_SPOKEN = 1;
    private static final int TAB_SKIPPED = 2;

    private final SimpleDateFormat fmt =
            new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault());

    private final List<SpeakRecord> data = new ArrayList<>();
    private Adapter adapter;

    private TextView tabAll;
    private TextView tabSpoken;
    private TextView tabSkipped;
    private TextView tvEmpty;

    private int tab = TAB_ALL;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record);
        setupTitle("语音播报记录", "删除记录", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClear();
            }
        });

        tabAll = findViewById(R.id.tabAll);
        tabSpoken = findViewById(R.id.tabSpoken);
        tabSkipped = findViewById(R.id.tabSkipped);
        tvEmpty = findViewById(R.id.tvEmpty);

        tabAll.setOnClickListener(new TabClick(TAB_ALL));
        tabSpoken.setOnClickListener(new TabClick(TAB_SPOKEN));
        tabSkipped.setOnClickListener(new TabClick(TAB_SKIPPED));

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        list.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private class TabClick implements View.OnClickListener {
        private final int which;

        TabClick(int which) {
            this.which = which;
        }

        @Override
        public void onClick(View v) {
            tab = which;
            reload();
        }
    }

    private void syncTabs() {
        style(tabAll, tab == TAB_ALL);
        style(tabSpoken, tab == TAB_SPOKEN);
        style(tabSkipped, tab == TAB_SKIPPED);
    }

    private void style(TextView tv, boolean on) {
        tv.setBackgroundResource(on ? R.drawable.bg_tab_on : R.drawable.bg_tab_off);
        tv.setTextColor(getResources().getColor(on ? R.color.white : R.color.text_sub));
    }

    private void reload() {
        syncTabs();
        data.clear();
        for (SpeakRecord r : RecordStore.all(this)) {
            if (tab == TAB_SPOKEN && !r.spoken()) continue;
            if (tab == TAB_SKIPPED && r.spoken()) continue;
            data.add(r);
        }
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("删除记录")
                .setMessage("确定清空全部播报记录？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        RecordStore.clear(RecordActivity.this);
                        reload();
                        Toast.makeText(RecordActivity.this, "已清空", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private class Adapter extends RecyclerView.Adapter<VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_record, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            final SpeakRecord r = data.get(position);

            Drawable icon = Apps.icon(RecordActivity.this, r.pkg);
            if (icon != null) {
                h.ivIcon.setImageDrawable(icon);
                h.ivIcon.setVisibility(View.VISIBLE);
            } else {
                h.ivIcon.setImageResource(R.drawable.ic_m_voice);
                h.ivIcon.setVisibility(View.VISIBLE);
            }

            h.tvApp.setText(TextUtils.isEmpty(r.appName) ? r.pkg : r.appName);
            h.tvTime.setText(fmt.format(new Date(r.time)));

            if (TextUtils.isEmpty(r.title)) {
                h.tvTitle.setVisibility(View.GONE);
            } else {
                h.tvTitle.setVisibility(View.VISIBLE);
                h.tvTitle.setText(r.title);
            }

            if (TextUtils.isEmpty(r.content)) {
                h.tvContent.setVisibility(View.GONE);
            } else {
                h.tvContent.setVisibility(View.VISIBLE);
                h.tvContent.setText(r.content);
            }

            String status = "消息状态：" + r.statusLabel();
            if (!TextUtils.isEmpty(r.reason)) status = status + r.reason;
            h.tvStatus.setText(status);

            h.btnPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SpeakGate.replay(RecordActivity.this, r);
                }
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvApp;
        final TextView tvTime;
        final TextView tvTitle;
        final TextView tvContent;
        final TextView tvStatus;
        final ImageView btnPlay;

        VH(View v) {
            super(v);
            ivIcon = v.findViewById(R.id.ivIcon);
            tvApp = v.findViewById(R.id.tvApp);
            tvTime = v.findViewById(R.id.tvTime);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvContent = v.findViewById(R.id.tvContent);
            tvStatus = v.findViewById(R.id.tvStatus);
            btnPlay = v.findViewById(R.id.btnPlay);
        }
    }
}
