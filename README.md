# SUMER Android

SUMER is a magnetometric geological survey application prototype. It is **not** a conventional metal-detector app.

## Current UI prototype: SUMER1

- Official app label: SUMER
- Temporary vector launcher icon
- First-launch serial-registration screen
- Tile-based home screen: LIVE / PIN / AUTOMATIC / MANUAL
- Wide Device Connection strip above the tiles
- Wide Settings strip below the tiles
- Single numeric input stream (0–5000)
- Live screen replaces its single current visual block; it does not build a live image history
- Manual value entry, Automatic session controls, and PIN point capture
- Settings: dark/light theme, accent colour, manual alert output, touch feedback, baud-rate selection
- Bluetooth Classic / SPP connection to paired devices
- Data terminal and recent-value log

## Registration limitation

The current prototype checks the serial string format and stores successful activation locally. It does **not** yet verify a unique license against a secure license service or signed offline license database. Do not use this prototype gate as production-grade copy protection. A secure per-user/per-install license issuer and verifier must be implemented before commercial release.

## Connection and data limitations

- The current app is configured for one numeric input channel.
- Incoming values are expected as text lines containing integers from 0 to 5000.
- A standalone `a` line is retained as a scan marker.
- Baud rate is a user setting for matching the transmitter configuration; selecting it in the app does not remotely change the Bluetooth module's UART speed.
- Automatic mode does not invent sample values; it displays actual incoming values.
- PIN points are session-local in this prototype. Persistent project storage is a later milestone.

## Build APK in GitHub

1. Open the **Actions** tab.
2. Select **Build SUMER Android APK**.
3. Wait for the workflow to finish successfully.
4. Open the completed run and download the `SUMER-debug-apk` artifact.
5. Extract the ZIP and install `app-debug.apk`.

The workflow uses JDK 17, Gradle 8.9 and Android Gradle Plugin 8.7.3. The APK is a debug build.

## Install on Android

The preview uses application ID `com.araztools.sumer.livepreview` so it can be installed alongside the earlier S1 package. Android 6.0+ is the configured minimum. Pair the Bluetooth device in Android settings before connecting from SUMER.
