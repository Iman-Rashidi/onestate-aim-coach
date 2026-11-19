package com.iman.onestateaimcoach;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.net.Uri;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class VideoAnalysisTask {
    private static final float PERSON_SCORE_THRESHOLD = 0.25f;
    private static final float NMS_IOU_THRESHOLD = 0.40f;
    private static final int MAX_SAMPLED_FRAMES = 90;

    public interface ProgressListener {
        void onProgress(int processedFrames, int totalFrames, long timestampMs, int peopleCount);
    }

    public static final class FrameSummary {
        public final long timestampMs;
        public final int peopleCount;

        FrameSummary(long timestampMs, int peopleCount) {
            this.timestampMs = timestampMs;
            this.peopleCount = peopleCount;
        }
    }

    public static final class Report {
        public final long durationMs;
        public final int sampledFrames;
        public final int framesWithPeople;
        public final int peakPeople;
        public final int totalObservations;
        public final List<FrameSummary> frames;

        Report(long durationMs, int sampledFrames, int framesWithPeople,
               int peakPeople, int totalObservations, List<FrameSummary> frames) {
            this.durationMs = durationMs;
            this.sampledFrames = sampledFrames;
            this.framesWithPeople = framesWithPeople;
            this.peakPeople = peakPeople;
            this.totalObservations = totalObservations;
            this.frames = frames;
        }

        public String toDisplayText() {
            StringBuilder out = new StringBuilder();
            out.append("نتیجهٔ تحلیل آفلاین\n");
            out.append("مدت ویدئو: ").append(formatTime(durationMs)).append("\n");
            out.append("فریم‌های بررسی‌شده: ").append(sampledFrames).append("\n");
            out.append("فریم‌های دارای شخص احتمالی: ").append(framesWithPeople).append("\n");
            out.append("بیشترین تعداد در یک فریم: ").append(peakPeople).append("\n");
            out.append("مجموع مشاهده‌ها در فریم‌های نمونه: ").append(totalObservations).append("\n\n");
            out.append("جزئیات زمانی (فقط فریم‌های دارای تشخیص):\n");
            int shown = 0;
            for (FrameSummary frame : frames) {
                if (frame.peopleCount <= 0) continue;
                if (shown >= 24) {
                    out.append("... ادامهٔ جزئیات در تحلیل کامل موجود نیست.\n");
                    break;
                }
                out.append(formatTime(frame.timestampMs))
                        .append("  |  ")
                        .append(frame.peopleCount)
                        .append(" شخص احتمالی\n");
                shown++;
            }
            if (shown == 0) out.append("در فریم‌های نمونه‌شده شخصی با اطمینان کافی پیدا نشد.\n");
            out.append("\nیادآوری: این مدل عمومی است؛ نتیجه شمارش قطعی بازیکنان نیست.");
            return out.toString();
        }
    }

    private static final class Candidate {
        final RectF box;
        final float score;

        Candidate(RectF box, float score) {
            this.box = box;
            this.score = score;
        }
    }

    private VideoAnalysisTask() {}

    public static Report analyze(Context context, Uri videoUri, ProgressListener listener)
            throws Exception {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        ObjectDetector detector = null;
        try {
            retriever.setDataSource(context, videoUri);
            String durationText = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationText == null) {
                throw new IllegalStateException("مدت ویدئو قابل‌خواندن نیست.");
            }
            long durationMs = Long.parseLong(durationText);
            if (durationMs <= 0) {
                throw new IllegalStateException("ویدئوی انتخاب‌شده خالی است.");
            }

            ObjectDetector.ObjectDetectorOptions options =
                    ObjectDetector.ObjectDetectorOptions.builder()
                            .setMaxResults(20)
                            .setScoreThreshold(PERSON_SCORE_THRESHOLD)
                            .build();
            detector = ObjectDetector.createFromFileAndOptions(
                    context.getApplicationContext(), "efficientdet-lite0.tflite", options);

            long intervalMs = Math.max(800L,
                    (long) Math.ceil(durationMs / (double) MAX_SAMPLED_FRAMES));
            int totalFrames = (int) Math.min(MAX_SAMPLED_FRAMES,
                    Math.max(1L, (durationMs + intervalMs - 1L) / intervalMs));

            List<FrameSummary> summaries = new ArrayList<>();
            int framesWithPeople = 0;
            int peakPeople = 0;
            int totalObservations = 0;
            int processed = 0;

            for (long timestampMs = 0;
                 timestampMs < durationMs && processed < MAX_SAMPLED_FRAMES;
                 timestampMs += intervalMs) {
                Bitmap frame = null;
                int count = 0;
                try {
                    frame = retriever.getFrameAtTime(
                            timestampMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST);
                    if (frame != null) {
                        count = detectPersonsInTiledFrame(detector, frame);
                        summaries.add(new FrameSummary(timestampMs, count));
                        if (count > 0) framesWithPeople++;
                        peakPeople = Math.max(peakPeople, count);
                        totalObservations += count;
                    }
                } finally {
                    if (frame != null && !frame.isRecycled()) frame.recycle();
                }
                processed++;
                if (listener != null) {
                    listener.onProgress(processed, totalFrames, timestampMs, count);
                }
            }

            return new Report(durationMs, processed, framesWithPeople, peakPeople,
                    totalObservations, summaries);
        } finally {
            if (detector != null) detector.close();
            retriever.release();
        }
    }

    /*
     * Each frame is examined as nine overlapping crops. A distant character occupies
     * a larger fraction of a crop than of the full frame, which can help a generic
     * detector. This is offline video analysis only, not a live game overlay.
     */
    private static int detectPersonsInTiledFrame(ObjectDetector detector, Bitmap frame) {
        int width = frame.getWidth();
        int height = frame.getHeight();
        int tileWidth = Math.max(1, (int) Math.ceil(width * 0.50d));
        int tileHeight = Math.max(1, (int) Math.ceil(height * 0.50d));
        int[] xPositions = tilePositions(width, tileWidth);
        int[] yPositions = tilePositions(height, tileHeight);
        List<Candidate> candidates = new ArrayList<>();

        for (int y : yPositions) {
            for (int x : xPositions) {
                Bitmap crop = null;
                try {
                    crop = Bitmap.createBitmap(frame, x, y, tileWidth, tileHeight);
                    List<Detection> detections = detector.detect(TensorImage.fromBitmap(crop));
                    for (Detection detection : detections) {
                        float confidence = 0f;
                        for (Category category : detection.getCategories()) {
                            if ("person".equalsIgnoreCase(category.getLabel())) {
                                confidence = Math.max(confidence, category.getScore());
                            }
                        }
                        if (confidence < PERSON_SCORE_THRESHOLD) continue;

                        RectF local = detection.getBoundingBox();
                        RectF mapped = new RectF(
                                clamp((x + local.left) / (float) width),
                                clamp((y + local.top) / (float) height),
                                clamp((x + local.right) / (float) width),
                                clamp((y + local.bottom) / (float) height));
                        if (mapped.width() > 0f && mapped.height() > 0f) {
                            candidates.add(new Candidate(mapped, confidence));
                        }
                    }
                } finally {
                    if (crop != null && crop != frame && !crop.isRecycled()) crop.recycle();
                }
            }
        }
        return nonMaximumSuppression(candidates, NMS_IOU_THRESHOLD).size();
    }

    private static int[] tilePositions(int dimension, int tileSize) {
        Set<Integer> positions = new LinkedHashSet<>();
        positions.add(0);
        positions.add(Math.max(0, (dimension - tileSize) / 2));
        positions.add(Math.max(0, dimension - tileSize));
        int[] result = new int[positions.size()];
        int index = 0;
        for (Integer value : positions) result[index++] = value;
        return result;
    }

    private static List<Candidate> nonMaximumSuppression(
            List<Candidate> candidates, float threshold) {
        Collections.sort(candidates, new Comparator<Candidate>() {
            @Override public int compare(Candidate left, Candidate right) {
                return Float.compare(right.score, left.score);
            }
        });

        List<Candidate> kept = new ArrayList<>();
        for (Candidate candidate : candidates) {
            boolean overlaps = false;
            for (Candidate accepted : kept) {
                if (intersectionOverUnion(candidate.box, accepted.box) >= threshold) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) kept.add(candidate);
        }
        return kept;
    }

    private static float intersectionOverUnion(RectF a, RectF b) {
        float left = Math.max(a.left, b.left);
        float top = Math.max(a.top, b.top);
        float right = Math.min(a.right, b.right);
        float bottom = Math.min(a.bottom, b.bottom);
        float intersection = Math.max(0f, right - left) * Math.max(0f, bottom - top);
        float union = a.width() * a.height() + b.width() * b.height() - intersection;
        return union <= 0f ? 0f : intersection / union;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static String formatTime(long millis) {
        long totalSeconds = millis / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        long tenths = (millis % 1000L) / 100L;
        return String.format(java.util.Locale.US, "%02d:%02d.%d", minutes, seconds, tenths);
    }
}
