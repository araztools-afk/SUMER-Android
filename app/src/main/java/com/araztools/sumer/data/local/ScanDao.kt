package com.araztools.sumer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertScan(scan: ScanEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSamples(samples: List<ScanSampleEntity>)

    @Query(
        """
        UPDATE scans
        SET sampleCount = sampleCount + :addedCount,
            minValue = CASE
                WHEN minValue IS NULL OR minValue > :batchMin THEN :batchMin
                ELSE minValue
            END,
            maxValue = CASE
                WHEN maxValue IS NULL OR maxValue < :batchMax THEN :batchMax
                ELSE maxValue
            END
        WHERE id = :scanId
        """
    )
    suspend fun updateSummary(
        scanId: Long,
        addedCount: Int,
        batchMin: Int,
        batchMax: Int
    )

    /**
     * Sample insertion and summary update are one transaction.
     * A failed batch therefore cannot leave the count/min/max out of sync.
     */
    @Transaction
    suspend fun appendBatch(
        scanId: Long,
        firstSampleIndex: Int,
        values: List<Int>
    ) {
        require(values.all { it in 0..5000 }) {
            "Every SUMER sample must be in the range 0..5000."
        }
        if (values.isEmpty()) return

        insertSamples(
            values.mapIndexed { offset, value ->
                ScanSampleEntity(
                    scanId = scanId,
                    sampleIndex = firstSampleIndex + offset,
                    value = value
                )
            }
        )

        updateSummary(
            scanId = scanId,
            addedCount = values.size,
            batchMin = values.minOrNull()!!,
            batchMax = values.maxOrNull()!!
        )
    }

    @Query(
        """
        UPDATE scans
        SET completedAt = :completedAt,
            isComplete = :isComplete
        WHERE id = :scanId
        """
    )
    suspend fun finishScan(
        scanId: Long,
        completedAt: Long?,
        isComplete: Boolean
    )

    // A row with no completedAt was still open when the app last stopped.
    @Query(
        """
        UPDATE scans
        SET isComplete = 0
        WHERE completedAt IS NULL
        """
    )
    suspend fun markOpenScansIncomplete()

    @Query("SELECT * FROM scans ORDER BY startedAt DESC")
    fun observeAllScans(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans ORDER BY startedAt DESC")
    suspend fun getAllScansOnce(): List<ScanEntity>

    @Query("SELECT * FROM scan_samples WHERE scanId = :scanId ORDER BY sampleIndex ASC")
    suspend fun getSamples(scanId: Long): List<ScanSampleEntity>

    @Query("DELETE FROM scans WHERE id = :scanId")
    suspend fun deleteScan(scanId: Long)
}
