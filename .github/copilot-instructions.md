# Copilot repository instructions

This repository is an Android Xposed/LSPosed module, not a normal app-only project. It hooks both `com.android.settings` and the `android` framework scope to allow native Wireless Debugging to operate over Wi‑Fi hotspot mode.

Assume the current codebase is a **legacy XposedBridge module** (`de.robv.android.xposed:api:82`, `assets/xposed_init`) unless a task explicitly requests a modern libxposed / API 101 migration.

Current upstream baseline to keep in mind:
- Magisk `v30.7` is the latest stable release and explicitly supports Android 16 QPR2 sepolicy and Zygisk on Android 16 QPR2+.
- Vector `v2.0` is the latest stable framework release and upstream positions it as the stable environment for legacy API-era users during the move toward API 101.
- Vector docs say it requires a recent Magisk or KernelSU installation with Zygisk enabled, and recommend the latest debug build for troubleshooting / bug reporting.
- Modern module work should target libxposed API `101.0.1` unless the task explicitly says otherwise.

When proposing or editing code:
- inspect current hook targets before changing them
- separate Settings-process UI hooks from framework / `system_server` hooks
- preserve hotspot-only behaviour changes
- prefer capability probing and fallbacks for Android-version drift
- keep log output precise and prefixed with `HotspotAdb:`
- avoid broad interception that could affect non-hotspot wireless debugging

For Android 16 work, treat framework-side drift in `com.android.server.adb.*` as the main risk area. Expect class ownership and monitoring structure changes across releases.

Do not claim Android 16 or API 101 support unless the relevant code path has actually been updated and validated.

Preferred validation for non-trivial changes:
- `./gradlew ktlintCheck detekt assembleDebug`
- update docs if compatibility claims changed
- when compatibility is discussed, state whether it refers to Magisk stable, Vector stable, or latest Vector debug build
