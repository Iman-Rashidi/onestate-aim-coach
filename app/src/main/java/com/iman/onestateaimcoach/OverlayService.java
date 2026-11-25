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
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.ScrollView;

import java.util.Locale;

public class OverlayService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String CHANNEL_ID = "aim_coach_overlay";
    private static final String PREFS = "coach_settings";
    private static final int BUBBLE_SIZE_DP = 48;
    private static final int BUBBLE_VISIBLE_DP = 40;

    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private WindowManager.LayoutParams drawingParams;
    private FrameLayout panel;
    private LinearLayout expandedPanel;
    private LinearLayout panelContent;
    private ScopeLensView scopeLensView;
    private WindowManager.LayoutParams scopeLensParams;
    private float scopeLensDownX, scopeLensDownY;
    private int scopeLensStartX, scopeLensStartY;
    private TextView bubble;
    private GradientDrawable bubbleBackground;
    private LinearLayout details;
    private TextView cameraValue;
    private TextView aimValue;
    private TextView recoilValue;
    private TextView motionValue;
    private TextView telemetryStatus;
    private CheckBox healthToggle;
    private CheckBox coordinatesToggle;
    private EditText healthValueInput;
    private EditText xValueInput;
    private EditText yValueInput;
    private EditText zValueInput;
    private CheckBox headZoneToggle;
    private CheckBox stickyLockToggle;
    private CheckBox showFovToggle;
    private CheckBox longRangeToggle;
    private CheckBox vehiclesToggle;
    private CheckBox selfFilterToggle;
    private CheckBox edgeAlertsToggle;
    private CheckBox scopePositionToggle;
    private TextView scopeCycleButton;
    private SeekBar confidenceSeekBar;
    private TextView confidenceValueLabel;
    private SeekBar fovSeekBar;
    private SeekBar smoothingSeekBar;
    private TextView fovValueLabel;
    private TextView smoothingValueLabel;
    private OffsetRepo offsetRepo;
    private boolean bindingTelemetry;
    private DetectionDrawingView detectionView;
    private boolean collapsed = true;

    private float downRawX, downRawY;
    private int downX, downY;
    private float bubbleDownRawX, bubbleDownRawY;
    private int bubbleDownX, bubbleDownY;
    private boolean bubbleDragging;
    private boolean bubbleMoved;
    private Runnable longPressRunnable;

    private SharedPreferences preferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable scopeUpdater = new Runnable() {
        @Override public void run() {
            updateScopeLensVisibility();
            if (scopeLensView != null && scopeLensView.getVisibility() == View.VISIBLE) scopeLensView.invalidate();
            if (panel != null) mainHandler.postDelayed(this, 66L);
        }
    };
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
        initializeTelemetryState();
        loadVisionSettings();
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
                .setContentText("پنل شناور کوچک و بازخورد تصویری آماده است")
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

        panel = new FrameLayout(this);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);

        expandedPanel = new LinearLayout(this);
        expandedPanel.setOrientation(LinearLayout.VERTICAL);
        expandedPanel.setPadding(dp(6), dp(6), dp(6), dp(6));
        GradientDrawable expandedBackground = new GradientDrawable();
        expandedBackground.setColor(Color.argb(232, 21, 31, 45));
        expandedBackground.setCornerRadius(dp(14));
        expandedBackground.setStroke(dp(1), Color.rgb(83, 111, 139));
        expandedPanel.setBackground(expandedBackground);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("AIM COACH", 13, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(34), 1));
        TextView collapse = action("−");
        header.addView(collapse);
        TextView close = action("×");
        header.addView(close);
        expandedPanel.addView(header);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setVerticalScrollBarEnabled(true);
        panelContent = new LinearLayout(this);
        panelContent.setOrientation(LinearLayout.VERTICAL);
        panelContent.setPadding(dp(6), dp(3), dp(6), dp(6));
        scrollView.addView(panelContent, new ScrollView.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                Math.max(dp(180), Math.round(screenHeight() * 0.78f) - dp(42)));
        expandedPanel.addView(scrollView, scrollParams);

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
        TextView hint = label("کادرها: شخص/بازیکن احتمالی، نه تشخیص قطعی دشمن", 10, Color.LTGRAY, false);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-2, -2);
        hp.topMargin = dp(5);
        details.addView(hint, hp);
        addVisionControls();
        panelContent.addView(details);
        addTelemetryControls();

        collapse.setOnClickListener(v -> setCompact(true));
        close.setOnClickListener(v -> stopSelf());
        header.setOnTouchListener((view, event) -> handleHeaderDrag(event));

        FrameLayout.LayoutParams expandedLp = new FrameLayout.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        panel.addView(expandedPanel, expandedLp);

        bubble = new TextView(this);
        bubble.setText("⊕");
        bubble.setTextSize(27);
        bubble.setTextColor(Color.rgb(100, 190, 255));
        bubble.setGravity(Gravity.CENTER);
        bubble.setIncludeFontPadding(false);
        bubble.setContentDescription("پنل شناور Aim Coach؛ لمس برای بازکردن، نگه‌داشتن برای جابه‌جایی");
        bubble.setClickable(true);
        bubble.setFocusable(true);
        bubbleBackground = new GradientDrawable();
        bubbleBackground.setShape(GradientDrawable.OVAL);
        bubbleBackground.setColor(Color.argb(238, 17, 27, 42));
        bubbleBackground.setStroke(dp(2), Color.rgb(53, 169, 255));
        bubble.setBackground(new InsetDrawable(bubbleBackground, dp((BUBBLE_SIZE_DP - BUBBLE_VISIBLE_DP) / 2)));
        FrameLayout.LayoutParams bubbleLp = new FrameLayout.LayoutParams(
                dp(BUBBLE_SIZE_DP), dp(BUBBLE_SIZE_DP), Gravity.TOP | Gravity.START);
        panel.addView(bubble, bubbleLp);
        bubble.setOnTouchListener((view, event) -> handleBubbleTouch(view, event));

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
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        params.x = Math.max(0, preferences.getInt("overlay_x", dp(16)));
        params.y = Math.max(0, preferences.getInt("overlay_y", dp(100)));
        expandedPanel.setVisibility(View.GONE);
        bubble.setVisibility(View.VISIBLE);
        collapsed = true;
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
        createScopeLensWindow();

        updateValues();
        mainHandler.post(metricsUpdater);
        mainHandler.post(scopeUpdater);
    }

    private boolean handleBubbleTouch(View view, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                bubbleDragging = false;
                bubbleMoved = false;
                bubbleDownRawX = event.getRawX();
                bubbleDownRawY = event.getRawY();
                bubbleDownX = params.x;
                bubbleDownY = params.y;
                longPressRunnable = () -> {
                    bubbleDragging = true;
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                };
                mainHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - bubbleDownRawX;
                float dy = event.getRawY() - bubbleDownRawY;
                int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
                if (!bubbleDragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    bubbleMoved = true;
                    if (longPressRunnable != null) mainHandler.removeCallbacks(longPressRunnable);
                }
                if (bubbleDragging) {
                    params.x = clamp(bubbleDownX + Math.round(dx), 0,
                            screenWidth() - dp(BUBBLE_SIZE_DP));
                    params.y = clamp(bubbleDownY + Math.round(dy), 0,
                            screenHeight() - dp(BUBBLE_SIZE_DP));
                    updatePanelPosition();
                }
                return true;

            case MotionEvent.ACTION_UP:
                cancelLongPress();
                if (!bubbleDragging && !bubbleMoved) {
                    setCompact(false);
                } else if (bubbleDragging) {
                    savePanelPosition();
                }
                bubbleDragging = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                cancelLongPress();
                bubbleDragging = false;
                return true;

            default:
                return true;
        }
    }

    private boolean handleHeaderDrag(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                downX = params.x;
                downY = params.y;
                return true;
            case MotionEvent.ACTION_MOVE:
                params.x = clamp(downX + Math.round(event.getRawX() - downRawX), 0,
                        screenWidth() - dp(80));
                params.y = clamp(downY + Math.round(event.getRawY() - downRawY), 0,
                        screenHeight() - dp(80));
                updatePanelPosition();
                return true;
            case MotionEvent.ACTION_UP:
                savePanelPosition();
                return true;
            default:
                return false;
        }
    }

    private void setCompact(boolean compact) {
        collapsed = compact;
        if (params != null) {
            if (compact) {
                params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                InputMethodManager input = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                View focused = panel == null ? null : panel.findFocus();
                if (input != null && focused != null) {
                    input.hideSoftInputFromWindow(focused.getWindowToken(), 0);
                }
            } else {
                params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            }
        }
        if (expandedPanel != null) expandedPanel.setVisibility(compact ? View.GONE : View.VISIBLE);
        if (bubble != null) bubble.setVisibility(compact ? View.VISIBLE : View.GONE);
        updatePanelPosition();
        if (!compact && panel != null) {
            panel.post(() -> {
                if (params == null || panel == null || collapsed) return;
                int maxX = Math.max(0, screenWidth() - panel.getWidth());
                params.x = Math.min(params.x, maxX);
                updatePanelPosition();
            });
        }
    }

    private void updatePanelPosition() {
        if (windowManager != null && panel != null && params != null) {
            try { windowManager.updateViewLayout(panel, params); } catch (Exception ignored) {}
        }
    }

    private void savePanelPosition() {
        if (preferences != null && params != null) {
            preferences.edit()
                    .putInt("overlay_x", params.x)
                    .putInt("overlay_y", params.y)
                    .apply();
        }
    }

    private void cancelLongPress() {
        if (longPressRunnable != null) {
            mainHandler.removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
    }

    private int screenWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private int screenHeight() {
        return getResources().getDisplayMetrics().heightPixels;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.max(0, Math.min(value, Math.max(min, max))));
    }


    private void initializeTelemetryState() {
        offsetRepo = new OffsetRepo(getApplicationContext());
        String pattern = offsetRepo.getPattern();
        String mask = offsetRepo.getMask();
        int hpOffset = offsetRepo.field("hp");
        int xOffset = offsetRepo.field("posX");
        int yOffset = offsetRepo.field("posY");
        int zOffset = offsetRepo.field("posZ");
        boolean ready = OffsetRepo.isMemoryAssistConfigured(
                pattern, mask, hpOffset, xOffset, yOffset, zOffset);
        ScreenMotionState.configureTelemetryAvailability(ready, ready);
        ScreenMotionState.initializeTelemetryDefaults((float) offsetRepo.freezeValue("hpValue"));
    }

    private void addTelemetryControls() {
        LinearLayout healthGroup = telemetryGroup("سلامت محلی (Telemetry)");
        LinearLayout healthRow = new LinearLayout(this);
        healthRow.setGravity(Gravity.CENTER_VERTICAL);
        healthToggle = new CheckBox(this);
        healthToggle.setText("فعال");
        healthToggle.setTextColor(Color.WHITE);
        healthToggle.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(100, 190, 255)));
        healthRow.addView(healthToggle, new LinearLayout.LayoutParams(0, dp(42), 1f));
        healthValueInput = decimalInput("0.0");
        healthRow.addView(healthValueInput, new LinearLayout.LayoutParams(dp(104), dp(42)));
        healthGroup.addView(healthRow);
        panelContent.addView(healthGroup);

        LinearLayout coordinateGroup = telemetryGroup("مختصات محلی (X / Y / Z)");
        coordinatesToggle = new CheckBox(this);
        coordinatesToggle.setText("فعال‌سازی پیش‌نمایش مختصات");
        coordinatesToggle.setTextColor(Color.WHITE);
        coordinatesToggle.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(100, 190, 255)));
        coordinateGroup.addView(coordinatesToggle);
        LinearLayout coordinateRow = new LinearLayout(this);
        coordinateRow.setGravity(Gravity.CENTER_VERTICAL);
        xValueInput = coordinateInput("X");
        yValueInput = coordinateInput("Y");
        zValueInput = coordinateInput("Z");
        coordinateRow.addView(xValueInput, weightedInputParams());
        coordinateRow.addView(yValueInput, weightedInputParams());
        coordinateRow.addView(zValueInput, weightedInputParams());
        coordinateGroup.addView(coordinateRow);
        panelContent.addView(coordinateGroup);

        telemetryStatus = label("", 10, Color.rgb(190, 205, 220), false);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(4);
        panelContent.addView(telemetryStatus, statusParams);

        bindingTelemetry = true;
        healthToggle.setChecked(ScreenMotionState.isHealthSlotEnabled());
        coordinatesToggle.setChecked(ScreenMotionState.isCoordinateSlotEnabled());
        healthValueInput.setText(formatFloat(ScreenMotionState.getHealthValue()));
        xValueInput.setText(formatFloat(ScreenMotionState.getXValue()));
        yValueInput.setText(formatFloat(ScreenMotionState.getYValue()));
        zValueInput.setText(formatFloat(ScreenMotionState.getZValue()));
        bindingTelemetry = false;

        healthToggle.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingTelemetry) ScreenMotionState.setHealthSlotEnabled(checked);
            updateTelemetryStatus();
        });
        coordinatesToggle.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingTelemetry) ScreenMotionState.setCoordinateSlotEnabled(checked);
            updateTelemetryStatus();
        });
        healthValueInput.addTextChangedListener(valueWatcher(ScreenMotionState::setHealthValue));
        xValueInput.addTextChangedListener(valueWatcher(ScreenMotionState::setXValue));
        yValueInput.addTextChangedListener(valueWatcher(ScreenMotionState::setYValue));
        zValueInput.addTextChangedListener(valueWatcher(ScreenMotionState::setZValue));
        updateTelemetryStatus();
    }

    private void loadVisionSettings() {
        ScreenMotionState.setFovRadius(preferences.getFloat("vision_fov_radius", 0.35f));
        ScreenMotionState.setHeadZoneEnabled(preferences.getBoolean("vision_head_zone", false));
        ScreenMotionState.setTargetLockEnabled(preferences.getBoolean("vision_sticky_lock", true));
        ScreenMotionState.setShowFovCircle(preferences.getBoolean("vision_show_fov", true));
        ScreenMotionState.setSmoothingAlpha(preferences.getFloat("vision_smoothing", 0.45f));
        ScreenMotionState.setLongRangeZoomEnabled(preferences.getBoolean("vision_long_range_zoom", true));
        ScreenMotionState.setDetectVehiclesEnabled(preferences.getBoolean("vision_detect_vehicles", true));
        ScreenMotionState.setSelfFilterEnabled(preferences.getBoolean("vision_self_filter", true));
        ScreenMotionState.setEdgeAlertsEnabled(preferences.getBoolean("vision_edge_alerts", true));
        ScreenMotionState.setCooldownEndTimeMs(preferences.getLong("vision_cooldown_end_ms", 0L));
        ScreenMotionState.setScopeZoomLevel(preferences.getFloat("vision_scope_zoom", 1.0f));
        ScreenMotionState.setScopePositionTop(preferences.getBoolean("vision_scope_position_top", true));
        ScreenMotionState.setConfidenceThreshold(preferences.getFloat("vision_confidence", 0.26f));
    }

    private void addVisionControls() {
        LinearLayout group = telemetryGroup("راهنمای بصری زنده");
        headZoneToggle = new CheckBox(this);
        headZoneToggle.setTextColor(Color.WHITE);
        headZoneToggle.setChecked(ScreenMotionState.isHeadZoneEnabled());
        group.addView(headZoneToggle);
        stickyLockToggle = new CheckBox(this);
        stickyLockToggle.setTextColor(Color.WHITE);
        stickyLockToggle.setChecked(ScreenMotionState.isTargetLockEnabled());
        group.addView(stickyLockToggle);
        showFovToggle = new CheckBox(this);
        showFovToggle.setText(ScreenMotionState.isShowFovCircle()
                ? "نمایش دایره FOV: فعال" : "نمایش دایره FOV: غیرفعال");
        showFovToggle.setTextColor(Color.WHITE);
        showFovToggle.setChecked(ScreenMotionState.isShowFovCircle());
        group.addView(showFovToggle);
        longRangeToggle = new CheckBox(this);
        longRangeToggle.setTextColor(Color.WHITE);
        longRangeToggle.setChecked(ScreenMotionState.isLongRangeZoomEnabled());
        group.addView(longRangeToggle);
        vehiclesToggle = new CheckBox(this);
        vehiclesToggle.setTextColor(Color.WHITE);
        vehiclesToggle.setChecked(ScreenMotionState.isDetectVehiclesEnabled());
        group.addView(vehiclesToggle);
        selfFilterToggle = new CheckBox(this);
        selfFilterToggle.setTextColor(Color.WHITE);
        selfFilterToggle.setChecked(ScreenMotionState.isSelfFilterEnabled());
        group.addView(selfFilterToggle);
        edgeAlertsToggle = new CheckBox(this);
        edgeAlertsToggle.setTextColor(Color.WHITE);
        edgeAlertsToggle.setChecked(ScreenMotionState.isEdgeAlertsEnabled());
        group.addView(edgeAlertsToggle);
        TextView cooldownAction = label("شروع تایمر ۱۵ ثانیه‌ای", 12, Color.rgb(0, 255, 136), true);
        cooldownAction.setGravity(Gravity.CENTER);
        cooldownAction.setPadding(dp(8), dp(8), dp(8), dp(8));
        cooldownAction.setBackground(roundedBackground(Color.argb(190, 15, 40, 49), Color.rgb(0, 190, 150)));
        cooldownAction.setOnClickListener(v -> {
            long end = System.currentTimeMillis() + 15000L;
            ScreenMotionState.setCooldownEndTimeMs(end);
            preferences.edit().putLong("vision_cooldown_end_ms", end).apply();
            if (detectionView != null) detectionView.invalidate();
        });
        group.addView(cooldownAction);
        scopeCycleButton = label("", 12, Color.rgb(0, 229, 255), true);
        scopeCycleButton.setGravity(Gravity.CENTER);
        scopeCycleButton.setPadding(dp(8), dp(9), dp(8), dp(9));
        scopeCycleButton.setBackground(roundedBackground(Color.argb(190, 12, 35, 48), Color.rgb(0, 170, 205)));
        scopeCycleButton.setOnClickListener(v -> cycleScopeZoom());
        group.addView(scopeCycleButton);
        scopePositionToggle = new CheckBox(this);
        scopePositionToggle.setTextColor(Color.WHITE);
        scopePositionToggle.setChecked(ScreenMotionState.isScopePositionTop());
        group.addView(scopePositionToggle);
        fovValueLabel = label("", 10, Color.LTGRAY, true);
        group.addView(fovValueLabel);
        fovSeekBar = new SeekBar(this);
        fovSeekBar.setMax(70);
        fovSeekBar.setProgress(Math.round((ScreenMotionState.getFovRadius() - 0.10f) * 100f));
        group.addView(fovSeekBar);
        smoothingValueLabel = label("", 10, Color.LTGRAY, true);
        group.addView(smoothingValueLabel);
        smoothingSeekBar = new SeekBar(this);
        smoothingSeekBar.setMax(90);
        smoothingSeekBar.setProgress(Math.round((ScreenMotionState.getSmoothingAlpha() - 0.10f) * 100f));
        group.addView(smoothingSeekBar);
        confidenceValueLabel = label("", 10, Color.LTGRAY, true);
        group.addView(confidenceValueLabel);
        confidenceSeekBar = new SeekBar(this);
        confidenceSeekBar.setMax(45);
        confidenceSeekBar.setProgress(Math.round((ScreenMotionState.getConfidenceThreshold() - 0.15f) * 100f));
        group.addView(confidenceSeekBar);

        group.removeView(scopeCycleButton);
        group.addView(scopeCycleButton, 0);
        group.removeView(selfFilterToggle);
        group.addView(selfFilterToggle, 1);
        group.removeView(headZoneToggle);
        group.addView(headZoneToggle, 2);
        group.removeView(stickyLockToggle);
        group.addView(stickyLockToggle, 3);
        group.removeView(showFovToggle);
        group.addView(showFovToggle, 4);
        group.removeView(fovValueLabel);
        group.addView(fovValueLabel, 5);
        group.removeView(fovSeekBar);
        group.addView(fovSeekBar, 6);
        group.removeView(confidenceValueLabel);
        group.addView(confidenceValueLabel, 7);
        group.removeView(confidenceSeekBar);
        group.addView(confidenceSeekBar, 8);
        group.removeView(smoothingValueLabel);
        group.addView(smoothingValueLabel, 9);
        group.removeView(smoothingSeekBar);
        group.addView(smoothingSeekBar, 10);
        panelContent.addView(group);
        updateVisionControlLabels();

        headZoneToggle.setOnCheckedChangeListener((button, checked) -> {
            ScreenMotionState.setHeadZoneEnabled(checked);
            preferences.edit().putBoolean("vision_head_zone", checked).apply();
            updateVisionControlLabels();
            if (detectionView != null) detectionView.invalidate();
        });
        stickyLockToggle.setOnCheckedChangeListener((button, checked) -> {
            ScreenMotionState.setTargetLockEnabled(checked);
            preferences.edit().putBoolean("vision_sticky_lock", checked).apply();
            updateVisionControlLabels();
        });
        longRangeToggle.setOnCheckedChangeListener((button, checked) -> {
            ScreenMotionState.setLongRangeZoomEnabled(checked);
            preferences.edit().putBoolean("vision_long_range_zoom", checked).apply();
            updateVisionControlLabels();
            if (detectionView != null) detectionView.invalidate();
        });
        vehiclesToggle.setOnCheckedChangeListener((button, checked) -> {
            ScreenMotionState.setDetectVehiclesEnabled(checked);
            preferences.edit().putBoolean("vision_detect_vehicles", checked).apply();
            updateVisionControlLabels();
        });
        selfFilterToggle.setOnCheckedChangeListener((button, checked) -> {
            ScreenMotionState.setSelfFilterEnabled(checked);
            preferences.edit().putBoolean("vision_self_filter", checked).apply();
            updateVisionControlLabels();
        });
        edgeAlertsToggle.setOnCheckedChangeListener((button, checked) -> {
            ScreenMotionState.setEdgeAlertsEnabled(checked);
            preferences.edit().putBoolean("vision_edge_alerts", checked).apply();
            if (!checked) ScreenMotionState.setPeripheralAlertMask(0);
            updateVisionControlLabels();
            if (detectionView != null) detectionView.invalidate();
        });
        scopePositionToggle.setOnCheckedChangeListener((button, checked) -> {
            ScreenMotionState.setScopePositionTop(checked);
            preferences.edit().putBoolean("vision_scope_position_top", checked).apply();
            updateVisionControlLabels();
            if (detectionView != null) detectionView.invalidate();
        });
        confidenceSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = (15 + progress) / 100f;
                ScreenMotionState.setConfidenceThreshold(value);
                confidenceValueLabel.setText(String.format(Locale.US, "آستانه تشخیص: %d%%", 15 + progress));
                if (fromUser) preferences.edit().putFloat("vision_confidence", value).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        showFovToggle.setOnCheckedChangeListener((button, checked) -> {
            ScreenMotionState.setShowFovCircle(checked);
            showFovToggle.setText(checked ? "نمایش دایره FOV: فعال" : "نمایش دایره FOV: غیرفعال");
            preferences.edit().putBoolean("vision_show_fov", checked).apply();
            if (detectionView != null) detectionView.invalidate();
        });
        fovSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float radius = (10 + progress) / 100f;
                ScreenMotionState.setFovRadius(radius);
                fovValueLabel.setText(String.format(Locale.US, "شعاع FOV: %d%%", 10 + progress));
                if (fromUser) preferences.edit().putFloat("vision_fov_radius", radius).apply();
                if (detectionView != null) detectionView.invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        smoothingSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float alpha = (10 + progress) / 100f;
                ScreenMotionState.setSmoothingAlpha(alpha);
                smoothingValueLabel.setText(String.format(Locale.US, "نرمی خط راهنما: %d%%", 10 + progress));
                if (fromUser) preferences.edit().putFloat("vision_smoothing", alpha).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private String scopeZoomLabel() {
        float zoom = ScreenMotionState.getScopeZoomLevel();
        return zoom <= 1.0f ? "دوربین زوم زنده: خاموش · لمس برای تغییر"
                : String.format(Locale.US, "دوربین زوم زنده: %.0fX · لمس برای تغییر", zoom);
    }

    private void cycleScopeZoom() {
        float current = ScreenMotionState.getScopeZoomLevel();
        float next = current < 1.5f ? 2.0f : current < 2.5f ? 3.0f : current < 3.5f ? 4.0f : 1.0f;
        ScreenMotionState.setScopeZoomLevel(next);
        preferences.edit().putFloat("vision_scope_zoom", next).apply();
        updateVisionControlLabels();
        if (detectionView != null) detectionView.invalidate();
    }

    private void updateVisionControlLabels() {
        if (headZoneToggle != null) headZoneToggle.setText(
                ScreenMotionState.isHeadZoneEnabled() ? "نقطه هدف: سر (HEAD)" : "نقطه هدف: بدن (BODY)");
        if (stickyLockToggle != null) stickyLockToggle.setText(
                ScreenMotionState.isTargetLockEnabled() ? "قفل پایدار روی هدف: فعال" : "قفل پایدار روی هدف: غیرفعال");
        if (longRangeToggle != null) longRangeToggle.setText(ScreenMotionState.isLongRangeZoomEnabled() ? "تحلیل برد دور: فعال" : "تحلیل برد دور: غیرفعال");
        if (vehiclesToggle != null) vehiclesToggle.setText(ScreenMotionState.isDetectVehiclesEnabled() ? "تشخیص خودرو: فعال" : "تشخیص خودرو: غیرفعال");
        if (selfFilterToggle != null) selfFilterToggle.setText(ScreenMotionState.isSelfFilterEnabled()
                ? "حذف کاراکتر خودی: فعال" : "حذف کاراکتر خودی: غیرفعال");
        if (edgeAlertsToggle != null) edgeAlertsToggle.setText(ScreenMotionState.isEdgeAlertsEnabled()
                ? "هشدار پیرامونی: فعال" : "هشدار پیرامونی: غیرفعال");
        if (scopeCycleButton != null) scopeCycleButton.setText(scopeZoomLabel());
        updateScopeLensVisibility();
        if (scopePositionToggle != null) scopePositionToggle.setText(
                ScreenMotionState.isScopePositionTop() ? "جایگاه لنز: بالا سمت راست" : "جایگاه لنز: بالا وسط");
        if (confidenceValueLabel != null) confidenceValueLabel.setText(String.format(Locale.US, "آستانه تشخیص: %d%%", Math.round(ScreenMotionState.getConfidenceThreshold() * 100f)));
        if (fovValueLabel != null) fovValueLabel.setText(String.format(Locale.US, "شعاع FOV: %d%%",
                Math.round(ScreenMotionState.getFovRadius() * 100f)));
        if (smoothingValueLabel != null) smoothingValueLabel.setText(String.format(Locale.US, "نرمی خط راهنما: %d%%",
                Math.round(ScreenMotionState.getSmoothingAlpha() * 100f)));
    }

    private LinearLayout telemetryGroup(String heading) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(dp(8), dp(5), dp(8), dp(5));
        group.setBackground(roundedBackground(Color.argb(150, 35, 49, 66), Color.rgb(72, 99, 126)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(7);
        TextView title = label(heading, 11, Color.rgb(132, 225, 178), true);
        group.addView(title);
        return group;
    }

    private GradientDrawable roundedBackground(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private EditText decimalInput(String hint) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(13);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.LTGRAY);
        input.setHint(hint);
        input.setGravity(Gravity.CENTER);
        input.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setSelectAllOnFocus(true);
        input.setPadding(dp(4), 0, dp(4), 0);
        input.setBackground(roundedBackground(Color.argb(210, 15, 24, 36), Color.rgb(83, 111, 139)));
        input.setFocusable(true);
        input.setFocusableInTouchMode(true);
        return input;
    }

    private EditText coordinateInput(String axis) {
        EditText input = decimalInput(axis);
        input.setContentDescription("مقدار محلی محور " + axis);
        return input;
    }

    private LinearLayout.LayoutParams weightedInputParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private TextWatcher valueWatcher(java.util.function.Consumer<Float> consumer) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!bindingTelemetry) consumer.accept(ScreenMotionState.parseFloatOrZero(s == null ? null : s.toString()));
            }
            @Override public void afterTextChanged(Editable editable) {}
        };
    }

    private void updateTelemetryStatus() {
        if (telemetryStatus == null) return;
        boolean healthEnabled = ScreenMotionState.isHealthSlotEnabled();
        boolean coordinatesEnabled = ScreenMotionState.isCoordinateSlotEnabled();
        boolean healthInert = ScreenMotionState.isHealthSlotInert();
        boolean coordinatesInert = ScreenMotionState.isCoordinateSlotInert();
        String healthText = healthEnabled
                ? (healthInert ? "سلامت: پیش‌نمایش محلی، آفست تنظیم نشده" : "سلامت: فقط مقدار محلی")
                : "سلامت: خاموش";
        String coordinateText = coordinatesEnabled
                ? (coordinatesInert ? "مختصات: پیش‌نمایش محلی، آفست تنظیم نشده" : "مختصات: فقط مقدار محلی")
                : "مختصات: خاموش";
        telemetryStatus.setText(healthText + " | " + coordinateText);
    }

    private String formatFloat(float value) {
        return String.format(Locale.US, "%.2f", value);
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
        updateTelemetryStatus();
        if (bubbleBackground != null) {
            bubbleBackground.setStroke(dp(2), ScreenMotionState.active
                    ? Color.rgb(74, 210, 145) : Color.rgb(53, 169, 255));
            if (bubble != null) bubble.invalidate();
        }
        if (detectionView != null) detectionView.invalidate();
    }

    private final class DetectionDrawingView extends View {
        private final Paint bracketPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelBackground = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fovPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint reticlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint guidancePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint leadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hudBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hudBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hudTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lockedFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint scopeBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint scopeBezelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint scopeCrosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint scopeDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint scopeBadgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint scopeBadgeBackground = new Paint(Paint.ANTI_ALIAS_FLAG);

        DetectionDrawingView() {
            super(OverlayService.this);
            bracketPaint.setStyle(Paint.Style.STROKE);
            bracketPaint.setStrokeWidth(dp(2));
            bracketPaint.setStrokeCap(Paint.Cap.SQUARE);
            labelPaint.setColor(Color.WHITE);
            labelPaint.setTextSize(dp(10));
            labelPaint.setTypeface(android.graphics.Typeface.create(
                    "sans-serif-medium", android.graphics.Typeface.BOLD));
            fovPaint.setStyle(Paint.Style.STROKE);
            fovPaint.setStrokeWidth(dp(2));
            tickPaint.setStyle(Paint.Style.STROKE);
            tickPaint.setStrokeWidth(dp(2));
            reticlePaint.setStyle(Paint.Style.STROKE);
            reticlePaint.setStrokeWidth(dp(2));
            guidancePaint.setStyle(Paint.Style.STROKE);
            guidancePaint.setStrokeWidth(dp(1));
            leadPaint.setStyle(Paint.Style.FILL);
            hudBackgroundPaint.setColor(Color.argb(204, 17, 22, 29));
            hudBorderPaint.setStyle(Paint.Style.STROKE);
            hudBorderPaint.setStrokeWidth(dp(1));
            hudTextPaint.setColor(Color.WHITE);
            hudTextPaint.setTextSize(dp(10));
            hudTextPaint.setTypeface(android.graphics.Typeface.create(
                    "sans-serif-medium", android.graphics.Typeface.BOLD));
            edgePaint.setStyle(Paint.Style.FILL);
            edgePaint.setStrokeWidth(dp(5));
            lockedFillPaint.setStyle(Paint.Style.FILL);
            scopeBezelPaint.setStyle(Paint.Style.STROKE);
            scopeBezelPaint.setStrokeWidth(dp(3));
            scopeCrosshairPaint.setStyle(Paint.Style.STROKE);
            scopeCrosshairPaint.setStrokeWidth(dp(1));
            scopeDotPaint.setStyle(Paint.Style.FILL);
            scopeBadgePaint.setColor(Color.WHITE);
            scopeBadgePaint.setTextSize(dp(11));
            scopeBadgePaint.setTypeface(android.graphics.Typeface.create(
                    "sans-serif-medium", android.graphics.Typeface.BOLD));
            scopeBadgeBackground.setColor(Color.argb(225, 10, 20, 29));
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth(), height = getHeight();
            if (width <= 0f || height <= 0f) return;
            float cx = width * 0.5f, cy = height * 0.5f;
            float minDimension = Math.min(width, height);
            ScreenMotionState.PlayerBox[] boxes = ScreenMotionState.detectedPlayers;
            ScreenMotionState.PlayerBox locked = ScreenMotionState.getLockedTargetBox();
            int accent = ScreenMotionState.isOnTarget ? Color.rgb(0,255,102) : Color.rgb(0,255,136);

            if (ScreenMotionState.isLongRangeZoomEnabled()) {
                Paint zone = new Paint(Paint.ANTI_ALIAS_FLAG);
                zone.setColor(Color.argb(78, 255, 190, 70));
                zone.setStyle(Paint.Style.STROKE);
                zone.setStrokeWidth(dp(1));
                zone.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(6),dp(5)},0));
                float cw=width*0.38f,ch=height*0.38f;
                canvas.drawRect((width-cw)*0.5f,(height-ch)*0.5f,
                        (width+cw)*0.5f,(height+ch)*0.5f,zone);
            }

            if (ScreenMotionState.isShowFovCircle()) {
                float radius=ScreenMotionState.getFovRadius()*minDimension;
                fovPaint.setColor(ScreenMotionState.isOnTarget ? Color.rgb(0,255,102)
                        : Color.argb(205,0,210,225));
                canvas.drawArc(cx-radius,cy-radius,cx+radius,cy+radius,8f,78f,false,fovPaint);
                canvas.drawArc(cx-radius,cy-radius,cx+radius,cy+radius,102f,78f,false,fovPaint);
                canvas.drawArc(cx-radius,cy-radius,cx+radius,cy+radius,188f,78f,false,fovPaint);
                canvas.drawArc(cx-radius,cy-radius,cx+radius,cy+radius,282f,78f,false,fovPaint);
                tickPaint.setColor(fovPaint.getColor());
                for(int angle=0;angle<360;angle+=90){
                    double a=Math.toRadians(angle);
                    float x1=cx+(float)Math.cos(a)*(radius-dp(5));
                    float y1=cy+(float)Math.sin(a)*(radius-dp(5));
                    float x2=cx+(float)Math.cos(a)*(radius+dp(4));
                    float y2=cy+(float)Math.sin(a)*(radius+dp(4));
                    canvas.drawLine(x1,y1,x2,y2,tickPaint);
                }
            }

            if(boxes!=null) for(ScreenMotionState.PlayerBox box:boxes){
                if(box==null||box==locked) continue;
                RectF rect=new RectF(box.left*width,box.top*height,box.right*width,box.bottom*height);
                if(rect.width()<dp(8)||rect.height()<dp(12)) continue;
                int color="VEHICLE".equals(box.category)?Color.rgb(255,187,51):Color.rgb(255,77,77);
                drawCornerBrackets(canvas,rect,color,false);
                drawTargetTag(canvas,rect,box,false);
            }
            if(locked!=null){
                RectF rect=new RectF(locked.left*width,locked.top*height,locked.right*width,locked.bottom*height);
                drawCornerBrackets(canvas,rect,Color.rgb(0,255,136),true);
                drawTargetTag(canvas,rect,locked,true);
            }

            reticlePaint.setColor(ScreenMotionState.isOnTarget?Color.rgb(0,255,102):Color.rgb(0,220,235));
            canvas.drawCircle(cx,cy,dp(8),reticlePaint);
            canvas.drawLine(cx-dp(14),cy,cx-dp(10),cy,reticlePaint);
            canvas.drawLine(cx+dp(10),cy,cx+dp(14),cy,reticlePaint);
            canvas.drawLine(cx,cy-dp(14),cx,cy-dp(10),reticlePaint);
            canvas.drawLine(cx,cy+dp(10),cx,cy+dp(14),reticlePaint);

            if(ScreenMotionState.aimGuideActive){
                float tx=ScreenMotionState.aimTargetX*width,ty=ScreenMotionState.aimTargetY*height;
                float vx=cx+ScreenMotionState.aimDeltaX*width,vy=cy+ScreenMotionState.aimDeltaY*height;
                guidancePaint.setColor(Color.argb(220,0,220,235));
                canvas.drawLine(cx,cy,vx,vy,guidancePaint);
                canvas.drawCircle(tx,ty,dp(7),guidancePaint);
                AimMath.Vec2 lead=ScreenMotionState.leadTargetPoint;
                if(locked!=null&&locked.isMoving&&lead!=null){
                    float lx=lead.x*width,ly=lead.y*height;
                    Paint dashed=new Paint(Paint.ANTI_ALIAS_FLAG);
                    dashed.setColor(Color.rgb(0,235,255));
                    dashed.setStyle(Paint.Style.STROKE);
                    dashed.setStrokeWidth(dp(1));
                    dashed.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(5),dp(4)},0));
                    canvas.drawLine(tx,ty,lx,ly,dashed);
                    leadPaint.setColor(Color.rgb(0,235,255));
                    int pulseRadius=3+(int)((System.currentTimeMillis()/180L)%3L);
                    canvas.drawCircle(lx,ly,dp(pulseRadius),leadPaint);
                    canvas.drawCircle(lx,ly,dp(pulseRadius+3),dashed);
                }
            }

            drawPeripheralAlerts(canvas,width,height);
            drawStatusHud(canvas,width,height);
            if (ScreenMotionState.getScopeZoomLevel() > 1.0f) postInvalidateDelayed(100L);
        }



        private void drawCornerBrackets(Canvas canvas,RectF r,int color,boolean locked){
            bracketPaint.setColor(color);
            float horizontal=Math.max(dp(7),r.width()*0.22f);
            float vertical=Math.max(dp(7),r.height()*0.22f);
            if(locked){
                lockedFillPaint.setColor(Color.argb(24,0,255,136));
                canvas.drawRoundRect(r,dp(4),dp(4),lockedFillPaint);
            }
            canvas.drawLine(r.left,r.top+vertical,r.left,r.top,bracketPaint);
            canvas.drawLine(r.left,r.top,r.left+horizontal,r.top,bracketPaint);
            canvas.drawLine(r.right-horizontal,r.top,r.right,r.top,bracketPaint);
            canvas.drawLine(r.right,r.top,r.right,r.top+vertical,bracketPaint);
            canvas.drawLine(r.left,r.bottom-vertical,r.left,r.bottom,bracketPaint);
            canvas.drawLine(r.left,r.bottom,r.left+horizontal,r.bottom,bracketPaint);
            canvas.drawLine(r.right-horizontal,r.bottom,r.right,r.bottom,bracketPaint);
            canvas.drawLine(r.right,r.bottom-vertical,r.right,r.bottom,bracketPaint);
        }

        private void drawTargetTag(Canvas canvas,RectF rect,ScreenMotionState.PlayerBox box,boolean locked){
            boolean close="CLOSE".equals(AimMath.classifyRange(rect.width()/getWidth(),rect.height()/getHeight()));
            String text=locked?AimMath.formatTacticalGuidance(box.category,box.isMoving,close,ScreenMotionState.isHeadZoneEnabled())
                    :("VEHICLE".equals(box.category)?(box.isMoving?"خودرو متحرک":"خودرو"):(close?"شخص نزدیک":"شخص"));
            float tw=labelPaint.measureText(text)+dp(12);
            float top=Math.max(dp(22),rect.top-dp(3));
            RectF bg=new RectF(rect.left,top-dp(21),rect.left+tw,top);
            int fill=locked?Color.argb(224,0,91,72):("VEHICLE".equals(box.category)
                    ?Color.argb(225,130,83,12):Color.argb(220,65,55,75));
            labelBackground.setColor(fill);
            canvas.drawRoundRect(bg,dp(4),dp(4),labelBackground);
            canvas.drawText(text,bg.left+dp(6),bg.bottom-dp(6),labelPaint);
        }

        private void drawPeripheralAlerts(Canvas canvas,float width,float height){
            if(!ScreenMotionState.isEdgeAlertsEnabled()) return;
            int mask=ScreenMotionState.getPeripheralAlertMask();
            if(mask==0) return;
            edgePaint.setColor(Color.argb(190,255,72,65));
            if((mask&AimMath.PERIPHERAL_LEFT)!=0)
                canvas.drawRoundRect(new RectF(dp(2),height*0.34f,dp(7),height*0.66f),dp(3),dp(3),edgePaint);
            if((mask&AimMath.PERIPHERAL_RIGHT)!=0)
                canvas.drawRoundRect(new RectF(width-dp(7),height*0.34f,width-dp(2),height*0.66f),dp(3),dp(3),edgePaint);
            if((mask&AimMath.PERIPHERAL_TOP)!=0)
                canvas.drawRoundRect(new RectF(width*0.34f,dp(2),width*0.66f,dp(7)),dp(3),dp(3),edgePaint);
            if((mask&AimMath.PERIPHERAL_BOTTOM)!=0)
                canvas.drawRoundRect(new RectF(width*0.34f,height-dp(7),width*0.66f,height-dp(2)),dp(3),dp(3),edgePaint);
        }

        private void drawStatusHud(Canvas canvas,float width,float height){
            long remaining=Math.max(0L,ScreenMotionState.getCooldownEndTimeMs()-System.currentTimeMillis());
            String cooldown=remaining>0L?" | CD: "+((remaining+999L)/1000L)+"s":" | GEAR READY";
            String hud=String.format(Locale.US,"FPS %.1f | %d MS | ZONE: %s%s",
                    ScreenMotionState.getVisionFps(),ScreenMotionState.getInferenceLatencyMs(),
                    ScreenMotionState.isHeadZoneEnabled()?"HEAD":"BODY",cooldown);
            float textWidth=hudTextPaint.measureText(hud);
            float pillWidth=Math.min(width-dp(16),textWidth+dp(20));
            RectF pill=new RectF(dp(8),dp(8),dp(8)+pillWidth,dp(36));
            canvas.drawRoundRect(pill,dp(9),dp(9),hudBackgroundPaint);
            hudBorderPaint.setColor(Color.argb(180,90,135,158));
            canvas.drawRoundRect(pill,dp(9),dp(9),hudBorderPaint);
            canvas.drawText(hud,pill.left+dp(10),pill.top+dp(18),hudTextPaint);
        }
    }

    private void createScopeLensWindow() {
        if (windowManager == null || scopeLensView != null) return;
        scopeLensView = new ScopeLensView();
        scopeLensParams = new WindowManager.LayoutParams(dp(170), dp(170),
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        scopeLensParams.gravity = Gravity.TOP | Gravity.START;
        scopeLensParams.x = clamp(preferences.getInt("scope_lens_x",screenWidth()-dp(186)),0,screenWidth()-dp(170));
        scopeLensParams.y = clamp(preferences.getInt("scope_lens_y",dp(54)),0,screenHeight()-dp(170));
        scopeLensView.setVisibility(View.GONE);
        scopeLensView.setOnTouchListener((view,event)->{
            switch(event.getAction()){
                case MotionEvent.ACTION_DOWN:
                    scopeLensDownX=event.getRawX();scopeLensDownY=event.getRawY();
                    scopeLensStartX=scopeLensParams.x;scopeLensStartY=scopeLensParams.y;return true;
                case MotionEvent.ACTION_MOVE:
                    scopeLensParams.x=clamp(scopeLensStartX+Math.round(event.getRawX()-scopeLensDownX),0,screenWidth()-dp(170));
                    scopeLensParams.y=clamp(scopeLensStartY+Math.round(event.getRawY()-scopeLensDownY),0,screenHeight()-dp(170));
                    try{windowManager.updateViewLayout(scopeLensView,scopeLensParams);}catch(Exception ignored){}return true;
                case MotionEvent.ACTION_UP:
                    preferences.edit().putInt("scope_lens_x",scopeLensParams.x).putInt("scope_lens_y",scopeLensParams.y).apply();return true;
                default:return true;
            }
        });
        windowManager.addView(scopeLensView,scopeLensParams);
    }

    private void updateScopeLensVisibility(){
        if(scopeLensView==null)return;
        int visibility=ScreenMotionState.getScopeZoomLevel()>1f?View.VISIBLE:View.GONE;
        if(scopeLensView.getVisibility()!=visibility)scopeLensView.setVisibility(visibility);
        if(visibility==View.VISIBLE)scopeLensView.invalidate();
    }

    private final class ScopeLensView extends View{
        private final Paint bitmapPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final Paint strokePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip=new Path();
        private final RectF lens=new RectF();
        private final RectF target=new RectF();
        ScopeLensView(){
            super(OverlayService.this);setLayerType(View.LAYER_TYPE_HARDWARE,null);
            strokePaint.setStyle(Paint.Style.STROKE);strokePaint.setStrokeWidth(dp(1));
            textPaint.setColor(Color.WHITE);textPaint.setTextSize(dp(10));
            textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float cx=getWidth()/2f,cy=getHeight()/2f,radius=Math.min(getWidth(),getHeight())/2f-dp(3);
            lens.set(cx-radius,cy-radius,cx+radius,cy+radius);
            fillPaint.setColor(Color.rgb(5,14,22));canvas.drawCircle(cx,cy,radius,fillPaint);
            int save=canvas.save();clip.reset();clip.addCircle(cx,cy,radius-dp(2),Path.Direction.CW);canvas.clipPath(clip);
            synchronized(ScreenMotionState.SCOPE_BITMAP_LOCK){
                Bitmap bitmap=ScreenMotionState.getScopeBitmap();
                if(bitmap!=null&&!bitmap.isRecycled())canvas.drawBitmap(bitmap,null,lens,bitmapPaint);
                ScreenMotionState.PlayerBox[] boxes=ScreenMotionState.detectedPlayers;
                ScreenMotionState.PlayerBox locked=ScreenMotionState.getLockedTargetBox();
                float zoom=ScreenMotionState.getScopeZoomLevel();
                if(boxes!=null)for(ScreenMotionState.PlayerBox box:boxes){
                    if(box==null)continue;
                    AimMath.Vec2 p1=AimMath.mapTargetToScopeLens(box.left,box.top,screenWidth(),screenHeight(),zoom);
                    AimMath.Vec2 p2=AimMath.mapTargetToScopeLens(box.right,box.bottom,screenWidth(),screenHeight(),zoom);
                    if(p1==null||p2==null)continue;
                    target.set(lens.left+p1.x*lens.width(),lens.top+p1.y*lens.height(),
                            lens.left+p2.x*lens.width(),lens.top+p2.y*lens.height());
                    if(!target.intersect(lens))continue;
                    strokePaint.setColor(box==locked?Color.rgb(0,255,136):
                            ("VEHICLE".equals(box.category)?Color.rgb(255,190,60):Color.rgb(255,85,85)));
                    drawBrackets(canvas,target);
                }
                if(locked!=null){
                    AimMath.Vec2 point=AimMath.computeTargetPoint(locked.left,locked.top,locked.right,locked.bottom,
                            ScreenMotionState.isHeadZoneEnabled());
                    AimMath.Vec2 mapped=AimMath.mapTargetToScopeLens(point.x,point.y,screenWidth(),screenHeight(),zoom);
                    if(mapped!=null&&mapped.x>=0f&&mapped.x<=1f&&mapped.y>=0f&&mapped.y<=1f){
                        float x=lens.left+mapped.x*lens.width(),y=lens.top+mapped.y*lens.height();
                        strokePaint.setColor(Color.WHITE);canvas.drawCircle(x,y,dp(5),strokePaint);
                        fillPaint.setColor(Color.rgb(0,255,136));canvas.drawCircle(x,y,dp(2),fillPaint);
                    }
                }
            }
            canvas.restoreToCount(save);
            strokePaint.setColor(Color.argb(235,240,250,255));
            canvas.drawLine(cx-radius,cy,cx+radius,cy,strokePaint);canvas.drawLine(cx,cy-radius,cx,cy+radius,strokePaint);
            for(int i=-4;i<=4;i++)if(i!=0){
                float offset=i*radius*0.17f,tick=i%2==0?dp(5):dp(3);
                canvas.drawLine(cx+offset,cy-tick,cx+offset,cy+tick,strokePaint);
                canvas.drawLine(cx-tick,cy+offset,cx+tick,cy+offset,strokePaint);
            }
            strokePaint.setStrokeWidth(dp(3));strokePaint.setColor(ScreenMotionState.isOnTarget?Color.GREEN:Color.CYAN);
            canvas.drawCircle(cx,cy,radius,strokePaint);
            String badge="دوربین زوم "+Math.round(ScreenMotionState.getScopeZoomLevel())+"X";
            float bw=textPaint.measureText(badge)+dp(10);
            RectF badgeRect=new RectF(cx-bw/2f,dp(5),cx+bw/2f,dp(24));
            fillPaint.setColor(Color.argb(225,4,17,27));canvas.drawRoundRect(badgeRect,dp(5),dp(5),fillPaint);
            canvas.drawText(badge,badgeRect.left+dp(5),badgeRect.bottom-dp(5),textPaint);
        }
        private void drawBrackets(Canvas canvas,RectF r){
            float h=Math.max(dp(5),r.width()*0.22f),v=Math.max(dp(5),r.height()*0.22f);
            canvas.drawLine(r.left,r.top,r.left+h,r.top,strokePaint);canvas.drawLine(r.left,r.top,r.left,r.top+v,strokePaint);
            canvas.drawLine(r.right-h,r.top,r.right,r.top,strokePaint);canvas.drawLine(r.right,r.top,r.right,r.top+v,strokePaint);
            canvas.drawLine(r.left,r.bottom-v,r.left,r.bottom,strokePaint);canvas.drawLine(r.left,r.bottom,r.left+h,r.bottom,strokePaint);
            canvas.drawLine(r.right-h,r.bottom,r.right,r.bottom,strokePaint);canvas.drawLine(r.right,r.bottom-v,r.right,r.bottom,strokePaint);
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
        cancelLongPress();
        mainHandler.removeCallbacks(metricsUpdater);
        mainHandler.removeCallbacks(scopeUpdater);
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(this);
        if (windowManager != null && scopeLensView != null) {
            try { windowManager.removeView(scopeLensView); } catch (Exception ignored) {}
        }
        if (windowManager != null && detectionView != null) {
            try { windowManager.removeView(detectionView); } catch (Exception ignored) {}
        }
        if (windowManager != null && panel != null) {
            try { windowManager.removeView(panel); } catch (Exception ignored) {}
        }
        detectionView = null;
        scopeLensView = null;
        scopeLensParams = null;
        panelContent = null;
        bubble = null;
        expandedPanel = null;
        panel = null;
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}