package com.guavacare.ai.domain.model

enum class ReleaseHighlightCategory {
    DESIGN,
    FEATURE,
    SECURITY,
    SYNC,
    PERFORMANCE,
    FIX,
    GENERAL
}

data class ReleaseNoteHighlight(
    val title: String,
    val detail: String? = null,
    val category: ReleaseHighlightCategory = ReleaseHighlightCategory.GENERAL
)

/**
 * Cleans and structures raw GitHub Release bodies or custom release notes into
 * scannable, user-friendly update highlights while removing technical boilerplate
 * such as `versionCode`, signing notices, and raw GitHub compare URLs.
 */
object ReleaseNotesFormatter {

    private val NOISE_LINE_PATTERNS = listOf(
        Regex("""^\s*version\s*[:=].*$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*(?:versionCode|version_code)\s*[:=].*$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*(?:forceUpdate|force_update)\s*[:=].*$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*signed\s+android\s+apk.*$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*\*{0,2}\s*full\s+changelog\s*\*{0,2}\s*:?.*$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*https?://\S+\s*$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*#{1,4}\s*(?:what'?s\s+changed|new\s+contributors|full\s+changelog)\s*$""", RegexOption.IGNORE_CASE),
        Regex("""^\s*[-*_]{3,}\s*$"""),
        Regex("""^\s*<!--.*-->\s*$""")
    )

    private val ENGLISH_HEADER_REGEX = Regex("""^\s*#{1,4}\s*(?:english|en|what'?s\s+new\s*\(en\))\s*:?\s*$""", RegexOption.IGNORE_CASE)
    private val BANGLA_HEADER_REGEX = Regex("""^\s*#{1,4}\s*(?:বাংলা|bangla|bengali|bn|নতুন\s+যা\s+যুক্ত\s+হয়েছে)\s*:?\s*$""", RegexOption.IGNORE_CASE)
    private val GENERIC_HEADING_REGEX = Regex("""^\s*#{1,4}\s+.+$""")

    private val LEADING_BULLET_REGEX = Regex("""^\s*(?:[-*•▪▸►✓✔✅✨🚀🔒☁️⚡🎨🛠️📱🌿]+|\d+[.)])\s+""")
    private val PR_AUTHOR_SUFFIX_REGEX = Regex("""\s+by\s+@[\w-]+\s+in\s+https?://\S+\s*$""", RegexOption.IGNORE_CASE)
    private val INLINE_URL_REGEX = Regex("""https?://\S+""")
    private val LEGACY_SUMMARY_PREFIX_REGEX = Regex("""^(?:guava\s+doctor|version)\s+v?\d+(?:\.\d+)*\s*[—–-]\s*""", RegexOption.IGNORE_CASE)

    /**
     * Extracts bilingual `### English` and `### বাংলা` sections when present in a GitHub release body.
     * Falls back to returning the original body as English when no explicit bilingual headers exist.
     */
    fun splitBilingualBody(rawBody: String): Pair<String, String> {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) return "" to ""

        val lines = trimmed.lines()
        val hasEnglishHeader = lines.any { ENGLISH_HEADER_REGEX.matches(it) }
        val hasBanglaHeader = lines.any { BANGLA_HEADER_REGEX.matches(it) }
        if (!hasEnglishHeader && !hasBanglaHeader) {
            return trimmed to ""
        }

        val enLines = mutableListOf<String>()
        val bnLines = mutableListOf<String>()
        var currentSection: String? = null

        for (line in lines) {
            when {
                ENGLISH_HEADER_REGEX.matches(line) -> currentSection = "en"
                BANGLA_HEADER_REGEX.matches(line) -> currentSection = "bn"
                GENERIC_HEADING_REGEX.matches(line) -> currentSection = null
                isNoiseLine(line) -> Unit
                else -> {
                    when (currentSection) {
                        "en" -> enLines.add(line)
                        "bn" -> bnLines.add(line)
                    }
                }
            }
        }

        val enResult = enLines.joinToString("\n").trim()
        val bnResult = bnLines.joinToString("\n").trim()
        return if (enResult.isEmpty() && bnResult.isEmpty()) {
            trimmed to ""
        } else {
            enResult to bnResult
        }
    }

    /**
     * Selects strictly one language's release notes matching [language], automatically splitting
     * combined bilingual markdown bodies if needed.
     */
    fun extractForLanguage(
        releaseNotesEn: String,
        releaseNotesBn: String,
        language: AppLanguage,
        fallback: String
    ): String {
        val (enText, bnText) = if (releaseNotesBn.isBlank() && releaseNotesEn.isNotBlank()) {
            splitBilingualBody(releaseNotesEn)
        } else {
            releaseNotesEn.trim() to releaseNotesBn.trim()
        }
        return when (language) {
            AppLanguage.BANGLA -> bnText.ifBlank { enText }.ifBlank { fallback }
            AppLanguage.ENGLISH -> enText.ifBlank { bnText }.ifBlank { fallback }
        }
    }

    fun parseHighlights(rawNotes: String): List<ReleaseNoteHighlight> {
        val cleanedLines = rawNotes
            .replace("\r\n", "\n")
            .lines()
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { isNoiseLine(it) }
            .filterNot { ENGLISH_HEADER_REGEX.matches(it) || BANGLA_HEADER_REGEX.matches(it) }
            .map { cleanMarkdownLine(it) }
            .filter { it.isNotBlank() }
            .toList()

        if (cleanedLines.isEmpty()) return emptyList()

        // Expand legacy single-line summary format (e.g. "Guava Doctor v2.1 — A, B, C, and D.")
        val normalizedItems = if (cleanedLines.size == 1 && shouldSplitLegacySummary(cleanedLines.first())) {
            splitLegacySummary(cleanedLines.first())
        } else {
            cleanedLines
        }

        return normalizedItems
            .distinct()
            .take(8)
            .map { toHighlight(it) }
    }

    private fun isNoiseLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return true
        return NOISE_LINE_PATTERNS.any { it.matches(trimmed) }
    }

    private fun cleanMarkdownLine(line: String): String {
        return line
            .replace(LEADING_BULLET_REGEX, "")
            .replace(PR_AUTHOR_SUFFIX_REGEX, "")
            .replace(INLINE_URL_REGEX, "")
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .trimEnd(',', ';')
            .trim()
    }

    private fun shouldSplitLegacySummary(line: String): Boolean {
        if (line.length < 75) return false
        val hasPrefix = LEGACY_SUMMARY_PREFIX_REGEX.containsMatchIn(line)
        val commaCount = line.count { it == ',' }
        return hasPrefix && commaCount >= 2
    }

    private fun splitLegacySummary(line: String): List<String> {
        val withoutPrefix = line.replace(LEGACY_SUMMARY_PREFIX_REGEX, "").trim().trimEnd('.')
        return withoutPrefix
            .split(Regex(""",\s*(?:and\s+)?|\s+এবং\s+"""))
            .map { it.trim().removePrefix("and ").trim().trimEnd('.') }
            .filter { it.length >= 4 }
            .map { item ->
                item.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase() else ch.toString()
                }
            }
    }

    private fun toHighlight(text: String): ReleaseNoteHighlight {
        val separatorMatch = Regex("""^([^:–—]{3,44})\s*[:–—]\s+(.+)$""").find(text)
        val (title, detail) = if (separatorMatch != null) {
            val candidateTitle = separatorMatch.groupValues[1].trim()
            val candidateDetail = separatorMatch.groupValues[2].trim()
            if (candidateDetail.length >= 8) {
                candidateTitle to candidateDetail
            } else {
                text to null
            }
        } else {
            text to null
        }

        return ReleaseNoteHighlight(
            title = title,
            detail = detail,
            category = detectCategory(text)
        )
    }

    private fun detectCategory(text: String): ReleaseHighlightCategory {
        val lower = text.lowercase()
        return when {
            listOf("home", "ui", "design", "layout", "card", "theme", "হোম", "ডিজাইন", "ইন্টারফেস", "কার্ড").any { lower.contains(it) } ->
                ReleaseHighlightCategory.DESIGN
            listOf("login", "sign-in", "sign in", "google", "password", "auth", "privacy", "security", "লগিন", "পাসওয়ার্ড", "গোপনীয়তা", "নিরাপত্তা", "অ্যাকাউন্ট").any { lower.contains(it) } ->
                ReleaseHighlightCategory.SECURITY
            listOf("sync", "cloud", "history", "insights", "backup", "হিস্ট্রি", "ইনসাইট", "ক্লাউড", "সিঙ্ক", "ব্যাকআপ").any { lower.contains(it) } ->
                ReleaseHighlightCategory.SYNC
            listOf("fix", "bug", "crash", "error", "resolve", "stability", "সমাধান", "বাগ", "স্থায়িত্ব", "ত্রুটি").any { lower.contains(it) } ->
                ReleaseHighlightCategory.FIX
            listOf("speed", "fast", "optimiz", "performance", "accuracy", "model", "দ্রুত", "অপটিমাইজ", "নির্ভুলতা", "মডেল").any { lower.contains(it) } ->
                ReleaseHighlightCategory.PERFORMANCE
            listOf("new", "added", "tool", "guide", "feature", "যুক্ত", "নতুন", "টুলস", "গাইড", "সুবিধা").any { lower.contains(it) } ->
                ReleaseHighlightCategory.FEATURE
            else -> ReleaseHighlightCategory.GENERAL
        }
    }
}
