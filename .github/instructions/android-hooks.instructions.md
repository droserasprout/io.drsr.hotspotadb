---
applyTo: "app/src/main/kotlin/**/*.kt,app/src/main/AndroidManifest.xml,app/build.gradle.kts,README.md"
---

This repository contains Android hook code for LSPosed/Xposed.

Additional current compatibility baseline:
- Treat Magisk `v30.7` as the default root-manager baseline for Android 16 work.
- Treat Vector `v2.0` stable as the default modern framework baseline.
- Treat libxposed API `101.0.1` as the default modern module API baseline.
- Remember that Vector expects a recent Magisk/KernelSU installation with Zygisk enabled, and upstream prefers latest debug builds for bug reports/troubleshooting.

When working on files matched by this instruction:

- Treat hidden/internal Android classes as unstable across Android versions.
- Verify symbol names and ownership before changing reflective hook code.
- Prefer ordered fallbacks for cross-version compatibility instead of a single hardcoded class path.
- Keep legacy XposedBridge compatibility unless the task explicitly requests modern libxposed / API 101 migration.
- For modern migration work, target real libxposed API `101.0.1` packaging and runtime behaviour compatible with current Vector `v2.0`, not historical LSPosed-only conventions.
- Maintain the separation between:
  - Settings UI hook logic
  - framework / `system_server` ADB logic
  - hotspot helper heuristics
- Keep hotspot-only gating explicit.
- Avoid behaviour changes that affect normal client-mode wireless debugging.
- When changing compatibility claims in `README.md`, align them with the actual implemented and validated code path.
- When changing Gradle/manifest metadata, preserve the correct module packaging semantics for the intended track: legacy XposedBridge on the legacy track, modern `META-INF/xposed/*` packaging on the Vector/libxposed API 101 track.
- Do not write documentation or comments that assume an LSPosed-branded manager UX when the task is about Vector compatibility.

Expected output for substantial changes:
- mention the exact Android internal symbols/classes touched
- mention branch/version assumptions
- mention validation commands run
