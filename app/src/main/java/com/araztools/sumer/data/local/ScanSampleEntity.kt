package com.araztools.sumer.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "scan_samples",
    primaryKeys = ["scanId", "sampleIndex"],
    foreignKeys = [
        ForeignKey(
            entity = ScanEntity::class,
            parentColumns = ["id"],
            childColumns = ["scanId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["scanId"])]
)
data class ScanSampleEntity(
    val scanId: Long,
    val sampleIndex: Int,
    val value: Int
)
