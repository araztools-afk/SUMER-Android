package com.araztools.sumer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.araztools.sumer.data.local.ScanEntity
import com.araztools.sumer.data.local.SumerDatabase
import com.araztools.sumer.data.repository.ScanRepository
import com.araztools.sumer.data.repository.ScanStorageWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private var readerThread: Thread? = null
    @Volatile
    private var scanning = false
    private val matrixValues = mutableListOf<Int>()
    private val matrixLock = Any()
    private val recentValues = ArrayDeque<Int>()
    private var rows = 10
    private var columns = 20

    // The current prototype is marker-driven by the hardware.
    // Manual start controls will be added in a later UI step.
    private val matrixMode = "AUTOMATIC"

    private lateinit var scanRepository: ScanRepository
    private lateinit var storageWorker: ScanStorageWorker
    private lateinit var historyScope: CoroutineScope
    @Volatile
    private var connectedDevice: BluetoothDevice? = null

    private lateinit var status: TextView
    private lateinit var deviceSpinner: Spinner
    private lateinit var rawValue: TextView
    private lateinit var matrixView: TextView
    private lateinit var logView: TextView
    private lateinit var rowsInput: EditText
    private lateinit var colsInput: EditText
    private var bondedDevices: List<BluetoothDevice> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()

        scanRepository = ScanRepository(
            SumerDatabase.getInstance(applicationContext).scanDao()
        )
        historyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        storageWorker = ScanStorageWorker(applicationContext) { message ->
            runOnUiThread {
                if (::logView.isInitialized) {
                    appendLog("خطای ذخیره‌سازی: $message")
                }
            }
        }

        if (hasBluetoothPermission()) loadBondedDevices() else requestBluetoothPermission()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 24)
            setBackgroundColor(0xFFF4F6F8.toInt())
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "SUMER"
            textSize = 30f
            setTextColor(0xFF16324F.toInt())
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "اسکنر چندحسگره | Bluetooth Classic / HC-05"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 18)
        })

        status = sectionText("وضعیت: آماده")
        root.addView(status)

        deviceSpinner = Spinner(this)
        root.addView(deviceSpinner, matchWidth())
        val refresh = Button(this).apply {
            text = "به‌روزرسانی دستگاه‌های جفت‌شده"
            setOnClickListener { if (hasBluetoothPermission()) loadBondedDevices() else requestBluetoothPermission() }
        }
        root.addView(refresh, matchWidth())

        val connect = Button(this).apply {
            text = "اتصال به دستگاه انتخاب‌شده"
            setOnClickListener { connectSelectedDevice() }
        }
        root.addView(connect, matchWidth())
        root.addView(Button(this).apply {
            text = "قطع اتصال"
            setOnClickListener { disconnect() }
        }, matchWidth())

        root.addView(sectionText("آخرین داده دریافتی"))
        rawValue = sectionText("—")
        rawValue.textSize = 26f
        root.addView(rawValue)

        root.addView(sectionText("تنظیم شبکه اسکن"))
        val dimensions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rowsInput = EditText(this).apply {
            setText(rows.toString()); hint = "ردیف"; inputType = 2
        }
        colsInput = EditText(this).apply {
            setText(columns.toString()); hint = "ستون"; inputType = 2
        }
        dimensions.addView(rowsInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        dimensions.addView(colsInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(dimensions)
        root.addView(Button(this).apply {
            text = "اعمال ابعاد شبکه"
            setOnClickListener { applyDimensions() }
        }, matchWidth())

        root.addView(sectionText("نمایش ماتریس"))
        matrixView = sectionText("هنوز اسکن ماتریسی دریافت نشده است.")
        matrixView.textSize = 13f
        root.addView(matrixView)
        root.addView(Button(this).apply {
            text = "تاریخچه اسکن‌های ذخیره‌شده"
            setOnClickListener { showScanHistory() }
        }, matchWidth())

        root.addView(sectionText("گزارش دریافت"))
        logView = sectionText("داده‌های ورودی در این قسمت نمایش داده می‌شوند.")
        logView.textSize = 12f
        root.addView(logView)
    }

    private fun sectionText(value: String) = TextView(this).apply {
        text = value
        textSize = 16f
        setTextColor(0xFF172B3A.toInt())
        setPadding(12, 12, 12, 12)
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 41)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadBondedDevices()
        } else {
            status.text = "مجوز بلوتوث داده نشد."
        }
    }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        return manager.adapter
    }

    private fun loadBondedDevices() {
        try {
            val adapter = bluetoothAdapter()
            if (adapter == null) {
                status.text = "این گوشی از بلوتوث پشتیبانی نمی‌کند."
                return
            }
            if (!adapter.isEnabled) {
                status.text = "ابتدا بلوتوث گوشی را روشن کنید."
                return
            }
            bondedDevices = adapter.bondedDevices.toList().sortedBy { it.name ?: it.address }
            val labels = bondedDevices.map { "${it.name ?: "Bluetooth device"}  —  ${it.address}" }
            deviceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
            status.text = "دستگاه‌های جفت‌شده: ${bondedDevices.size}"
        } catch (e: SecurityException) {
            status.text = "مجوز دسترسی به دستگاه‌های بلوتوث لازم است."
        }
    }

    private fun connectSelectedDevice() {
        if (!hasBluetoothPermission()) {
            requestBluetoothPermission()
            return
        }
        if (bondedDevices.isEmpty() || deviceSpinner.selectedItemPosition !in bondedDevices.indices) {
            status.text = "ابتدا HC-05 را از تنظیمات گوشی Pair کنید."
            return
        }
        val device = bondedDevices[deviceSpinner.selectedItemPosition]
        connectedDevice = device
        status.text = "در حال اتصال به ${device.name ?: device.address} ..."
        thread {
            try {
                // Discovery is not started by this screen; avoiding cancelDiscovery
                // also avoids requesting BLUETOOTH_SCAN on Android 12+.
                val newSocket = device.createRfcommSocketToServiceRecord(sppUuid)
                newSocket.connect()
                socket = newSocket
                runOnUiThread { status.text = "متصل شد: ${device.name ?: device.address}" }
                listen(newSocket)
            } catch (e: Exception) {
                runOnUiThread { status.text = "اتصال ناموفق: ${e.message ?: "خطای بلوتوث"}" }
                try { socket?.close() } catch (_: Exception) {}
                socket = null
            }
        }
    }

    private fun listen(activeSocket: BluetoothSocket) {
        readerThread = thread {
            try {
                val reader = BufferedReader(InputStreamReader(activeSocket.inputStream, Charsets.UTF_8))
                while (!Thread.currentThread().isInterrupted && activeSocket.isConnected) {
                    val line = reader.readLine() ?: break
                    processLine(line.trim())
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "دریافت متوقف شد: ${e.message ?: "قطع ارتباط"}" }
            }
        }
    }

    private fun processLine(line: String) {
        if (line.isEmpty()) return

        // Only a complete standalone lowercase "a" line toggles matrix scanning.
        if (line == "a") {
            if (!scanning) {
                scanning = true
                synchronized(matrixLock) {
                    matrixValues.clear()
                }

                val device = connectedDevice
                storageWorker.beginScan(
                    deviceName = try { device?.name } catch (_: SecurityException) { null },
                    deviceMac = try { device?.address } catch (_: SecurityException) { null },
                    mode = matrixMode,
                    rows = rows,
                    columns = columns,
                    startedAt = System.currentTimeMillis()
                )
                runOnUiThread { status.text = "اسکن ماتریسی آغاز شد." }
                appendLog("a  → شروع اسکن")
            } else {
                scanning = false
                storageWorker.finishScan(completed = true)
                renderMatrix()
                val count = synchronized(matrixLock) { matrixValues.size }
                runOnUiThread {
                    status.text = "اسکن پایان یافت؛ تعداد نقاط: $count"
                }
                appendLog("a  → پایان اسکن")
            }
            return
        }

        val value = line.toIntOrNull()
        if (value == null || value !in 0..5000) {
            appendLog("داده نامعتبر یا ناشناخته: $line")
            return
        }

        synchronized(recentValues) {
            recentValues.addLast(value)
            while (recentValues.size > 5) recentValues.removeFirst()
        }
        runOnUiThread { rawValue.text = value.toString() }

        if (scanning) {
            val accepted = synchronized(matrixLock) {
                if (matrixValues.size >= MAX_SAMPLES_PER_SCAN) {
                    false
                } else {
                    matrixValues.add(value)
                    true
                }
            }
            if (accepted) {
                storageWorker.addSample(value)
            } else {
                appendLog("حداکثر تعداد نمونه این اسکن پر شد؛ نمونه جدید رد شد.")
            }
        }

        appendLog(value.toString())
    }

    private fun renderMatrix() {
        val snapshot = synchronized(matrixLock) { matrixValues.toList() }
        val visibleValues = snapshot.take(MAX_VISIBLE_MATRIX_CELLS)
        val displayRows = maxOf(rows, (visibleValues.size + columns - 1) / columns)

        val output = buildString {
            append("تعداد کل نمونه‌های ذخیره‌شده: ${snapshot.size}\n")
            for (r in 0 until displayRows) {
                val line = (0 until columns).joinToString("  ") { col ->
                    visibleValues.getOrNull(r * columns + col)?.toString() ?: "·"
                }
                append(line)
                append('\n')
            }
            if (snapshot.size > visibleValues.size) {
                append("نمایش به ${visibleValues.size} خانه محدود شده است؛ همه نمونه‌ها در تاریخچه ذخیره می‌شوند.")
            }
        }
        runOnUiThread { matrixView.text = output }
    }

    private fun applyDimensions() {
        if (scanning) {
            Toast.makeText(this, "ابعاد شبکه را پس از پایان اسکن تغییر دهید.", Toast.LENGTH_LONG).show()
            return
        }

        val r = rowsInput.text.toString().toIntOrNull()
        val col = colsInput.text.toString().toIntOrNull()
        if (r == null || col == null || r !in 1..100 || col !in 1..100 || r * col > 2500) {
            Toast.makeText(this, "ابعاد معتبر وارد کنید؛ حداکثر ۲۵۰۰ خانه نمایشی.", Toast.LENGTH_LONG).show()
            return
        }
        rows = r
        columns = col
        matrixView.text = "شبکه جدید: ${rows} × ${columns}"
    }

    private fun showScanHistory() {
        historyScope.launch {
            try {
                val scans = scanRepository.getHistoryOnce()
                runOnUiThread {
                    if (scans.isEmpty()) {
                        Toast.makeText(this@MainActivity, "هنوز اسکن ذخیره‌شده‌ای وجود ندارد.", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }

                    val formatter = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
                    val labels = scans.map { scan ->
                        val date = formatter.format(Date(scan.startedAt))
                        val state = if (scan.isComplete) "کامل" else "ناقص"
                        "$date | ${scan.mode} | ${scan.sampleCount} نمونه | $state"
                    }

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("تاریخچه اسکن‌ها")
                        .setItems(labels.toTypedArray()) { _, index ->
                            showScanDetails(scans[index])
                        }
                        .setNegativeButton("بستن", null)
                        .show()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "خواندن تاریخچه ناموفق بود: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showScanDetails(scan: ScanEntity) {
        historyScope.launch {
            try {
                val samples = scanRepository.getSamples(scan.id)
                val preview = samples.take(MAX_HISTORY_PREVIEW)
                    .joinToString(", ") { it.value.toString() }

                val details = buildString {
                    append("شناسه: ${scan.id}\n")
                    append("حالت: ${scan.mode}\n")
                    append("دستگاه: ${scan.deviceName ?: "نامشخص"}\n")
                    append("MAC: ${scan.deviceMac ?: "نامشخص"}\n")
                    append("ابعاد هنگام شروع: ${scan.rows} × ${scan.columns}\n")
                    append("تعداد نمونه: ${scan.sampleCount}\n")
                    append("کمینه: ${scan.minValue ?: "—"}\n")
                    append("بیشینه: ${scan.maxValue ?: "—"}\n")
                    append("وضعیت: ${if (scan.isComplete) "کامل" else "ناقص"}\n")
                    append("نمونه‌های نخست: ")
                    append(if (preview.isEmpty()) "—" else preview)
                    if (samples.size > MAX_HISTORY_PREVIEW) {
                        append("\n... فقط ${MAX_HISTORY_PREVIEW} نمونه نخست نمایش داده شد.")
                    }
                }

                runOnUiThread {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("جزئیات اسکن #${scan.id}")
                        .setMessage(details)
                        .setPositiveButton("بستن", null)
                        .show()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "خواندن جزئیات اسکن ناموفق بود: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun appendLog(message: String) {
        runOnUiThread {
            val previous = logView.text.toString().lines().takeLast(12)
            logView.text = (previous + message).takeLast(12).joinToString("\n")
        }
    }

    private fun disconnect() {
        if (scanning) {
            scanning = false
            storageWorker.finishScan(completed = false)
        }
        try { readerThread?.interrupt() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        connectedDevice = null
        status.text = "اتصال قطع شد؛ اسکن فعال به‌صورت ناقص ذخیره می‌شود."
    }

    override fun onDestroy() {
        disconnect()
        if (::storageWorker.isInitialized) storageWorker.shutdown()
        if (::historyScope.isInitialized) historyScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val REQUEST_BLUETOOTH_PERMISSION = 41
        const val MAX_SAMPLES_PER_SCAN = 1_000_000
        const val MAX_VISIBLE_MATRIX_CELLS = 2500
        const val MAX_HISTORY_PREVIEW = 100
    }
}
