# SUMER Android

Android application prototype for the SUMER multi-sensor scanner.

## Current prototype

- Android application ID: `com.araztools.sumer`
- Bluetooth: Bluetooth Classic / SPP, intended for HC-05
- Controller: ATmega128
- Incoming text protocol: one numeric value per line, range `0..5000`
- A standalone lowercase `a` line starts or ends a matrix scan
- User-configurable rectangular matrix dimensions (default display: 10 rows × 20 columns)
- Raw receive log and latest-value display
- Room database for scan metadata and individually indexed samples
- Scan history screen with sample count, min/max, completion state and a short sample preview
- Scan samples are written in ordered batches of 25; a scan interrupted by disconnect is stored as incomplete

## Storage behavior

A scan row is created when the first standalone `a` marker is received. Numeric lines received while the scan is active are stored as separate rows in `scan_samples`, preserving their original order and values. The second standalone `a` marks the scan complete.

If Bluetooth disconnects while a matrix scan is active, the app flushes the pending batch and marks the scan incomplete. If Android terminates the process without running the shutdown path, the next app launch identifies rows that were still open and keeps them in history as incomplete. At most 24 samples may still be in the in-memory batch at the instant of an abrupt process kill.

The database supports both `MANUAL` and `AUTOMATIC` scan mode labels. This prototype currently records marker-driven scans as `AUTOMATIC`; the manual start/stop controls are not yet implemented.

## Protocol and hardware limitations

The app does not guess which incoming numeric value belongs to each of the four FLC100 channels or the inductive channel. A channel identifier or a confirmed fixed transmission order is required before assigning samples to separate sensor displays.

The inductive channel remains reserved until its analog front-end output range and signal conditioning are confirmed. Do not connect an unknown bipolar or over-voltage analog output directly to an ATmega128 ADC input.

The current prototype is not yet the full requested product: the Live graph/sound mode, manual scan controls, channel-specific display, configurable baud-rate procedure and Compose UI are still pending. Android SPP itself does not change the HC-05 UART baud rate; the module and ATmega128 firmware must be configured consistently.

## Build APK

The workflow **Build SUMER Android APK** builds a debug APK on pushes to the main or feature branches, pull requests, or manual workflow dispatch.

1. Open the repository's **Actions** tab.
2. Select **Build SUMER Android APK**.
3. Open a successful run and download the `SUMER-debug-apk` artifact.
4. Extract the ZIP; the installable file is `app-debug.apk`.

The build uses JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3 and Android SDK 35. The APK is a debug build, not a Play Store release.

## Install and test on Android

1. Pair the HC-05 in Android Bluetooth settings first.
2. Install `app-debug.apk`.
3. Grant the Nearby devices/Bluetooth permission when requested.
4. Select the paired HC-05 and connect.
5. Send test lines such as `1250\n`, `1280\n`, `a\n`, `1300\n`, `a\n`.
6. Open **تاریخچه اسکن‌های ذخیره‌شده** to verify that the scan and its samples were retained.

A successful cloud build does not prove the Bluetooth link or sensor protocol on real hardware. Physical HC-05/ATmega128 testing is still required.
