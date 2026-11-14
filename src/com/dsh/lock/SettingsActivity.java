package com.dsh.lock;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class SettingsActivity extends Activity {
    private static final int REQUEST_MODE = 11;

    private LinearLayout rows;
    private TextView modeSubtitle;
    private TextView passwordSubtitle;
    private TextView remoteSubtitle;
    private TextView freezeSubtitle;
    private Switch floatingSwitch;
    private boolean updatingSwitch;
    private boolean pendingFloating;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(20), dp(14), dp(20), dp(14));

        root.addView(buildHeader());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, dp(14), 0, dp(14));
        scroll.addView(rows, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView version = new TextView(this);
        version.setText("TE cvb 3.7");
        version.setTextColor(0xFF636366);
        version.setTextSize(11);
        version.setGravity(Gravity.CENTER);
        root.addView(version, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        buildRows();
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
        title.setText("设置");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titles.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("TE cvb");
        subtitle.setTextColor(0xFF8E8E93);
        subtitle.setTextSize(12.5f);
        subtitle.setPadding(0, dp(3), 0, 0);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return header;
    }

    private void buildRows() {
        modeSubtitle = addTextRow("模式", LockModes.name(LockPrefs.getMode(this)), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(SettingsActivity.this, ModePickerActivity.class);
                intent.putExtra(ModePickerActivity.EXTRA_FIRST_RUN, false);
                startActivityForResult(intent, REQUEST_MODE);
            }
        }, true);

        passwordSubtitle = addTextRow("解锁密码", passwordSummary(),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        PasswordDialogs.showChange(SettingsActivity.this, new Runnable() {
                            @Override
                            public void run() {
                                refreshSummaries();
                            }
                        });
                    }
                }, true);

        remoteSubtitle = addTextRow("远程锁机", remoteSummary(),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        startActivity(new Intent(SettingsActivity.this, RemoteActivity.class));
                    }
                }, true);

        freezeSubtitle = addTextRow("锁机时冻结的 App", freezeSummary(),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        startActivity(new Intent(SettingsActivity.this, AppPickerActivity.class));
                    }
                }, true);

        addTextRow("安全自测", "检查 BL / ADB / 权限 / 设备所有者",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        startActivity(new Intent(SettingsActivity.this, SelfTestActivity.class));
                    }
                }, true);

        addTextRow("设备所有者设置", "恢复出厂 + ADB 设置向导",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        startActivity(new Intent(SettingsActivity.this,
                                DeviceOwnerSetupActivity.class));
                    }
                }, true);

        addTextRow("救援中心", "设备所有者 / 强力模式 / 冻结恢复",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        startActivity(new Intent(SettingsActivity.this,
                                RecoveryActivity.class));
                    }
                }, true);

        addFloatingRow();

        addTextRow("社区", "QQ 群 / QQ 频道",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        showCommunity();
                    }
                }, true);

        addTextRow("开发人员名单", "Nan",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        showDeveloperInfo();
                    }
                }, true);
    }

    private TextView addTextRow(String title, String subtitle, View.OnClickListener listener,
                                boolean clickable) {
        LinearLayout card = card();
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(15.5f);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        texts.addView(titleView);
        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextColor(0xFF8E8E93);
        subtitleView.setTextSize(12);
        subtitleView.setPadding(0, dp(3), 0, 0);
        texts.addView(subtitleView);
        card.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (clickable) {
            TextView arrow = new TextView(this);
            arrow.setText("›");
            arrow.setTextColor(0xFF636366);
            arrow.setTextSize(24);
            arrow.setGravity(Gravity.CENTER);
            card.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(40)));
            card.setClickable(true);
            card.setOnClickListener(listener);
        }
        addCard(card);
        return subtitleView;
    }

    private void addFloatingRow() {
        LinearLayout card = card();
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("悬浮窗一键锁机");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15.5f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        texts.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("桌面悬浮按钮，点一下直接锁");
        subtitle.setTextColor(0xFF8E8E93);
        subtitle.setTextSize(12);
        subtitle.setPadding(0, dp(3), 0, 0);
        texts.addView(subtitle);
        card.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        floatingSwitch = new Switch(this);
        floatingSwitch.setShowText(false);
        floatingSwitch.setChecked(LockPrefs.isFloatingEnabled(this));
        floatingSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (updatingSwitch) {
                    return;
                }
                if (isChecked) {
                    enableFloating();
                } else {
                    disableFloating();
                }
            }
        });
        card.addView(floatingSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                floatingSwitch.setChecked(!floatingSwitch.isChecked());
            }
        });
        addCard(card);
    }

    private void enableFloating() {
        if (!LockPrefs.hasPassword(this)) {
            Toast.makeText(this, "请先在“解锁密码”里设置密码", Toast.LENGTH_SHORT).show();
            setSwitch(false);
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            pendingFloating = true;
            setSwitch(false);
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception exception) {
                pendingFloating = false;
                Toast.makeText(this, "无法打开悬浮窗权限设置", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        LockPrefs.setFloatingEnabled(this, true);
        OverlayLockService.start(this, OverlayLockService.ACTION_SHOW_BUTTON);
        setSwitch(true);
        Toast.makeText(this, "悬浮锁机按钮已开启", Toast.LENGTH_SHORT).show();
    }

    private void disableFloating() {
        LockPrefs.setFloatingEnabled(this, false);
        try {
            OverlayLockService.start(this, OverlayLockService.ACTION_HIDE_BUTTON);
        } catch (Exception ignored) {
        }
        setSwitch(false);
    }

    private void setSwitch(boolean checked) {
        updatingSwitch = true;
        floatingSwitch.setChecked(checked);
        updatingSwitch = false;
    }

    private void refreshSummaries() {
        modeSubtitle.setText(LockModes.name(LockPrefs.getMode(this)));
        passwordSubtitle.setText(passwordSummary());
        if (remoteSubtitle != null) {
            remoteSubtitle.setText(remoteSummary());
        }
    }

    private String freezeSummary() {
        if (FreezePrefs.isFreezeAll(this)) {
            return "全部冻结已开启";
        }
        int count = FreezePrefs.count(this);
        return count == 0 ? "未选择；Shizuku/root 锁机时禁用，解锁恢复"
                : ("已选 " + count + " 个；Shizuku/root 锁机时禁用，解锁恢复");
    }

    private String remoteSummary() {
        if (!RemotePrefs.isConfigured(this)) {
            return "SSH 服务器 · 未配置";
        }
        String role = RemotePrefs.getRole(this) == RemotePrefs.ROLE_CONTROLLER
                ? "控制端" : "被锁机端";
        return role + " · " + RemotePrefs.getHost(this);
    }

    private String passwordSummary() {
        if (!LockPrefs.hasPassword(this)) {
            return "未设置 · 开始锁机前会要求设置";
        }
        return "已设置 · " + LockPrefs.getPasswordLength(this) + " 位数字";
    }

    private void showCommunity() {
        AppDialogs.message(this, "社区",
                "QQ 官方群聊：201943803\n\nQQ 官方频道：pd17789932",
                "知道了");
    }

    private void showDeveloperInfo() {
        AppDialogs.confirm(this, "开发人员名单", "Nan\nQQ：865582375",
                "关闭", "复制 QQ", new AppDialogs.OnClick() {
                    @Override
                    public void onClick() {
                        ClipboardManager clipboard = (ClipboardManager)
                                getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("QQ", "865582375"));
                            Toast.makeText(SettingsActivity.this,
                                    "QQ 已复制", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(14), dp(14));
        card.setBackground(roundRect(0xFF111113, 18, 0x1FFFFFFF));
        return card;
    }

    private void addCard(View card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        rows.addView(card, params);
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MODE && resultCode == RESULT_OK && data != null) {
            int mode = data.getIntExtra(ModePickerActivity.EXTRA_MODE, LockModes.UNSET);
            if (LockModes.isValid(mode)) {
                LockPrefs.setMode(this, mode);
                refreshSummaries();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (freezeSubtitle != null) {
            freezeSubtitle.setText(freezeSummary());
        }
        if (modeSubtitle != null) {
            refreshSummaries();
        }
        if (floatingSwitch != null) {
            setSwitch(LockPrefs.isFloatingEnabled(this));
        }
        if (pendingFloating) {
            pendingFloating = false;
            if (Settings.canDrawOverlays(this)) {
                LockPrefs.setFloatingEnabled(this, true);
                OverlayLockService.start(this, OverlayLockService.ACTION_SHOW_BUTTON);
                setSwitch(true);
            } else {
                Toast.makeText(this, "没有获得悬浮窗权限", Toast.LENGTH_SHORT).show();
                setSwitch(false);
            }
        }
    }
}
