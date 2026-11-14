package com.dsh.lock;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public final class SelfTestActivity extends Activity {
    private static final int BG = 0xFF000000;
    private static final int CARD = 0xFF111113;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF8E8E93;
    private static final int LINE = 0x1FFFFFFF;
    private static final int GOOD = 0xFF6EE7A8;
    private static final int WARN = 0xFFFFCC66;
    private static final int BAD = 0xFFFF6B6B;

    private LinearLayout checks;
    private TextView scoreView;
    private TextView warningsView;
    private final StringBuilder report = new StringBuilder();
    private final StringBuilder warnings = new StringBuilder();
    private int passed;
    private int total;
    private final List<CheckBox> manualChecks = new ArrayList<>();

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

        LinearLayout scoreCard = card();
        scoreView = new TextView(this);
        scoreView.setTextColor(TEXT);
        scoreView.setTextSize(20);
        scoreView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        scoreCard.addView(scoreView);
        TextView scoreHint = new TextView(this);
        scoreHint.setText("检查当前锁机环境，不代替实际锁机测试");
        scoreHint.setTextColor(MUTED);
        scoreHint.setTextSize(11.5f);
        scoreHint.setPadding(0, dp(4), 0, 0);
        scoreCard.addView(scoreHint);
        content.addView(scoreCard, cardParams());

        TextView listTitle = label("检查项");
        content.addView(listTitle);
        checks = new LinearLayout(this);
        checks.setOrientation(LinearLayout.VERTICAL);
        content.addView(checks);

        TextView warnTitle = label("风险提示");
        content.addView(warnTitle);
        LinearLayout warnCard = card();
        warningsView = new TextView(this);
        warningsView.setTextColor(MUTED);
        warningsView.setTextSize(12.5f);
        warningsView.setLineSpacing(dp(2), 1f);
        warnCard.addView(warningsView);
        content.addView(warnCard, cardParams());

        TextView manualTitle = label("手动测试");
        content.addView(manualTitle);
        LinearLayout manualCard = card();
        TextView manual = new TextView(this);
        manual.setText("开始锁机后，手动试：上滑 Home、最近任务、下拉通知栏、进设置、截屏。\n这里只检查权限和环境，不模拟真实绕过。");
        manual.setTextColor(MUTED);
        manual.setTextSize(12.5f);
        manual.setLineSpacing(dp(2), 1f);
        manualCard.addView(manual);
        String[] manualItems = {
                "Home 上滑后仍锁",
                "最近任务被挡住",
                "下拉通知栏被收回",
                "设置页被挡住",
                "截屏/录屏被阻止",
                "重启后仍恢复锁机"
        };
        for (String item : manualItems) {
            CheckBox check = new CheckBox(this);
            check.setText(item);
            check.setTextColor(TEXT);
            check.setTextSize(13);
            check.setPadding(0, dp(4), 0, dp(4));
            manualCard.addView(check);
            manualChecks.add(check);
        }
        content.addView(manualCard, cardParams());

        content.addView(buildActions());
        setContentView(root);

        runChecks();
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
        title.setText("安全自测");
        title.setTextColor(TEXT);
        title.setTextSize(23);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        texts.addView(title);
        TextView sub = new TextView(this);
        sub.setText("BL / ADB / 权限 / 设备所有者");
        sub.setTextColor(MUTED);
        sub.setTextSize(11.5f);
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private void runChecks() {
        passed = 0;
        total = 0;
        report.setLength(0);
        warnings.setLength(0);

        boolean owner = KioskController.isDeviceOwner(this);
        add("设备所有者", owner ? "已配置" : "未配置", owner,
                owner ? null : "没有设备所有者，不能防卸载/锁任务");

        boolean strong = LockPrefs.isLocked(this)
                && LockPrefs.getLockMode(this) == LockModes.KIOSK_STRONG;
        add("强力模式", strong ? "锁机中" : "未开启", strong,
                strong ? null : "强力模式未开启，安全模式/恢复出厂/ADB 未限制");

        boolean accessibility = LockAccessibilityService.isRunning();
        add("无障碍服务", accessibility ? "运行中" : "未运行", accessibility,
                accessibility ? null : "无障碍未运行，Home/通知/设置拦截不完整");

        boolean overlay = Settings.canDrawOverlays(this);
        add("悬浮窗权限", overlay ? "已授权" : "未授权", overlay,
                overlay ? null : "悬浮窗锁机无法使用");

        boolean notification = notificationAllowed();
        add("通知权限", notification ? "已允许" : "未允许", notification,
                notification ? null : "前台服务/锁机通知可能不显示");

        KeyguardManager keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        boolean secure = keyguard != null && keyguard.isDeviceSecure();
        add("系统锁屏", secure ? "已设置" : "未设置", secure,
                secure ? null : "系统锁屏未设置，设备安全性较低");

        boolean battery = batteryIgnored();
        add("电池优化", battery ? "已忽略" : "未忽略", battery,
                battery ? null : "后台可能被系统限制，锁机服务容易被杀");

        String flash = prop("ro.boot.flash.locked");
        String verified = prop("ro.boot.verifiedbootstate");
        boolean blLocked = "1".equals(flash);
        String blValue = blLocked
                ? ("已锁定" + (verified.length() > 0 ? " · " + verified : ""))
                : ("未锁定" + (verified.length() > 0 ? " · " + verified : ""));
        add("Bootloader", blValue, blLocked,
                blLocked ? null : "BL 已解锁：fastboot 可绕过所有 App 锁");

        boolean adb = adbEnabled();
        add("USB 调试 / ADB", adb ? "开启" : "关闭", !adb,
                adb ? "ADB 开启：可停用设备所有者或卸载" : null);

        boolean dev = devOptionsEnabled();
        add("开发者选项", dev ? "开启" : "关闭", !dev,
                dev ? "开发者选项开启，便于调试/绕过" : null);

        boolean shizuku = ShizukuHelper.isAvailable() && ShizukuHelper.hasPermission();
        add("Shizuku", shizuku ? "可用" : "不可用（可选）", true, null);

        String mode = LockModes.name(LockPrefs.getMode(this));
        add("当前默认模式", mode, true, null);

        scoreView.setText(passed + " / " + total + " 项通过");
        scoreView.setTextColor(passed == total ? GOOD : (passed >= total - 2 ? WARN : BAD));
        warningsView.setText(warnings.length() == 0
                ? "未发现明显配置风险。" : warnings.toString());
        warningsView.setTextColor(warnings.length() == 0 ? GOOD : WARN);

        report.insert(0, "TE cvb 安全自测\n通过：" + passed + " / " + total + "\n\n");
        if (warnings.length() > 0) {
            report.append("\n风险：\n").append(warnings);
        }
    }

    private void add(String name, String value, boolean ok, String warning) {
        total++;
        if (ok) {
            passed++;
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(roundRect(CARD, 14, 1, LINE));

        TextView dot = new TextView(this);
        dot.setText("●");
        dot.setTextColor(ok ? GOOD : WARN);
        dot.setTextSize(12);
        row.addView(dot, new LinearLayout.LayoutParams(dp(20), dp(24)));

        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextColor(TEXT);
        nameView.setTextSize(13.5f);
        row.addView(nameView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(ok ? GOOD : WARN);
        valueView.setTextSize(12.5f);
        row.addView(valueView);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(6);
        checks.addView(row, params);

        report.append(ok ? "[通过] " : "[警告] ").append(name).append("：")
                .append(value).append('\n');
        if (warning != null && warning.length() > 0) {
            warnings.append("• ").append(warning).append('\n');
            report.append("  → ").append(warning).append('\n');
        }
    }

    private View buildActions() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, dp(14), 0, 0);
        wrap.addView(action("无障碍设置", new Runnable() {
            @Override
            public void run() {
                open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        }));
        wrap.addView(action("悬浮窗设置", new Runnable() {
            @Override
            public void run() {
                open(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
        }));
        wrap.addView(action("开发者选项", new Runnable() {
            @Override
            public void run() {
                open(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            }
        }));
        wrap.addView(action("电池优化", new Runnable() {
            @Override
            public void run() {
                open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        }));
        TextView share = action("分享报告", new Runnable() {
            @Override
            public void run() {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, "TE cvb 安全自测");
                send.putExtra(Intent.EXTRA_TEXT, fullReport());
                try {
                    startActivity(Intent.createChooser(send, "分享安全自测报告"));
                } catch (Exception exception) {
                    Toast.makeText(SelfTestActivity.this, "没有可分享的应用",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        wrap.addView(share);

        TextView copy = action("复制报告", new Runnable() {
            @Override
            public void run() {
                ClipboardManager clipboard = (ClipboardManager)
                        getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("TE cvb 安全自测",
                            fullReport()));
                    Toast.makeText(SelfTestActivity.this, "报告已复制",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        copy.setTextColor(Color.BLACK);
        copy.setBackground(roundRect(Color.WHITE, 18, 0, 0));
        wrap.addView(copy);
        return wrap;
    }

    private String fullReport() {
        StringBuilder out = new StringBuilder(report);
        out.append("\n手动测试：\n");
        for (CheckBox check : manualChecks) {
            out.append(check.isChecked() ? "[通过] " : "[未测] ")
                    .append(check.getText()).append('\n');
        }
        return out.toString();
    }

    private TextView action(String text, final Runnable runnable) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(TEXT);
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setBackground(roundRect(0xFF1C1C1E, 18, 0, 0));
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runnable.run();
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        params.bottomMargin = dp(8);
        view.setLayoutParams(params);
        return view;
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (Exception exception) {
            Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean notificationAllowed() {
        if (Build.VERSION.SDK_INT < 33) {
            return true;
        }
        return checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private boolean batteryIgnored() {
        try {
            PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return power != null && power.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean adbEnabled() {
        try {
            return Settings.Global.getInt(getContentResolver(),
                    Settings.Global.ADB_ENABLED, 0) == 1;
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean devOptionsEnabled() {
        try {
            return Settings.Global.getInt(getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1;
        } catch (Exception exception) {
            return false;
        }
    }

    private String prop(String name) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"getprop", name});
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            reader.close();
            process.waitFor();
            return line == null ? "" : line.trim();
        } catch (Exception exception) {
            return "";
        }
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(MUTED);
        view.setTextSize(12);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setPadding(0, dp(14), 0, dp(6));
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
        params.bottomMargin = dp(10);
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
