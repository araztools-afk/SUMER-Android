# SUMER Android

SUMER is a magnetometric survey application. It is not a conventional metal-detector app.

## SUMER2 software scope

This release candidate consolidates the software requirements from the SUMER design discussions. Hardware, sensor electronics, circuit design and firmware are outside this application-only milestone.

### Main interface
- Brand: SUMER — MAGNETOMETRIC SURVEY SYSTEM.
- Home tiles: row 1 = LIVE / PIN; row 2 = AUTOMATIC / MANUAL.
- Device Connection strip above the tiles and Settings strip below them.
- Dark silver and light themes, plus selectable accent colours.
- Touch feedback can be Sound, Vibration, Both or None.

### LIVE
- Shows only the latest coloured measurement block.
- Each new reading replaces the previous block.
- CURRENT, MIN and MAX are displayed.
- No live trend chart and no live sample/image history are created.
- Manual test-value entry is available.

### PIN
- Point name/ID and manual test input.
- First two seconds of received values establish the baseline.
- A horizontal sine trace responds to deviations from baseline.
- Square-wave audio tone is mapped to 1–400 Hz as the value moves below/above baseline.
- Saved point records remain on the phone.

### MANUAL and AUTOMATIC
- Configurable rows and columns; default grid is 4 × 3, valid range 1–50 per dimension, maximum 2,500 cells.
- Accepted scan path starts at bottom-left and proceeds vertically, alternating direction column by column.
- Matrix scan begins/ends on a standalone `a` line. Manual test-scan start/finish controls are also available.
- Manual numeric test input (0–5000) uses the same processing path as received values.
- Heatmap cells are equal-sized, bordered and labelled with block number and value.
- A scan graph is shown after completion.
- Matrix alerts use a 200 ms signal for ordinary samples and 500 ms for the last cell in each row.
- Completed scans are saved as PNG and CSV in the app's SUMER gallery. PNG images can be exported to Pictures/SUMER.

### Bluetooth and terminal
- Bluetooth Classic / SPP connection to paired devices such as HC-05.
- Last selected device can be reconnected automatically when enabled.
- The terminal shows received lines and can send a text command/value back over SPP.
- Input protocol currently supported by the app: one integer per line, 0–5000; standalone `a` toggles matrix scan start/end.
- Baud selection is a saved project setting only. It does not remotely change the HC-05 UART setting.

### Registration
- First-run SERIAL REGISTRATION screen.
- The current test build accepts the previously specified code `13651365` and remembers activation locally.
- This is local prototype gating, not production-grade license security or server verification.

## Build and compatibility
- Application ID remains `com.araztools.sumer.livepreview` so SUMER2 updates the installed SUMER preview rather than creating a different package.
- Stable debug signing key is retained for in-place updates.
- Android minimum SDK 23; target SDK 28; Java 17; Gradle 8.9.
- GitHub Actions validates APK build, package metadata, alignment and signature.

## Verification boundary
A successful CI build confirms compilation, APK packaging, alignment and signing. It does not confirm real-world Bluetooth behaviour, hardware measurements, audio quality on every device, or sensor accuracy. Those require on-device testing with the actual scanner.