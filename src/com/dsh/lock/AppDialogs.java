package com.dsh.lock;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class AppDialogs {
    public interface OnClick {
        void onClick();
    }

    private AppDialogs() {
    }

    public static void message(Activity activity, String title, String message,
                               String positiveLabel) {
        show(activity, title, message, null, positiveLabel, null, null);
    }

    public static void confirm(Activity activity, String title, String message,
                               String negativeLabel, String positiveLabel,
                               final OnClick positive) {
        show(activity, title, message, negativeLabel, positiveLabel, positive, null);
    }

    public static void confirm(Activity activity, String title, String message,
                               String negativeLabel, String positiveLabel,
                               final OnClick positive, final OnClick negative) {
        show(activity, title, message, negativeLabel, positiveLabel, positive, negative);
    }

    private static void show(Activity activity, String title, String message,
                             String negativeLabel, String positiveLabel,
                             final OnClick positive, final OnClick negative) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 22), dp(activity, 22), dp(activity, 22), dp(activity, 20));
        root.setBackground(roundRect(activity, 0xFF1C1C1E, 24));

        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(20);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        root.addView(titleView);

        if (!TextUtils.isEmpty(message)) {
            TextView messageView = new TextView(activity);
            messageView.setText(message);
            messageView.setTextColor(0xFFB8B8BD);
            messageView.setTextSize(14.5f);
            messageView.setLineSpacing(dp(activity, 4), 1f);
            messageView.setPadding(0, dp(activity, 10), 0, dp(activity, 4));
            root.addView(messageView);
        }

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(activity, 18), 0, 0);

        if (negativeLabel != null) {
            TextView cancel = pill(activity, negativeLabel, false);
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dialog.dismiss();
                    if (negative != null) {
                        negative.onClick();
                    }
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, dp(activity, 46), 1f);
            lp.rightMargin = dp(activity, 10);
            buttons.addView(cancel, lp);
        }

        TextView ok = pill(activity, positiveLabel, true);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                if (positive != null) {
                    positive.onClick();
                }
            }
        });
        LinearLayout.LayoutParams okParams = new LinearLayout.LayoutParams(
                negativeLabel == null ? ViewGroup.LayoutParams.MATCH_PARENT : 0,
                dp(activity, 46), negativeLabel == null ? 0f : 1f);
        buttons.addView(ok, okParams);
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
        if (window != null) {
            window.setLayout(
                    (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.90f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private static TextView pill(Activity activity, String text, boolean primary) {
        TextView button = new TextView(activity);
        button.setText(text);
        button.setTextSize(15);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(primary ? Color.BLACK : Color.WHITE);
        button.setBackground(roundRect(activity, primary ? Color.WHITE : 0xFF2C2C2E, 23));
        button.setClickable(true);
        return button;
    }

    private static GradientDrawable roundRect(Activity activity, int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(activity, radiusDp));
        return drawable;
    }

    private static int dp(Activity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
