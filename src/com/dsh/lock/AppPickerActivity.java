package com.dsh.lock;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AppPickerActivity extends Activity {
    private static final int BG = 0xFF000000;
    private static final int CARD = 0xFF111113;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF8E8E93;
    private static final int LINE = 0x1FFFFFFF;

    private final List<CheckBox> checks = new ArrayList<>();
    private final List<String> packages = new ArrayList<>();
    private TextView hintView;
    private ScrollView appScroll;
    private Switch freezeAllSwitch;

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

        LinearLayout allCard = new LinearLayout(this);
        allCard.setOrientation(LinearLayout.HORIZONTAL);
        allCard.setGravity(Gravity.CENTER_VERTICAL);
        allCard.setPadding(dp(14), dp(12), dp(10), dp(12));
        allCard.setBackground(roundRect(CARD, 16));
        TextView allTitle = new TextView(this);
        allTitle.setText("全部冻结");
        allTitle.setTextColor(TEXT);
        allTitle.setTextSize(15.5f);
        allTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        allCard.addView(allTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        freezeAllSwitch = new Switch(this);
        freezeAllSwitch.setChecked(FreezePrefs.isFreezeAll(this));
        freezeAllSwitch.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean checked) {
                        FreezePrefs.setFreezeAll(AppPickerActivity.this, checked);
                        updateFreezeAllUi(checked);
                    }
                });
        allCard.addView(freezeAllSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        LinearLayout.LayoutParams allParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        allParams.topMargin = dp(12);
        root.addView(allCard, allParams);

        hintView = new TextView(this);
        hintView.setText("锁机时禁用这些 App，解锁后自动恢复。");
        hintView.setTextColor(MUTED);
        hintView.setTextSize(12);
        hintView.setPadding(0, dp(10), 0, dp(10));
        root.addView(hintView);

        appScroll = new ScrollView(this);
        appScroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        appScroll.addView(list);
        root.addView(appScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        buildAppList(list);
        updateFreezeAllUi(freezeAllSwitch.isChecked());

        TextView save = new TextView(this);
        save.setText("保存");
        save.setTextColor(Color.BLACK);
        save.setTextSize(15);
        save.setGravity(Gravity.CENTER);
        save.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        save.setBackground(roundRect(Color.WHITE, 18));
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveSelection();
            }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        sp.topMargin = dp(12);
        root.addView(save, sp);
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
        TextView title = new TextView(this);
        title.setText("锁机时冻结的 App");
        title.setTextColor(TEXT);
        title.setTextSize(22);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private void buildAppList(LinearLayout list) {
        Set<String> selected = new HashSet<>();
        JSONArray saved = FreezePrefs.getPackages(this);
        for (int i = 0; i < saved.length(); i++) {
            try {
                selected.add(saved.getString(i));
            } catch (Exception ignored) {
            }
        }

        PackageManager manager = getPackageManager();
        List<ApplicationInfo> apps = manager.getInstalledApplications(0);
        List<ApplicationInfo> visible = new ArrayList<>();
        for (ApplicationInfo app : apps) {
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                continue;
            }
            if (app.packageName.equals(getPackageName())) {
                continue;
            }
            if (app.packageName.equals("moe.shizuku.privileged.api")
                    || app.packageName.equals("com.topjohnwu.magisk")) {
                continue;
            }
            if (manager.getLaunchIntentForPackage(app.packageName) == null) {
                continue;
            }
            visible.add(app);
        }
        Collections.sort(visible, new Comparator<ApplicationInfo>() {
            @Override
            public int compare(ApplicationInfo a, ApplicationInfo b) {
                return a.loadLabel(getPackageManager()).toString()
                        .compareToIgnoreCase(b.loadLabel(getPackageManager()).toString());
            }
        });

        if (visible.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("没有可选择的第三方 App（Shizuku/root 管理器不会列出）");
            empty.setTextColor(MUTED);
            empty.setTextSize(14);
            empty.setPadding(dp(4), dp(20), 0, 0);
            list.addView(empty);
            return;
        }

        for (ApplicationInfo app : visible) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(10), dp(10), dp(10));
            row.setBackground(roundRect(CARD, 16));
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView name = new TextView(this);
            name.setText(app.loadLabel(manager));
            name.setTextColor(TEXT);
            name.setTextSize(14.5f);
            texts.addView(name);
            TextView pkg = new TextView(this);
            pkg.setText(app.packageName);
            pkg.setTextColor(MUTED);
            pkg.setTextSize(10.5f);
            texts.addView(pkg);
            row.addView(texts, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            CheckBox check = new CheckBox(this);
            check.setChecked(selected.contains(app.packageName));
            row.addView(check, new LinearLayout.LayoutParams(dp(44), dp(40)));
            checks.add(check);
            packages.add(app.packageName);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(8);
            list.addView(row, params);
        }
    }

    private void updateFreezeAllUi(boolean enabled) {
        if (hintView != null) {
            hintView.setVisibility(enabled ? View.GONE : View.VISIBLE);
        }
        if (appScroll != null) {
            appScroll.setVisibility(enabled ? View.GONE : View.VISIBLE);
        }
    }

    private void saveSelection() {
        boolean all = freezeAllSwitch != null && freezeAllSwitch.isChecked();
        FreezePrefs.setFreezeAll(this, all);
        JSONArray array = new JSONArray();
        for (int i = 0; i < checks.size(); i++) {
            if (checks.get(i).isChecked()) {
                array.put(packages.get(i));
            }
        }
        FreezePrefs.setPackages(this, array);
        Toast.makeText(this, all ? "已开启全部冻结" : ("已保存 " + array.length() + " 个"),
                Toast.LENGTH_SHORT).show();
        finish();
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), LINE);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
