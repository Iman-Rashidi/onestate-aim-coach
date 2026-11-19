package com.iman.onestateaimcoach;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
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
    private static final int REQUEST_OVERLAY = 411;
    private static final int REQUEST_CAPTURE = 412;
    private static final String EXTRA_RESULT = "projection_result";
    private static final String EXTRA_DATA = "projection_data";
    private TextView overlayStatus;
    private TextView analysisStatus;

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

        root.addView(text("OneState Aim Coach", 26, Color.WHITE, true));
        addTop(root, text("مربی زندهٔ تصویر و تنظیمات برای زمان اجرای بازی", 15, Color.LTGRAY, false), 5);
        addTop(root, text("تحلیل محدود حرکت تصویر در مرکز صفحه، بدون تشخیص دشمن یا کنترل بازی", 14, Color.rgb(121, 220, 170), true), 18);

        addSlider(root, "حساسیت دوربین (مرجع شخصی)", "camera_sensitivity", 50);
        addSlider(root, "حساسیت نشانه‌گیری (مرجع شخصی)", "aim_sensitivity", 50);
        addSlider(root, "شدت یادآوری کنترل لگد", "recoil_reminder", 50);

        addTop(root, text("تحلیل زنده هنگام بازی", 18, Color.WHITE, true), 22);
        addTop(root, text(
                "با اجازهٔ ضبط صفحهٔ اندروید، فقط تغییرات تصویری یک ناحیهٔ کوچک در مرکز صفحه اندازه‌گیری می‌شود. " +
                "فریم‌ها ذخیره یا ارسال نمی‌شوند. عدد خروجی شاخص تغییر تصویر است، نه احتمال اصابت یا تشخیص هدف.",
                13, Color.LTGRAY, false), 8);

        Button startAnalysis = button("شروع تحلیل زندهٔ صفحه");
        addTop(root, startAnalysis, 10);
        startAnalysis.setOnClickListener(v -> requestLiveAnalysis());

        Button stopAnalysis = button("توقف تحلیل زنده");
        addTop(root, stopAnalysis, 8);
        stopAnalysis.setOnClickListener(v -> stopService(new Intent(this, ScreenAnalysisService.class)));

        analysisStatus = text("", 13, Color.LTGRAY, false);
        addTop(root, analysisStatus, 8);

        addTop(root, text("پنل شناور", 18, Color.WHITE, true), 22);
        overlayStatus = text("", 13, Color.LTGRAY, false);
        addTop(root, overlayStatus, 8);

        Button startOverlay = button("فعال‌کردن پنل شناور روی بازی");
        addTop(root, startOverlay, 8);
        startOverlay.setOnClickListener(v -> enableOverlay());

        Button stopOverlay = button("بستن پنل شناور");
        addTop(root, stopOverlay, 8);
        stopOverlay.setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));

        addTop(root, text(
                "برنامه ورودی بازی را کنترل نمی‌کند، نشانه را جابه‌جا نمی‌کند، شلیک خودکار ندارد و دشمن یا اطلاعات پنهان را شناسایی نمی‌کند. " +
                "سازگاری و اثر شاخص حرکت باید روی دستگاه واقعی و در بازی آزمایش شود.",
                12, Color.rgb(180, 190, 202), false), 18);

        setContentView(scroll);
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void requestLiveAnalysis() {
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent service = new Intent(this, ScreenAnalysisService.class);
                service.putExtra(EXTRA_RESULT, resultCode);
                service.putExtra(EXTRA_DATA, data);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(service);
                } else {
                    startService(service);
                }
                if (analysisStatus != null) {
                    analysisStatus.setText("اجازه صادر شد؛ تحلیل زنده شروع می‌شود.");
                }
            } else if (analysisStatus != null) {
                analysisStatus.setText("اجازهٔ ضبط صفحه داده نشد؛ تحلیل شروع نشد.");
            }
        }
    }

    private void enableOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(permission, REQUEST_OVERLAY);
            return;
        }
        Intent service = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }
        refreshStatus();
    }

    private void refreshStatus() {
        if (overlayStatus != null) {
            overlayStatus.setText(Settings.canDrawOverlays(this)
                    ? "مجوز نمایش روی برنامه‌های دیگر فعال است."
                    : "برای پنل شناور، مجوز نمایش روی برنامه‌های دیگر لازم است.");
        }
        if (analysisStatus != null) {
            analysisStatus.setText(ScreenMotionState.active
                    ? "تحلیل فعال است: " + ScreenMotionState.level
                    : "تحلیل صفحه خاموش است.");
        }
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
