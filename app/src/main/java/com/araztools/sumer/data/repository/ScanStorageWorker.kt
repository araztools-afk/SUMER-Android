package com.araztools.sumer.data.repository

import android.content.Context
import com.araztools.sumer.data.local.SumerDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Serializes scan persistence commands so sample indexes cannot be reordered.
 * It flushes every 25 values (about 250 ms at the target 100 Hz sample rate).
 */
class ScanStorageWorker(
    context: Context,
    private val onError: (String) -> Unit
) {
    private sealed interface Command {
        data class Begin(
            val deviceName: String?,
            val deviceMac: String?,
            val mode: String,
            val rows: Int,
            val columns: Int,
            val startedAt: Long
        ) : Command

        data class Sample(val value: Int) : Command

        data class Finish(
            val completed: Boolean,
            val shutdown: Boolean = false
        ) : Command
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository = ScanRepository(
        SumerDatabase.getInstance(context).scanDao()
    )
    private val commands = Channel<Command>(Channel.UNLIMITED)

    init {
        scope.launch {
            try {
                repository.markOpenScansIncomplete()
            } catch (error: Exception) {
                report(error)
            }

            var activeScanId: Long? = null
            var nextSampleIndex = 0
            val pending = ArrayList<Int>(BATCH_SIZE)

            suspend fun flushPending() {
                val scanId = activeScanId ?: return
                if (pending.isEmpty()) return

                val batch = pending.toList()
                var lastError: Exception? = null

                for (attempt in 0 until MAX_BATCH_RETRIES) {
                    try {
                        repository.appendBatch(
                            scanId = scanId,
                            firstSampleIndex = nextSampleIndex,
                            values = batch
                        )
                        nextSampleIndex += batch.size
                        pending.clear()
                        return
                    } catch (error: Exception) {
                        lastError = error
                        if (attempt < MAX_BATCH_RETRIES - 1) {
                            delay(200L * (attempt + 1))
                        }
                    }
                }

                throw lastError ?: IllegalStateException(
                    "ذخیره دسته نمونه‌ها ناموفق بود."
                )
            }

            for (command in commands) {
                var stopAfterCommand = false
                try {
                    when (command) {
                        is Command.Begin -> {
                            val previousId = activeScanId
                            if (previousId != null) {
                                flushPending()
                                repository.finishScan(
                                    previousId,
                                    completed = false,
                                    finishedAt = null
                                )
                                activeScanId = null
                                nextSampleIndex = 0
                                pending.clear()
                            }

                            activeScanId = repository.startScan(
                                deviceName = command.deviceName,
                                deviceMac = command.deviceMac,
                                mode = command.mode,
                                rows = command.rows,
                                columns = command.columns,
                                startedAt = command.startedAt
                            )
                            nextSampleIndex = 0
                            pending.clear()
                        }

                        is Command.Sample -> {
                            if (activeScanId != null && command.value in 0..5000) {
                                pending.add(command.value)
                                if (pending.size >= BATCH_SIZE) flushPending()
                            }
                        }

                        is Command.Finish -> {
                            val currentId = activeScanId
                            if (currentId != null) {
                                flushPending()
                                repository.finishScan(
                                    currentId,
                                    completed = command.completed,
                                    finishedAt = if (command.completed) {
                                        System.currentTimeMillis()
                                    } else {
                                        null
                                    }
                                )
                                activeScanId = null
                                nextSampleIndex = 0
                                pending.clear()
                            }
                            stopAfterCommand = command.shutdown
                        }
                    }
                } catch (error: Exception) {
                    report(error)
                    // Retry shutdown if the final batch could not be committed.
                    if (command is Command.Finish && command.shutdown) {
                        delay(1000)
                        commands.trySend(command)
                    }
                }

                if (stopAfterCommand) break
            }

            commands.close()
            scope.cancel()
        }
    }

    fun beginScan(
        deviceName: String?,
        deviceMac: String?,
        mode: String,
        rows: Int,
        columns: Int,
        startedAt: Long
    ) {
        send(
            Command.Begin(
                deviceName, deviceMac, mode, rows, columns, startedAt
            )
        )
    }

    fun addSample(value: Int) {
        if (value in 0..5000) send(Command.Sample(value))
    }

    fun finishScan(completed: Boolean) {
        send(Command.Finish(completed))
    }

    fun shutdown() {
        send(Command.Finish(completed = false, shutdown = true))
    }

    private fun send(command: Command) {
        val result = commands.trySend(command)
        if (result.isFailure) {
            report(IllegalStateException("صف ذخیره‌سازی اسکن بسته شده است."))
        }
    }

    private fun report(error: Throwable) {
        runCatching {
            onError(error.message ?: error.javaClass.simpleName)
        }
    }

    private companion object {
        const val BATCH_SIZE = 25
        const val MAX_BATCH_RETRIES = 3
    }
}
