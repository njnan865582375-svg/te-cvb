package com.dsh.lock;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public final class RemoteApprovalActivity extends Activity {
    private static final int COLOR_BG = 0xFF000000;
    private static final int COLOR_CARD = 0xFF111113;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFF8E8E93;
    private static final int COLOR_BLACK = 0xFFFFFFFF;

    private String requestId;
    private String from;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestId = getIntent().getStringExtra("request_id");
        from = getIntent().getStringExtra("request_from");
        if (requestId == null) {
            String pending = RemotePrefs.getPending(this);
            if (pending != null) {
                try {
                    JSONObject object = new JSONObject(pending);
                    requestId = object.optString("id", null);
                    from = object.optString("from", "控制端");
                } catch (Exception ignored) {
                }
            }
        }
        if (requestId == null || requestId.length() == 0) {
            Toast.makeText(this, "请求已失效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (from == null || from.length() == 0) {
            from = "控制端";
        }
        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(24), dp(22), dp(20));
        card.setBackground(roundRect(COLOR_CARD, 26));

        TextView title = new TextView(this);
        title.setText("远程锁机请求");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(23);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        card.addView(title);

        TextView message = new TextView(this);
        message.setText(from + " 请求锁住这台设备。\\n\\n同意后，这台设备会立即进入锁机状态，需要你自己的解锁密码才能退出。");
        message.setTextColor(COLOR_MUTED);
        message.setTextSize(14);
        message.setLineSpacing(dp(3), 1f);
        message.setPadding(0, dp(10), 0, dp(8));
        card.addView(message);

        status = new TextView(this);
        status.setTextColor(COLOR_MUTED);
        status.setTextSize(12);
        status.setPadding(0, dp(6), 0, dp(4));
        card.addView(status);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(16), 0, 0);

        TextView deny = button("拒绝", false);
        deny.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                respond(false);
            }
        });
        LinearLayout.LayoutParams denyParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        denyParams.rightMargin = dp(10);
        buttons.addView(deny, denyParams);

        TextView approve = button("同意并锁机", true);
        approve.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                respond(true);
            }
        });
        buttons.addView(approve, new LinearLayout.LayoutParams(0, dp(50), 1f));
        card.addView(buttons);

        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    private void respond(final boolean approved) {
        status.setText(approved ? "正在发送同意结果…" : "正在拒绝…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String error = null;
                try {
                    RemoteSsh.writeResponse(RemoteApprovalActivity.this,
                            RemotePrefs.getDeviceId(RemoteApprovalActivity.this),
                            requestId, approved);
                } catch (Exception exception) {
                    error = exception.getMessage();
                }
                final String message = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (message != null) {
                            status.setText("发送失败：" + message);
                            return;
                        }
                        RemotePrefs.setPending(RemoteApprovalActivity.this, null);
                        if (approved) {
                            Intent intent = new Intent(RemoteApprovalActivity.this, MainActivity.class);
                            intent.putExtra("remote_lock", true);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            startActivity(intent);
                        }
                        finish();
                    }
                });
            }
        }, "te-cvb-approval").start();
    }

    private TextView button(String text, boolean primary) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(primary ? Color.BLACK : COLOR_TEXT);
        view.setBackground(roundRect(primary ? Color.WHITE : 0xFF2C2C2E, 20));
        view.setClickable(true);
        return view;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
