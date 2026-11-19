package com.iman.onestateaimcoach;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String PREFS = "coach_settings";
    private TextView overlayStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(15, 23, 34));
        getWindow().setNavigationBarColor(Color.rgb(15, 23, 34));

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        root.setBackgroundColor(Color.rgb(15, 23, 34));
        scroll.addView(root);

        TextView title = text("OneState Aim Coach", 26, Color.WHITE, true);
        root.addView(title);
        TextView subtitle = text("ابزار کمکی تنظیمات و تمرکز حین بازی", 15, Color.LTGRAY, false);
        addTop(root, subtitle, 5);

        TextView online = text("حالت استفاده: قابل استفاده هنگام اجرای بازی", 14, Color.rgb(121, 220, 170), true);
        addTop(root, online, 18);

        addSlider(root, "حساسیت دوربین (مرجع شخصی)", "camera_sensitivity", 50);
        addSlider(root, "حساسیت نشانه‌گیری (مرجع شخصی)", "aim_sensitivity", 50);
        addSlider(root, "شدت یادآوری کنترل لگد", "recoil_reminder", 50);

        TextView noteTitle = text("راهنمای کوتاه کنترل لگد", 18, Color.WHITE, true);
        addTop(root, noteTitle, 22);
        TextView note = text(
                "• حرکت انگشت را نرم و یکنواخت نگه دار.\n" +
                "• برای هر سلاح، تنظیمات را جداگانه یادداشت کن.\n" +
                "• هنگام شلیک، اصلاح نشانه را فقط دستی انجام بده.\n" +
                "• این برنامه ورودی بازی را کنترل یا شلیک را خودکار نمی‌کند.",
                14, Color.LTGRAY, false);
        addTop(root, note, 8);

        overlayStatus = text("", 14, Color.LTGRAY, false);
        addTop(root, overlayStatus, 20);

        Button start = button("فعال‌کردن پنل شناور روی بازی");
        addTop(root, start, 10);
        start.setOnClickListener(v -> enableOverlay());

        Button stop = button("بستن پنل شناور");
        addTop(root, stop, 8);
        stop.setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));

        TextView limitation = text(
                "این نسخه فقط تنظیمات و راهنمای دستی را نشان می‌دهد. " +
                "هیچ نشانه‌گیری خودکار، شلیک خودکار، خواندن حافظه بازی یا نمایش اطلاعات مخفی ندارد.",
                12, Color.rgb(180, 190, 202), false);
        addTop(root, limitation, 18);

        setContentView(scroll);
        refreshOverlayStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshOverlayStatus();
    }

    private void enableOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(permission);
            return;
        }
        Intent service = new Intent(this, OverlayService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }
        refreshOverlayStatus();
    }

    private void refreshOverlayStatus() {
        if (overlayStatus == null) return;
        overlayStatus.setText(Settings.canDrawOverlays(this)
                ? "مجوز نمایش روی برنامه‌های دیگر فعال است."
                : "برای پنل شناور، مجوز نمایش روی برنامه‌های دیگر لازم است.");
    }

    private void addSlider(LinearLayout root, String caption, String key, int fallback) {
        TextView label = text("", 15, Color.WHITE, true);
        addTop(root, label, 18);
        SeekBar bar = new SeekBar(this);
        bar.setMax(99);
        int current = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(key, fallback);
        bar.setProgress(Math.max(1, Math.min(100, current)) - 1);
        updateSliderLabel(label, caption, bar.getProgress() + 1);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress + 1;
                updateSliderLabel(label, caption, value);
                if (fromUser) getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(key, value).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void updateSliderLabel(TextView label, String caption, int value) {
        label.setText(caption + ": " + value + "/100");
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        return b;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setGravity(Gravity.START);
        return t;
    }

    private void addTop(LinearLayout root, android.view.View view, int marginDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(marginDp);
        root.addView(view, p);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
