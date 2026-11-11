package com.araztools.sumer.data.repository

import com.araztools.sumer.data.local.ScanDao
import com.araztools.sumer.data.local.ScanEntity
import com.araztools.sumer.data.local.ScanSampleEntity
import kotlinx.coroutines.flow.Flow

class ScanRepository(
    private val dao: ScanDao
) {
    suspend fun startScan(
        deviceName: String?,
        deviceMac: String?,
        mode: String,
        rows: Int,
        columns: Int,
        startedAt: Long
    ): Long {
        require(mode == "MANUAL" || mode == "AUTOMATIC")
        require(rows in 1..100 && columns in 1..100)
        return dao.insertScan(
            ScanEntity(
                deviceName = deviceName,
                deviceMac = deviceMac,
                mode = mode,
                rows = rows,
                columns = columns,
                startedAt = startedAt,
                completedAt = null,
                sampleCount = 0,
                minValue = null,
                maxValue = null,
                isComplete = false
            )
        )
    }

    suspend fun appendBatch(
        scanId: Long,
        firstSampleIndex: Int,
        values: List<Int>
    ) {
        dao.appendBatch(scanId, firstSampleIndex, values)
    }

    suspend fun finishScan(
        scanId: Long,
        completed: Boolean,
        finishedAt: Long?
    ) {
        dao.finishScan(
            scanId = scanId,
            completedAt = if (completed) finishedAt else null,
            isComplete = completed
        )
    }

    suspend fun markOpenScansIncomplete() {
        dao.markOpenScansIncomplete()
    }

    fun observeHistory(): Flow<List<ScanEntity>> = dao.observeAllScans()

    suspend fun getHistoryOnce(): List<ScanEntity> = dao.getAllScansOnce()

    suspend fun getSamples(scanId: Long): List<ScanSampleEntity> =
        dao.getSamples(scanId)

    suspend fun deleteScan(scanId: Long) {
        dao.deleteScan(scanId)
    }
}
