# Engineering Baseline Audit

Date: 2026-11-24
Repository: Iman-Rashidi/onestate-aim-coach
Audited base: main
Base commit: ce319244133e68cd0368b0d86a60a3c1bbb03d86
Audit branch: chore/procinspector-phase0-audit

## 1. Repository identity

- Public GitHub repository.
- Default branch: main.
- Primary language: Java.
- Existing application: OneState Aim Coach 0.3.
- Existing applicationId and namespace: com.iman.onestateaimcoach.
- Requested Process Inspector applicationId: com.iman.procinspector.
- The requested package identifier does not match the current application. This is a known Phase 1 migration requirement, not an accidental edit.

## 2. Current main-branch structure

- .github/workflows/android-apk.yml
- .github/workflows/android-apk-release.yml
- README.md
- app/build.gradle
- app/src/main/AndroidManifest.xml
- app/src/main/java/com/iman/onestateaimcoach/MainActivity.java
- app/src/main/java/com/iman/onestateaimcoach/OverlayService.java
- app/src/main/java/com/iman/onestateaimcoach/ScreenAnalysisService.java
- app/src/main/java/com/iman/onestateaimcoach/ScreenMotionState.java
- build.gradle
- gradle.properties
- settings.gradle

The main branch tree does not contain a Gradle wrapper (gradlew, gradlew.bat, or gradle/wrapper files), native CMake files, instrumentation tests, or the requested Process Inspector packages.

## 3. Existing Android configuration

- Java source/target compatibility: Java 17.
- Android Gradle Plugin: 8.7.3.
- CI-installed Gradle: 8.9.
- compileSdk: 35.
- minSdk: 26.
- targetSdk: 35.
- Existing applicationId: com.iman.onestateaimcoach.
- Existing dependency: org.tensorflow:tensorflow-lite-task-vision:0.4.4.
- Current app module does not declare an arm64-v8a-only ABI filter.

## 4. Existing application capabilities

The current README and source tree describe:
- A user-controlled floating overlay.
- MediaProjection screen capture after explicit consent.
- On-device EfficientDet Lite0 object detection.
- Visual bounding-box feedback.
- Screen-motion measurement.
- Preference controls for sensitivity and reminders.

The current README explicitly describes the app as visual-only and says it does not use root access, game-memory access, packet inspection, input injection, aim movement, or automatic firing.

## 5. Existing CI baseline

Main commit: ce319244133e68cd0368b0d86a60a3c1bbb03d86

Observed workflow runs:
- Android APK run 44610092595: completed successfully; debug APK build and artifact upload passed.
- Android APK release run 44610093188: completed successfully; debug APK build, output verification, and release publication passed.

The CI workflows install Gradle 8.9 directly and invoke `gradle`; the repository does not currently use a committed Gradle wrapper.

The main-branch workflow is configured for pushes to main and pull requests targeting main. The release workflow publishes a debug APK as a GitHub release asset; it is not a separately configured signed release build.

## 6. Existing open pull request and branch work

Open PR #4:
https://github.com/Iman-Rashidi/onestate-aim-coach/pull/4

Head branch: chore/contract
Head commit: 3c54802d253278af0c4ff02ba497a577f01584e3
Observed CI run: 45493814256, completed successfully.

PR #4 adds:
- AGENTS.md.
- A checked-in offsets JSON contract.
- OffsetRepo.java.
- AimMath.java.
- Aob.java.
- AimMathTest.java.
- AobMaskTest.java.
- A branch-triggered Android CI workflow with unit tests and build-log artifact upload.

The PR description says the offset values and AOB pattern are unset/inert, no native memory layer was added, and target-device runtime validation remains outstanding. PR #4 is open and has not been merged. Its changes are not part of the main-branch baseline.

## 7. Baseline gaps against the requested Process Inspector specification

The main branch does not yet provide:
- Package com.iman.procinspector.
- arm64-v8a-only ABI enforcement.
- Persistent managed root-shell infrastructure.
- Authorized peer-process discovery.
- Process maps parsing for the requested diagnostic workflow.
- A managed process-memory diagnostic session.
- Chunked AOB scanning with overlap.
- Typed float and pointer diagnostics.
- Pointer-chain diagnostics.
- A lifecycle-managed periodic diagnostic worker.
- The requested accessibility service.
- The requested warm-tone region clustering.
- The two requested on-device integration test classes.
- The requested pointer-delta/deadzone/clamp unit tests.

Some existing overlay and MediaProjection code can be inspected for reuse, but it must not be assumed to satisfy the new service lifecycle, permission, or test requirements without code-level review.

## 8. Build and test commands available in CI

The repository currently uses system Gradle 8.9 in GitHub Actions:

```bash
gradle --no-daemon testDebugUnitTest --stacktrace
gradle --no-daemon assembleDebug --stacktrace
```

The following commands from the project kickoff are not yet guaranteed to work locally because the Gradle wrapper is absent:

```bash
./gradlew clean assembleDebug
./gradlew assembleRelease
./gradlew testDebugUnitTest
./gradlew compileDebugAndroidTestSources
./gradlew connectedDebugAndroidTest
```

A wrapper must be added and validated during the foundation phase before relying on wrapper commands.

## 9. Phase 0 conclusion

Status: PASS

The repository, main-branch structure, Android configuration, existing workflows, recent main CI runs, and open PR #4 have been inspected.

No application source files were modified during this audit.

The next implementation phase must preserve existing OneState Aim Coach behavior where reusable, establish the requested com.iman.procinspector application identity, enforce arm64-v8a-only packaging, add the Gradle wrapper, and validate the foundation before introducing dependent subsystems.

## 10. Change-control notes

- Do not merge PR #4 as part of this audit.
- Do not overwrite the existing OneState application source without reviewing and preserving reusable behavior.
- Do not infer or invent game-specific offsets.
- Do not claim device-level functionality from CI compilation alone.
- Keep the Process Inspector implementation separately testable and limited to explicitly authorized diagnostic fixtures.
