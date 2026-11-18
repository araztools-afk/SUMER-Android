package com.araztools.sumer.data.local;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(
    tableName = "scan_samples",
    primaryKeys = {"scanId", "sampleIndex"},
    foreignKeys = {
        @ForeignKey(
            entity = ScanEntity.class,
            parentColumns = {"id"},
            childColumns = {"scanId"},
            onDelete = ForeignKey.CASCADE
        )
    },
    indices = {
        @Index(value = {"scanId"})
    }
)
public class ScanSampleEntity {
    public long scanId;
    public int sampleIndex;
    public int value;
}
