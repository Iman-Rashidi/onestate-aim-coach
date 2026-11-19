# OneState Aim Coach

An Android companion app for sensitivity reference, a movable optional floating panel, recoil practice notes, and an offline aim-training drill.

## Scope and safety

- The app does not read or modify game memory, inspect game traffic, inject input, move the in-game crosshair, lock onto opponents, or auto-fire.
- Aim training runs inside this app. The floating panel only displays user-configured reference values and can be dismissed at any time.
- No root access is required.

## Build

The project uses Android Gradle Plugin and Java 17. GitHub Actions builds a debug APK and uploads it as a workflow artifact.

## Device

Target device for manual compatibility testing: Samsung Galaxy S23 Ultra. No device test is claimed until an APK is installed and exercised on the phone.
