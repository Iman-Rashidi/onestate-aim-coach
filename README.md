# OneState Aim Coach 0.4

An Android companion for personal sensitivity references, recoil reminders, offline review of recorded OneState videos, and a compact floating settings bubble.

## Features

- Camera and aim sensitivity reference sliders.
- Recoil-control reminder setting.
- A draggable floating settings panel that collapses into a 46 dp circular bubble.
- Offline video selection through Android's document picker.
- Local frame sampling from recorded video.
- Nine overlapping image crops per sampled frame to give small, distant people more pixels for a general-purpose detector.
- Non-maximum suppression to merge duplicate detections from overlapping crops.
- A local textual summary with sampled timestamps and estimated person counts.
- No live screen capture, live player boxes, game-memory access, packet inspection, input injection, aim movement, or automatic firing.
- Selected video is read locally by the app; raw frames are not uploaded by this code.

## Detection limitations

The bundled EfficientDet Lite0 model is a general COCO detector, not a model trained on OneState. Tiled crops can improve the detector's chance of finding small people in recorded frames, but cannot guarantee detection at very long distances. Game avatars, visual effects, posters, and stylized characters may be missed or misclassified. Counts are estimates from sampled frames, not a definitive player count.

## Build

Java 17, Android Gradle Plugin 8.7.3, Gradle 8.9, compile SDK 35. GitHub Actions downloads the EfficientDet Lite0 model and builds a debug APK.

## Device validation

Samsung Galaxy S23 Ultra is the intended test device. Runtime speed and detection quality must be measured on actual recordings; video duration, resolution, heat, and battery use affect processing time.
