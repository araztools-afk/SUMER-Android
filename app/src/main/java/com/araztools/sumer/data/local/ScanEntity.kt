package com.araztools.sumer.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scans",
    indices = [Index(value = ["startedAt"])]
)
data class ScanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceName: String?,
    val deviceMac: String?,
    // AUTOMATIC is used by the current marker-driven prototype.
    // MANUAL will be set when the manual-start UI/command is added.
    val mode: String,
    val rows: Int,
    val columns: Int,
    val startedAt: Long,
    val completedAt: Long?,
    val sampleCount: Int,
    val minValue: Int?,
    val maxValue: Int?,
    val isComplete: Boolean
)
