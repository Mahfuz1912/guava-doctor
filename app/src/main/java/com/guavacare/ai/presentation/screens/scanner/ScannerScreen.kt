package com.guavacare.ai.presentation.screens.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.guavacare.ai.ai.inference.ModelHealthStatus
import com.guavacare.ai.core.common.AppStrings
import com.guavacare.ai.domain.model.AppLanguage
import com.guavacare.ai.domain.model.GUEST_FREE_SCAN_LIMIT
import com.guavacare.ai.presentation.ScanFlowState
import com.guavacare.ai.presentation.components.DevMockModeBanner
import java.io.File

@Composable
fun ScannerScreen(
    strings: AppStrings,
    language: AppLanguage,
    showLeafGuide: Boolean,
    devMockModeEnabled: Boolean,
    scanFlowState: ScanFlowState,
    autoLaunchGallery: Boolean,
    onGalleryAutoLaunchConsumed: () -> Unit,
    onCreateCaptureFile: () -> File,
    onImageCaptured: (File) -> Unit,
    onGalleryImageSelected: (Uri) -> Unit,
    onRetryScan: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onConfirmPreview: () -> Unit = {},
    onDiscardPreview: () -> Unit = onRetryScan,
    modelHealth: ModelHealthStatus? = null,
    isVerifyingModel: Boolean = false,
    isImportingModel: Boolean = false,
    onImportTfliteModel: (Uri) -> Unit = {},
    isAuthenticated: Boolean = false,
    remainingGuestScans: Int = GUEST_FREE_SCAN_LIMIT,
    onOpenLoginForLimit: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var torchEnabled by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var isCapturingFrame by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onGalleryImageSelected(uri)
        }
    }

    val tflitePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportTfliteModel(uri)
        }
    }

    fun launchGalleryPicker() {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    LaunchedEffect(autoLaunchGallery) {
        if (autoLaunchGallery) {
            onGalleryAutoLaunchConsumed()
            launchGalleryPicker()
        }
    }

    BackHandler(
        enabled = scanFlowState is ScanFlowState.Preview ||
            scanFlowState is ScanFlowState.NonLeafWarning ||
            scanFlowState is ScanFlowState.QualityRejected ||
            scanFlowState is ScanFlowState.ModelMissing ||
            scanFlowState is ScanFlowState.Error
    ) {
        onDiscardPreview()
    }

    val isCameraLive = hasCameraPermission && cameraError == null
    val hasFlash = isCameraLive && boundCamera?.cameraInfo?.hasFlashUnit() == true
    val canCapture = isCameraLive && imageCapture != null && !isCapturingFrame && scanFlowState !is ScanFlowState.Processing
    val isFullOverlayActive = scanFlowState is ScanFlowState.Preview || scanFlowState is ScanFlowState.NonLeafWarning

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Minimal Clean Scanner Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Eco,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = strings.scannerTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (!isAuthenticated) {
                    val isLow = remainingGuestScans <= 1
                    Surface(
                        onClick = onOpenLoginForLimit,
                        shape = RoundedCornerShape(999.dp),
                        color = if (isLow) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        },
                        modifier = Modifier.testTag("scanner_guest_quota_badge")
                    ) {
                        Text(
                            text = "${strings.guestFreeScansLeft}: $remainingGuestScans/$GUEST_FREE_SCAN_LIMIT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isLow) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (devMockModeEnabled) {
                DevMockModeBanner(text = strings.devMockBanner)
            }

            // Main Camera Viewport Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        if (isCameraLive || isFullOverlayActive) {
                            Color(0xFF0E1612)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    )
            ) {
                if (isCameraLive) {
                    val previewView = remember {
                        PreviewView(context).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    }

                    DisposableEffect(lifecycleOwner) {
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                        val executor = ContextCompat.getMainExecutor(context)
                        val listener = Runnable {
                            runCatching {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                val capture = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                    .build()
                                cameraProvider.unbindAll()
                                val cam = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    capture
                                )
                                boundCamera = cam
                                imageCapture = capture
                            }.onFailure { err ->
                                cameraError = err.message ?: "Camera unavailable on this device."
                            }
                        }
                        cameraProviderFuture.addListener(listener, executor)
                        onDispose {
                            torchEnabled = false
                            boundCamera = null
                            imageCapture = null
                            runCatching {
                                cameraProviderFuture.get().unbindAll()
                            }
                        }
                    }

                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (showLeafGuide && !isFullOverlayActive) {
                        LeafCornerGuideOverlay(
                            hint = strings.alignLeafHint,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (!isFullOverlayActive) {
                    CameraFallbackCard(
                        title = if (cameraError != null) "Camera Preview Unavailable" else strings.cameraPermissionTitle,
                        body = cameraError ?: strings.cameraPermissionBody,
                        primaryLabel = if (cameraError != null) strings.galleryScan else strings.grantCameraPermission,
                        secondaryLabel = strings.useGalleryInstead,
                        showSecondary = cameraError == null,
                        onPrimary = {
                            if (cameraError != null) {
                                launchGalleryPicker()
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        onSecondary = { launchGalleryPicker() }
                    )
                }

                // Captured / Gallery Leaf Image Preview Confirmation
                if (scanFlowState is ScanFlowState.Preview) {
                    val previewImage = remember(scanFlowState.bitmap) {
                        scanFlowState.bitmap.asImageBitmap()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0E1612))
                    ) {
                        Image(
                            bitmap = previewImage,
                            contentDescription = strings.previewTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(14.dp),
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = strings.previewTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = strings.previewSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onDiscardPreview,
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 50.dp)
                                            .testTag("preview_retake_button"),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = strings.retakePhoto,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Button(
                                        onClick = onConfirmPreview,
                                        modifier = Modifier
                                            .weight(1.25f)
                                            .defaultMinSize(minHeight = 50.dp)
                                            .testTag("preview_analyze_button"),
                                        shape = RoundedCornerShape(14.dp),
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = strings.analyzeLeaf,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Processing Overlay
                if (scanFlowState is ScanFlowState.Processing) {
                    val stageText = when (scanFlowState.stageKey) {
                        "quality" -> strings.analyzingQuality
                        "explainability" -> strings.generatingExplanation
                        else -> strings.runningHybridModel
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.62f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 26.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3.dp
                                )
                                Text(
                                    text = stageText,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Dedicated Non-Leaf Warning Overlay (immediate feedback for camera & gallery)
                if (scanFlowState is ScanFlowState.NonLeafWarning) {
                    NonLeafWarningPanel(
                        previewBitmap = scanFlowState.bitmap,
                        badgeLabel = strings.nonLeafWarningBadge,
                        title = strings.nonLeafWarningTitle,
                        body = strings.nonLeafWarningBody,
                        tip = strings.nonLeafWarningTip,
                        captureLeafLabel = strings.nonLeafCaptureLeafAction,
                        galleryLeafLabel = strings.nonLeafChooseGalleryAction,
                        onCaptureLeaf = onDiscardPreview,
                        onPickGallery = {
                            onDiscardPreview()
                            launchGalleryPicker()
                        }
                    )
                }

                // Quality Rejection Overlay
                if (scanFlowState is ScanFlowState.QualityRejected) {
                    if (scanFlowState.report.isNonLeaf) {
                        NonLeafWarningPanel(
                            previewBitmap = null,
                            badgeLabel = strings.nonLeafWarningBadge,
                            title = strings.nonLeafWarningTitle,
                            body = strings.nonLeafWarningBody,
                            tip = strings.nonLeafWarningTip,
                            captureLeafLabel = strings.nonLeafCaptureLeafAction,
                            galleryLeafLabel = strings.nonLeafChooseGalleryAction,
                            onCaptureLeaf = onRetryScan,
                            onPickGallery = {
                                onRetryScan()
                                launchGalleryPicker()
                            }
                        )
                    } else {
                        QualityRejectionPanel(
                            title = strings.qualityRejectedTitle,
                            issues = scanFlowState.report.issues.map { it.localizedMessage(language) },
                            retakeLabel = strings.retakePhoto,
                            galleryLabel = strings.chooseAnotherImage,
                            onRetake = onRetryScan,
                            onPickGallery = {
                                onRetryScan()
                                launchGalleryPicker()
                            }
                        )
                    }
                }

                // Missing Production Model Assets Gate
                if (scanFlowState is ScanFlowState.ModelMissing) {
                    ModelMissingPanel(
                        title = strings.modelMissingTitle,
                        body = strings.modelMissingBody,
                        missingPaths = scanFlowState.healthStatus.missingAssets,
                        importLabel = strings.importTfliteModel,
                        isImportingModel = isImportingModel,
                        settingsLabel = strings.openSettingsForMock,
                        dismissLabel = strings.back,
                        onImportTflite = {
                            tflitePickerLauncher.launch(arrayOf("*/*"))
                        },
                        onOpenSettings = {
                            onRetryScan()
                            onOpenSettings()
                        },
                        onDismiss = onRetryScan
                    )
                }

                // Generic Error Overlay
                if (scanFlowState is ScanFlowState.Error) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = scanFlowState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = onRetryScan,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Text(text = strings.retakePhoto)
                                }
                            }
                        }
                    }
                }
            }

            // Modern Symmetrical 3-Control Camera Dock (Hidden while confirming preview or showing non-leaf warning)
            if (!isFullOverlayActive) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Gallery Picker Button
                        ScannerDockAction(
                            icon = Icons.Filled.PhotoLibrary,
                            label = strings.galleryScan,
                            enabled = scanFlowState !is ScanFlowState.Processing,
                            active = false,
                            testTag = "scanner_gallery_button",
                            onClick = { launchGalleryPicker() }
                        )

                        // Center: Iconic Botanical Circular Shutter Button
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(74.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (canCapture) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerLow
                                        }
                                    )
                                    .clickable(
                                        enabled = canCapture,
                                        role = Role.Button,
                                        onClick = {
                                            val capture = imageCapture ?: return@clickable
                                            if (isCapturingFrame) return@clickable
                                            isCapturingFrame = true
                                            val file = onCreateCaptureFile()
                                            val opts = ImageCapture.OutputFileOptions.Builder(file).build()
                                            capture.takePicture(
                                                opts,
                                                ContextCompat.getMainExecutor(context),
                                                object : ImageCapture.OnImageSavedCallback {
                                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                        isCapturingFrame = false
                                                        onImageCaptured(file)
                                                    }

                                                    override fun onError(exception: ImageCaptureException) {
                                                        isCapturingFrame = false
                                                        file.delete()
                                                        cameraError = exception.message ?: "Capture failed."
                                                    }
                                                }
                                            )
                                        }
                                    )
                                    .semantics { contentDescription = strings.captureLeaf }
                                    .testTag("scanner_capture_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    modifier = Modifier.size(60.dp),
                                    shape = CircleShape,
                                    color = if (canCapture) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isCapturingFrame) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.5.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.CameraAlt,
                                                contentDescription = null,
                                                tint = if (canCapture) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Right: Flash / Torch Toggle
                        ScannerDockAction(
                            icon = if (torchEnabled && hasFlash) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            label = if (torchEnabled && hasFlash) strings.torchOn else strings.torchOff,
                            enabled = hasFlash,
                            active = torchEnabled && hasFlash,
                            testTag = "scanner_torch_button",
                            onClick = {
                                val next = !torchEnabled
                                torchEnabled = next
                                boundCamera?.cameraControl?.enableTorch(next)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerDockAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    active: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(84.dp)
            .alpha(if (enabled) 1f else 0.45f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(50.dp)
                .testTag(testTag),
            shape = CircleShape,
            color = if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            border = if (active) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            } else {
                BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LeafCornerGuideOverlay(
    hint: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frameWidth = size.width * 0.72f
            val frameHeight = size.height * 0.56f
            val left = (size.width - frameWidth) / 2f
            val top = (size.height - frameHeight) / 2f
            val right = left + frameWidth
            val bottom = top + frameHeight
            val cornerLen = 30.dp.toPx()
            val strokePx = 3.5.dp.toPx()
            val bracketColor = Color.White.copy(alpha = 0.92f)

            // Top-left bracket
            drawLine(bracketColor, Offset(left, top + cornerLen), Offset(left, top), strokePx, StrokeCap.Round)
            drawLine(bracketColor, Offset(left, top), Offset(left + cornerLen, top), strokePx, StrokeCap.Round)

            // Top-right bracket
            drawLine(bracketColor, Offset(right - cornerLen, top), Offset(right, top), strokePx, StrokeCap.Round)
            drawLine(bracketColor, Offset(right, top), Offset(right, top + cornerLen), strokePx, StrokeCap.Round)

            // Bottom-left bracket
            drawLine(bracketColor, Offset(left, bottom - cornerLen), Offset(left, bottom), strokePx, StrokeCap.Round)
            drawLine(bracketColor, Offset(left, bottom), Offset(left + cornerLen, bottom), strokePx, StrokeCap.Round)

            // Bottom-right bracket
            drawLine(bracketColor, Offset(right - cornerLen, bottom), Offset(right, bottom), strokePx, StrokeCap.Round)
            drawLine(bracketColor, Offset(right, bottom), Offset(right, bottom - cornerLen), strokePx, StrokeCap.Round)
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            shape = RoundedCornerShape(999.dp),
            color = Color.Black.copy(alpha = 0.56f)
        ) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
            )
        }
    }
}

@Composable
private fun CameraFallbackCard(
    title: String,
    body: String,
    primaryLabel: String,
    secondaryLabel: String,
    showSecondary: Boolean,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                Button(
                    onClick = onPrimary,
                    shape = RoundedCornerShape(14.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 50.dp)
                        .testTag("camera_permission_primary_button")
                ) {
                    Text(text = primaryLabel)
                }
                if (showSecondary) {
                    OutlinedButton(
                        onClick = onSecondary,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("camera_permission_gallery_button")
                    ) {
                        Text(text = secondaryLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityRejectionPanel(
    title: String,
    issues: List<String>,
    retakeLabel: String,
    galleryLabel: String,
    onRetake: () -> Unit,
    onPickGallery: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.68f))
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                issues.forEach { msg ->
                    Text(
                        text = "• $msg",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onRetake,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = retakeLabel)
                    }
                    OutlinedButton(
                        onClick = onPickGallery,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(text = galleryLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun NonLeafWarningPanel(
    previewBitmap: Bitmap?,
    badgeLabel: String,
    title: String,
    body: String,
    tip: String,
    captureLeafLabel: String,
    galleryLeafLabel: String,
    onCaptureLeaf: () -> Unit,
    onPickGallery: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1612)),
        contentAlignment = Alignment.Center
    ) {
        if (previewBitmap != null) {
            val imageBitmap = remember(previewBitmap) { previewBitmap.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.38f)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.48f))
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("non_leaf_warning_card"),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = badgeLabel,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Eco,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(18.dp)
                            )
                            Text(
                                text = tip,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Button(
                        onClick = onCaptureLeaf,
                        shape = RoundedCornerShape(14.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 50.dp)
                            .testTag("non_leaf_retake_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = captureLeafLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onPickGallery,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("non_leaf_gallery_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = galleryLeafLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelMissingPanel(
    title: String,
    body: String,
    missingPaths: List<String>,
    importLabel: String,
    isImportingModel: Boolean,
    settingsLabel: String,
    dismissLabel: String,
    onImportTflite: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        missingPaths.take(6).forEach { path ->
                            Text(
                                text = "• $path",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Button(
                    onClick = onImportTflite,
                    enabled = !isImportingModel,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("model_missing_import_button")
                ) {
                    if (isImportingModel) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(text = importLabel)
                    }
                }
                FilledTonalButton(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("model_missing_open_settings_button")
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = settingsLabel)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(text = dismissLabel)
                }
            }
        }
    }
}
