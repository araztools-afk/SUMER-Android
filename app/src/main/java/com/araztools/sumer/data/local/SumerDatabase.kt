package com.araztools.sumer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ScanEntity::class,
        ScanSampleEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SumerDatabase : RoomDatabase() {

    abstract fun scanDao(): ScanDao

    companion object {
        @Volatile
        private var INSTANCE: SumerDatabase? = null

        fun getInstance(context: Context): SumerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SumerDatabase::class.java,
                    "sumer_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
