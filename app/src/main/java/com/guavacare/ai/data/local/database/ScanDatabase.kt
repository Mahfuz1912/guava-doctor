package com.guavacare.ai.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.guavacare.ai.data.local.dao.ScanDao
import com.guavacare.ai.data.local.entities.ScanEntity

@Database(
    entities = [ScanEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ScanDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao

    companion object {
        private const val DATABASE_NAME = "guavacare_scans.db"

        @Volatile
        private var instance: ScanDatabase? = null

        fun getInstance(context: Context): ScanDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScanDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }
        }
    }
}
