# SUMER Android

Initial Android application for the SUMER multi-sensor scanner.

## Current prototype

- Android app module: `com.araztools.sumer`
- Bluetooth: Bluetooth Classic / SPP, intended for HC-05
- Controller: ATmega128
- Incoming text protocol: one value per line, numeric range 0–5000
- Standalone `a` line toggles matrix scan start/end
- User-configurable matrix dimensions
- Raw incoming values and receive log

## Important protocol note

The current prototype does not guess which incoming numeric value belongs to each of the four FLC100 channels or the inductive channel. A channel identifier or a confirmed fixed transmission order is needed before assigning the five channels to separate displays. During matrix mode, valid numeric values are added sequentially to the matrix.

## Build APK in GitHub

1. Open the **Actions** tab in this repository.
2. Select **Build SUMER Android APK**.
3. If required, press **Run workflow** on branch `main`.
4. Wait until the run finishes successfully.
5. Open the completed run and download the `SUMER-debug-apk` artifact.
6. Extract the ZIP file. The installable debug package is `app-debug.apk`.

The workflow builds with JDK 17, Gradle 8.9 and Android Gradle Plugin 8.7.3. The APK is a debug build, not a Play Store release.

## Install on Android

Copy `app-debug.apk` to the phone, open it, and allow installation from that file source if Android asks. Pair the HC-05 first in Android Bluetooth settings. Then open SUMER, grant Nearby devices/Bluetooth permission, refresh paired devices, select HC-05 and connect.

## Hardware validation

A successful cloud build does not prove the Bluetooth link or sensor protocol on real hardware. HC-05/ATmega128 testing requires the physical scanner.