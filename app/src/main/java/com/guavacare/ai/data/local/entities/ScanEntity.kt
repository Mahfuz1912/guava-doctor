package com.guavacare.ai.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.guavacare.ai.domain.model.GuavaLeafClass
import com.guavacare.ai.domain.model.ScanRecord

@Entity(tableName = "leaf_scans")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val imagePath: String,
    val predictedClass: String,
    val decisionScore: Float,
    val calibratedConfidence: Float?,
    val marginToRunnerUp: Float,
    val allScoresJson: String,
    val inferenceTimeMs: Long,
    val timestamp: Long,
    val orchardName: String,
    val plotId: String,
    val notes: String,
    val explanationMethod: String,
    val isMockResult: Boolean
) {
    fun toDomain(): ScanRecord = ScanRecord(
        id = id,
        imagePath = imagePath,
        predictedClass = GuavaLeafClass.fromLabel(predictedClass),
        decisionScore = decisionScore,
        calibratedConfidence = calibratedConfidence,
        marginToRunnerUp = marginToRunnerUp,
        allScoresJson = allScoresJson,
        inferenceTimeMs = inferenceTimeMs,
        timestamp = timestamp,
        orchardName = orchardName,
        plotId = plotId,
        notes = notes,
        explanationMethod = explanationMethod,
        isMockResult = isMockResult
    )

    companion object {
        fun fromDomain(record: ScanRecord): ScanEntity = ScanEntity(
            id = record.id,
            imagePath = record.imagePath,
            predictedClass = record.predictedClass.canonicalName,
            decisionScore = record.decisionScore,
            calibratedConfidence = record.calibratedConfidence,
            marginToRunnerUp = record.marginToRunnerUp,
            allScoresJson = record.allScoresJson,
            inferenceTimeMs = record.inferenceTimeMs,
            timestamp = record.timestamp,
            orchardName = record.orchardName,
            plotId = record.plotId,
            notes = record.notes,
            explanationMethod = record.explanationMethod,
            isMockResult = record.isMockResult
        )
    }
}
