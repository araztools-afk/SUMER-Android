package com.araztools.sumer.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface DeviceHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(DeviceHistoryEntity device);

    @Query("SELECT * FROM device_history WHERE address = :address LIMIT 1")
    DeviceHistoryEntity findByAddress(String address);

    @Query(
        "SELECT * FROM device_history " +
        "ORDER BY lastConnectedAt DESC, lastAttemptAt DESC LIMIT 50"
    )
    List<DeviceHistoryEntity> getRecentDevices();

    @Query("UPDATE device_history SET alias = :alias WHERE address = :address")
    void updateAlias(String address, String alias);
}
