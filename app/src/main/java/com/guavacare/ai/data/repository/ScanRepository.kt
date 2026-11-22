package com.guavacare.ai.data.repository

import com.guavacare.ai.core.utils.LeafImageStorage
import com.guavacare.ai.data.local.dao.ScanDao
import com.guavacare.ai.data.local.entities.ScanEntity
import com.guavacare.ai.domain.model.GuavaLeafClass
import com.guavacare.ai.domain.model.ScanRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ScanRepository(
    private val scanDao: ScanDao,
    private val imageStorage: LeafImageStorage
) {
    val allScans: Flow<List<ScanRecord>> = scanDao.observeAllScans().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun getScanById(id: Long): ScanRecord? = withContext(Dispatchers.IO) {
        scanDao.getScanById(id)?.toDomain()
    }

    suspend fun saveScan(record: ScanRecord): Long = withContext(Dispatchers.IO) {
        scanDao.insertScan(ScanEntity.fromDomain(record))
    }

    suspend fun updateScanMetadata(
        id: Long,
        orchardName: String,
        plotId: String,
        notes: String
    ) = withContext(Dispatchers.IO) {
        val existing = scanDao.getScanById(id) ?: return@withContext
        scanDao.updateScan(
            existing.copy(
                orchardName = orchardName.trim(),
                plotId = plotId.trim(),
                notes = notes.trim()
            )
        )
    }

    suspend fun deleteScan(id: Long) = withContext(Dispatchers.IO) {
        val existing = scanDao.getScanById(id)
        scanDao.deleteScanById(id)
        if (existing != null) {
            imageStorage.deleteImageSafely(existing.imagePath)
        }
    }

    suspend fun deleteAllScans() = withContext(Dispatchers.IO) {
        val paths = scanDao.getAllImagePaths()
        scanDao.deleteAllScans()
        paths.forEach { imageStorage.deleteImageSafely(it) }
        imageStorage.deleteAllScanImages()
    }

    fun storageUsageBytes(): Long = imageStorage.calculateStorageBytes()

    companion object {
        fun encodeScoresJson(scores: Map<GuavaLeafClass, Float>): String {
            val obj = JSONObject()
            scores.forEach { (leafClass, value) ->
                obj.put(leafClass.canonicalName, value.toDouble())
            }
            return obj.toString()
        }

        fun decodeScoresJson(json: String): Map<GuavaLeafClass, Float> {
            val result = linkedMapOf<GuavaLeafClass, Float>()
            runCatching {
                val obj = JSONObject(json)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    result[GuavaLeafClass.fromLabel(key)] = obj.getDouble(key).toFloat()
                }
            }
            return result
        }
    }
}
