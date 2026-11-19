# OneState Aim Coach 0.4

An Android companion for OneState that analyzes only the visible screen after explicit Android MediaProjection consent. Image processing stays on-device. The app displays small, non-interactive candidate markers over visible person-like objects.

## Features

- Movable, collapsible floating panel.
- Camera and aim sensitivity reference sliders.
- Recoil-control reminder setting.
- Live screen capture with explicit Android consent.
- On-device EfficientDet Lite0 object detection for visible `person` candidates.
- Compact circular markers instead of large bounding boxes.
- Temporary IDs and position smoothing across consecutive detections to reduce marker flicker.
- Screen-motion metric from a small center patch.
- No root access, game-memory access, packet inspection, input injection, aim movement, or automatic firing.
- Raw frames are processed in memory and are not saved or uploaded by the app.

## Detection limitations

The bundled detector is a general COCO model, not a model trained on OneState screenshots. It may miss game characters or mark teammates, NPCs, posters, or other person-like objects. It does **not** reliably distinguish enemies from teammates. Temporary IDs only associate nearby detections across adjacent processed frames; they are not verified player identities. The markers are visual annotations only, not an aim lock.

A reliable OneState-specific classifier would require a representative, labelled dataset and evaluation on real game frames.

## Build

Java 17, Android Gradle Plugin 8.7.3, Gradle 8.9, compile SDK 35. GitHub Actions downloads the EfficientDet Lite0 model and builds a debug APK on pushes to `main` and pull requests targeting `main`.

## Device validation

Samsung Galaxy S23 Ultra is the intended first test device. No compatibility or performance claim is made until the APK is installed and tested on that phone. MediaProjection and overlay behavior must be checked on-device. Detection is scheduled no more often than once every 250 ms; actual speed depends on device load. High-resolution screen capture and inference may affect battery use, heat, and game frame rate.
