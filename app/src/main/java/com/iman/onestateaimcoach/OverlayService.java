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
        expandedPanel.setPadding(dp(12), dp(9), dp(12), dp(10));
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
        expandedPanel.addView(details);
        addTelemetryControls();
        addVisionControls();

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

        updateValues();
        mainHandler.post(metricsUpdater);
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
        expandedPanel.addView(healthGroup);

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
        expandedPanel.addView(coordinateGroup);

        telemetryStatus = label("", 10, Color.rgb(190, 205, 220), false);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(4);
        expandedPanel.addView(telemetryStatus, statusParams);

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
        showFovToggle.setText("Show FOV Circle");
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
        confidenceSeekBar.setMax(84);
        confidenceSeekBar.setProgress(Math.round((ScreenMotionState.getConfidenceThreshold() - 0.15f) * 100f));
        group.addView(confidenceSeekBar);
        expandedPanel.addView(group);
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
        confidenceSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = (15 + progress) / 100f;
                ScreenMotionState.setConfidenceThreshold(value);
                confidenceValueLabel.setText(String.format(Locale.US, "Confidence: %d%%", 15 + progress));
                if (fromUser) preferences.edit().putFloat("vision_confidence", value).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        showFovToggle.setOnCheckedChangeListener((button, checked) -> {
            ScreenMotionState.setShowFovCircle(checked);
            preferences.edit().putBoolean("vision_show_fov", checked).apply();
            if (detectionView != null) detectionView.invalidate();
        });
        fovSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float radius = (10 + progress) / 100f;
                ScreenMotionState.setFovRadius(radius);
                fovValueLabel.setText(String.format(Locale.US, "FOV Radius: %d%%", 10 + progress));
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
                smoothingValueLabel.setText(String.format(Locale.US, "Smoothing: %d%%", 10 + progress));
                if (fromUser) preferences.edit().putFloat("vision_smoothing", alpha).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void updateVisionControlLabels() {
        if (headZoneToggle != null) headZoneToggle.setText(
                ScreenMotionState.isHeadZoneEnabled() ? "Target Zone: HEAD" : "Target Zone: BODY");
        if (stickyLockToggle != null) stickyLockToggle.setText(
                ScreenMotionState.isTargetLockEnabled() ? "Sticky Lock: ON" : "Sticky Lock: OFF");
        if (longRangeToggle != null) longRangeToggle.setText(ScreenMotionState.isLongRangeZoomEnabled() ? "Long-Range Zoom: ON" : "Long-Range Zoom: OFF");
        if (vehiclesToggle != null) vehiclesToggle.setText(ScreenMotionState.isDetectVehiclesEnabled() ? "Vehicles: ON" : "Vehicles: OFF");
        if (confidenceValueLabel != null) confidenceValueLabel.setText(String.format(Locale.US, "Confidence: %d%%", Math.round(ScreenMotionState.getConfidenceThreshold() * 100f)));
        if (fovValueLabel != null) fovValueLabel.setText(String.format(Locale.US, "FOV Radius: %d%%",
                Math.round(ScreenMotionState.getFovRadius() * 100f)));
        if (smoothingValueLabel != null) smoothingValueLabel.setText(String.format(Locale.US, "Smoothing: %d%%",
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
        private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelBackground = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint aimGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint aimCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fovPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lockedBoxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hudBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hudTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint targetPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        DetectionDrawingView() {
            super(OverlayService.this);
            boxPaint.setColor(Color.rgb(255, 70, 70));
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setStrokeWidth(dp(2));
            labelPaint.setColor(Color.WHITE);
            labelPaint.setTextSize(dp(11));
            labelPaint.setTypeface(android.graphics.Typeface.create(
                    "sans-serif-medium", android.graphics.Typeface.BOLD));
            aimGuidePaint.setColor(Color.rgb(70, 220, 190));
            aimGuidePaint.setStyle(Paint.Style.STROKE);
            aimGuidePaint.setStrokeWidth(dp(2));
            aimCenterPaint.setColor(Color.rgb(70, 220, 190));
            aimCenterPaint.setStyle(Paint.Style.STROKE);
            aimCenterPaint.setStrokeWidth(dp(2));
            fovPaint.setStyle(Paint.Style.STROKE);
            fovPaint.setStrokeWidth(dp(2));
            lockedBoxPaint.setColor(Color.rgb(70, 235, 145));
            lockedBoxPaint.setStyle(Paint.Style.STROKE);
            lockedBoxPaint.setStrokeWidth(dp(3));
            hudBackgroundPaint.setColor(Color.argb(205, 12, 20, 31));
            hudTextPaint.setColor(Color.WHITE);
            hudTextPaint.setTextSize(dp(11));
            hudTextPaint.setTypeface(android.graphics.Typeface.create(
                    "sans-serif-medium", android.graphics.Typeface.BOLD));
            targetPointPaint.setColor(Color.WHITE);
            targetPointPaint.setStyle(Paint.Style.STROKE);
            targetPointPaint.setStrokeWidth(dp(2));
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth(), height = getHeight();
            if (width <= 0f || height <= 0f) return;
            float centerX = width * 0.5f, centerY = height * 0.5f;
            ScreenMotionState.PlayerBox[] boxes = ScreenMotionState.detectedPlayers;
            ScreenMotionState.PlayerBox locked = ScreenMotionState.getLockedTargetBox();

            if (ScreenMotionState.isLongRangeZoomEnabled()) {
                Paint zone = new Paint(Paint.ANTI_ALIAS_FLAG);
                zone.setColor(Color.argb(105, 255, 205, 90)); zone.setStyle(Paint.Style.STROKE);
                zone.setStrokeWidth(dp(1)); zone.setPathEffect(new android.graphics.DashPathEffect(new float[] {dp(7),dp(5)},0));
                float cw=width*0.38f,ch=height*0.38f;
                canvas.drawRect((width-cw)*0.5f,(height-ch)*0.5f,(width+cw)*0.5f,(height+ch)*0.5f,zone);
            }
            if (ScreenMotionState.isShowFovCircle()) {
                float radius = ScreenMotionState.getFovRadius() * Math.min(width, height);
                fovPaint.setColor(locked == null
                        ? Color.argb(190, 70, 205, 235)
                        : Color.argb(235, 75, 225, 145));
                canvas.drawCircle(centerX, centerY, radius, fovPaint);
            }

            if (boxes != null) {
                for (ScreenMotionState.PlayerBox box : boxes) {
                    if (box == null || box == locked) continue;
                    RectF rect = new RectF(box.left * width, box.top * height,
                            box.right * width, box.bottom * height);
                    if (rect.width() < dp(8) || rect.height() < dp(12)) continue;
                    boxPaint.setColor("VEHICLE".equals(box.category) ? Color.rgb(255,190,55) : Color.rgb(255,92,92));
                    canvas.drawRoundRect(rect, dp(5), dp(5), boxPaint);
                    drawBoxLabel(canvas, rect, box, false);
                }
            }
            if (locked != null) {
                RectF rect = new RectF(locked.left * width, locked.top * height,
                        locked.right * width, locked.bottom * height);
                lockedBoxPaint.setColor("VEHICLE".equals(locked.category) ? Color.rgb(255,190,55) : Color.rgb(70,235,145));
                canvas.drawRoundRect(rect, dp(5), dp(5), lockedBoxPaint);
                drawBoxLabel(canvas, rect, locked, true);
            }

            if (ScreenMotionState.aimGuideActive) {
                float targetX = ScreenMotionState.aimTargetX * width;
                float targetY = ScreenMotionState.aimTargetY * height;
                float vectorX = centerX + ScreenMotionState.aimDeltaX * width;
                float vectorY = centerY + ScreenMotionState.aimDeltaY * height;
                canvas.drawCircle(centerX, centerY, dp(7), aimCenterPaint);
                canvas.drawLine(centerX, centerY, vectorX, vectorY, aimGuidePaint);
                canvas.drawCircle(targetX, targetY, dp(9), aimGuidePaint);
                canvas.drawLine(targetX - dp(5), targetY, targetX + dp(5), targetY, targetPointPaint);
                canvas.drawLine(targetX, targetY - dp(5), targetX, targetY + dp(5), targetPointPaint);
                canvas.drawCircle(targetX, targetY, dp(3), targetPointPaint);
            }

            String mode = ScreenMotionState.isHeadZoneEnabled() ? "HEAD" : "BODY";
            String hud = String.format(Locale.US, "%.1f FPS | %d ms | %s",
                    ScreenMotionState.visionFps, ScreenMotionState.inferenceLatencyMs, mode);
            float hudWidth = hudTextPaint.measureText(hud) + dp(16);
            RectF hudRect = new RectF(Math.max(dp(6), width - hudWidth - dp(8)),
                    dp(8), width - dp(8), dp(34));
            canvas.drawRoundRect(hudRect, dp(7), dp(7), hudBackgroundPaint);
            canvas.drawText(hud, hudRect.left + dp(8), hudRect.top + dp(18), hudTextPaint);
        }

        private void drawBoxLabel(Canvas canvas, RectF rect, ScreenMotionState.PlayerBox box, boolean isLocked) {
            String type = "VEHICLE".equals(box.category) ? "CAR" : "PLAYER";
            if (box.isLongRangeZoom) type = "FAR";
            String caption = String.format(Locale.US, "%s %d%%%s", type,
                    Math.round(box.confidence * 100f), box.isMoving ? " [MOV]" : "");
            float textWidth = labelPaint.measureText(caption);
            float labelTop = Math.max(dp(20), rect.top - dp(4));
            RectF bg = new RectF(rect.left, labelTop - dp(18), rect.left + textWidth + dp(10), labelTop);
            int color = "VEHICLE".equals(box.category) ? Color.argb(225,145,100,18) : Color.argb(220,145,28,28);
            if (isLocked) color = Color.argb(225,20,115,65);
            labelBackground.setColor(color);
            canvas.drawRoundRect(bg, dp(4), dp(4), labelBackground);
            canvas.drawText(caption, rect.left + dp(5), labelTop - dp(5), labelPaint);
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
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(this);
        if (windowManager != null && detectionView != null) {
            try { windowManager.removeView(detectionView); } catch (Exception ignored) {}
        }
        if (windowManager != null && panel != null) {
            try { windowManager.removeView(panel); } catch (Exception ignored) {}
        }
        detectionView = null;
        bubble = null;
        expandedPanel = null;
        panel = null;
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}