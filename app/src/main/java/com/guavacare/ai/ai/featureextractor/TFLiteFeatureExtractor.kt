package com.guavacare.ai.ai.featureextractor

import android.content.Context
import android.os.SystemClock
import com.guavacare.ai.core.constants.ModelConstants
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class FeatureExtractionOutput(
    val featureVector: FloatArray,
    val spatialFeatureMap: Array<Array<FloatArray>>? = null,
    val inferenceTimeMs: Long
)

data class TFLiteTensorMetadata(
    val inputShape: IntArray,
    val outputFeatureShape: IntArray,
    val spatialMapShape: IntArray?,
    val coldInitTimeMs: Long
)

/**
 * Persistent TensorFlow Lite Interpreter wrapper for MobileNetV2 FP16 feature extraction.
 * Keeps the interpreter warm in memory to avoid repeated initialization latency per scan.
 *
 * Loads `feature_extractor_fp16.tflite` from:
 *  1. App-private internal storage (`filesDir/models/feature_extractor_fp16.tflite`) if imported in-app, OR
 *  2. Bundled APK assets (`assets/models/feature_extractor_fp16.tflite`).
 *
 * Supports both:
 *  - Single-output TFLite: `[1, 224, 224, 3]` -> `[1, 1280]`
 *  - Dual-output TFLite (for exact SVM-weighted Feature-Map CAM):
 *      output 0: `[1, 1280]` pooled feature vector
 *      output 1: `[1, H, W, 1280]` final convolutional spatial activations
 */
class TFLiteFeatureExtractor(
    private val context: Context,
    private val modelAssetPath: String = ModelConstants.TFLITE_MODEL_ASSET_PATH,
    private val numThreads: Int = 4,
    private val modelFileStorage: ModelFileStorage = ModelFileStorage(context)
) : AutoCloseable {

    private val lock = Any()
    @Volatile
    private var interpreter: Interpreter? = null
    @Volatile
    private var tensorMetadata: TFLiteTensorMetadata? = null

    private var pooledOutputIndex: Int = 0
    private var spatialOutputIndex: Int = -1

    fun ensureInitialized(): TFLiteTensorMetadata {
        tensorMetadata?.let { return it }
        synchronized(lock) {
            tensorMetadata?.let { return it }
            val startMs = SystemClock.elapsedRealtime()
            val mappedModel = loadModelBuffer()
            val options = Interpreter.Options().apply {
                setNumThreads(numThreads)
            }
            val tflite = try {
                Interpreter(mappedModel, options)
            } catch (t: Throwable) {
                throw IllegalStateException(
                    "Unable to initialize TFLite interpreter (${t.message}). " +
                        "Ensure the model was exported with standard TFLITE_BUILTINS ops for TFLite 2.16+.",
                    t
                )
            }

            val inspected = try {
                inspectInterpreterContract(tflite, startMs)
            } catch (t: Throwable) {
                tflite.close()
                throw t
            }

            pooledOutputIndex = inspected.pooledIndex
            spatialOutputIndex = inspected.spatialIndex
            interpreter = tflite
            tensorMetadata = inspected.metadata
            return inspected.metadata
        }
    }

    fun extractFeatures(
        inputBuffer: ByteBuffer,
        includeSpatialMap: Boolean = true
    ): FeatureExtractionOutput {
        val meta = ensureInitialized()
        synchronized(lock) {
            val tflite = requireNotNull(interpreter) { "TFLite interpreter was closed." }
            inputBuffer.rewind()
            val startMs = SystemClock.elapsedRealtime()

            val pooledOut = Array(1) { FloatArray(ModelConstants.FEATURE_DIMENSION) }
            val spatialShape = meta.spatialMapShape

            if (includeSpatialMap && spatialOutputIndex >= 0 && spatialShape != null) {
                val h = spatialShape[1]
                val w = spatialShape[2]
                val c = spatialShape[3]
                val spatialOut = Array(1) { Array(h) { Array(w) { FloatArray(c) } } }
                val outputs = mutableMapOf<Int, Any>(
                    pooledOutputIndex to pooledOut,
                    spatialOutputIndex to spatialOut
                )
                tflite.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
                val duration = SystemClock.elapsedRealtime() - startMs
                return FeatureExtractionOutput(
                    featureVector = pooledOut[0],
                    spatialFeatureMap = spatialOut[0],
                    inferenceTimeMs = duration
                )
            } else {
                tflite.run(inputBuffer, pooledOut)
                val duration = SystemClock.elapsedRealtime() - startMs
                return FeatureExtractionOutput(
                    featureVector = pooledOut[0],
                    spatialFeatureMap = null,
                    inferenceTimeMs = duration
                )
            }
        }
    }

    fun currentMetadata(): TFLiteTensorMetadata? = tensorMetadata

    override fun close() {
        synchronized(lock) {
            interpreter?.close()
            interpreter = null
            tensorMetadata = null
        }
    }

    private fun loadModelBuffer(): MappedByteBuffer {
        if (modelFileStorage.hasImportedModel()) {
            return mapFileReadOnly(modelFileStorage.importedModelFile())
        }
        return try {
            context.assets.openFd(modelAssetPath).use { afd ->
                FileInputStream(afd.fileDescriptor).use { fis ->
                    fis.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
                }
            }
        } catch (e: Exception) {
            throw FileNotFoundException(
                "Missing feature_extractor_fp16.tflite in APK assets ($modelAssetPath) and app storage. " +
                    "Import the .tflite file in Settings or bundle it under app/src/main/assets/$modelAssetPath."
            ).apply { initCause(e) }
        }
    }

    private data class InspectedContract(
        val pooledIndex: Int,
        val spatialIndex: Int,
        val metadata: TFLiteTensorMetadata
    )

    companion object {
        fun validateModelFile(file: File): TFLiteTensorMetadata {
            val startMs = SystemClock.elapsedRealtime()
            val mapped = mapFileReadOnly(file)
            val tflite = try {
                Interpreter(mapped, Interpreter.Options().apply { setNumThreads(2) })
            } catch (t: Throwable) {
                throw IllegalArgumentException(
                    "TFLite rejected this model (${t.message}). " +
                        "Re-export using TensorFlow Lite TFLITE_BUILTINS with FP16 quantization.",
                    t
                )
            }
            return try {
                inspectInterpreterContract(tflite, startMs).metadata
            } finally {
                tflite.close()
            }
        }

        private fun mapFileReadOnly(file: File): MappedByteBuffer {
            FileInputStream(file).use { fis ->
                return fis.channel.map(FileChannel.MapMode.READ_ONLY, 0L, file.length())
            }
        }

        private fun inspectInterpreterContract(
            tflite: Interpreter,
            startMs: Long
        ): InspectedContract {
            val inputTensor = tflite.getInputTensor(0)
            val inputShape = inputTensor.shape()
            val inputType = inputTensor.dataType()

            require(inputType == DataType.FLOAT32) {
                "Unsupported TFLite input tensor type: $inputType. Expected FLOAT32 in range [-1, 1]."
            }
            require(
                inputShape.contentEquals(
                    intArrayOf(
                        1,
                        ModelConstants.INPUT_IMAGE_SIZE,
                        ModelConstants.INPUT_IMAGE_SIZE,
                        ModelConstants.INPUT_CHANNELS
                    )
                )
            ) {
                "Unexpected TFLite input tensor shape: ${inputShape.contentToString()}. " +
                    "Expected [1, 224, 224, 3]."
            }

            var detectedPooledIdx = -1
            var detectedSpatialIdx = -1
            var pooledShape: IntArray? = null
            var spatialShape: IntArray? = null

            for (i in 0 until tflite.outputTensorCount) {
                val tensor = tflite.getOutputTensor(i)
                val shape = tensor.shape()
                if (tensor.dataType() != DataType.FLOAT32) continue
                when {
                    shape.size == 2 && shape[0] == 1 && shape[1] == ModelConstants.FEATURE_DIMENSION -> {
                        detectedPooledIdx = i
                        pooledShape = shape
                    }
                    shape.size == 4 && shape[0] == 1 && shape[3] == ModelConstants.FEATURE_DIMENSION -> {
                        detectedSpatialIdx = i
                        spatialShape = shape
                    }
                }
            }

            require(detectedPooledIdx >= 0 && pooledShape != null) {
                "TFLite model missing required FLOAT32 [1, ${ModelConstants.FEATURE_DIMENSION}] feature output tensor."
            }

            val elapsedMs = SystemClock.elapsedRealtime() - startMs
            return InspectedContract(
                pooledIndex = detectedPooledIdx,
                spatialIndex = detectedSpatialIdx,
                metadata = TFLiteTensorMetadata(
                    inputShape = inputShape,
                    outputFeatureShape = pooledShape,
                    spatialMapShape = spatialShape,
                    coldInitTimeMs = elapsedMs
                )
            )
        }
    }
}
