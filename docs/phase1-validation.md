# Phase 1 supplemental validation

## Scope

This change completes the missing build-tool and test-reporting pieces for Phase 1. It does not change the Android application identity, Manifest permissions, service declarations, overlay behavior, or process-inspection scope.

## Gradle Wrapper

- Gradle distribution: 8.9 binary distribution
- Distribution URL: \`https://services.gradle.org/distributions/gradle-8.9-bin.zip\`
- Official distribution SHA-256: \`d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab\`
- Official Gradle 8.9 Wrapper JAR SHA-256: \`498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17\`
- \`gradle/actions/setup-gradle@v4\` performs Wrapper JAR validation before the build.

The Wrapper JAR was independently checked against Gradle's published checksum before being added.

## Unit test coverage

The unit test suite checks:

- The four slot identifiers and default values.
- The configured target package and module metadata.
- Persistence behavior for enabled flags and Float values.
- Rejection of NaN and positive/negative infinity without replacing the last valid value.
- Stability of all eight SharedPreferences keys.

The CI job requires non-empty JUnit XML reports, summarizes test counts in the workflow summary, and uploads XML/HTML reports as a separate artifact.

## Manifest and overlay review

Static review confirmed:

- The exported launcher Activity is explicitly marked \`exported=true\`.
- Overlay and screen-analysis services are explicitly \`exported=false\`.
- The overlay service declares \`specialUse\` and its special-use subtype property.
- The screen-analysis service declares \`mediaProjection\`; the matching foreground-service permissions are present.
- The overlay retains compact/expanded panel switching, drag handling, persisted position, and the detection drawing layer.

No Manifest or overlay code changes were made because this review did not identify a Phase 1 blocker in those areas.

## Remaining validation

- The new CI run must pass with the checked-in Wrapper.
- Device-level testing on a real Android 15/target-35 device is still required.
- Runtime permission flows and long-running foreground-service behavior are not proven by JVM unit tests.
