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
- A standalone lowercase `a` line starts or ends a matrix scan; uppercase `A` and embedded text do not act as markers.
- Manual test-scan start/finish controls use the same scan persistence pipeline.
- Each accepted numeric line (0–5000) is stored as an individual, ordered sample row in Room.
- Room stores scan mode, device identity, grid dimensions, start/end time, count, minimum, maximum and completion state.
- Samples are committed in batches of 25 inside database transactions. Interrupted scans are retained as INCOMPLETE.
- The most recent 100 scans can be opened from SCAN DATA HISTORY; each record shows its metadata and first 40 values.
- Completed scans are also saved as PNG and CSV in the app's SUMER gallery. PNG images can be exported to Pictures/SUMER.
- Values beyond the configured grid capacity are explicitly rejected and reported; increase rows/columns before starting a larger scan.

### Bluetooth and terminal
- Bluetooth Classic / SPP discovery of nearby devices plus paired-device selection (including HC-05).
- Last selected device can be reconnected automatically when enabled; the device search dialog shows paired and newly discovered devices.
- Device history is stored in Room: name, MAC address, nickname, last attempt, last successful connection, selected baud rate, last result and connection count.
- Open DEVICE HISTORY to reconnect to a saved device; long-press an entry to edit its nickname.
- The terminal shows received lines and can send one text command/value line back over SPP; embedded CR/LF is rejected to prevent accidental multi-command injection.
- The SPP reader treats Bluetooth as a byte stream: fragmented reads are reassembled, multiple lines in one read are split, and LF, CRLF or CR line endings are accepted.
- Incoming lines are limited to 128 bytes. An overlong line is discarded through its next line delimiter and reported; parsing then resumes with the next line.
- Input protocol: one unsigned decimal integer per line, 0–5000; standalone lowercase `a` is the only matrix marker. Uppercase `A`, signed numbers and embedded marker text are not accepted as valid samples/markers.
- Known status lines (`READY`, `SCANNING`, `STOPPED`, `SENSOR_ERROR`, `LOW_BATTERY`) and unknown text are logged separately and never enter the numeric matrix.
- The Automatic session remains armed between successive `a` start/end pairs until the user stops it. Markers received in Live or unrelated tabs are ignored.
- The on-screen Manual test controls are local simulation only. Automatic hardware commands such as `!START` / `!STOP` remain provisional examples and are not sent automatically until the ATmega128 firmware command contract is finalized.
- Baud selection is a saved project setting only. It does not remotely change the HC-05 UART setting.

### Registration
- First-run SERIAL REGISTRATION screen.
- The current test build accepts the previously specified code `13651365` and remembers activation locally.
- This is local prototype gating, not production-grade license security or server verification.

## Build and compatibility
- Application ID remains `com.araztools.sumer.livepreview` so SUMER2 updates the installed SUMER preview rather than creating a different package.
- Stable debug signing key is retained for in-place updates.
- Android minimum SDK 23; target SDK 28; Java 17; Gradle 8.9.
- Android unit tests include the SPP line-framing and message-parser contract. A passing unit test does not replace an Android APK build or physical HC-05 test.

## Sensor-channel boundary
- The agreed hardware target remains four FLC100 channels plus one inductive channel.
- This application branch currently accepts one integer per line and does not yet identify which FLC100 produced a value.
- The inductive channel remains reserved until its analog front-end range and conditioning are confirmed.
- Do not connect an unknown bipolar or over-voltage analog output directly to an ATmega128 ADC input.
- A channel identifier or a confirmed fixed transmission order is required before independent channel displays can be implemented.

## Verification boundary
A successful CI build confirms compilation, APK packaging, alignment and signing. It does not confirm real-world Bluetooth behaviour, hardware measurements, audio quality on every device, or sensor accuracy. Those require on-device testing with the actual scanner. Room commits every 25 samples; if Android is forcibly killed between commits, up to 24 newest samples may remain only in memory. A normal matrix end or detected Bluetooth disconnect flushes the pending batch and records completion/incompletion.