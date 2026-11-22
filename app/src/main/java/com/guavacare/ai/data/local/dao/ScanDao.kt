package com.guavacare.ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.guavacare.ai.data.local.entities.ScanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Query("SELECT * FROM leaf_scans ORDER BY timestamp DESC")
    fun observeAllScans(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM leaf_scans WHERE id = :id LIMIT 1")
    suspend fun getScanById(id: Long): ScanEntity?

    @Query("SELECT imagePath FROM leaf_scans")
    suspend fun getAllImagePaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanEntity): Long

    @Update
    suspend fun updateScan(scan: ScanEntity)

    @Query("DELETE FROM leaf_scans WHERE id = :id")
    suspend fun deleteScanById(id: Long)

    @Query("DELETE FROM leaf_scans")
    suspend fun deleteAllScans()
}
