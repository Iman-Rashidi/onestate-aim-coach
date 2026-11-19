package com.iman.onestateaimcoach;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class OverlayService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String CHANNEL_ID = "aim_coach_overlay";
    private static final String PREFS = "coach_settings";
    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private WindowManager.LayoutParams drawingParams;
    private LinearLayout panel;
    private LinearLayout details;
    private TextView cameraValue;
    private TextView aimValue;
    private TextView recoilValue;
    private TextView motionValue;
    private DetectionDrawingView detectionView;
    private boolean collapsed = false;
    private float downRawX, downRawY;
    private int downX, downY;
    private SharedPreferences preferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable metricsUpdater = new Runnable() {
        @Override public void run() {
            updateValues();
            if (panel != null) mainHandler.postDelayed(this, 350);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        preferences.registerOnSharedPreferenceChangeListener(this);
        createNotificationChannel();
        startAsForeground();
        if (Settings.canDrawOverlays(this)) createPanel();
        else stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (panel == null && Settings.canDrawOverlays(this)) createPanel();
        return START_NOT_STICKY;
    }

    private void startAsForeground() {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle("OneState Aim Coach فعال است")
                .setContentText("پنل تنظیمات و بازخورد تصویری آماده است")
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "پنل شناور Aim Coach", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("اعلان ماندگاری پنل شناور");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void createPanel() {
        if (panel != null) return;
        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (windowManager == null) {
                stopSelf();
                return;
            }
        }

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(9), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(232, 21, 31, 45));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.rgb(83, 111, 139));
        panel.setBackground(bg);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("AIM COACH", 13, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(34), 1));
        TextView collapse = action("−");
        header.addView(collapse);
        TextView close = action("×");
        header.addView(close);
        panel.addView(header);

        details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        cameraValue = label("", 12, Color.WHITE, false);
        aimValue = label("", 12, Color.WHITE, false);
        recoilValue = label("", 12, Color.WHITE, false);
        motionValue = label("", 12, Color.rgb(132, 225, 178), true);
        details.addView(cameraValue);
        details.addView(aimValue);
        details.addView(recoilValue);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-2, -2);
        mp.topMargin = dp(6);
        details.addView(motionValue, mp);
        TextView hint = label("کادرها: بازیکن/شخص احتمالی، نه تشخیص قطعی دشمن", 10, Color.LTGRAY, false);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-2, -2);
        hp.topMargin = dp(5);
        details.addView(hint, hp);
        panel.addView(details);

        collapse.setOnClickListener(v -> {
            collapsed = !collapsed;
            details.setVisibility(collapsed ? View.GONE : View.VISIBLE);
            collapse.setText(collapsed ? "+" : "−");
        });
        close.setOnClickListener(v -> stopSelf());
        header.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    downX = params.x;
                    downY = params.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    params.x = downX + Math.round(event.getRawX() - downRawX);
                    params.y = downY + Math.round(event.getRawY() - downRawY);
                    try { windowManager.updateViewLayout(panel, params); } catch (Exception ignored) {}
                    return true;
                default:
                    return false;
            }
        });

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(16);
        params.y = dp(100);
        windowManager.addView(panel, params);

        detectionView = new DetectionDrawingView();
        drawingParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        drawingParams.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(detectionView, drawingParams);

        updateValues();
        mainHandler.post(metricsUpdater);
    }

    private void updateValues() {
        if (preferences == null || cameraValue == null) return;
        cameraValue.setText("دوربین: " + preferences.getInt("camera_sensitivity", 50) + "/100");
        aimValue.setText("نشانه: " + preferences.getInt("aim_sensitivity", 50) + "/100");
        recoilValue.setText("کنترل لگد: " + preferences.getInt("recoil_reminder", 50) + "/100");
        if (motionValue != null) {
            if (ScreenMotionState.active) {
                ScreenMotionState.PlayerBox[] boxes = ScreenMotionState.detectedPlayers;
                int count = boxes == null ? 0 : boxes.length;
                motionValue.setText("تغییر تصویر: " +
                        String.format(Locale.US, "%.1f", ScreenMotionState.motionScore) +
                        " | کاندیداها: " + count);
            } else {
                motionValue.setText("تحلیل زنده: خاموش");
            }
        }
        if (detectionView != null) detectionView.invalidate();
    }

    private final class DetectionDrawingView extends View {
        private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelBackground = new Paint(Paint.ANTI_ALIAS_FLAG);

        DetectionDrawingView() {
            super(OverlayService.this);
            boxPaint.setColor(Color.rgb(255, 70, 70));
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setStrokeWidth(dp(2));
            labelPaint.setColor(Color.WHITE);
            labelPaint.setTextSize(dp(11));
            labelPaint.setTypeface(android.graphics.Typeface.create(
                    "sans-serif-medium", android.graphics.Typeface.BOLD));
            labelBackground.setColor(Color.argb(220, 145, 28, 28));
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            ScreenMotionState.PlayerBox[] boxes = ScreenMotionState.detectedPlayers;
            if (boxes == null) return;
            float canvasWidth = getWidth();
            float canvasHeight = getHeight();

            for (ScreenMotionState.PlayerBox box : boxes) {
                RectF rect = new RectF(
                        box.left * canvasWidth,
                        box.top * canvasHeight,
                        box.right * canvasWidth,
                        box.bottom * canvasHeight);
                if (rect.width() < dp(8) || rect.height() < dp(12)) continue;
                canvas.drawRoundRect(rect, dp(5), dp(5), boxPaint);

                String caption = String.format(Locale.US, "PLAYER? %d%%",
                        Math.round(box.confidence * 100f));
                float textWidth = labelPaint.measureText(caption);
                float labelTop = Math.max(dp(20), rect.top - dp(4));
                RectF bg = new RectF(
                        rect.left,
                        labelTop - dp(18),
                        rect.left + textWidth + dp(10),
                        labelTop);
                canvas.drawRoundRect(bg, dp(4), dp(4), labelBackground);
                canvas.drawText(caption, rect.left + dp(5), labelTop - dp(5), labelPaint);
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        updateValues();
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private TextView action(String value) {
        TextView t = label(value, 19, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), 0, dp(10), 0);
        return t;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(metricsUpdater);
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(this);
        if (windowManager != null && detectionView != null) {
            try { windowManager.removeView(detectionView); } catch (Exception ignored) {}
        }
        if (windowManager != null && panel != null) {
            try { windowManager.removeView(panel); } catch (Exception ignored) {}
        }
        detectionView = null;
        panel = null;
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
