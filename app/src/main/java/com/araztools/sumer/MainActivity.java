package com.araztools.sumer;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int REQUEST_BT = 701;
    private static final int MAX_CELLS = 2500;

    private final Object dataLock = new Object();
    private final List<Integer> matrixValues = new ArrayList<>();
    private volatile boolean scanning = false;
    private volatile boolean connected = false;
    private volatile int rows = 10;
    private volatile int columns = 10;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private Thread readerThread;
    private List<BluetoothDevice> bondedDevices = Collections.emptyList();

    private TextView statusView;
    private TextView latestValueView;
    private TextView progressView;
    private TextView logView;
    private TextView matrixTitleView;
    private Spinner deviceSpinner;
    private EditText rowsInput;
    private EditText columnsInput;
    private EditText manualValueInput;
    private GridLayout matrixGrid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = manager == null ? null : manager.getAdapter();

        if (Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BT);
        } else {
            loadBondedDevices();
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private TextView label(String value, float size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(28, 43, 56));
        view.setPadding(dp(8), dp(8), dp(8), dp(8));
        return view;
    }

    private Button button(String value, Runnable action) {
        Button button = new Button(this);
        button.setText(value);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(24));
        root.setBackgroundColor(Color.rgb(244, 247, 250));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = label("SUMER", 30);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Color.rgb(20, 55, 85));
        root.addView(title, fullWidth());

        TextView subtitle = label("اسکن ماتریسی آزمایشی | ورودی تک‌کانال", 15);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, fullWidth());

        statusView = label("وضعیت: آماده", 15);
        statusView.setBackgroundColor(Color.WHITE);
        root.addView(statusView, fullWidth());

        root.addView(label("اتصال Bluetooth Classic / HC-05", 17), fullWidth());
        deviceSpinner = new Spinner(this);
        root.addView(deviceSpinner, fullWidth());
        root.addView(button("به‌روزرسانی دستگاه‌های جفت‌شده", this::loadBondedDevices), fullWidth());
        root.addView(button("اتصال به دستگاه انتخاب‌شده", this::connectSelectedDevice), fullWidth());
        root.addView(button("قطع اتصال", this::disconnect), fullWidth());

        root.addView(label("آخرین مقدار دریافتی", 16), fullWidth());
        latestValueView = label("—", 30);
        latestValueView.setGravity(Gravity.CENTER);
        latestValueView.setBackgroundColor(Color.WHITE);
        root.addView(latestValueView, fullWidth());
        progressView = label("نقاط ثبت‌شده: 0 / 100", 14);
        root.addView(progressView, fullWidth());

        root.addView(label("ابعاد ماتریس", 17), fullWidth());
        LinearLayout dimensions = new LinearLayout(this);
        dimensions.setOrientation(LinearLayout.HORIZONTAL);
        rowsInput = new EditText(this);
        rowsInput.setSingleLine(true);
        rowsInput.setInputType(2);
        rowsInput.setHint("تعداد ردیف");
        rowsInput.setText(String.valueOf(rows));
        columnsInput = new EditText(this);
        columnsInput.setSingleLine(true);
        columnsInput.setInputType(2);
        columnsInput.setHint("تعداد ستون");
        columnsInput.setText(String.valueOf(columns));
        dimensions.addView(rowsInput, new LinearLayout.LayoutParams(0, dp(54), 1));
        dimensions.addView(columnsInput, new LinearLayout.LayoutParams(0, dp(54), 1));
        root.addView(dimensions, fullWidth());
        root.addView(button("اعمال ابعاد شبکه", this::applyDimensions), fullWidth());

        root.addView(label("کنترل آزمایشی دستی", 17), fullWidth());
        TextView manualHint = label("برای آزمایش بدون سخت‌افزار، مقدار ۰ تا ۵۰۰۰ وارد کنید. این ورودی از همان پردازشگر داده بلوتوث عبور می‌کند.", 13);
        root.addView(manualHint, fullWidth());
        manualValueInput = new EditText(this);
        manualValueInput.setSingleLine(true);
        manualValueInput.setInputType(2);
        manualValueInput.setHint("مثلاً 1250");
        root.addView(manualValueInput, fullWidth());
        root.addView(button("ثبت مقدار آزمایشی", this::submitManualValue), fullWidth());
        LinearLayout scanButtons = new LinearLayout(this);
        scanButtons.setOrientation(LinearLayout.HORIZONTAL);
        scanButtons.addView(button("شروع اسکن آزمایشی", () -> processLine("a")),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        scanButtons.addView(button("پایان اسکن آزمایشی", () -> processLine("a")),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(scanButtons, fullWidth());

        matrixTitleView = label("نقشه ماتریسی", 18);
        root.addView(matrixTitleView, fullWidth());
        matrixGrid = new GridLayout(this);
        matrixGrid.setUseDefaultMargins(false);
        matrixGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        matrixGrid.setBackgroundColor(Color.rgb(220, 226, 232));
        root.addView(matrixGrid, fullWidth());
        renderMatrix(Collections.emptyList());

        root.addView(label("گزارش داده‌های ورودی", 16), fullWidth());
        logView = label("داده‌های دریافتی در این قسمت ثبت می‌شوند.", 12);
        logView.setBackgroundColor(Color.WHITE);
        root.addView(logView, fullWidth());
    }

    private boolean hasBluetoothPermission() {
        return Build.VERSION.SDK_INT < 31 ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("MissingPermission")
    private void loadBondedDevices() {
        if (bluetoothAdapter == null) {
            setStatus("این گوشی آداپتور بلوتوث ندارد.");
            return;
        }
        if (!hasBluetoothPermission()) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BT);
            return;
        }
        try {
            if (!bluetoothAdapter.isEnabled()) {
                setStatus("بلوتوث گوشی را روشن کنید.");
                return;
            }
            bondedDevices = new ArrayList<>(bluetoothAdapter.getBondedDevices());
            Collections.sort(bondedDevices, (a, b) ->
                    String.valueOf(a.getName()).compareToIgnoreCase(String.valueOf(b.getName())));
            List<String> names = new ArrayList<>();
            for (BluetoothDevice device : bondedDevices) {
                names.add((device.getName() == null ? "Bluetooth device" : device.getName())
                        + " — " + device.getAddress());
            }
            if (names.isEmpty()) names.add("ابتدا HC-05 را در تنظیمات گوشی Pair کنید");
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, names);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            deviceSpinner.setAdapter(adapter);
            setStatus("دستگاه‌های جفت‌شده: " + bondedDevices.size());
        } catch (SecurityException e) {
            setStatus("مجوز دسترسی به دستگاه‌های بلوتوث داده نشده است.");
        }
    }

    @SuppressWarnings("MissingPermission")
    private void connectSelectedDevice() {
        if (!hasBluetoothPermission()) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BT);
            return;
        }
        if (bondedDevices.isEmpty() || deviceSpinner.getSelectedItemPosition() < 0 ||
                deviceSpinner.getSelectedItemPosition() >= bondedDevices.size()) {
            setStatus("ابتدا HC-05 را از تنظیمات بلوتوث گوشی Pair کنید.");
            return;
        }
        final BluetoothDevice device = bondedDevices.get(deviceSpinner.getSelectedItemPosition());
        setStatus("در حال اتصال به " + (device.getName() == null ? device.getAddress() : device.getName()));
        Thread connector = new Thread(() -> {
            BluetoothSocket newSocket = null;
            try {
                newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                newSocket.connect();
                bluetoothSocket = newSocket;
                connected = true;
                runOnUiThread(() -> setStatus("متصل شد: " +
                        (device.getName() == null ? device.getAddress() : device.getName())));
                listen(newSocket);
            } catch (Exception e) {
                connected = false;
                if (newSocket != null) try { newSocket.close(); } catch (Exception ignored) {}
                runOnUiThread(() -> setStatus("اتصال ناموفق: " +
                        (e.getMessage() == null ? "خطای بلوتوث" : e.getMessage())));
            }
        });
        connector.start();
    }

    private void listen(BluetoothSocket activeSocket) {
        readerThread = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(activeSocket.getInputStream(), "UTF-8"));
                String line;
                while (connected && activeSocket.isConnected() && (line = reader.readLine()) != null) {
                    processLine(line.trim());
                }
                if (connected) runOnUiThread(() -> setStatus("جریان داده پایان یافت."));
            } catch (Exception e) {
                if (connected) runOnUiThread(() -> setStatus("دریافت متوقف شد: " +
                        (e.getMessage() == null ? "قطع ارتباط" : e.getMessage())));
            }
        });
        readerThread.start();
    }

    private void submitManualValue() {
        String value = manualValueInput.getText().toString().trim();
        if (value.isEmpty()) {
            Toast.makeText(this, "یک عدد بین ۰ تا ۵۰۰۰ وارد کنید.", Toast.LENGTH_SHORT).show();
            return;
        }
        processLine(value);
        manualValueInput.setText("");
    }

    private void processLine(String line) {
        if (line == null) return;
        String valueText = line.trim();
        if (valueText.isEmpty()) return;

        if ("a".equalsIgnoreCase(valueText)) {
            final boolean started;
            final List<Integer> snapshot;
            synchronized (dataLock) {
                scanning = !scanning;
                started = scanning;
                if (started) matrixValues.clear();
                snapshot = new ArrayList<>(matrixValues);
            }
            runOnUiThread(() -> {
                setStatus(started ? "اسکن ماتریسی آغاز شد؛ منتظر داده‌ها..." :
                        "اسکن پایان یافت؛ تعداد نقاط: " + snapshot.size());
                renderMatrix(snapshot);
                updateProgress(snapshot.size());
            });
            appendLog(started ? "a → شروع اسکن" : "a → پایان اسکن");
            return;
        }

        final int value;
        try {
            value = Integer.parseInt(valueText);
        } catch (NumberFormatException e) {
            appendLog("داده نامعتبر: " + valueText);
            return;
        }
        if (value < 0 || value > 5000) {
            appendLog("خارج از محدوده ۰ تا ۵۰۰۰: " + value);
            return;
        }

        final List<Integer> snapshot;
        final boolean wasScanning;
        synchronized (dataLock) {
            wasScanning = scanning;
            if (scanning && matrixValues.size() < rows * columns && matrixValues.size() < MAX_CELLS) {
                matrixValues.add(value);
            }
            snapshot = new ArrayList<>(matrixValues);
        }
        runOnUiThread(() -> {
            latestValueView.setText(String.valueOf(value));
            if (wasScanning) {
                renderMatrix(snapshot);
                updateProgress(snapshot.size());
            }
        });
        if (wasScanning && snapshot.size() >= rows * columns) {
            appendLog("ظرفیت ماتریس تکمیل شد؛ برای پایان اسکن، نشانگر a ارسال شود.");
        }
        appendLog(String.valueOf(value));
    }

    private void applyDimensions() {
        int r, c;
        try {
            r = Integer.parseInt(rowsInput.getText().toString().trim());
            c = Integer.parseInt(columnsInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "تعداد ردیف و ستون را وارد کنید.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (r < 1 || c < 1 || r > 100 || c > 100 || r * c > MAX_CELLS) {
            Toast.makeText(this, "ابعاد معتبر نیست؛ حداکثر ۲۵۰۰ خانه.", Toast.LENGTH_LONG).show();
            return;
        }
        synchronized (dataLock) {
            if (scanning) {
                Toast.makeText(this, "هنگام اسکن، ابعاد را تغییر ندهید.", Toast.LENGTH_SHORT).show();
                return;
            }
            rows = r;
            columns = c;
            matrixValues.clear();
        }
        matrixTitleView.setText("نقشه ماتریسی — " + rows + " × " + columns);
        renderMatrix(Collections.emptyList());
        updateProgress(0);
    }

    private void updateProgress(int count) {
        progressView.setText("نقاط ثبت‌شده: " + count + " / " + (rows * columns));
    }

    private void renderMatrix(List<Integer> values) {
        matrixGrid.removeAllViews();
        matrixGrid.setColumnCount(columns);
        matrixGrid.setRowCount(rows);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        for (int index = 0; index < rows * columns; index++) {
            TextView cell = new TextView(this);
            cell.setGravity(Gravity.CENTER);
            cell.setTextSize(11);
            cell.setTextColor(Color.WHITE);
            cell.setPadding(dp(2), dp(3), dp(2), dp(3));
            cell.setBackgroundColor(Color.rgb(83, 96, 108));
            if (index < values.size()) {
                int value = values.get(index);
                float t = max == min ? 0.5f : (value - min) / (float) (max - min);
                float[] hsv = new float[]{240f * (1f - t), 0.88f, 0.92f};
                cell.setBackgroundColor(Color.HSVToColor(hsv));
                cell.setText(String.valueOf(value));
            } else {
                cell.setText("·");
            }
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(index / columns, 1),
                    GridLayout.spec(index % columns, 1, 1f));
            params.width = 0;
            params.height = dp(42);
            params.setMargins(dp(1), dp(1), dp(1), dp(1));
            matrixGrid.addView(cell, params);
        }
    }

    private void appendLog(String message) {
        runOnUiThread(() -> {
            String old = logView.getText().toString();
            String[] lines = old.split("\n");
            StringBuilder builder = new StringBuilder();
            int start = Math.max(0, lines.length - 10);
            for (int i = start; i < lines.length; i++) {
                if (!lines[i].isEmpty()) builder.append(lines[i]).append('\n');
            }
            builder.append(message);
            logView.setText(builder.toString());
        });
    }

    private void setStatus(String value) {
        statusView.setText("وضعیت: " + value);
    }

    private void disconnect() {
        connected = false;
        if (readerThread != null) readerThread.interrupt();
        if (bluetoothSocket != null) {
            try { bluetoothSocket.close(); } catch (Exception ignored) {}
            bluetoothSocket = null;
        }
        synchronized (dataLock) { scanning = false; }
        if (statusView != null) setStatus("اتصال قطع شد.");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BT) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadBondedDevices();
            } else {
                setStatus("برای اتصال HC-05 مجوز Bluetooth Connect لازم است.");
            }
        }
    }

    @Override
    protected void onDestroy() {
        disconnect();
        super.onDestroy();
    }
}
