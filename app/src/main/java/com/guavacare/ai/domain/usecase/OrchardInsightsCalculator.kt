package com.guavacare.ai.domain.usecase

import com.guavacare.ai.domain.model.DailyScanActivity
import com.guavacare.ai.domain.model.GuavaLeafClass
import com.guavacare.ai.domain.model.OrchardInsightsSummary
import com.guavacare.ai.domain.model.ScanRecord
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class OrchardInsightsCalculator {

    fun calculate(scans: List<ScanRecord>, nowMillis: Long = System.currentTimeMillis()): OrchardInsightsSummary {
        if (scans.isEmpty()) return OrchardInsightsSummary()

        val distribution = linkedMapOf<GuavaLeafClass, Int>()
        GuavaLeafClass.entries.forEach { distribution[it] = 0 }
        scans.forEach { record ->
            distribution[record.predictedClass] = (distribution[record.predictedClass] ?: 0) + 1
        }

        val healthyCount = distribution[GuavaLeafClass.HEALTHY] ?: 0
        val affectedCount = scans.size - healthyCount
        val mockCount = scans.count { it.isMockResult }

        val mostFrequent = distribution.entries
            .filter { it.value > 0 }
            .maxByOrNull { it.value }
            ?.key

        val dayFormat = SimpleDateFormat("EEE", Locale.ENGLISH)
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -6)
        }

        val weekly = mutableListOf<DailyScanActivity>()
        val dayMs = 24L * 60L * 60L * 1000L
        for (i in 0 until 7) {
            val start = cal.timeInMillis
            val end = start + dayMs
            val count = scans.count { it.timestamp in start until end }
            weekly += DailyScanActivity(
                dayLabel = dayFormat.format(Date(start)),
                count = count
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        return OrchardInsightsSummary(
            totalScans = scans.size,
            healthyCount = healthyCount,
            affectedCount = affectedCount,
            mockScanCount = mockCount,
            classDistribution = distribution,
            mostFrequentCondition = mostFrequent,
            weeklyActivity = weekly
        )
    }
}
