package com.dsh.lock;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RemoteActivity extends Activity {
    private static final int COLOR_BG = 0xFF000000;
    private static final int COLOR_CARD = 0xFF111113;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFF8E8E93;
    private static final int COLOR_LINE = 0x1FFFFFFF;
    private static final int COLOR_TRACK = 0xFF1C1C1E;
    private static final int COLOR_FIELD = 0xFF1A1A1C;

    private int role;
    private int auth;

    private EditText nameField;
    private EditText hostField;
    private EditText userField;
    private EditText portField;
    private EditText passwordField;
    private EditText keyField;
    private LinearLayout passwordBlock;
    private LinearLayout keyBlock;
    private TextView passwordSegment;
    private TextView keySegment;
    private TextView roleController;
    private TextView roleControlled;
    private LinearLayout controllerSection;
    private LinearLayout controlledSection;
    private LinearLayout deviceContainer;
    private TextView deviceStatus;
    private Switch masterSwitch;
    private Switch serviceSwitch;
    private TextView serviceStatus;
    private TextView testStatus;

    private final Map<String, RemoteSsh.Device> online = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);
        role = RemotePrefs.getRole(this);
        auth = RemotePrefs.getAuth(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);
        root.setPadding(dp(20), dp(14), dp(20), 0);
        root.addView(buildHeader());

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(12), 0, dp(30));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(sectionLabel("模式"));
        content.addView(buildRoleSegment());
        content.addView(sectionLabel("基本信息"));
        content.addView(buildServerCard());
        content.addView(sectionLabel("登录方式"));
        content.addView(buildAuthCard());
        content.addView(buildActionButtons());
        testStatus = new TextView(this);
        testStatus.setTextColor(COLOR_MUTED);
        testStatus.setTextSize(12);
        testStatus.setPadding(0, dp(8), 0, dp(2));
        content.addView(testStatus);

        controllerSection = buildControllerSection();
        controlledSection = buildControlledSection();
        content.addView(controllerSection);
        content.addView(controlledSection);

        setRoleUi();
        setAuthUi();
        refreshSavedList();
        setContentView(root);
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.TOP);

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextColor(COLOR_TEXT);
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
        title.setText("远程锁机");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(25);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titles.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("控制端保存被锁机，打开开关选择要锁的那台");
        subtitle.setTextColor(COLOR_MUTED);
        subtitle.setTextSize(12.5f);
        subtitle.setPadding(0, dp(3), 0, 0);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return header;
    }

    private View buildRoleSegment() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setPadding(dp(4), dp(4), dp(4), dp(4));
        wrap.setBackground(roundRect(COLOR_TRACK, 22, 0, 0));
        roleController = segment("我是控制端");
        roleControlled = segment("我是被锁机端");
        roleController.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                role = RemotePrefs.ROLE_CONTROLLER;
                setRoleUi();
            }
        });
        roleControlled.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                role = RemotePrefs.ROLE_CONTROLLED;
                setRoleUi();
            }
        });
        wrap.addView(roleController, new LinearLayout.LayoutParams(0, dp(42), 1f));
        wrap.addView(roleControlled, new LinearLayout.LayoutParams(0, dp(42), 1f));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(6);
        wrap.setLayoutParams(params);
        return wrap;
    }

    private TextView segment(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setClickable(true);
        return view;
    }

    private View buildServerCard() {
        LinearLayout card = card();
        nameField = field("例如：家里的电脑", false);
        hostField = field("192.168.1.20 或 example.com", false);
        userField = field("me", false);
        portField = field("22", false);
        portField.setInputType(InputType.TYPE_CLASS_NUMBER);
        portField.setText(String.valueOf(RemotePrefs.getPort(this)));
        nameField.setText(RemotePrefs.getName(this));
        hostField.setText(RemotePrefs.getHost(this));
        userField.setText(RemotePrefs.getUser(this));
        card.addView(fieldBlock("名称", nameField));
        card.addView(fieldBlock("主机", hostField));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        up.rightMargin = dp(12);
        row.addView(fieldBlock("用户名", userField), up);
        row.addView(fieldBlock("端口", portField), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(row);
        return card;
    }

    private View buildAuthCard() {
        LinearLayout card = card();
        LinearLayout segment = new LinearLayout(this);
        segment.setOrientation(LinearLayout.HORIZONTAL);
        segment.setPadding(dp(4), dp(4), dp(4), dp(4));
        segment.setBackground(roundRect(COLOR_TRACK, 21, 0, 0));
        passwordSegment = segment("密码");
        keySegment = segment("私钥");
        passwordSegment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                auth = RemotePrefs.AUTH_PASSWORD;
                setAuthUi();
            }
        });
        keySegment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                auth = RemotePrefs.AUTH_KEY;
                setAuthUi();
            }
        });
        segment.addView(passwordSegment, new LinearLayout.LayoutParams(0, dp(42), 1f));
        segment.addView(keySegment, new LinearLayout.LayoutParams(0, dp(42), 1f));
        card.addView(segment, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        passwordField = field("密码", true);
        passwordField.setText(RemotePrefs.getSecret(this));
        passwordBlock = fieldBlock("密码", passwordField);
        passwordBlock.setPadding(0, dp(10), 0, 0);
        card.addView(passwordBlock);

        keyField = field("粘贴私钥内容", false);
        keyField.setSingleLine(false);
        keyField.setGravity(Gravity.TOP);
        keyField.setMinLines(4);
        keyField.setMaxLines(8);
        keyField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        keyField.setText(RemotePrefs.getKey(this));
        keyBlock = fieldBlock("私钥内容", keyField);
        keyBlock.setPadding(0, dp(10), 0, 0);
        ViewGroup.LayoutParams keyParams = keyField.getLayoutParams();
        if (keyParams != null) {
            keyParams.height = dp(126);
            keyField.setLayoutParams(keyParams);
        }
        card.addView(keyBlock);
        return card;
    }

    private View buildActionButtons() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(14), 0, 0);
        TextView test = pill("测试连接", false);
        test.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                testConnection();
            }
        });
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        tp.rightMargin = dp(10);
        row.addView(test, tp);

        TextView save = pill("保存", true);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (role == RemotePrefs.ROLE_CONTROLLER) {
                    saveController();
                } else {
                    saveControlled();
                }
            }
        });
        row.addView(save, new LinearLayout.LayoutParams(0, dp(50), 1f));
        return row;
    }

    private LinearLayout buildControllerSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);

        LinearLayout masterCard = card();
        LinearLayout masterText = new LinearLayout(this);
        masterText.setOrientation(LinearLayout.VERTICAL);
        TextView masterTitle = new TextView(this);
        masterTitle.setText("远程锁机");
        masterTitle.setTextColor(COLOR_TEXT);
        masterTitle.setTextSize(16);
        masterTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        masterText.addView(masterTitle);
        TextView masterDesc = new TextView(this);
        masterDesc.setText("开启后，点首页“开始测试”会锁选中的被锁机；关闭则锁自己。");
        masterDesc.setTextColor(COLOR_MUTED);
        masterDesc.setTextSize(11.5f);
        masterDesc.setPadding(0, dp(3), 0, 0);
        masterText.addView(masterDesc);
        LinearLayout masterRow = new LinearLayout(this);
        masterRow.setOrientation(LinearLayout.HORIZONTAL);
        masterRow.setGravity(Gravity.CENTER_VERTICAL);
        masterRow.addView(masterText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        masterSwitch = new Switch(this);
        masterSwitch.setChecked(RemotePrefs.isMasterEnabled(this));
        masterSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                RemotePrefs.setMasterEnabled(RemoteActivity.this, masterSwitch.isChecked());
                if (masterSwitch.isChecked() && RemotePrefs.getSelectedId(RemoteActivity.this).length() == 0) {
                    Toast.makeText(RemoteActivity.this,
                            "还没有选择被锁机，打开列表里的开关选一台",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
        masterRow.addView(masterSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        masterCard.addView(masterRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams mcp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mcp.topMargin = dp(18);
        section.addView(masterCard, mcp);

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = sectionLabel("被锁机列表");
        label.setPadding(0, dp(18), 0, dp(4));
        heading.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView scan = new TextView(this);
        scan.setText("扫描");
        scan.setTextColor(COLOR_TEXT);
        scan.setTextSize(13);
        scan.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        scan.setPadding(dp(12), dp(6), 0, dp(6));
        scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scanDevices();
            }
        });
        heading.addView(scan);
        section.addView(heading);

        deviceStatus = new TextView(this);
        deviceStatus.setTextColor(COLOR_MUTED);
        deviceStatus.setTextSize(11.5f);
        deviceStatus.setPadding(0, 0, 0, dp(8));
        section.addView(deviceStatus);

        deviceContainer = new LinearLayout(this);
        deviceContainer.setOrientation(LinearLayout.VERTICAL);
        section.addView(deviceContainer);
        return section;
    }

    private LinearLayout buildControlledSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, dp(18), 0, 0);
        LinearLayout card = card();
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("允许被远程锁机");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(16);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.addView(title);
        TextView desc = new TextView(this);
        desc.setText("保持后台在线；收到锁机请求会先通知你同意。");
        desc.setTextColor(COLOR_MUTED);
        desc.setTextSize(11.5f);
        desc.setPadding(0, dp(3), 0, 0);
        text.addView(desc);
        LinearLayout serviceRow = new LinearLayout(this);
        serviceRow.setOrientation(LinearLayout.HORIZONTAL);
        serviceRow.setGravity(Gravity.CENTER_VERTICAL);
        serviceRow.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        serviceSwitch = new Switch(this);
        serviceSwitch.setChecked(RemotePrefs.isServiceEnabled(this));
        serviceSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (serviceSwitch.isChecked()) {
                    startRemoteService();
                } else {
                    stopRemoteService();
                }
            }
        });
        serviceRow.addView(serviceSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        card.addView(serviceRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        section.addView(card);

        serviceStatus = new TextView(this);
        serviceStatus.setTextColor(COLOR_MUTED);
        serviceStatus.setTextSize(11.5f);
        serviceStatus.setPadding(0, dp(8), 0, 0);
        section.addView(serviceStatus);
        updateServiceUi();
        return section;
    }

    private void setRoleUi() {
        if (role == RemotePrefs.ROLE_CONTROLLER) {
            styleSegment(roleController, true);
            styleSegment(roleControlled, false);
        } else {
            styleSegment(roleController, false);
            styleSegment(roleControlled, true);
        }
        if (controllerSection != null) {
            controllerSection.setVisibility(
                    role == RemotePrefs.ROLE_CONTROLLER ? View.VISIBLE : View.GONE);
        }
        if (controlledSection != null) {
            controlledSection.setVisibility(
                    role == RemotePrefs.ROLE_CONTROLLED ? View.VISIBLE : View.GONE);
        }
    }

    private void setAuthUi() {
        if (passwordBlock == null || keyBlock == null) {
            return;
        }
        if (auth == RemotePrefs.AUTH_PASSWORD) {
            styleSegment(passwordSegment, true);
            styleSegment(keySegment, false);
            passwordBlock.setVisibility(View.VISIBLE);
            keyBlock.setVisibility(View.GONE);
        } else {
            styleSegment(passwordSegment, false);
            styleSegment(keySegment, true);
            passwordBlock.setVisibility(View.GONE);
            keyBlock.setVisibility(View.VISIBLE);
        }
    }

    private void styleSegment(TextView view, boolean selected) {
        if (view == null) {
            return;
        }
        view.setTextColor(selected ? Color.BLACK : COLOR_MUTED);
        view.setBackground(roundRect(selected ? COLOR_TEXT : Color.TRANSPARENT,
                20, 0, 0));
    }

    private boolean saveConfig() {
        String name = nameField.getText().toString().trim();
        String host = hostField.getText().toString().trim();
        String user = userField.getText().toString().trim();
        String portText = portField.getText().toString().trim();
        if (name.length() == 0) {
            Toast.makeText(this, "请填写名称", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (host.length() == 0) {
            Toast.makeText(this, "请填写主机", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (user.length() == 0) {
            Toast.makeText(this, "请填写用户名", Toast.LENGTH_SHORT).show();
            return false;
        }
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (Exception exception) {
            Toast.makeText(this, "端口格式不正确", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (port < 1 || port > 65535) {
            Toast.makeText(this, "端口范围是 1-65535", Toast.LENGTH_SHORT).show();
            return false;
        }
        String secret = passwordField.getText().toString();
        String key = keyField.getText().toString().trim();
        if (auth == RemotePrefs.AUTH_PASSWORD && secret.length() == 0) {
            Toast.makeText(this, "请填写密码", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (auth == RemotePrefs.AUTH_KEY && key.length() == 0) {
            Toast.makeText(this, "请粘贴私钥", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (RemotePrefs.isServiceEnabled(this) && role == RemotePrefs.ROLE_CONTROLLER) {
            stopService(new Intent(this, RemoteService.class));
            RemotePrefs.setServiceEnabled(this, false);
        }
        RemotePrefs.save(this, name, host, port, user, auth, secret, key, role);
        return true;
    }

    private void testConnection() {
        final String snapshot = RemotePrefs.snapshot(this);
        if (!saveConfig()) {
            return;
        }
        testStatus.setText("正在连接 SSH 服务器…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                try {
                    RemoteSsh.test(RemoteActivity.this);
                    message = "连接成功";
                } catch (Exception exception) {
                    message = "连接失败：" + shortMessage(exception);
                }
                RemotePrefs.restore(RemoteActivity.this, snapshot);
                final String result = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (testStatus != null) {
                            testStatus.setText(result);
                        }
                        Toast.makeText(RemoteActivity.this, result, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, "te-cvb-ssh-test").start();
    }

    private void saveController() {
        final String snapshot = RemotePrefs.snapshot(this);
        if (!saveConfig()) {
            return;
        }
        deviceStatus.setText("正在探测被锁机…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<RemoteSsh.Device> devices = null;
                String error = null;
                try {
                    devices = RemoteSsh.listDevices(RemoteActivity.this);
                } catch (Exception exception) {
                    error = shortMessage(exception);
                }
                final List<RemoteSsh.Device> list = devices;
                final String problem = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (problem != null || list == null || list.isEmpty()) {
                            RemotePrefs.restore(RemoteActivity.this, snapshot);
                            refreshSavedList();
                            deviceStatus.setText("未连接：没有探测到被锁机，信息未保存");
                            Toast.makeText(RemoteActivity.this,
                                    "未连接，信息未保存", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        online.clear();
                        for (RemoteSsh.Device device : list) {
                            online.put(device.id, device);
                            RemotePrefs.saveDevice(RemoteActivity.this, device.id, device.name);
                        }
                        refreshSavedList();
                        Toast.makeText(RemoteActivity.this,
                                "已连接，探测到 " + list.size() + " 台", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, "te-cvb-controller-save").start();
    }

    private void saveControlled() {
        final String snapshot = RemotePrefs.snapshot(this);
        if (!saveConfig()) {
            return;
        }
        testStatus.setText("正在连接 SSH 服务器…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String error = null;
                try {
                    RemoteSsh.test(RemoteActivity.this);
                } catch (Exception exception) {
                    error = shortMessage(exception);
                }
                final String problem = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (problem != null) {
                            RemotePrefs.restore(RemoteActivity.this, snapshot);
                            testStatus.setText("未连接：" + problem + "，信息未保存");
                            Toast.makeText(RemoteActivity.this,
                                    "未连接，信息未保存", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        testStatus.setText("已连接");
                        Toast.makeText(RemoteActivity.this, "已保存", Toast.LENGTH_SHORT).show();
                        updateServiceUi();
                    }
                });
            }
        }, "te-cvb-controlled-save").start();
    }

    private void scanDevices() {
        if (role != RemotePrefs.ROLE_CONTROLLER) {
            return;
        }
        saveController();
    }

    private void refreshSavedList() {
        if (deviceContainer == null) {
            return;
        }
        deviceContainer.removeAllViews();
        JSONArray saved = RemotePrefs.getSavedDevices(this);
        int selectedCount = RemotePrefs.selectedCount(this);
        if (saved.length() == 0) {
            deviceStatus.setText("还没有保存的被锁机。点“扫描”把在线的被锁机加进来。");
            return;
        }
        deviceStatus.setText("已保存 " + saved.length() + " 台 · 已选 " + selectedCount + " 台");
        for (int i = 0; i < saved.length(); i++) {
            try {
                JSONObject object = saved.getJSONObject(i);
                final String id = object.optString("id", "");
                final String name = object.optString("name", "被锁机");
                RemoteSsh.Device device = online.get(id);
                boolean isOnline = device != null;
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(14), dp(12), dp(12), dp(12));
                row.setBackground(roundRect(COLOR_CARD, 16, 1, COLOR_LINE));

                LinearLayout texts = new LinearLayout(this);
                texts.setOrientation(LinearLayout.VERTICAL);
                TextView title = new TextView(this);
                title.setText(name);
                title.setTextColor(COLOR_TEXT);
                title.setTextSize(14.5f);
                title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                texts.addView(title);
                TextView status = new TextView(this);
                status.setText(isOnline ? "在线" : "离线");
                status.setTextColor(isOnline ? 0xFF6EE7A8 : COLOR_MUTED);
                status.setTextSize(11);
                status.setPadding(0, dp(2), 0, 0);
                texts.addView(status);
                row.addView(texts, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                Switch check = new Switch(this);
                check.setChecked(RemotePrefs.isSelected(this, id));
                check.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Switch source = (Switch) view;
                        RemotePrefs.setSelected(RemoteActivity.this, id, source.isChecked());
                        int selectedNow = RemotePrefs.selectedCount(RemoteActivity.this);
                        deviceStatus.setText("已保存 " + saved.length() + " 台 · 已选 "
                                + selectedNow + " 台");
                    }
                });
                row.addView(check, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.bottomMargin = dp(8);
                deviceContainer.addView(row, params);
            } catch (Exception ignored) {
            }
        }
    }

    private void startRemoteService() {
        if (!saveConfig()) {
            serviceSwitch.setChecked(false);
            return;
        }
        RemotePrefs.setRole(this, RemotePrefs.ROLE_CONTROLLED);
        RemotePrefs.setServiceEnabled(this, true);
        Intent intent = new Intent(this, RemoteService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        updateServiceUi();
    }

    private void stopRemoteService() {
        RemotePrefs.setServiceEnabled(this, false);
        stopService(new Intent(this, RemoteService.class));
        updateServiceUi();
    }

    private void updateServiceUi() {
        if (serviceStatus == null) {
            return;
        }
        boolean enabled = RemotePrefs.isServiceEnabled(this);
        if (serviceSwitch != null) {
            serviceSwitch.setChecked(enabled);
        }
        serviceStatus.setText(enabled
                ? "服务已开启；控制端请求锁机时会通知你同意。"
                : "未开启；开启后这台设备才会出现在控制端的扫描列表。");
    }

    private TextView sectionLabel(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(COLOR_MUTED);
        view.setTextSize(12.5f);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setPadding(0, dp(16), 0, dp(6));
        return view;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(roundRect(COLOR_CARD, 22, 1, COLOR_LINE));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout fieldBlock(String labelText, EditText field) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(COLOR_TEXT);
        label.setTextSize(13);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        block.addView(label);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        params.topMargin = dp(6);
        block.addView(field, params);
        block.setPadding(0, dp(5), 0, dp(5));
        return block;
    }

    private EditText field(String hint, boolean password) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setHintTextColor(0xFF6E6E73);
        field.setTextColor(COLOR_TEXT);
        field.setTextSize(14.5f);
        field.setSingleLine(true);
        field.setPadding(dp(14), 0, dp(14), 0);
        field.setBackground(roundRect(COLOR_FIELD, 12, 1, COLOR_LINE));
        if (password) {
            field.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            field.setTransformationMethod(PasswordTransformationMethod.getInstance());
        } else {
            field.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        return field;
    }

    private TextView pill(String text, boolean primary) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(primary ? Color.BLACK : COLOR_TEXT);
        view.setBackground(roundRect(primary ? COLOR_TEXT : COLOR_TRACK, 18, 0, 0));
        view.setClickable(true);
        return view;
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

    private String shortMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.length() == 0) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() > 90 ? message.substring(0, 90) : message;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSavedList();
        updateServiceUi();
    }
}
