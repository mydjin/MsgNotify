package com.msgnotify.app.ui;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.msgnotify.app.R;

public abstract class BaseActivity extends AppCompatActivity {

    protected void setupTitle(String title) {
        setupTitle(title, null, null);
    }

    protected void setupTitle(String title, @Nullable String rightText,
                              @Nullable View.OnClickListener rightClick) {
        TextView tv = findViewById(R.id.tvTitle);
        if (tv != null) tv.setText(title);

        View back = findViewById(R.id.btnBack);
        if (back != null) {
            back.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        TextView right = findViewById(R.id.btnRight);
        if (right != null) {
            if (rightText == null) {
                right.setVisibility(View.GONE);
            } else {
                right.setVisibility(View.VISIBLE);
                right.setText(rightText);
                right.setOnClickListener(rightClick);
            }
        }
    }

    /** 隐藏返回键（首页用） */
    protected void hideBack() {
        View back = findViewById(R.id.btnBack);
        if (back != null) back.setVisibility(View.INVISIBLE);
    }
}
