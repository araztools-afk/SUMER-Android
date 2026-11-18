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

    public String deviceName;
    public String deviceAddress;
    public String mode;

    public int rows;
    public int columns;

    public long startedAt;
    public Long completedAt;

    public int sampleCount;
    public Integer minValue;
    public Integer maxValue;

    public boolean isComplete;
}
