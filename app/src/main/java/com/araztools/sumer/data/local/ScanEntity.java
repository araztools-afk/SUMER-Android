package com.araztools.sumer.data.local;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "scans",
    indices = {
        @Index(value = {"startedAt"}),
        @Index(value = {"deviceAddress"})
    }
)
public class ScanEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    // Empty deviceAddress identifies a manual/offline test scan.
    public String deviceName;
    public String deviceAddress;
    public String mode;

    public int rows;
    public int columns;

    public long startedAt;
    // Zero means the scan did not complete.
    public long completedAt;

    public int sampleCount;
    // -1 means no numeric samples were stored.
    public int minValue;
    public int maxValue;

    public boolean isComplete;
}
