package com.iman.onestateaimcoach;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
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

public class OverlayService extends Service
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String CHANNEL_ID = "aim_coach_overlay";
    private static final String PREFS = "coach_settings";
    private static final int BUBBLE_SIZE_DP = 46;

    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private LinearLayout panel;
    private LinearLayout header;
    private LinearLayout details;
    private TextView title;
    private TextView collapse;
    private TextView close;
    private TextView cameraValue;
    private TextView aimValue;
    private TextView recoilValue;
    private boolean collapsed = false;
    private float downRawX;
    private float downRawY;
    private int downX;
    private int downY;
    private boolean dragging = false;
    private SharedPreferences preferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable metricsUpdater = new Runnable() {
        @Override public void run() {
            updateValues();
            if (panel != null) mainHandler.postDelayed(this, 700);
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
                .setContentText("پنل کوچک تنظیمات آماده است")
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
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            stopSelf();
            return;
        }

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(9), dp(12), dp(10));
        panel.setBackground(panelBackground(false));

        header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        title = label("AIM COACH", 13, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(34), 1f));

        collapse = action("−");
        header.addView(collapse, new LinearLayout.LayoutParams(dp(34), dp(34)));

        close = action("×");
        header.addView(close, new LinearLayout.LayoutParams(dp(34), dp(34)));
        panel.addView(header);

        details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);

        cameraValue = label("", 12, Color.WHITE, false);
        aimValue = label("", 12, Color.WHITE, false);
        recoilValue = label("", 12, Color.WHITE, false);
        details.addView(cameraValue);
        details.addView(aimValue);
        details.addView(recoilValue);

        TextView hint = label("تنظیمات مرجع؛ بدون کنترل ورودی بازی", 10,
                Color.LTGRAY, false);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-2, -2);
        hintParams.topMargin = dp(5);
        details.addView(hint, hintParams);
        panel.addView(details);

        collapse.setOnClickListener(v -> setCollapsed(true));
        close.setOnClickListener(v -> stopSelf());
        attachDrag(title, false);
        
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
        setCollapsed(true); // Start as a compact circle so the game view stays clear.

        updateValues();
        mainHandler.post(metricsUpdater);
    }

    private GradientDrawable panelBackground(boolean circle) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(226, 21, 31, 45));
        bg.setCornerRadius(circle ? dp(BUBBLE_SIZE_DP) : dp(14));
        if (circle) bg.setShape(GradientDrawable.OVAL);
        bg.setStroke(dp(1), Color.rgb(83, 111, 139));
        return bg;
    }

    private void setCollapsed(boolean value) {
        if (panel == null || params == null || collapsed == value) return;
        collapsed = value;

        if (collapsed) {
            details.setVisibility(View.GONE);
            title.setVisibility(View.GONE);
            close.setVisibility(View.GONE);
            collapse.setText("A");
            collapse.setTextSize(16);
            collapse.setPadding(0, 0, 0, 0);
            collapse.setBackgroundColor(Color.TRANSPARENT);
            LinearLayout.LayoutParams bubbleParams =
                    new LinearLayout.LayoutParams(dp(BUBBLE_SIZE_DP), dp(BUBBLE_SIZE_DP));
            collapse.setLayoutParams(bubbleParams);
            collapse.setOnClickListener(null);
            attachDrag(collapse, true);

            panel.setPadding(0, 0, 0, 0);
            panel.setBackground(panelBackground(true));
            header.setGravity(Gravity.CENTER);
            header.setLayoutParams(new LinearLayout.LayoutParams(
                    dp(BUBBLE_SIZE_DP), dp(BUBBLE_SIZE_DP)));
            params.width = dp(BUBBLE_SIZE_DP);
            params.height = dp(BUBBLE_SIZE_DP);
        } else {
            details.setVisibility(View.VISIBLE);
            title.setVisibility(View.VISIBLE);
            close.setVisibility(View.VISIBLE);
            collapse.setText("−");
            collapse.setTextSize(19);
            collapse.setPadding(dp(8), 0, dp(8), 0);
            collapse.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(34)));
            collapse.setOnTouchListener(null);
            collapse.setOnClickListener(v -> setCollapsed(true));
            attachDrag(title, false);

            panel.setPadding(dp(12), dp(9), dp(12), dp(10));
            panel.setBackground(panelBackground(false));
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroupLayout.MATCH_PARENT, dp(34)));
            params.width = WindowManager.LayoutParams.WRAP_CONTENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        }

        try { windowManager.updateViewLayout(panel, params); } catch (Exception ignored) {}
    }

    private void attachDrag(View handle, boolean tapToExpand) {
        handle.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    downX = params.x;
                    downY = params.y;
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downRawX;
                    float dy = event.getRawY() - downRawY;
                    if (Math.abs(dx) > dp(3) || Math.abs(dy) > dp(3)) dragging = true;
                    if (dragging) {
                        params.x = downX + Math.round(dx);
                        params.y = downY + Math.round(dy);
                        try { windowManager.updateViewLayout(panel, params); }
                        catch (Exception ignored) {}
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!dragging && tapToExpand) setCollapsed(false);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return false;
            }
        });
    }

    private void updateValues() {
        if (preferences == null || cameraValue == null) return;
        cameraValue.setText("دوربین: " +
                preferences.getInt("camera_sensitivity", 50) + "/100");
        aimValue.setText("نشانه: " +
                preferences.getInt("aim_sensitivity", 50) + "/100");
        recoilValue.setText("کنترل لگد: " +
                preferences.getInt("recoil_reminder", 50) + "/100");
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        updateValues();
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) text.setTypeface(null, android.graphics.Typeface.BOLD);
        return text;
    }

    private TextView action(String value) {
        TextView text = label(value, 19, Color.WHITE, true);
        text.setGravity(Gravity.CENTER);
        text.setPadding(dp(8), 0, dp(8), 0);
        return text;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(metricsUpdater);
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(this);
        if (windowManager != null && panel != null) {
            try { windowManager.removeView(panel); } catch (Exception ignored) {}
        }
        panel = null;
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private static final class ViewGroupLayout {
        static final int MATCH_PARENT = -1;
    }
}
