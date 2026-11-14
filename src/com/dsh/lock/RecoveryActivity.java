package com.dsh.lock;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

public final class RecoveryActivity extends Activity {
    private static final int BG = 0xFF000000;
    private static final int CARD = 0xFF111113;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF8E8E93;
    private static final int LINE = 0x1FFFFFFF;
    private static final int WARN = 0xFFFFCC66;
    private static final int BAD = 0xFFFF6B6B;

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

        content.addView(statusCard(), cardParams());

        content.addView(section("设备所有者救援"));
        content.addView(cardText(
                "如果是设备所有者模式被锁住，用电脑 ADB 移除管理员：",
                "adb shell dpm remove-active-admin com.dsh.lock/.AdminReceiver",
                "复制移除命令"));

        content.addView(section("强力模式救援"));
        content.addView(cardText(
                "强力模式会关闭 ADB、禁恢复出厂、禁安全模式。\n如果还能连 ADB，先恢复 ADB，再移除管理员：\n\n如果 ADB 已经断了，只能进 fastboot / recovery 处理。",
                "adb shell settings put global adb_enabled 1\n"
                        + "adb shell dpm remove-active-admin com.dsh.lock/.AdminReceiver",
                "复制强力救援命令"));

        content.addView(section("冻结 App 救援"));
        String freezeCommand = freezeCommand();
        content.addView(cardText(
                freezeCommand.length() == 0
                        ? "当前没有冻结记录。"
                        : "如果 TE cvb 被卸载/清数据，冻结的 App 可能还处于禁用状态。用 ADB 恢复：",
                freezeCommand,
                freezeCommand.length() == 0 ? "没有可复制的命令" : "复制恢复命令"));

        content.addView(section("通用建议"));
        content.addView(cardText(
                "1. 先记住密码，别在主力机上开强力模式。\n"
                        + "2. 设备所有者模式必须恢复出厂 + ADB 设置。\n"
                        + "3. BL 解锁后，fastboot 可以绕过所有 App 锁。\n"
                        + "4. 不确定时先导出安全自测报告。",
                "",
                ""));

        setContentView(root);
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
        title.setText("救援中心");
        title.setTextColor(TEXT);
        title.setTextSize(23);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        texts.addView(title);
        TextView sub = new TextView(this);
        sub.setText("设备所有者 / 强力模式 / 冻结恢复");
        sub.setTextColor(MUTED);
        sub.setTextSize(11.5f);
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private View statusCard() {
        LinearLayout card = card();
        TextView title = new TextView(this);
        title.setText("当前状态");
        title.setTextColor(TEXT);
        title.setTextSize(15);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(title);

        addStatus(card, "设备所有者", KioskController.isDeviceOwner(this));
        addStatus(card, "强力模式",
                LockPrefs.isLocked(this) && LockPrefs.getLockMode(this) == LockModes.KIOSK_STRONG);
        addStatus(card, "冻结中", FreezePrefs.isActive(this));
        addStatus(card, "无障碍服务", LockAccessibilityService.isRunning());
        return card;
    }

    private void addStatus(LinearLayout card, String name, boolean ok) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        TextView left = new TextView(this);
        left.setText(name);
        left.setTextColor(MUTED);
        left.setTextSize(12.5f);
        row.addView(left, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView right = new TextView(this);
        right.setText(ok ? "是" : "否");
        right.setTextColor(ok ? 0xFF6EE7A8 : WARN);
        right.setTextSize(12.5f);
        row.addView(right);
        card.addView(row);
    }

    private View cardText(String desc, final String command, String actionText) {
        LinearLayout card = card();
        TextView text = new TextView(this);
        text.setText(desc);
        text.setTextColor(MUTED);
        text.setTextSize(12.5f);
        text.setLineSpacing(dp(2), 1f);
        card.addView(text);
        if (command != null && command.length() > 0) {
            TextView code = new TextView(this);
            code.setText(command);
            code.setTextColor(TEXT);
            code.setTextSize(11.5f);
            code.setTypeface(Typeface.MONOSPACE);
            code.setPadding(dp(10), dp(10), dp(10), dp(10));
            code.setBackground(roundRect(0xFF1A1A1C, 10, 1, LINE));
            LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            codeParams.topMargin = dp(8);
            card.addView(code, codeParams);
        }
        if (actionText != null && actionText.length() > 0) {
            TextView action = new TextView(this);
            action.setText(actionText);
            action.setTextColor(Color.BLACK);
            action.setTextSize(13);
            action.setGravity(Gravity.CENTER);
            action.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            action.setBackground(roundRect(Color.WHITE, 16, 0, 0));
            action.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ClipboardManager clipboard = (ClipboardManager)
                            getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null && command.length() > 0) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("TE cvb 救援命令", command));
                        Toast.makeText(RecoveryActivity.this, "已复制",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
            actionParams.topMargin = dp(10);
            card.addView(action, actionParams);
        }
        return card;
    }

    private String freezeCommand() {
        JSONArray active = FreezePrefs.getActivePackages(this);
        JSONArray selected = FreezePrefs.getPackages(this);
        JSONArray packages = active.length() > 0 ? active : selected;
        if (packages.length() == 0) {
            return "";
        }
        StringBuilder command = new StringBuilder("adb shell \"");
        for (int i = 0; i < packages.length(); i++) {
            try {
                String pkg = packages.getString(i);
                if (pkg.length() > 0) {
                    if (command.length() > "adb shell \"".length()) {
                        command.append("; ");
                    }
                    command.append("pm enable ").append(pkg);
                }
            } catch (Exception ignored) {
            }
        }
        command.append("\"");
        return command.toString();
    }

    private TextView section(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(MUTED);
        view.setTextSize(12);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setPadding(0, dp(16), 0, dp(6));
        return view;
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
