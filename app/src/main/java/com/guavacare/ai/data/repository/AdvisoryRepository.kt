package com.guavacare.ai.data.repository

import android.content.Context
import com.guavacare.ai.core.constants.ModelConstants
import com.guavacare.ai.domain.model.AdvisoryArticle
import com.guavacare.ai.domain.model.AdvisoryBundle
import com.guavacare.ai.domain.model.GuavaLeafClass
import com.guavacare.ai.domain.model.OrchardTip
import org.json.JSONArray
import org.json.JSONObject

class AdvisoryRepository(private val context: Context) {

    @Volatile
    private var cachedBundle: AdvisoryBundle? = null

    fun loadBundle(): AdvisoryBundle {
        cachedBundle?.let { return it }
        val raw = context.assets.open(ModelConstants.ADVISORY_ASSET_PATH).bufferedReader().use { it.readText() }
        val root = JSONObject(raw)

        val tipsArr = root.getJSONArray("tips")
        val tips = (0 until tipsArr.length()).map { i ->
            val obj = tipsArr.getJSONObject(i)
            OrchardTip(
                id = obj.getString("id"),
                titleEn = obj.getString("titleEn"),
                titleBn = obj.getString("titleBn"),
                bodyEn = obj.getString("bodyEn"),
                bodyBn = obj.getString("bodyBn")
            )
        }

        val advisoriesArr = root.getJSONArray("advisories")
        val advisories = (0 until advisoriesArr.length()).map { i ->
            val obj = advisoriesArr.getJSONObject(i)
            AdvisoryArticle(
                leafClass = GuavaLeafClass.fromLabel(obj.getString("classKey")),
                nameEn = obj.getString("nameEn"),
                nameBn = obj.getString("nameBn"),
                severityEn = obj.getString("severityEn"),
                severityBn = obj.getString("severityBn"),
                overviewEn = obj.getString("overviewEn"),
                overviewBn = obj.getString("overviewBn"),
                symptomsEn = obj.getJSONArray("symptomsEn").toStringList(),
                symptomsBn = obj.getJSONArray("symptomsBn").toStringList(),
                causesEn = obj.getJSONArray("causesEn").toStringList(),
                causesBn = obj.getJSONArray("causesBn").toStringList(),
                preventionEn = obj.getJSONArray("preventionEn").toStringList(),
                preventionBn = obj.getJSONArray("preventionBn").toStringList(),
                managementEn = obj.getJSONArray("managementEn").toStringList(),
                managementBn = obj.getJSONArray("managementBn").toStringList(),
                whenToConsultEn = obj.getString("whenToConsultEn"),
                whenToConsultBn = obj.getString("whenToConsultBn")
            )
        }

        return AdvisoryBundle(
            version = root.optString("version", "1.0.0"),
            disclaimerEn = root.getString("disclaimerEn"),
            disclaimerBn = root.getString("disclaimerBn"),
            tips = tips,
            advisories = advisories
        ).also { cachedBundle = it }
    }

    fun getAdvisoryFor(leafClass: GuavaLeafClass): AdvisoryArticle? {
        return loadBundle().advisories.firstOrNull { it.leafClass == leafClass }
    }

    private fun JSONArray.toStringList(): List<String> = (0 until length()).map { getString(it) }
}
