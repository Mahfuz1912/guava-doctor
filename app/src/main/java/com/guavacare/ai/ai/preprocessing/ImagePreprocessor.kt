package com.guavacare.ai.ai.preprocessing

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.scale
import com.guavacare.ai.core.constants.ModelConstants
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Exact MobileNetV2 RGB preprocessing matching `tf.keras.applications.mobilenet_v2.preprocess_input`:
 * Resizes to 224 x 224 RGB and normalizes each pixel channel from [0, 255] to [-1.0, 1.0].
 */
class ImagePreprocessor(
    private val inputSize: Int = ModelConstants.INPUT_IMAGE_SIZE
) {
    fun resizeForModel(source: Bitmap): Bitmap {
        val argb = if (source.config != Bitmap.Config.ARGB_8888) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            source
        }
        return if (argb.width == inputSize && argb.height == inputSize) {
            argb
        } else {
            val resized = argb.scale(inputSize, inputSize)
            if (argb != source) {
                argb.recycle()
            }
            resized
        }
    }

    fun preprocessToByteBuffer(source: Bitmap): ByteBuffer {
        val resized = resizeForModel(source)
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        if (resized != source) {
            resized.recycle()
        }

        val buffer = ByteBuffer
            .allocateDirect(1 * inputSize * inputSize * ModelConstants.INPUT_CHANNELS * 4)
            .order(ByteOrder.nativeOrder())

        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            buffer.putFloat(normalizeChannel(r))
            buffer.putFloat(normalizeChannel(g))
            buffer.putFloat(normalizeChannel(b))
        }
        buffer.rewind()
        return buffer
    }

    fun preprocessPixelsToFloatArray(argbPixels: IntArray): FloatArray {
        val out = FloatArray(argbPixels.size * 3)
        var offset = 0
        for (pixel in argbPixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            out[offset++] = normalizeChannel(r)
            out[offset++] = normalizeChannel(g)
            out[offset++] = normalizeChannel(b)
        }
        return out
    }

    fun normalizeChannel(channel0To255: Int): Float = (channel0To255 / 127.5f) - 1.0f
}
