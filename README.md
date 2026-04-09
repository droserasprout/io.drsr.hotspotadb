# Hotspot Wireless Debugging

Xposed module that enables Android's Wireless Debugging (ADB over Wi-Fi) when the device is running a Wi-Fi Hotspot instead of being connected to a Wi-Fi network.

Normally, Android requires an active Wi-Fi client connection to use Wireless Debugging. This module hooks the relevant framework and Settings app code so that:

- The Wireless Debugging toggle works when hotspot is active
- The Settings UI shows connection info (IP and port) for the hotspot interface
- Devices connected to the hotspot can use `adb connect <ip>:<port>`
- The connection stays alive as long as the hotspot is on

## Requirements

- Android 15 (API 35)
- LSPosed or compatible Xposed framework

## Installation

Download the latest debug APK from [GitHub Actions](https://github.com/droserasprout/xposed-hotspot-adb/actions) artifacts, or build it yourself:

1. Build the APK: `make build`
2. Install: `make install`
3. Enable the module in LSPosed for both scopes:
   - `com.android.settings`
   - `android` (System Framework)
4. Reboot

## Usage

1. Enable Wi-Fi Hotspot on your device
2. Open Settings > Developer Options > Wireless Debugging
3. Enable the toggle and pair your client device
4. From a device connected to the hotspot:

```shell
adb pair <ip>:<pairing_port>
adb connect <ip>:<port>
```

## Building

Requires JDK 21 and Android SDK.

```shell
make build     # build debug APK
make install   # install via Gradle
make clean     # clean build artifacts
```
