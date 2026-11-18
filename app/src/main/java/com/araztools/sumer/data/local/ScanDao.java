package com.araztools.sumer.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ScanDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insertScan(ScanEntity scan);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertSamples(List<ScanSampleEntity> samples);

    @Query(
        "UPDATE scans SET " +
        "sampleCount = sampleCount + :addedCount, " +
        "minValue = CASE WHEN minValue < 0 OR minValue > :batchMin THEN :batchMin ELSE minValue END, " +
        "maxValue = CASE WHEN maxValue < 0 OR maxValue < :batchMax THEN :batchMax ELSE maxValue END " +
        "WHERE id = :scanId"
    )
    void updateSummary(long scanId, int addedCount, int batchMin, int batchMax);

    @Query(
        "UPDATE scans SET completedAt = :completedAt, isComplete = :isComplete " +
        "WHERE id = :scanId"
    )
    void finishScan(long scanId, long completedAt, boolean isComplete);

    @Query("UPDATE scans SET isComplete = 0 WHERE completedAt = 0")
    void markOpenScansIncomplete();

    @Query("SELECT * FROM scans ORDER BY startedAt DESC LIMIT 100")
    List<ScanEntity> getRecentScans();

    @Query(
        "SELECT * FROM scan_samples " +
        "WHERE scanId = :scanId ORDER BY sampleIndex ASC"
    )
    List<ScanSampleEntity> getSamples(long scanId);

    @Query("DELETE FROM scans WHERE id = :scanId")
    void deleteScan(long scanId);
}
