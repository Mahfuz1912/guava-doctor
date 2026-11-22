package com.guavacare.ai.domain.model

enum class GuavaLeafClass(
    val canonicalName: String,
    val displayNameEn: String,
    val displayNameBn: String,
    val isHealthy: Boolean
) {
    ANTHRACNOSE(
        canonicalName = "Anthracnose",
        displayNameEn = "Anthracnose",
        displayNameBn = "অ্যানথ্রাকনোজ",
        isHealthy = false
    ),
    HEALTHY(
        canonicalName = "Healthy",
        displayNameEn = "Healthy",
        displayNameBn = "সুস্থ পাতা",
        isHealthy = true
    ),
    NUTRIENT_STRESS(
        canonicalName = "Nutrient Stress",
        displayNameEn = "Nutrient Stress",
        displayNameBn = "পুষ্টির ঘাটতি",
        isHealthy = false
    ),
    PEST_DAMAGE(
        canonicalName = "Pest Damage",
        displayNameEn = "Pest Damage",
        displayNameBn = "পোকামাকড়ের আক্রমণ",
        isHealthy = false
    ),
    SOOTY_MOLD(
        canonicalName = "Sooty Mold",
        displayNameEn = "Sooty Mold",
        displayNameBn = "সুটি মোল্ড",
        isHealthy = false
    );

    fun localizedName(language: AppLanguage): String = when (language) {
        AppLanguage.ENGLISH -> displayNameEn
        AppLanguage.BANGLA -> displayNameBn
    }

    companion object {
        fun fromLabel(label: String): GuavaLeafClass {
            val normalized = label.trim().lowercase().replace("_", " ")
            return entries.firstOrNull {
                it.canonicalName.lowercase() == normalized ||
                    it.name.lowercase().replace("_", " ") == normalized
            } ?: HEALTHY
        }
    }
}
