package com.dsh.lock;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class DeviceOwnerSetupActivity extends Activity {
    private static final int BG = 0xFF000000;
    private static final int CARD = 0xFF111113;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF8E8E93;
    private static final int LINE = 0x1FFFFFFF;
    private static final int GOOD = 0xFF6EE7A8;
    private static final int WARN = 0xFFFFCC66;

    private TextView statusView;
    private static final String OWNER_COMMAND =
            "adb shell dpm set-device-owner com.dsh.lock/.AdminReceiver";
    private static final String REMOVE_COMMAND =
            "adb shell dpm remove-active-admin com.dsh.lock/.AdminReceiver";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(20), dp(14), dp(20), dp(14));
        root.addView(buildHeader());

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(10), 0, dp(20));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout statusCard = card();
        statusView = new TextView(this);
        statusView.setTextSize(16);
        statusView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        statusCard.addView(statusView);
        TextView statusHint = new TextView(this);
        statusHint.setText("必须是恢复出厂、没有账号的设备才能设置");
        statusHint.setTextColor(MUTED);
        statusHint.setTextSize(11.5f);
        statusHint.setPadding(0, dp(4), 0, 0);
        statusCard.addView(statusHint);
        content.addView(statusCard, cardParams());

        content.addView(stepCard("1", "准备设备",
                "备份数据，恢复出厂。不要登录 Google/厂商账号。\n强烈建议用备用机，不要用主力机。"));
        content.addView(stepCard("2", "安装 APK",
                "安装 TE cvb，打开一次，先不要配置锁机。"));
        content.addView(stepCard("3", "开启 USB 调试",
                "设置 → 关于手机 → 连点版本号 → 开发者选项 → USB 调试。"));
        content.addView(stepCard("4", "电脑执行命令",
                "手机连电脑，执行下面命令。成功后再打开 TE cvb。\n\n" + OWNER_COMMAND));
        content.addView(stepCard("5", "回来检查",
                "点下面的“检查状态”。显示“已配置”就可以选设备所有者锁机。"));
        content.addView(stepCard("!", "危险提示",
                "设备所有者会防卸载、限制锁任务。强力模式还会禁 ADB、恢复出厂、安全模式。\n忘记密码可能需要 fastboot 处理。"));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(0, dp(12), 0, 0);
        actions.addView(button("复制设置命令", new Runnable() {
            @Override
            public void run() {
                copy(OWNER_COMMAND);
            }
        }, false));
        actions.addView(button("复制移除命令", new Runnable() {
            @Override
            public void run() {
                copy(REMOVE_COMMAND);
            }
        }, false));
        actions.addView(button("打开开发者选项", new Runnable() {
            @Override
            public void run() {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                } catch (Exception exception) {
                    Toast.makeText(DeviceOwnerSetupActivity.this,
                            "无法打开开发者选项", Toast.LENGTH_SHORT).show();
                }
            }
        }, false));
        actions.addView(button("检查状态", new Runnable() {
            @Override
            public void run() {
                updateStatus();
            }
        }, true));
        content.addView(actions);

        setContentView(root);
        updateStatus();
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextColor(TEXT);
        back.setTextSize(34);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        row.addView(back, new LinearLayout.LayoutParams(dp(40), dp(44)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("设备所有者设置");
        title.setTextColor(TEXT);
        title.setTextSize(23);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        texts.addView(title);
        TextView sub = new TextView(this);
        sub.setText("恢复出厂 + ADB，官方 kiosk");
        sub.setTextColor(MUTED);
        sub.setTextSize(11.5f);
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private View stepCard(String number, String title, String desc) {
        LinearLayout card = card();
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = new TextView(this);
        badge.setText(number);
        badge.setTextColor(Color.BLACK);
        badge.setTextSize(12);
        badge.setGravity(Gravity.CENTER);
        badge.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        badge.setBackground(roundRect(Color.WHITE, 10, 0, 0));
        head.addView(badge, new LinearLayout.LayoutParams(dp(26), dp(26)));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(TEXT);
        titleView.setTextSize(14.5f);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleView.setPadding(dp(10), 0, 0, 0);
        head.addView(titleView);
        card.addView(head);

        TextView descView = new TextView(this);
        descView.setText(desc);
        descView.setTextColor(MUTED);
        descView.setTextSize(12.5f);
        descView.setLineSpacing(dp(2), 1f);
        descView.setPadding(0, dp(8), 0, 0);
        card.addView(descView);
        return card;
    }

    private TextView button(String text, final Runnable action, boolean primary) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(primary ? Color.BLACK : TEXT);
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setBackground(roundRect(primary ? Color.WHITE : 0xFF1C1C1E, 18, 0, 0));
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        params.bottomMargin = dp(8);
        view.setLayoutParams(params);
        return view;
    }

    private void updateStatus() {
        boolean owner = KioskController.isDeviceOwner(this);
        statusView.setText(owner ? "已配置：设备所有者" : "未配置：设备所有者");
        statusView.setTextColor(owner ? GOOD : WARN);
    }

    private void copy(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("TE cvb ADB", text));
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(roundRect(CARD, 18, 1, LINE));
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        return params;
    }

    private GradientDrawable roundRect(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) {
            drawable.setStroke(dp(strokeDp), strokeColor);
        }
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
