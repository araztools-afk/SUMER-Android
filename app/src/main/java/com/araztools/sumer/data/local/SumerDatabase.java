package com.araztools.sumer.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
    entities = {
        ScanEntity.class,
        ScanSampleEntity.class,
        DeviceHistoryEntity.class
    },
    version = 1,
    exportSchema = false
)
public abstract class SumerDatabase extends RoomDatabase {
    private static volatile SumerDatabase INSTANCE;

    public abstract ScanDao scanDao();
    public abstract DeviceHistoryDao deviceHistoryDao();

    public static SumerDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SumerDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        SumerDatabase.class,
                        "sumer_database"
                    ).build();
                }
            }
        }
        return INSTANCE;
    }
}
