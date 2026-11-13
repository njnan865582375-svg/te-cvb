package com.dsh.lock;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class PasswordDialogs {
    private PasswordDialogs() {
    }

    public static void showSet(final Activity activity, final Runnable onSuccess) {
        final EditText password = passwordField(activity, "新密码（4-8 位数字）");
        final EditText confirm = passwordField(activity, "再输入一遍");
        showForm(activity, "设置解锁密码", "锁机后需要输入这个密码才能退出。",
                new EditText[]{password, confirm}, "保存", new Validator() {
                    @Override
                    public boolean validate(Dialog dialog) {
                        String first = password.getText().toString();
                        String second = confirm.getText().toString();
                        if (!LockPrefs.isValidPassword(first)) {
                            password.setError("请输入 4-8 位数字");
                            return false;
                        }
                        if (!first.equals(second)) {
                            confirm.setError("两次输入的密码不一致");
                            return false;
                        }
                        LockPrefs.setPassword(activity, first);
                        dialog.dismiss();
                        Toast.makeText(activity, "密码已设置", Toast.LENGTH_SHORT).show();
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                        return true;
                    }
                });
    }

    public static void showChange(final Activity activity, final Runnable onSuccess) {
        if (!LockPrefs.hasPassword(activity)) {
            showSet(activity, onSuccess);
            return;
        }
        final EditText oldPassword = passwordField(activity, "原密码");
        final EditText newPassword = passwordField(activity, "新密码（4-8 位数字）");
        final EditText confirm = passwordField(activity, "再输入一遍新密码");
        showForm(activity, "修改解锁密码", "需要先输入原密码。",
                new EditText[]{oldPassword, newPassword, confirm}, "修改", new Validator() {
                    @Override
                    public boolean validate(Dialog dialog) {
                        String oldValue = oldPassword.getText().toString();
                        String newValue = newPassword.getText().toString();
                        String confirmValue = confirm.getText().toString();
                        if (!LockPrefs.checkPassword(activity, oldValue)) {
                            oldPassword.setError("原密码不正确");
                            return false;
                        }
                        if (!LockPrefs.isValidPassword(newValue)) {
                            newPassword.setError("请输入 4-8 位数字");
                            return false;
                        }
                        if (!newValue.equals(confirmValue)) {
                            confirm.setError("两次输入的密码不一致");
                            return false;
                        }
                        LockPrefs.setPassword(activity, newValue);
                        dialog.dismiss();
                        Toast.makeText(activity, "密码已修改", Toast.LENGTH_SHORT).show();
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                        return true;
                    }
                });
    }

    private interface Validator {
        boolean validate(Dialog dialog);
    }

    private static void showForm(final Activity activity, String title, String subtitle,
                                 EditText[] fields, String positive, final Validator validator) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 22), dp(activity, 20), dp(activity, 22), dp(activity, 20));
        root.setBackground(roundRect(activity, 0xFF1C1C1E, 24, 0));

        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(20);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        root.addView(titleView);

        TextView subtitleView = new TextView(activity);
        subtitleView.setText(subtitle);
        subtitleView.setTextColor(0xFF8E8E93);
        subtitleView.setTextSize(13);
        subtitleView.setPadding(0, dp(activity, 5), 0, dp(activity, 8));
        root.addView(subtitleView);

        for (EditText field : fields) {
            root.addView(field);
        }

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        buttons.setPadding(0, dp(activity, 18), 0, 0);

        TextView cancel = button(activity, "取消", false);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                0, dp(activity, 46), 1f);
        cancelParams.rightMargin = dp(activity, 10);
        buttons.addView(cancel, cancelParams);

        TextView confirm = button(activity, positive, true);
        confirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                validator.validate(dialog);
            }
        });
        buttons.addView(confirm, new LinearLayout.LayoutParams(
                0, dp(activity, 46), 1f));
        root.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.62f;
            params.width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.90f);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }
        dialog.show();

        Window shown = dialog.getWindow();
        if (shown != null) {
            shown.setLayout(
                    (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.90f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private static EditText passwordField(Activity activity, String hint) {
        EditText field = new EditText(activity);
        field.setHint(hint);
        field.setHintTextColor(0xFF7C7C80);
        field.setTextColor(Color.WHITE);
        field.setTextSize(16);
        field.setSingleLine(true);
        field.setLetterSpacing(0.10f);
        field.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        field.setBackground(roundRect(activity, 0xFF2A2A2C, 12, 0));
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        field.setTransformationMethod(PasswordTransformationMethod.getInstance());
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 50));
        params.topMargin = dp(activity, 10);
        field.setLayoutParams(params);
        return field;
    }

    private static TextView button(Activity activity, String text, boolean primary) {
        TextView button = new TextView(activity);
        button.setText(text);
        button.setTextSize(15);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(primary ? Color.BLACK : Color.WHITE);
        button.setBackground(roundRect(activity, primary ? Color.WHITE : 0xFF2C2C2E, 23, 0));
        button.setClickable(true);
        return button;
    }

    private static GradientDrawable roundRect(Activity activity, int color,
                                               int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(activity, radiusDp));
        if (strokeColor != 0) {
            drawable.setStroke(1, strokeColor);
        }
        return drawable;
    }

    private static int dp(Activity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
