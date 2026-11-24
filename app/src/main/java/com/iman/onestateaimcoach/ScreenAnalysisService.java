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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ScreenAnalysisService extends Service {
    private static final String CHANNEL_ID = "aim_coach_analysis";
    private static final int NOTIFICATION_ID = 2;
    private static final String EXTRA_RESULT = "projection_result";
    private static final String EXTRA_DATA = "projection_data";
    private static final long MIN_FRAME_INTERVAL_MS = 110;
    private static final long MIN_DETECTION_INTERVAL_MS = 350;
    private static final float NMS_IOU_THRESHOLD = 0.45f;
    private static final int GRID = 8;
    private static final int MAX_DETECTIONS = 30;
    private static final float CROP_FRACTION = 0.38f;
    private HandlerThread workerThread;
    private Handler worker;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private ObjectDetector objectDetector;
    private byte[] previousGrid;
    private boolean[] movingGrid = new boolean[GRID * GRID];
    private long lastFrameAt, lastDetectionAt, lastFpsTimestampNs;
    private boolean fpsInitialized;
    private float smoothedScore;
    private OffsetRepo offsetRepo;
    private final TargetTracker targetTracker = new TargetTracker(3);
    private AimMath.Vec2 smoothedAimVector;

    @Override public void onCreate() {
        super.onCreate();
        offsetRepo = new OffsetRepo(getApplicationContext());
        int[] fields = {offsetRepo.field("hp"), offsetRepo.field("posX"), offsetRepo.field("posY"),
                offsetRepo.field("posZ"), offsetRepo.field("speed")};
        ScreenMotionState.offsetContractReady = OffsetRepo.isMemoryAssistConfigured(
                offsetRepo.getPattern(), offsetRepo.getMask(), fields) && offsetRepo.getChain() != null;
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !intent.hasExtra(EXTRA_DATA)) { stopSelf(); return START_NOT_STICKY; }
        startAsForeground();
        try {
            int resultCode = intent.getIntExtra(EXTRA_RESULT, 0);
            Intent data = intent.getParcelableExtra(EXTRA_DATA);
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(resultCode, data);
            if (projection == null) { stopSelf(); return START_NOT_STICKY; }
            workerThread = new HandlerThread("AimCoachVision");
            workerThread.start();
            worker = new Handler(workerThread.getLooper());
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { stopSelf(); }
            }, worker);
            loadDetector();
            startCapture();
            ScreenMotionState.active = true;
            ScreenMotionState.level = objectDetector == null ? "مدل در دسترس نیست" : "تحلیل زنده چندمقیاسی فعال";
        } catch (Exception exception) {
            ScreenMotionState.active = false; ScreenMotionState.level = "خطا در شروع تحلیل"; stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void loadDetector() {
        try {
            ObjectDetector.ObjectDetectorOptions options = ObjectDetector.ObjectDetectorOptions.builder()
                    .setMaxResults(MAX_DETECTIONS).setScoreThreshold(0.0f).build();
            objectDetector = ObjectDetector.createFromFileAndOptions(
                    getApplicationContext(), "efficientdet-lite0.tflite", options);
        } catch (Exception error) { objectDetector = null; ScreenMotionState.level = "مدل تشخیص بارگذاری نشد"; }
    }

    private void startCapture() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);
        int width = Math.max(320, metrics.widthPixels), height = Math.max(320, metrics.heightPixels);
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try { image = reader.acquireLatestImage(); if (image != null) analyze(image); }
            catch (Exception ignored) { }
            finally { if (image != null) image.close(); }
        }, worker);
        virtualDisplay = projection.createVirtualDisplay("OneStateAimCoachAnalysis", width, height,
                metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, worker);
    }

    private void analyze(Image image) {
        long started = System.nanoTime();
        boolean processed = false;
        try {
            long now = System.currentTimeMillis();
            if (now - lastFrameAt < MIN_FRAME_INTERVAL_MS) return;
            lastFrameAt = now; processed = true;
            Image.Plane[] planes = image.getPlanes();
            if (planes.length == 0) return;
            ByteBuffer buffer = planes[0].getBuffer();
            int rowStride = planes[0].getRowStride(), pixelStride = planes[0].getPixelStride();
            int width = image.getWidth(), height = image.getHeight();
            updateMotionGrid(buffer, rowStride, pixelStride, width, height);
            if (objectDetector != null && now - lastDetectionAt >= MIN_DETECTION_INTERVAL_MS) {
                lastDetectionAt = now;
                Bitmap full = null, crop = null;
                try {
                    full = imageToBitmap(buffer, rowStride, pixelStride, width, height);
                    List<Candidate> candidates = new ArrayList<>();
                    float threshold = ScreenMotionState.getConfidenceThreshold();
                    runPass(full, 0f, 0f, 1f, 1f, threshold, false, candidates);
                    if (ScreenMotionState.isLongRangeZoomEnabled()) {
                        int cw = Math.max(1, Math.round(width * CROP_FRACTION));
                        int ch = Math.max(1, Math.round(height * CROP_FRACTION));
                        int cl = (width - cw) / 2, ct = (height - ch) / 2;
                        crop = Bitmap.createBitmap(full, cl, ct, cw, ch);
                        runPass(crop, cl / (float) width, ct / (float) height,
                                cw / (float) width, ch / (float) height,
                                Math.min(0.20f, threshold), true, candidates);
                    }
                    List<ScreenMotionState.PlayerBox> boxes = nms(candidates);
                    ScreenMotionState.detectedPlayers = boxes.toArray(new ScreenMotionState.PlayerBox[0]);
                    updateAimGuide(boxes);
                    ScreenMotionState.level = boxes.isEmpty() ? "هدف قابل تشخیص نیست" : boxes.size() + " هدف بصری";
                } catch (Exception ignored) { ScreenMotionState.level = "خطا در تحلیل فریم"; }
                finally {
                    if (crop != null && !crop.isRecycled()) crop.recycle();
                    if (full != null && !full.isRecycled()) full.recycle();
                }
            }
        } finally {
            if (processed) {
                long finished = System.nanoTime();
                ScreenMotionState.setInferenceLatencyMs(Math.max(0L, (finished-started)/1_000_000L));
                if (fpsInitialized) {
                    float instant = 1_000_000_000f / Math.max(1L, finished-lastFpsTimestampNs);
                    float old = ScreenMotionState.getVisionFps();
                    ScreenMotionState.setVisionFps(old <= 0f ? instant : old*0.8f + instant*0.2f);
                } else fpsInitialized = true;
                lastFpsTimestampNs = finished;
            }
        }
    }

    private void runPass(Bitmap bitmap, float cropLeft, float cropTop, float cropWidth, float cropHeight,
                         float scoreFloor, boolean zoomPass, List<Candidate> output) {
        List<Detection> detections = objectDetector.detect(TensorImage.fromBitmap(bitmap));
        for (Detection detection : detections) {
            String label = null; float confidence = 0f;
            for (Category category : detection.getCategories()) {
                String name = category.getLabel();
                if (name == null) continue;
                boolean person = "person".equalsIgnoreCase(name);
                boolean vehicle = isVehicle(name) && ScreenMotionState.isDetectVehiclesEnabled();
                if ((person || vehicle) && category.getScore() > confidence) {
                    label = name; confidence = category.getScore();
                }
            }
            if (label == null || confidence < scoreFloor) continue;
            android.graphics.RectF r = detection.getBoundingBox();
            float[] b = AimMath.mapCropBoxToFullScreen(r.left/bitmap.getWidth(), r.top/bitmap.getHeight(),
                    r.right/bitmap.getWidth(), r.bottom/bitmap.getHeight(),
                    cropLeft, cropTop, cropWidth, cropHeight);
            String type = "person".equalsIgnoreCase(label) ? "PERSON" : "VEHICLE";
            ScreenMotionState.PlayerBox box = new ScreenMotionState.PlayerBox(
                    b[0], b[1], b[2], b[3], confidence, type,
                    isBoxMoving(b[0], b[1], b[2], b[3]), zoomPass);
            output.add(new Candidate(box));
        }
    }

    private boolean isVehicle(String label) {
        return "car".equalsIgnoreCase(label) || "motorcycle".equalsIgnoreCase(label)
                || "truck".equalsIgnoreCase(label) || "bus".equalsIgnoreCase(label);
    }

    private List<ScreenMotionState.PlayerBox> nms(List<Candidate> candidates) {
        Collections.sort(candidates, Comparator.comparingDouble((Candidate c) -> c.box.confidence).reversed());
        List<ScreenMotionState.PlayerBox> kept = new ArrayList<>();
        for (Candidate candidate : candidates) {
            boolean duplicate = false;
            for (int i=0; i<kept.size(); i++) {
                ScreenMotionState.PlayerBox old = kept.get(i), next = candidate.box;
                if (!old.category.equals(next.category)) continue;
                float iou = AimMath.computeIoU(old.left,old.top,old.right,old.bottom,
                        next.left,next.top,next.right,next.bottom);
                if (iou > NMS_IOU_THRESHOLD) {
                    duplicate = true;
                    if (next.isMoving && !old.isMoving) kept.set(i, new ScreenMotionState.PlayerBox(
                            old.left,old.top,old.right,old.bottom,old.confidence,old.category,true,
                            old.isLongRangeZoom || next.isLongRangeZoom));
                    break;
                }
            }
            if (!duplicate) kept.add(candidate.box);
            if (kept.size() >= MAX_DETECTIONS) break;
        }
        return kept;
    }

    private boolean isBoxMoving(float left, float top, float right, float bottom) {
        int x0=Math.max(0,Math.min(GRID-1,(int)(left*GRID))), y0=Math.max(0,Math.min(GRID-1,(int)(top*GRID)));
        int x1=Math.max(0,Math.min(GRID-1,(int)(right*GRID))), y1=Math.max(0,Math.min(GRID-1,(int)(bottom*GRID)));
        for (int y=y0;y<=y1;y++) for (int x=x0;x<=x1;x++) if (movingGrid[y*GRID+x]) return true;
        return false;
    }

    private void updateMotionGrid(ByteBuffer buffer, int rowStride, int pixelStride, int width, int height) {
        byte[] current = new byte[GRID*GRID]; boolean[] changed = new boolean[GRID*GRID]; long total=0;
        for (int gy=0;gy<GRID;gy++) for (int gx=0;gx<GRID;gx++) {
            int x=Math.min(width-1,(gx*2+1)*width/(GRID*2)), y=Math.min(height-1,(gy*2+1)*height/(GRID*2));
            int index=y*rowStride+x*pixelStride, lum=0;
            if (index+2<buffer.limit()) {
                int r=buffer.get(index)&255,g=buffer.get(index+1)&255,b=buffer.get(index+2)&255;
                lum=(r*30+g*59+b*11)/100;
            }
            int cell=gy*GRID+gx; current[cell]=(byte)lum;
            if (previousGrid!=null) {
                int delta=Math.abs(lum-(previousGrid[cell]&255)); changed[cell]=delta>=18; total+=delta;
            }
        }
        if (previousGrid!=null) {
            float raw=total/(float)current.length*100f/255f;
            smoothedScore=smoothedScore==0f?raw:smoothedScore*0.65f+raw*0.35f;
            ScreenMotionState.motionScore=smoothedScore;
        }
        movingGrid=changed; previousGrid=current;
    }

    private void updateAimGuide(List<ScreenMotionState.PlayerBox> boxes) {
        float radius=ScreenMotionState.getFovRadius();
        ScreenMotionState.PlayerBox selected;
        if (ScreenMotionState.isTargetLockEnabled())
            selected=targetTracker.update(boxes.toArray(new ScreenMotionState.PlayerBox[0]),radius);
        else { targetTracker.reset(); selected=closestTarget(boxes,radius); }
        ScreenMotionState.setLockedTargetBox(selected);
        if (selected==null) {
            ScreenMotionState.aimGuideActive=false; ScreenMotionState.aimDeltaX=0f;
            ScreenMotionState.aimDeltaY=0f; smoothedAimVector=null; return;
        }
        AimMath.Vec2 point=AimMath.computeTargetPoint(selected.left,selected.top,selected.right,selected.bottom,
                ScreenMotionState.isHeadZoneEnabled());
        AimMath.Vec2 delta=AimMath.aimDelta(point,new AimMath.Vec2(0.5f,0.5f),
                getSharedPreferences("coach_settings",MODE_PRIVATE).getInt("aim_sensitivity",50)/100f,0.01f,0.25f);
        if (delta==null) {
            ScreenMotionState.aimGuideActive=false; ScreenMotionState.aimDeltaX=0f;
            ScreenMotionState.aimDeltaY=0f; smoothedAimVector=null; return;
        }
        smoothedAimVector=AimMath.smoothVector(delta,smoothedAimVector,ScreenMotionState.getSmoothingAlpha());
        ScreenMotionState.aimTargetX=point.x; ScreenMotionState.aimTargetY=point.y;
        ScreenMotionState.aimDeltaX=smoothedAimVector.x; ScreenMotionState.aimDeltaY=smoothedAimVector.y;
        ScreenMotionState.aimGuideActive=true;
    }

    private ScreenMotionState.PlayerBox closestTarget(List<ScreenMotionState.PlayerBox> boxes,float radius) {
        ScreenMotionState.PlayerBox best=null; float bestDistance=Float.MAX_VALUE;
        AimMath.Vec2 center=new AimMath.Vec2(0.5f,0.5f);
        for (ScreenMotionState.PlayerBox box:boxes) {
            AimMath.Vec2 point=AimMath.computeTargetPoint(box.left,box.top,box.right,box.bottom,
                    ScreenMotionState.isHeadZoneEnabled());
            if (!AimMath.inCone(point,center,radius)) continue;
            float distance=(float)Math.hypot(point.x-center.x,point.y-center.y);
            if (distance<bestDistance) { bestDistance=distance; best=box; }
        }
        return best;
    }

    private Bitmap imageToBitmap(ByteBuffer source,int rowStride,int pixelStride,int width,int height) {
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        int[] pixels=new int[width*height];
        for (int y=0;y<height;y++) for (int x=0;x<width;x++) {
            int index=y*rowStride+x*pixelStride;
            if (index+3>=source.limit()) pixels[y*width+x]=Color.TRANSPARENT;
            else {
                int r=source.get(index)&255,g=source.get(index+1)&255,b=source.get(index+2)&255,a=source.get(index+3)&255;
                pixels[y*width+x]=(a<<24)|(r<<16)|(g<<8)|b;
            }
        }
        bitmap.setPixels(pixels,0,width,0,0,width,height); return bitmap;
    }

    private void startAsForeground() {
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL_ID):new Notification.Builder(this);
        Notification notification=builder.setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("OneState Aim Coach").setContentText("تحلیل زندهٔ تصویر با رضایت شما فعال است")
                .setOngoing(true).build();
        if (Build.VERSION.SDK_INT>=29) startForeground(NOTIFICATION_ID,notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(NOTIFICATION_ID,notification);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT>=26) {
            NotificationChannel channel=new NotificationChannel(CHANNEL_ID,"تحلیل زنده تصویر",NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("اعلان سرویس تحلیل تصویر با اجازه کاربر");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override public void onDestroy() {
        ScreenMotionState.active=false; ScreenMotionState.level="خاموش";
        ScreenMotionState.detectedPlayers=new ScreenMotionState.PlayerBox[0];
        ScreenMotionState.aimGuideActive=false; ScreenMotionState.aimDeltaX=0f; ScreenMotionState.aimDeltaY=0f;
        ScreenMotionState.setLockedTargetBox(null); ScreenMotionState.setVisionFps(0f);
        ScreenMotionState.setInferenceLatencyMs(0L); targetTracker.reset(); smoothedAimVector=null;
        previousGrid=null; ScreenMotionState.offsetContractReady=false;
        if (virtualDisplay!=null) virtualDisplay.release();
        if (imageReader!=null) imageReader.close();
        if (projection!=null) try { projection.stop(); } catch (Exception ignored) {}
        if (objectDetector!=null) { try { objectDetector.close(); } catch (Exception ignored) {} objectDetector=null; }
        if (workerThread!=null) { workerThread.quitSafely(); workerThread=null; }
        stopForeground(true); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }

    private static final class Candidate {
        final ScreenMotionState.PlayerBox box;
        Candidate(ScreenMotionState.PlayerBox box) { this.box=box; }
    }
}