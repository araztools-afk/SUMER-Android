package com.araztools.sumer

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.os.Bundle
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
    private var scanning = false
    private val matrixValues = mutableListOf<Int>()
    private val recentValues = ArrayDeque<Int>()
    private var rows = 10
    private var columns = 10

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
        loadBondedDevices()
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
        root.addView(Button(this).apply {
            text = "به‌روزرسانی دستگاه‌های جفت‌شده"
            setOnClickListener { loadBondedDevices() }
        }, matchWidth())
        root.addView(Button(this).apply {
            text = "اتصال به دستگاه انتخاب‌شده"
            setOnClickListener { connectSelectedDevice() }
        }, matchWidth())
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
        rowsInput = EditText(this).apply { setText(rows.toString()); hint = "ردیف"; inputType = 2 }
        colsInput = EditText(this).apply { setText(columns.toString()); hint = "ستون"; inputType = 2 }
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

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        return manager.adapter
    }

    @Suppress("DEPRECATION")
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
            status.text = "دسترسی بلوتوث توسط سیستم محدود شده است."
        }
    }

    @Suppress("DEPRECATION")
    private fun connectSelectedDevice() {
        if (bondedDevices.isEmpty() || deviceSpinner.selectedItemPosition !in bondedDevices.indices) {
            status.text = "ابتدا HC-05 را از تنظیمات گوشی Pair کنید."
            return
        }
        val device = bondedDevices[deviceSpinner.selectedItemPosition]
        status.text = "در حال اتصال به ${device.name ?: device.address} ..."
        thread {
            try {
                bluetoothAdapter()?.cancelDiscovery()
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
        if (line.equals("a", ignoreCase = true)) {
            scanning = !scanning
            if (scanning) {
                matrixValues.clear()
                runOnUiThread { status.text = "اسکن ماتریسی آغاز شد." }
            } else {
                renderMatrix()
                runOnUiThread { status.text = "اسکن پایان یافت؛ تعداد نقاط: ${matrixValues.size}" }
            }
            appendLog(if (scanning) "a  → شروع اسکن" else "a  → پایان اسکن")
            return
        }
        val value = line.toIntOrNull()
        if (value == null || value !in 0..5000) {
            appendLog("داده نامعتبر: $line")
            return
        }
        synchronized(recentValues) {
            recentValues.addLast(value)
            while (recentValues.size > 5) recentValues.removeFirst()
        }
        runOnUiThread { rawValue.text = value.toString() }
        if (scanning) {
            synchronized(matrixValues) {
                if (matrixValues.size < rows * columns) matrixValues.add(value)
            }
        }
        appendLog(value.toString())
    }

    private fun renderMatrix() {
        val snapshot = synchronized(matrixValues) { matrixValues.toList() }
        val output = buildString {
            append("نقاط ثبت‌شده: ${snapshot.size}\n")
            for (r in 0 until rows) {
                val line = (0 until columns).joinToString("  ") { c ->
                    snapshot.getOrNull(r * columns + c)?.toString() ?: "·"
                }
                append(line)
                append('\n')
            }
        }
        runOnUiThread { matrixView.text = output }
    }

    private fun applyDimensions() {
        val r = rowsInput.text.toString().toIntOrNull()
        val c = colsInput.text.toString().toIntOrNull()
        if (r == null || c == null || r !in 1..100 || c !in 1..100 || r * c > 2500) {
            Toast.makeText(this, "ابعاد معتبر وارد کنید؛ حداکثر ۲۵۰۰ نقطه.", Toast.LENGTH_LONG).show()
            return
        }
        rows = r
        columns = c
        matrixValues.clear()
        matrixView.text = "شبکه جدید: ${rows} × ${columns}"
    }

    private fun appendLog(message: String) {
        runOnUiThread {
            val previous = logView.text.toString().lines().takeLast(12)
            logView.text = (previous + message).takeLast(12).joinToString("\n")
        }
    }

    private fun disconnect() {
        try { readerThread?.interrupt() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        scanning = false
        status.text = "اتصال قطع شد."
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }
}
