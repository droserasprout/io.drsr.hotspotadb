# AGENTS.md

## Scope of this file

These instructions apply specifically to the Kotlin hook implementation under `app/src/main/kotlin/io/drsr/hotspotadb`.

## Local architecture

Current source files have distinct responsibilities:

- `HotspotAdbModule.kt`: package dispatch by scope / process
- `SettingsHook.kt`: Settings UI hooks and injected hotspot preference behaviour
- `FrameworkHook.kt`: framework / system_server ADB Wi‑Fi hooks
- `HotspotHelper.kt`: hotspot state and IP-address discovery helpers

Preserve that separation unless there is a strong reason to refactor.

## Current runtime/framework assumptions

For code in this directory, assume the important modern host stack is:
- Android 16
- Magisk `v30.7` or other recent root solution with equivalent Zygisk environment
- Vector `v2.0` stable as the primary framework target
- libxposed API `101.0.1` for real modern-migration work

Implications:
- Do not write code that depends on LSPosed-specific branding, manager package names, or UI strings.
- For modern-track changes, use real libxposed APIs rather than legacy helper semantics hidden behind wrappers.
- Keep logs and comments framework-neutral unless a behaviour is genuinely Vector-specific.

## Editing rules for framework hooks

`FrameworkHook.kt` is the highest-risk file in the repo.

When editing it:
- verify the exact target class/method on the intended AOSP branch
- prefer helper functions such as `findFirstClass(...)` / `hookFirstAvailable(...)` if they make branch probing clearer
- log the selected target path
- keep fallback order explicit and deterministic
- preserve hotspot-only gating

For Android 16 compatibility work, expect to probe multiple candidates for:
- `AdbConnectionInfo`
- receiver / monitor classes in `com.android.server.adb`
- any method whose ownership moved from nested to top-level classes

Do not leave raw one-off reflective literals duplicated across the file if they represent version-dependent alternatives.

## Editing rules for settings hooks

`SettingsHook.kt` should remain UI-focused.

- Only fake Wi‑Fi-connected state when hotspot is genuinely active.
- Keep injected preference behaviour aligned with actual global ADB Wi‑Fi state.
- Preserve the ability to open the system Wireless Debugging screen.
- Avoid UI changes that diverge from system semantics or confuse pairing/connection flow.
- Be cautious with receiver/observer registration to avoid duplicate registration and lifecycle leaks.

## Editing rules for helper logic

`HotspotHelper.kt` should stay small and conservative.

- Keep hotspot-state checks and IP-discovery logic easy to audit.
- If adding new interface-name patterns, document why.
- Avoid hiding failures; log diagnostic information when behaviour changes materially.
- Do not rewrite helper logic just for style.

## Logging rules

- Prefix logs with `HotspotAdb:`.
- Log compatibility branch selection once per relevant path.
- Log failure cause precisely enough that LSPosed logs can identify the missing class/method.
- Do not spam repetitive logs in hot paths.

## Refactoring constraints

Refactor only when it improves one of these:
- cross-version hook selection
- diagnosability
- safety of reflection / fallback handling
- readability of hotspot gating

Do not refactor purely for aesthetic reasons if it increases uncertainty around runtime hook behaviour.

## Modern runtime compatibility notes

When touching hook installation or lifecycle code, ensure the implementation remains credible under the current Magisk/Vector runtime model:
- Vector is delivered as a system module installed through Magisk/KernelSU and depends on a Zygisk-capable environment.
- Framework-side failures may be caused by host framework drift rather than app/module code; call that out explicitly in comments or summaries.
- If the task is a modern migration, avoid leaving behind code paths that only make sense for legacy `XposedHelpers`/`XC_MethodHook` flow.

## Validation expectations for code in this directory

For substantial edits in this directory, aim to provide:
- exact symbols changed
- Android-version assumptions
- whether the change is legacy-track or API-101-track
- build/lint result
- any remaining runtime-only validation gaps
