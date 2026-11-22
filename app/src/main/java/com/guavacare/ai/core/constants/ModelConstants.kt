package com.guavacare.ai.core.constants

object ModelConstants {
    const val TFLITE_MODEL_ASSET_PATH = "models/feature_extractor_fp16.tflite"
    const val CLASSIFIER_DIR = "classifier"
    const val SCALER_MEAN_ASSET_PATH = "classifier/scaler_mean.json"
    const val SCALER_SCALE_ASSET_PATH = "classifier/scaler_scale.json"
    const val SVM_COEF_ASSET_PATH = "classifier/svm_coef.json"
    const val SVM_INTERCEPT_ASSET_PATH = "classifier/svm_intercept.json"
    const val CLASS_LABELS_ASSET_PATH = "classifier/class_labels.json"
    const val ADVISORY_ASSET_PATH = "advisory/advisory_content.json"

    const val INPUT_IMAGE_SIZE = 224
    const val INPUT_CHANNELS = 3
    const val FEATURE_DIMENSION = 1280
    const val NUM_CLASSES = 5

    // Image Quality Gate & Non-Leaf Detection defaults
    const val MIN_IMAGE_DIMENSION_PX = 224
    const val MIN_BLUR_LAPLACIAN_VARIANCE = 28.0
    const val MIN_MEAN_LUMINANCE = 28.0
    const val MAX_MEAN_LUMINANCE = 238.0
    const val MIN_FOLIAGE_PIXEL_RATIO = 0.09f
    const val MIN_CENTER_FOLIAGE_PIXEL_RATIO = 0.12f
    const val MAX_CENTER_SKIN_RATIO_WITHOUT_GREEN = 0.22f
    const val MAX_CENTER_NON_BOTANICAL_RATIO = 0.25f
    const val MAX_CENTER_NEUTRAL_RATIO = 0.80f

    // Uncertainty flag threshold on margin between top-1 and runner-up SVM score
    const val LOW_MARGIN_WARNING_THRESHOLD = 0.35f

    // Research reference metadata (explicitly labeled as reference benchmarks)
    const val RESEARCH_TITLE =
        "An Explainable Lightweight Hybrid Framework for Real-Time Guava Leaf Health Assessment: Insights from Bangladesh"
    const val REFERENCE_TEST_ACCURACY = "96.27%"
    const val REFERENCE_FEATURE_EXTRACTOR_SIZE = "4.28 MB"
    const val REFERENCE_TOTAL_MODEL_SIZE = "14.03 MB"
    const val REFERENCE_INFERENCE_TIME = "14.94 ms"
}
