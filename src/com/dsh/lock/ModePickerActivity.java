package com.dsh.lock;

import android.app.Activity;
import android.content.res.ColorStateList;
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

import java.util.ArrayList;
import java.util.List;

public final class ModePickerActivity extends Activity {
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_FIRST_RUN = "first_run";

    private final List<ModeRow> rows = new ArrayList<>();
    private int selected;
    private boolean firstRun;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        selected = LockPrefs.getMode(this);
        if (!LockModes.isValid(selected)) {
            selected = LockModes.ACCESSIBILITY;
        }
        firstRun = getIntent().getBooleanExtra(EXTRA_FIRST_RUN, false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(20), dp(14), dp(20), dp(14));

        root.addView(buildHeader());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(12), 0, dp(10));
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        addRow(list, LockModes.NORMAL, "普通锁机",
                "无需权限；Home 手势可能退出，重新打开仍锁定", "无需权限");
        addRow(list, LockModes.ACCESSIBILITY, "无障碍锁机",
                "全屏覆盖，阻止 Home / 最近任务 / 通知栏；需要无障碍权限", accessibilityStatus());
        addRow(list, LockModes.SHIZUKU, "Shizuku 锁机",
                "用 Shizuku 自动开无障碍/悬浮窗、切桌面、冻结选中 App", shizukuStatus());
        addRow(list, LockModes.ROOT, "root 锁机",
                "用 root 自动开无障碍/悬浮窗、切桌面、冻结选中 App", "需要 root");
        addRow(list, LockModes.OVERLAY, "悬浮窗锁机",
                "用系统悬浮窗覆盖桌面；需要悬浮窗权限", overlayStatus());
        addRow(list, LockModes.KIOSK, "设备所有者锁机",
                "官方 kiosk：防 Home/最近任务/通知栏/卸载；需恢复出厂+ADB 配置",
                KioskController.isDeviceOwner(this) ? "已配置" : "未配置");

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, scrollParams);

        TextView save = new TextView(this);
        save.setText(firstRun ? "保存并继续" : "保存");
        save.setTextColor(Color.BLACK);
        save.setTextSize(16);
        save.setGravity(Gravity.CENTER);
        save.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        save.setBackground(roundRect(Color.WHITE, 26, Color.WHITE));
        save.setClickable(true);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LockPrefs.setMode(ModePickerActivity.this, selected);
                setResult(RESULT_OK, getIntent().putExtra(EXTRA_MODE, selected));
                finish();
            }
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        saveParams.topMargin = dp(8);
        root.addView(save, saveParams);

        refreshRows();
        setContentView(root);
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.TOP);

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextColor(Color.WHITE);
        back.setTextSize(34);
        back.setGravity(Gravity.CENTER);
        back.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        header.addView(back, new LinearLayout.LayoutParams(dp(40), dp(48)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(8), dp(2), 0, 0);

        TextView title = new TextView(this);
        title.setText("锁机模式");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titles.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("选一种默认方式，之后可在设置里修改");
        subtitle.setTextColor(0xFF8E8E93);
        subtitle.setTextSize(12.5f);
        subtitle.setPadding(0, dp(3), 0, 0);
        titles.addView(subtitle);

        header.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return header;
    }

    private void addRow(LinearLayout parent, final int mode, String title, String description,
                        String status) {
        ModeRow row = new ModeRow();
        row.mode = mode;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(14), dp(14));
        row.background = roundRect(0xFF111113, 20, 0x1FFFFFFF);
        card.setBackground(row.background);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(16);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.addView(name);
        TextView desc = new TextView(this);
        desc.setText(description);
        desc.setTextColor(0xFF8E8E93);
        desc.setTextSize(11.5f);
        desc.setPadding(0, dp(3), 0, 0);
        text.addView(desc);
        card.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView statusView = new TextView(this);
        statusView.setText(status);
        statusView.setTextColor(0xFF8E8E93);
        statusView.setTextSize(10.5f);
        statusView.setGravity(Gravity.CENTER);
        right.addView(statusView);
        TextView check = new TextView(this);
        check.setTextSize(15);
        check.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        checkParams.topMargin = dp(5);
        right.addView(check, checkParams);
        card.addView(right, new LinearLayout.LayoutParams(dp(58),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        row.root = card;
        row.status = statusView;
        row.check = check;
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selected = mode;
                refreshRows();
            }
        });

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(10);
        parent.addView(card, cardParams);
        rows.add(row);
    }

    private void refreshRows() {
        for (ModeRow row : rows) {
            boolean active = row.mode == selected;
            row.background.setColor(active ? 0xFF171719 : 0xFF111113);
            row.background.setStroke(dp(active ? 2 : 1),
                    active ? Color.WHITE : 0x1FFFFFFF);
            row.check.setText(active ? "✓" : "○");
            row.check.setTextColor(active ? Color.BLACK : 0xFF5A5A5E);
            row.check.setBackground(roundRect(active ? Color.WHITE : 0x00000000,
                    12, active ? Color.WHITE : 0xFF3A3A3C));
            if (row.mode == LockModes.ACCESSIBILITY) {
                row.status.setText(accessibilityStatus());
                row.status.setTextColor(LockAccessibilityService.isRunning()
                        || LockAccessibilityService.isEnabledInSettings(this)
                        ? Color.WHITE : 0xFF8E8E93);
            }
            if (row.mode == LockModes.SHIZUKU) {
                row.status.setText(shizukuStatus());
            }
            if (row.mode == LockModes.OVERLAY) {
                row.status.setText(overlayStatus());
            }
        }
    }

    private String accessibilityStatus() {
        return LockAccessibilityService.isRunning()
                || LockAccessibilityService.isEnabledInSettings(this)
                ? "已开启" : "需要开启";
    }

    private String shizukuStatus() {
        if (!ShizukuHelper.isAvailable()) {
            return "未连接";
        }
        return ShizukuHelper.hasPermission() ? "已授权" : "需授权";
    }

    private String overlayStatus() {
        return Settings.canDrawOverlays(this) ? "已授权" : "需授权";
    }

    private GradientDrawable roundRect(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class ModeRow {
        int mode;
        LinearLayout root;
        TextView status;
        TextView check;
        GradientDrawable background;
    }
}
