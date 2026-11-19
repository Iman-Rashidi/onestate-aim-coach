# OneState Aim Coach

A lightweight Android companion for online play sessions. It provides a movable floating panel with personal sensitivity references and manual recoil-control reminders while the game remains in the foreground.

## Included

- Adjustable camera sensitivity reference.
- Adjustable aim sensitivity reference.
- Adjustable recoil reminder level.
- Movable, collapsible floating panel.
- One-tap close control.
- User-controlled overlay permission; no root required.
- GitHub Actions workflow that builds a debug APK and uploads it as an artifact.

## Boundaries

The app does not inject touch or controller input, move the in-game crosshair, auto-fire, read or modify game memory, inspect game traffic, reveal hidden opponents, or draw live enemy ESP markers. Settings are references for the player to apply manually.

## Build

The project uses Java 17, Android Gradle Plugin 8.7.3, Gradle 8.9, and compile SDK 35. Pushes to `main` trigger the APK workflow.

## Device validation

The Samsung Galaxy S23 Ultra is the intended first test device. Compatibility is not claimed until the APK is installed and tested on the physical phone. Overlay permission and Samsung One UI behavior must be checked on-device.
