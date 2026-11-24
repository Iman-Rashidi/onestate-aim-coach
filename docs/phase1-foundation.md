# Phase 1 — Android foundation

## Application identity

- Namespace: `com.iman.procinspector`
- Application ID: `com.iman.procinspector`
- Minimum SDK: 26
- Target SDK: 35
- Compile SDK: 35
- Java: 17
- Native ABI filter: `arm64-v8a` only
- Existing visual-analysis and overlay components remain registered under their existing Java package to avoid an unnecessary source migration.

## Target metadata

- Peer-test package configured for later read-only process discovery: `com.Chillgaming.oneState`
- Module of interest: `libil2cpp.so`

These strings are metadata only. This phase does not open the target process memory or resolve offsets.

## Four configurable float slots

The overlay exposes four persistent rows:

| Slot | Type | Default |
|---|---|---:|
| Health | Float | 100.0 |
| Coordinate X | Float | 0.0 |
| Coordinate Y | Float | 0.0 |
| Coordinate Z | Float | 0.0 |

Each row has an enabled toggle and an editable float field. Values and enabled flags are stored in app-private SharedPreferences. Invalid intermediate text does not overwrite the last valid stored float.

**Important:** these controls are configuration/display state only. They are not wired to process memory reads, writes, or periodic freezing. The target package and module labels do not imply that such access is implemented.

## Tests and build

The Android CI workflow runs:

```sh
gradle --no-daemon testDebugUnitTest assembleDebug --stacktrace
```

Unit tests verify the four-slot contract and target metadata. The CI environment installs Gradle 8.9 directly. A checked-in Gradle wrapper is still outstanding; this phase does not claim wrapper validation or device-level testing.

## Preserved behavior

The existing overlay positioning, compact/expanded behavior, visual screen-analysis service, and player-candidate drawing remain in place. No input automation or arbitrary process-memory modification is added.
