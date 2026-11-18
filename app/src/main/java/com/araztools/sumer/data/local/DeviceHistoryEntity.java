package com.araztools.sumer.data.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "device_history")
public class DeviceHistoryEntity {
    @PrimaryKey
    public String address;

    public String name;
    public String alias;

    public long lastAttemptAt;
    public long lastConnectedAt;

    public int baudRate;
    public String lastResult;

    public int connectionCount;
}
