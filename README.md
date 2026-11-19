# OneState Aim Coach 0.3

An Android companion intended to run alongside OneState during online play. It uses Android MediaProjection after explicit user consent to analyze visible screen pixels locally, and draws non-interactive candidate boxes in a floating overlay.

## Features

- Compact floating control that starts as a 56dp circular bubble to minimize obstruction during gameplay.
- Tap the bubble to expand the settings panel; use the minimize control to return to the bubble.
- Long-press the bubble to drag it; its position is saved for the next start.
- The bubble outline changes color while live screen analysis is active.
- Camera and aim sensitivity reference sliders.
- Recoil-control reminder setting.
- Live screen capture with explicit Android consent.
- On-device EfficientDet Lite0 object detection for visible `person` candidates.
- Floating bounding boxes and confidence values for person-like detections.
- Screen-motion metric from a small center patch.
- No root access, game-memory access, packet inspection, input injection, aim movement, or automatic firing.
- Raw frames are processed in memory and are not saved or uploaded by the app.

## Detection limitations

The bundled detector is a general COCO model, not a model trained on OneState screenshots. It may miss game characters or mark teammates, NPCs, posters, or other person-like objects. It does **not** reliably distinguish enemies from teammates. A reliable OneState-specific enemy classifier requires a representative, labelled dataset and evaluation on real game frames.

## Build

Java 17, Android Gradle Plugin 8.7.3, Gradle 8.9, compile SDK 35. GitHub Actions downloads the EfficientDet Lite0 model and builds a debug APK on pushes to `main` and pull requests targeting `main`.

## Device validation

Samsung Galaxy S23 Ultra is the intended first test device. No compatibility or performance claim is made until the APK is installed and tested on that phone. MediaProjection and overlay behavior must be checked on-device. High-resolution screen capture and inference may affect battery use, heat, and game frame rate.
