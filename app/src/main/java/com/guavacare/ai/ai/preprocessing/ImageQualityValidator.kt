package com.guavacare.ai.ai.preprocessing

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.scale
import com.guavacare.ai.core.constants.ModelConstants
import com.guavacare.ai.domain.model.AppLanguage
import kotlin.math.max
import kotlin.math.min

enum class QualityIssueType {
    INVALID_DIMENSIONS,
    TOO_BLURRY,
    TOO_DARK,
    TOO_BRIGHT,
    POTENTIALLY_NON_LEAF
}

data class QualityIssue(
    val type: QualityIssueType,
    val messageEn: String,
    val messageBn: String
) {
    fun localizedMessage(language: AppLanguage): String = when (language) {
        AppLanguage.ENGLISH -> messageEn
        AppLanguage.BANGLA -> messageBn
    }
}

data class ImageQualityReport(
    val isValid: Boolean,
    val width: Int,
    val height: Int,
    val blurVariance: Double,
    val meanLuminance: Double,
    val foliageRatio: Float,
    val issues: List<QualityIssue>,
    val centerFoliageRatio: Float = foliageRatio
) {
    val isNonLeaf: Boolean
        get() = issues.any { it.type == QualityIssueType.POTENTIALLY_NON_LEAF }
}

class ImageQualityValidator {

    fun validate(bitmap: Bitmap): ImageQualityReport {
        val width = bitmap.width
        val height = bitmap.height
        val issues = mutableListOf<QualityIssue>()

        if (width < ModelConstants.MIN_IMAGE_DIMENSION_PX || height < ModelConstants.MIN_IMAGE_DIMENSION_PX) {
            issues += QualityIssue(
                type = QualityIssueType.INVALID_DIMENSIONS,
                messageEn = "Image resolution (${width}×${height}) is too small. Minimum required is 224×224 pixels.",
                messageBn = "ছবির রেজোলিউশন (${width}×${height}) খুবই কম। ন্যূনতম ২২৪×২২৪ পিক্সেল প্রয়োজন।"
            )
            return ImageQualityReport(
                isValid = false,
                width = width,
                height = height,
                blurVariance = 0.0,
                meanLuminance = 0.0,
                foliageRatio = 0f,
                issues = issues,
                centerFoliageRatio = 0f
            )
        }

        // Downsample to 160x160 for fast, consistent on-device quality and leaf-presence metrics
        val sampleSize = 160
        val scaled = bitmap.scale(sampleSize, sampleSize)
        val pixels = IntArray(sampleSize * sampleSize)
        scaled.getPixels(pixels, 0, sampleSize, 0, 0, sampleSize, sampleSize)
        if (scaled != bitmap) {
            scaled.recycle()
        }

        val gray = DoubleArray(pixels.size)
        var lumSum = 0.0
        var fullFoliagePixels = 0

        // Center 60% x 60% subject region where a leaf should sit
        val roiStart = (sampleSize * 0.20f).toInt()
        val roiEnd = (sampleSize * 0.80f).toInt()
        var centerTotalPixels = 0
        var centerFoliagePixels = 0
        var centerChlorophyllPixels = 0
        var centerSkinPixels = 0
        var centerNonBotanicalPixels = 0
        var centerNeutralPixels = 0

        for (y in 0 until sampleSize) {
            val rowOffset = y * sampleSize
            val inCenterY = y in roiStart until roiEnd
            for (x in 0 until sampleSize) {
                val idx = rowOffset + x
                val c = pixels[idx]
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)

                val lum = 0.299 * r + 0.587 * g + 0.114 * b
                gray[idx] = lum
                lumSum += lum

                val isChlorophyll = isChlorophyllGreenPixel(r, g, b)
                val isLeaf = isChlorophyll || isOliveOrSootyLeafPixel(r, g, b)
                if (isLeaf) {
                    fullFoliagePixels++
                }

                if (inCenterY && x in roiStart until roiEnd) {
                    centerTotalPixels++
                    if (isLeaf) {
                        centerFoliagePixels++
                        if (isChlorophyll) {
                            centerChlorophyllPixels++
                        }
                    } else {
                        when {
                            isLikelySkinTonePixel(r, g, b) -> centerSkinPixels++
                            isNonBotanicalVividPixel(r, g, b) -> centerNonBotanicalPixels++
                            isNeutralAchromaticPixel(r, g, b) -> centerNeutralPixels++
                        }
                    }
                }
            }
        }

        val meanLum = lumSum / pixels.size
        val blurVariance = computeLaplacianVariance(gray, sampleSize, sampleSize)
        val foliageRatio = fullFoliagePixels.toFloat() / pixels.size.toFloat()
        val centerDenom = max(1, centerTotalPixels).toFloat()
        val centerFoliageRatio = centerFoliagePixels.toFloat() / centerDenom
        val centerChlorophyllRatio = centerChlorophyllPixels.toFloat() / centerDenom
        val centerSkinRatio = centerSkinPixels.toFloat() / centerDenom
        val centerNonBotanicalRatio = centerNonBotanicalPixels.toFloat() / centerDenom
        val centerNeutralRatio = centerNeutralPixels.toFloat() / centerDenom

        if (meanLum < ModelConstants.MIN_MEAN_LUMINANCE) {
            issues += QualityIssue(
                type = QualityIssueType.TOO_DARK,
                messageEn = "Image is too dark (luminance ${meanLum.toInt()}). Move to natural daylight or turn on the flash.",
                messageBn = "ছবিটি অতিরিক্ত অন্ধকার। পর্যাপ্ত প্রাকৃতিক আলোতে যান অথবা ফ্ল্যাশ চালু করুন।"
            )
        } else if (meanLum > ModelConstants.MAX_MEAN_LUMINANCE) {
            issues += QualityIssue(
                type = QualityIssueType.TOO_BRIGHT,
                messageEn = "Image is overexposed (luminance ${meanLum.toInt()}). Shield the leaf from harsh direct glare.",
                messageBn = "ছবিতে অতিরিক্ত আলো পড়েছে। সরাসরি তীব্র রোদ এড়িয়ে ছায়ায় পাতার ছবি তুলুন।"
            )
        }

        if (blurVariance < ModelConstants.MIN_BLUR_LAPLACIAN_VARIANCE) {
            issues += QualityIssue(
                type = QualityIssueType.TOO_BLURRY,
                messageEn = "Image appears out of focus (sharpness ${"%.1f".format(blurVariance)}). Hold steady and tap to focus on the leaf.",
                messageBn = "ছবিটি ঝাপসা এসেছে। হাত স্থির রেখে পাতার ওপর ফোকাস করে আবার ছবি তুলুন।"
            )
        }

        val insufficientFoliage =
            foliageRatio < ModelConstants.MIN_FOLIAGE_PIXEL_RATIO ||
                centerFoliageRatio < ModelConstants.MIN_CENTER_FOLIAGE_PIXEL_RATIO
        val dominantSkin =
            (centerSkinRatio >= ModelConstants.MAX_CENTER_SKIN_RATIO_WITHOUT_GREEN && centerChlorophyllRatio < 0.25f) ||
                (centerSkinRatio > 0.12f && centerSkinRatio > centerFoliageRatio * 0.9f)
        val dominantNonBotanical =
            centerNonBotanicalRatio >= ModelConstants.MAX_CENTER_NON_BOTANICAL_RATIO &&
                centerFoliageRatio < 0.45f
        val dominantNeutralSurface =
            centerNeutralRatio >= ModelConstants.MAX_CENTER_NEUTRAL_RATIO &&
                centerFoliageRatio < 0.16f

        if (insufficientFoliage || dominantSkin || dominantNonBotanical || dominantNeutralSurface) {
            issues += QualityIssue(
                type = QualityIssueType.POTENTIALLY_NON_LEAF,
                messageEn = "This does not appear to be a guava leaf. Please capture or choose a clear guava leaf photo for accurate screening.",
                messageBn = "এটি পেয়ারা পাতার ছবি বলে মনে হচ্ছে না। সঠিক ফলাফলের জন্য অনুগ্রহ করে একটি স্পষ্ট পেয়ারা পাতার ছবি তুলুন বা গ্যালারি থেকে নির্বাচন করুন।"
            )
        }

        return ImageQualityReport(
            isValid = issues.isEmpty(),
            width = width,
            height = height,
            blurVariance = blurVariance,
            meanLuminance = meanLum,
            foliageRatio = foliageRatio,
            issues = issues,
            centerFoliageRatio = centerFoliageRatio
        )
    }

    internal fun computeLaplacianVariance(gray: DoubleArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3) return 0.0
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val center = gray[row + x]
                val lap = (
                    gray[row - width + x] +
                        gray[row + width + x] +
                        gray[row + x - 1] +
                        gray[row + x + 1] -
                        4.0 * center
                    )
                sum += lap
                sumSq += lap * lap
                count++
            }
        }
        if (count == 0) return 0.0
        val mean = sum / count
        return max(0.0, (sumSq / count) - mean * mean)
    }

    /**
     * Evaluates whether a pixel falls within natural botanical leaf hues:
     * chlorophyll green (Healthy / Anthracnose / Pest Damage) or
     * chlorotic yellow-green & muted olive-sooty foliage (Nutrient Stress / Sooty Mold).
     */
    internal fun isPotentialLeafPixel(r: Int, g: Int, b: Int): Boolean =
        isChlorophyllGreenPixel(r, g, b) || isOliveOrSootyLeafPixel(r, g, b)

    internal fun isChlorophyllGreenPixel(r: Int, g: Int, b: Int): Boolean {
        val hsv = rgbToHsv(r, g, b)
        val hue = hsv.hue
        val sat = hsv.saturation
        val value = hsv.value
        if (value < 0.08f || sat < 0.12f) return false
        return hue in 60f..165f && g > b + 5 && g >= r - 8
    }

    internal fun isOliveOrSootyLeafPixel(r: Int, g: Int, b: Int): Boolean {
        val hsv = rgbToHsv(r, g, b)
        val hue = hsv.hue
        val sat = hsv.saturation
        val value = hsv.value
        if (value < 0.08f || value > 0.94f || sat < 0.11f) return false
        // Yellow-green, chlorotic golden-olive, and shaded/sooty foliage while excluding warm pinkish skin & orange wood
        return hue in 34f..64f && g >= b + 10 && (g - b) >= (r - g)
    }

    internal fun isLikelySkinTonePixel(r: Int, g: Int, b: Int): Boolean {
        if (r <= 85 || g <= 40 || b <= 20) return false
        if (r <= g + 14 || r <= b + 18) return false
        if ((g - b) > (r - g) * 1.15f) return false
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        if (maxC - minC <= 15) return false

        val cb = 128.0 - 0.168736 * r - 0.331264 * g + 0.5 * b
        val cr = 128.0 + 0.5 * r - 0.418688 * g - 0.081312 * b
        return cb in 77.0..127.0 && cr in 133.0..173.0
    }

    internal fun isNonBotanicalVividPixel(r: Int, g: Int, b: Int): Boolean {
        val hsv = rgbToHsv(r, g, b)
        val hue = hsv.hue
        val sat = hsv.saturation
        val value = hsv.value
        if (value < 0.15f) return false
        val isBlueOrCyan = hue in 175f..255f && sat >= 0.20f
        val isPurpleOrPink = hue in 255f..340f && sat >= 0.20f
        val isVividRedOrOrange = (hue < 24f || hue > 340f) && sat >= 0.34f && value >= 0.28f
        return isBlueOrCyan || isPurpleOrPink || isVividRedOrOrange
    }

    internal fun isNeutralAchromaticPixel(r: Int, g: Int, b: Int): Boolean {
        val hsv = rgbToHsv(r, g, b)
        return hsv.saturation < 0.12f
    }

    private data class HsvTriplet(val hue: Float, val saturation: Float, val value: Float)

    private fun rgbToHsv(r: Int, g: Int, b: Int): HsvTriplet {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val maxC = max(rf, max(gf, bf))
        val minC = min(rf, min(gf, bf))
        val delta = maxC - minC
        val saturation = if (maxC == 0f) 0f else delta / maxC
        val hue = when {
            delta == 0f -> 0f
            maxC == rf -> 60f * (((gf - bf) / delta) % 6f)
            maxC == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        return HsvTriplet(hue = hue, saturation = saturation, value = maxC)
    }
}
