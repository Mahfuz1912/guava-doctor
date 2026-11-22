package com.guavacare.ai.domain.model

data class ScanRecord(
    val id: Long = 0L,
    val imagePath: String,
    val predictedClass: GuavaLeafClass,
    val decisionScore: Float,
    val calibratedConfidence: Float? = null,
    val marginToRunnerUp: Float = 0f,
    val allScoresJson: String = "{}",
    val inferenceTimeMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val orchardName: String = "",
    val plotId: String = "",
    val notes: String = "",
    val explanationMethod: String = "",
    val isMockResult: Boolean = false
)

data class AdvisoryArticle(
    val leafClass: GuavaLeafClass,
    val nameEn: String,
    val nameBn: String,
    val severityEn: String,
    val severityBn: String,
    val overviewEn: String,
    val overviewBn: String,
    val symptomsEn: List<String>,
    val symptomsBn: List<String>,
    val causesEn: List<String>,
    val causesBn: List<String>,
    val preventionEn: List<String>,
    val preventionBn: List<String>,
    val managementEn: List<String>,
    val managementBn: List<String>,
    val whenToConsultEn: String,
    val whenToConsultBn: String
)

data class OrchardTip(
    val id: String,
    val titleEn: String,
    val titleBn: String,
    val bodyEn: String,
    val bodyBn: String
)

data class AdvisoryBundle(
    val version: String,
    val disclaimerEn: String,
    val disclaimerBn: String,
    val tips: List<OrchardTip>,
    val advisories: List<AdvisoryArticle>
)

data class DailyScanActivity(
    val dayLabel: String,
    val count: Int
)

data class OrchardInsightsSummary(
    val totalScans: Int = 0,
    val healthyCount: Int = 0,
    val affectedCount: Int = 0,
    val mockScanCount: Int = 0,
    val classDistribution: Map<GuavaLeafClass, Int> = emptyMap(),
    val mostFrequentCondition: GuavaLeafClass? = null,
    val weeklyActivity: List<DailyScanActivity> = emptyList()
)

enum class HistoryDateFilter {
    ALL,
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS
}
