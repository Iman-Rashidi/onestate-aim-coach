# Repository Agent Contract

- Full files only; never diffs, never `...`, never `// unchanged`.
- Definition of Done: `gradle testDebugUnitTest assembleDebug` passes.
- Language & Structure: Java only, package stays `com.iman.onestateaimcoach`, no class renames.
- JNI Synchronization: JNI signatures must stay in sync with `NativeBridge.java` in the same commit/response.
- AOB Equivalence: `Aob.matches` (Java) and `matchMask` (C++) must remain semantically equal; `AobMaskTest` is the arbiter.
- CI Error Loop: CI writes build failures to orphan branch `ci-log`; read it, fix the root cause, max 8 iterations (if a single error survives 2 iterations, stop and report).
