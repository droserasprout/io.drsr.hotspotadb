# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - 2026-04-12

### Changed

- **Migrated to modern libxposed API 101** — replaced legacy `de.robv.android.xposed:api:82`
  with `io.github.libxposed:api:101.0.1`.  The module now requires an API 101-compatible
  framework (LSPosed Vector era or equivalent).
- **Entry point**: replaced `assets/xposed_init` + `IXposedHookLoadPackage` with
  `META-INF/xposed/java_init.list` + `XposedModule`.  Lifecycle now uses
  `onSystemServerStarting` (framework hooks) and `onPackageLoaded` (Settings hooks).
- **Manifest**: removed all legacy `xposedmodule`, `xposeddescription`, `xposedminversion`,
  `xposedscope` metadata.  Module name and description come from `android:label`/
  `android:description`; scope from `META-INF/xposed/scope.list`; API version from
  `META-INF/xposed/module.prop`.
- **Hook API**: all hooks migrated from `XC_MethodHook` to the modern interceptor chain model
  (`hook(method).intercept { chain -> ... }`).  `XposedHelpers` is no longer used.
- **SDK**: `compileSdk` and `targetSdk` raised from 35 to **36** (Android 16).

### Added

- Android 16 compatibility for `AdbConnectionInfo`:  tries top-level
  `com.android.server.adb.AdbConnectionInfo` before falling back to the Android 15 nested
  class `AdbDebuggingManager$AdbConnectionInfo`.
- Android 16 compatibility for ADB network monitoring: tries named classes
  (`AdbBroadcastReceiver`, `AdbNetworkMonitor`, `AdbWifiNetworkMonitor`) before the
  anonymous inner class scan used on Android 15.
- `deoptimize()` call on `getCurrentWifiApInfo` to prevent JIT inlining bypassing the hook
  in system_server.
- `getContext()` helper now handles both inner-class and (potential) top-level
  `AdbDebuggingHandler` field layouts.

### Removed

- `assets/xposed_init` — replaced by `META-INF/xposed/java_init.list`.
- `res/values/arrays.xml` (`xposed_scope` array) — replaced by `META-INF/xposed/scope.list`.
- All imports of `de.robv.android.xposed.*`.

## [1.0.1] - 2026-04-10

### Fixed

- Fixed wrong IP shown on Wireless Debugging screen when hotspot is active.
- Fixed button label not updating on hotspot/Wi-Fi state changes.

## [1.0.0] - 2026-04-09

Initial release.

[2.0.0]: https://github.com/cbkii/hotspotadb/compare/1.0.1...2.0.0
[1.0.1]: https://github.com/cbkii/hotspotadb/compare/1.0.0...1.0.1
[1.0.0]: https://github.com/cbkii/hotspotadb/releases/tag/1.0.0

