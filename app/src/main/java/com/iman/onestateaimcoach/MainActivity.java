package com.iman.onestateaimcoach;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS = "coach_settings";
    private static final int REQUEST_OVERLAY = 411;
    private static final int REQUEST_VIDEO = 413;

    private TextView overlayStatus;
    private TextView videoStatus;
    private TextView videoResult;
    private ProgressBar videoProgress;
    private Button analyzeButton;
    private Uri selectedVideo;
    private boolean analyzing = false;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();

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
        addTop(root, text("ابزار تنظیمات و تحلیل آفلاین ویدئو", 15, Color.LTGRAY, false), 5);
        addTop(root, text(
                "ویدئوی ضبط‌شدهٔ OneState را انتخاب کن تا فریم‌ها به‌صورت محلی بررسی شوند. " +
                "این نسخه هنگام مسابقه بازیکنان را ردیابی یا علامت‌گذاری نمی‌کند.",
                14, Color.rgb(121, 220, 170), true), 18);

        addSlider(root, "حساسیت دوربین (مرجع شخصی)", "camera_sensitivity", 50);
        addSlider(root, "حساسیت نشانه‌گیری (مرجع شخصی)", "aim_sensitivity", 50);
        addSlider(root, "شدت یادآوری کنترل لگد", "recoil_reminder", 50);

        addTop(root, text("تحلیل ویدئوی ضبط‌شده", 18, Color.WHITE, true), 24);
        addTop(root, text(
                "برای بهتر دیدن اشخاص کوچک، هر فریم با برش‌های هم‌پوشان ۳×۳ بررسی می‌شود. " +
                "این روش ممکن است افراد دورتر را بهتر آشکار کند، اما تشخیص آن‌ها را تضمین نمی‌کند؛ " +
                "مدل همچنان عمومی است و برای شخصیت‌های OneState آموزش ندیده است.",
                13, Color.LTGRAY, false), 8);

        Button selectVideo = button("انتخاب ویدئو از گوشی");
        addTop(root, selectVideo, 8);
        selectVideo.setOnClickListener(v -> chooseVideo());

        videoStatus = text("هنوز ویدئویی انتخاب نشده است.", 13, Color.LTGRAY, false);
        addTop(root, videoStatus, 8);

        analyzeButton = button("شروع تحلیل آفلاین");
        analyzeButton.setEnabled(false);
        addTop(root, analyzeButton, 8);
        analyzeButton.setOnClickListener(v -> startVideoAnalysis());

        videoProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        videoProgress.setMax(100);
        videoProgress.setProgress(0);
        addTop(root, videoProgress, 8);

        videoResult = text("", 13, Color.WHITE, false);
        videoResult.setTextIsSelectable(true);
        addTop(root, videoResult, 10);

        addTop(root, text("پنل شناور تنظیمات", 18, Color.WHITE, true), 24);
        overlayStatus = text("", 13, Color.LTGRAY, false);
        addTop(root, overlayStatus, 8);

        Button startOverlay = button("فعال‌کردن پنل شناور");
        addTop(root, startOverlay, 8);
        startOverlay.setOnClickListener(v -> enableOverlay());

        Button stopOverlay = button("بستن پنل شناور");
        addTop(root, stopOverlay, 8);
        stopOverlay.setOnClickListener(v -> stopService(
                new Intent(this, OverlayService.class)));

        addTop(root, text(
                "پنل شناور در حالت جمع‌شده به شکل دایرهٔ کوچک نمایش داده می‌شود. " +
                "برنامه ورودی بازی، نشانه‌گیری یا شلیک را کنترل نمی‌کند.",
                12, Color.rgb(180, 190, 202), false), 18);

        setContentView(scroll);
        refreshStatus();
    }

    private void chooseVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        startActivityForResult(intent, REQUEST_VIDEO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_VIDEO) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                selectedVideo = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(
                            selectedVideo, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                    // Some document providers do not offer persistable grants.
                }
                analyzeButton.setEnabled(!analyzing);
                videoStatus.setText("ویدئوی انتخاب‌شده: " + getDisplayName(selectedVideo));
                videoResult.setText("");
                videoProgress.setProgress(0);
            }
            return;
        }

        if (requestCode == REQUEST_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                startOverlayService();
                if (overlayStatus != null) {
                    overlayStatus.setText("مجوز فعال شد؛ پنل شناور آماده است.");
                }
            }
            refreshStatus();
        }
    }

    private String getDisplayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return "ویدئوی انتخاب‌شده";
    }

    private void startVideoAnalysis() {
        if (selectedVideo == null || analyzing) return;

        analyzing = true;
        analyzeButton.setEnabled(false);
        videoProgress.setProgress(0);
        videoResult.setText("");
        videoStatus.setText("در حال آماده‌سازی تحلیل محلی...");
        final Uri uri = selectedVideo;

        analysisExecutor.execute(() -> {
            try {
                VideoAnalysisTask.Report report = VideoAnalysisTask.analyze(
                        getApplicationContext(), uri,
                        (processed, total, timestampMs, peopleCount) -> runOnUiThread(() -> {
                            int percent = total <= 0 ? 0 : (processed * 100 / total);
                            videoProgress.setProgress(percent);
                            videoStatus.setText("تحلیل آفلاین: " + percent + "% | فریم " +
                                    processed + " از " + total);
                        }));

                runOnUiThread(() -> {
                    videoProgress.setProgress(100);
                    videoStatus.setText("تحلیل کامل شد: " + getDisplayName(uri));
                    videoResult.setText(report.toDisplayText());
                    analyzing = false;
                    analyzeButton.setEnabled(selectedVideo != null);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    videoStatus.setText("تحلیل انجام نشد.");
                    videoResult.setText("خطا: " +
                            (error.getMessage() == null ? "فایل یا مدل قابل‌خواندن نیست." : error.getMessage()));
                    analyzing = false;
                    analyzeButton.setEnabled(selectedVideo != null);
                });
            }
        });
    }

    private void enableOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(permission, REQUEST_OVERLAY);
            return;
        }
        startOverlayService();
        refreshStatus();
    }

    private void startOverlayService() {
        Intent service = new Intent(this, OverlayService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }
    }

    private void refreshStatus() {
        if (overlayStatus != null) {
            overlayStatus.setText(Settings.canDrawOverlays(this)
                    ? "مجوز نمایش روی برنامه‌های دیگر فعال است."
                    : "برای نمایش پنل شناور، مجوز نمایش روی برنامه‌های دیگر لازم است.");
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
                if (fromUser) getSharedPreferences(PREFS, MODE_PRIVATE)
                        .edit().putInt(key, value).apply();
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

    private void addTop(LinearLayout root, View view, int marginDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(marginDp);
        root.addView(view, p);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        analysisExecutor.shutdownNow();
        super.onDestroy();
    }
}
