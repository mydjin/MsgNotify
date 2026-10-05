package com.msgnotify.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.msgnotify.app.R;

/**
 * 常见问题页：点击问题展开/收起答案
 */
public class FaqActivity extends BaseActivity {

    private static final String[] QUESTIONS = {
            "为什么播报开启后没有声音？",
            "亮屏播报正常，息屏却不播报？",
            "为何无论亮屏息屏都不播报？",
            "微信消息被折叠，无法播报其中内容？",
            "播报记录里显示「未播报」是什么原因？"
    };

    private static final String[] ANSWERS = {
            "1. 检查手机媒体音量是否过低，本应用使用「媒体音量」通道播报；\n"
                    + "2. 进入「音频音色设置」，确认声音大小不为 0，并点击「试听播报效果」验证；\n"
                    + "3. 若试听也没有声音，说明系统 TTS 引擎异常，请在系统设置中安装或切换一个可用的语音引擎；\n"
                    + "4. 确认「播报设置」中未开启「仅耳机播报」，否则未连接耳机时不会出声。",

            "这是系统后台限制导致监听服务被冻结，请依次设置：\n"
                    + "1. 「检查权限」→「应用自启动权限设置」中允许本应用自启动；\n"
                    + "2. 「检查权限」→「加入电池优化白名单」并确认已忽略电池优化；\n"
                    + "3. 在最近任务界面下拉本应用卡片将其「锁定」；\n"
                    + "4. 保持「播报设置」中的「常驻通知保活」为开启状态；\n"
                    + "5. MagicOS 可在 系统设置 → 电池 → 启动管理 中把本应用改为「手动管理」，并勾选全部三项。",

            "1. 先到「检查权限」确认「读取通知内容权限」为绿色勾选，否则点击该行去系统设置中开启；\n"
                    + "2. 确认首页总开关已打开；\n"
                    + "3. 确认「播报应用设置」中已勾选需要播报的应用（未勾选的应用不会播报）；\n"
                    + "4. 确认当前时间不在「免打扰时段」内；\n"
                    + "5. 点击「检测并修复」重新绑定监听服务，再点「发条通知检测播报」验证。",

            "微信会把多条消息折叠成「[3条] 内容」这样的汇总通知，系统只会下发一条。\n"
                    + "本应用已做兼容：优先读取通知的消息体（MessagingStyle）中最后一条内容，再依次回退到长文本、正文、多行文本。\n"
                    + "若仍只播报「你收到了N条新消息」，请关闭微信的「消息免打扰 / 折叠通知」，或在微信里保持与该会话的通知为展开状态。",

            "打开「语音播报记录」，点击对应条目可看到状态后面的原因说明，常见原因：\n"
                    + "• 应用未被勾选 —— 到「播报应用设置」中勾选；\n"
                    + "• 命中屏蔽关键字 / 未命中只读关键字 —— 到「关键词设置」中调整；\n"
                    + "• 处于免打扰时段 —— 到「播报设置」中调整时段；\n"
                    + "• 未连接耳机 —— 关闭「仅耳机播报」；\n"
                    + "• 内容为空 —— 该通知本身没有可播报文本。\n"
                    + "任意记录都可点击右侧绿色播放按钮强制重播。"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_faq);
        setupTitle("常见问题");
        build();
    }

    private void build() {
        LinearLayout container = findViewById(R.id.faqContainer);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < QUESTIONS.length; i++) {
            View item = inflater.inflate(R.layout.item_faq, container, false);
            TextView tvQuestion = item.findViewById(R.id.tvQuestion);
            final TextView tvAnswer = item.findViewById(R.id.tvAnswer);
            final ImageView ivArrow = item.findViewById(R.id.ivArrow);

            tvQuestion.setText((i + 1) + ". " + QUESTIONS[i]);
            tvAnswer.setText(ANSWERS[i]);

            item.findViewById(R.id.rowQuestion).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean show = tvAnswer.getVisibility() != View.VISIBLE;
                    tvAnswer.setVisibility(show ? View.VISIBLE : View.GONE);
                    ivArrow.setRotation(show ? 90f : 0f);
                }
            });

            container.addView(item);
        }
    }
}
