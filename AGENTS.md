# Repository Agent Contract

- Write complete files only. Never provide diffs, ellipses (`...`), or `// unchanged`.
- Work is done only when both `gradle testDebugUnitTest assembleDebug` pass.
- Java only: do not add Kotlin, a Kotlin Gradle plugin, or `.kt` files.
- Keep the package name `com.iman.onestateaimcoach`; do not rename existing classes or packages.
- Keep JNI declarations and native implementations synchronized with `NativeBridge.java` in the same response whenever either side changes.
- Java `Aob.matches` and C++ `matchMask` must remain semantically equivalent. `AobMaskTest` is the arbiter for Java behavior.
- Never invent memory offsets. The only source of offsets is `docs/offsets.json`.
- A field whose configured value is `0` is unavailable: its toggle must remain inert and emit a warning.
- Do not change the `OverlayService` panel layout, gesture timing constants, the `AndroidManifest.xml` permission block, or `.github/workflows/android-apk-release.yml` without a stated reason.
- CI writes build failures to the `ci-log` branch. Read that branch, identify and fix the root cause, then rerun CI.
- Allow at most 8 repair iterations. If the same error survives two iterations, stop and report it instead of looping.

<!-- CI trigger no-op: chore: trigger ci -->
