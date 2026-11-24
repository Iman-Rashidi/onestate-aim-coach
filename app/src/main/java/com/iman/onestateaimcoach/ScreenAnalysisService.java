package com.iman.onestateaimcoach;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ScreenAnalysisService extends Service {
    private static final String CHANNEL_ID = "aim_coach_analysis";
    private static final int NOTIFICATION_ID = 2;
    private static final String EXTRA_RESULT = "projection_result";
    private static final String EXTRA_DATA = "projection_data";
    private static final long MIN_FRAME_INTERVAL_MS = 110;
    private static final long MIN_DETECTION_INTERVAL_MS = 350;
    private static final float PERSON_SCORE_THRESHOLD = 0.38f;

    private HandlerThread workerThread;
    private Handler worker;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private ObjectDetector objectDetector;
    private byte[] previous;
    private long lastFrameAt;
    private long lastDetectionAt;
    private float smoothedScore;
    private OffsetRepo offsetRepo;

    @Override
    public void onCreate() {
        super.onCreate();
        offsetRepo = new OffsetRepo(getApplicationContext());
        String pattern = offsetRepo.getPattern();
        String mask = offsetRepo.getMask();
        int[] fields = new int[] {
                offsetRepo.field("hp"),
                offsetRepo.field("posX"),
                offsetRepo.field("posY"),
                offsetRepo.field("posZ"),
                offsetRepo.field("speed")
        };
        ScreenMotionState.offsetContractReady =
                OffsetRepo.isMemoryAssistConfigured(pattern, mask, fields)
                        && offsetRepo.getChain() != null;
        // No process-memory reader exists in this visual-only app. JNI is limited to byte arrays.
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !intent.hasExtra(EXTRA_DATA)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startAsForeground();
        try {
            int resultCode = intent.getIntExtra(EXTRA_RESULT, 0);
            Intent data = intent.getParcelableExtra(EXTRA_DATA);
            MediaProjectionManager manager =
                    (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(resultCode, data);
            if (projection == null) {
                stopSelf();
                return START_NOT_STICKY;
            }

            workerThread = new HandlerThread("AimCoachVision");
            workerThread.start();
            worker = new Handler(workerThread.getLooper());

            projection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    stopSelf();
                }
            }, worker);

            loadDetector();
            startCapture();
            ScreenMotionState.active = true;
            ScreenMotionState.level = objectDetector == null
                    ? "تحلیل حرکت؛ مدل بازیکن در دسترس نیست"
                    : "تحلیل زنده فعال";
        } catch (Exception exception) {
            ScreenMotionState.active = false;
            ScreenMotionState.level = "خطا در شروع تحلیل";
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void loadDetector() {
        try {
            ObjectDetector.ObjectDetectorOptions options =
                    ObjectDetector.ObjectDetectorOptions.builder()
                            .setMaxResults(12)
                            .setScoreThreshold(PERSON_SCORE_THRESHOLD)
                            .build();
            objectDetector = ObjectDetector.createFromFileAndOptions(
                    getApplicationContext(), "efficientdet-lite0.tflite", options);
        } catch (Exception error) {
            objectDetector = null;
            ScreenMotionState.level = "مدل تشخیص بازیکن بارگذاری نشد";
        }
    }

    private void startCapture() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);

        int width = Math.max(320, metrics.widthPixels / 2);
        int height = Math.max(320, metrics.heightPixels / 2);

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image != null) analyze(image);
            } catch (Exception ignored) {
                // A dropped or malformed frame must not interrupt the game.
            } finally {
                if (image != null) image.close();
            }
        }, worker);

        virtualDisplay = projection.createVirtualDisplay(
                "OneStateAimCoachAnalysis",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                worker);
    }

    private void analyze(Image image) {
        long now = System.currentTimeMillis();
        if (now - lastFrameAt < MIN_FRAME_INTERVAL_MS) return;
        lastFrameAt = now;

        Image.Plane[] planes = image.getPlanes();
        if (planes.length == 0) return;
        Image.Plane plane = planes[0];
        ByteBuffer buffer = plane.getBuffer();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        int width = image.getWidth();
        int height = image.getHeight();

        updateMotionMetric(buffer, rowStride, pixelStride, width, height);

        if (objectDetector != null && now - lastDetectionAt >= MIN_DETECTION_INTERVAL_MS) {
            lastDetectionAt = now;
            Bitmap bitmap = null;
            try {
                bitmap = imageToBitmap(buffer, rowStride, pixelStride, width, height);
                List<Detection> results = objectDetector.detect(TensorImage.fromBitmap(bitmap));
                List<ScreenMotionState.PlayerBox> boxes = new ArrayList<>();

                for (Detection detection : results) {
                    float confidence = 0f;
                    for (Category category : detection.getCategories()) {
                        if ("person".equalsIgnoreCase(category.getLabel())) {
                            confidence = Math.max(confidence, category.getScore());
                        }
                    }
                    if (confidence < PERSON_SCORE_THRESHOLD) continue;

                    android.graphics.RectF rect = detection.getBoundingBox();
                    boxes.add(new ScreenMotionState.PlayerBox(
                            rect.left / (float) width,
                            rect.top / (float) height,
                            rect.right / (float) width,
                            rect.bottom / (float) height,
                            confidence));
                }

                ScreenMotionState.detectedPlayers =
                        boxes.toArray(new ScreenMotionState.PlayerBox[0]);
                updateAimGuide(boxes);
                ScreenMotionState.level = boxes.size() == 0
                        ? "بازیکن قابل تشخیص نیست"
                        : boxes.size() + " بازیکن احتمالی";
            } catch (Exception ignored) {
                ScreenMotionState.level = "خطا در تحلیل فریم";
            } finally {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            }
        }
    }


    private void updateAimGuide(List<ScreenMotionState.PlayerBox> boxes) {
        final AimMath.Vec2 center = new AimMath.Vec2(0.5f, 0.5f);
        ScreenMotionState.PlayerBox best = null;
        AimMath.Vec2 bestPoint = null;
        double bestDistance = Double.MAX_VALUE;

        for (ScreenMotionState.PlayerBox box : boxes) {
            AimMath.Vec2 target = new AimMath.Vec2(
                    (box.left + box.right) * 0.5f,
                    (box.top + box.bottom) * 0.5f);
            if (!AimMath.inCone(target, center, 0.35f)) continue;

            double distance = Math.hypot(target.x - center.x, target.y - center.y);
            if (distance < bestDistance
                    || (distance == bestDistance && best != null
                    && box.confidence > best.confidence)) {
                best = box;
                bestPoint = target;
                bestDistance = distance;
            }
        }

        if (bestPoint == null) {
            ScreenMotionState.aimGuideActive = false;
            ScreenMotionState.aimDeltaX = 0f;
            ScreenMotionState.aimDeltaY = 0f;
            return;
        }

        float sensitivity = getSharedPreferences("coach_settings", MODE_PRIVATE)
                .getInt("aim_sensitivity", 50) / 100.0f;
        AimMath.Vec2 delta = AimMath.aimDelta(
                bestPoint, center, sensitivity, 0.01f, 0.25f);
        if (delta == null) {
            ScreenMotionState.aimGuideActive = false;
            ScreenMotionState.aimDeltaX = 0f;
            ScreenMotionState.aimDeltaY = 0f;
            return;
        }

        ScreenMotionState.aimTargetX = bestPoint.x;
        ScreenMotionState.aimTargetY = bestPoint.y;
        ScreenMotionState.aimDeltaX = delta.x;
        ScreenMotionState.aimDeltaY = delta.y;
        ScreenMotionState.aimGuideActive = true;
    }

    private void updateMotionMetric(
            ByteBuffer buffer, int rowStride, int pixelStride, int width, int height) {
        int patch = Math.min(112, Math.min(width, height) / 3);
        int left = Math.max(0, width / 2 - patch / 2);
        int top = Math.max(0, height / 2 - patch / 2);
        int sampleStep = 2;
        int sampleWidth = (patch + sampleStep - 1) / sampleStep;
        byte[] current = new byte[sampleWidth * sampleWidth];
        int out = 0;

        for (int y = 0; y < patch; y += sampleStep) {
            int py = Math.min(height - 1, top + y);
            for (int x = 0; x < patch; x += sampleStep) {
                int px = Math.min(width - 1, left + x);
                int index = py * rowStride + px * pixelStride;
                if (index + 2 >= buffer.limit()) {
                    current[out++] = 0;
                    continue;
                }
                int red = buffer.get(index) & 0xff;
                int green = buffer.get(index + 1) & 0xff;
                int blue = buffer.get(index + 2) & 0xff;
                int luminance = (red * 30 + green * 59 + blue * 11) / 100;
                current[out++] = (byte) luminance;
            }
        }

        if (previous != null && previous.length == current.length) {
            long sum = 0;
            for (int i = 0; i < current.length; i++) {
                sum += Math.abs((current[i] & 0xff) - (previous[i] & 0xff));
            }
            float raw = (sum / (float) current.length) * 100f / 255f;
            smoothedScore = smoothedScore == 0f ? raw : (smoothedScore * 0.65f + raw * 0.35f);
            ScreenMotionState.motionScore = smoothedScore;
        }
        previous = current;
    }

    private Bitmap imageToBitmap(
            ByteBuffer source, int rowStride, int pixelStride, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int row = y * rowStride;
            int dest = y * width;
            for (int x = 0; x < width; x++) {
                int index = row + x * pixelStride;
                if (index + 3 >= source.limit()) {
                    pixels[dest + x] = Color.TRANSPARENT;
                    continue;
                }
                int red = source.get(index) & 0xff;
                int green = source.get(index + 1) & 0xff;
                int blue = source.get(index + 2) & 0xff;
                int alpha = source.get(index + 3) & 0xff;
                pixels[dest + x] = (alpha << 24) | (red << 16) | (green << 8) | blue;
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    private void startAsForeground() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("OneState Aim Coach")
                .setContentText("تحلیل زندهٔ تصویر با رضایت شما فعال است")
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "تحلیل زنده تصویر", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("اعلان سرویس تحلیل تصویر با اجازه کاربر");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        ScreenMotionState.active = false;
        ScreenMotionState.level = "خاموش";
        ScreenMotionState.detectedPlayers = new ScreenMotionState.PlayerBox[0];
        ScreenMotionState.aimGuideActive = false;
        ScreenMotionState.aimDeltaX = 0f;
        ScreenMotionState.aimDeltaY = 0f;
        ScreenMotionState.offsetContractReady = false;
        if (virtualDisplay != null) virtualDisplay.release();
        if (imageReader != null) imageReader.close();
        if (projection != null) {
            try { projection.stop(); } catch (Exception ignored) {}
        }
        if (objectDetector != null) {
            try { objectDetector.close(); } catch (Exception ignored) {}
            objectDetector = null;
        }
        if (workerThread != null) {
            workerThread.quitSafely();
            workerThread = null;
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
