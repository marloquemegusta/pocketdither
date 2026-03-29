package com.arquimea.dithercamera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ActivityNotFoundException
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Range
import android.util.Size
import android.widget.Toast
import androidx.camera.core.ZoomState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.arquimea.dithercamera.camera.BitmapStorage
import com.arquimea.dithercamera.camera.ColorProfile
import com.arquimea.dithercamera.camera.DitherPattern
import com.arquimea.dithercamera.camera.DitherPreset
import com.arquimea.dithercamera.camera.DitherPresets
import com.arquimea.dithercamera.camera.DitherProcessor
import com.arquimea.dithercamera.camera.DitherSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            alpha = 0.01f
        }
    }
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(
                CameraController.IMAGE_ANALYSIS or CameraController.IMAGE_CAPTURE,
            )
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pixelSize by rememberSaveable { mutableIntStateOf(6) }
    var contrast by rememberSaveable { mutableFloatStateOf(1.0f) }
    var detail by rememberSaveable { mutableFloatStateOf(1.0f) }
    var patternName by rememberSaveable { mutableStateOf(DitherPattern.BAYER_4X4.name) }
    var colorProfileName by rememberSaveable { mutableStateOf(ColorProfile.FULL_COLOR.name) }
    val pattern = remember(patternName) { DitherPattern.valueOf(patternName) }
    val colorProfile = remember(colorProfileName) { ColorProfile.valueOf(colorProfileName) }
    var exposureIndex by rememberSaveable { mutableIntStateOf(0) }
    var exposureRange by remember { mutableStateOf(Range(0, 0)) }
    var isCapturing by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    var focusPoint by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var lastSavedUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val lastSavedUri = remember(lastSavedUriString) { lastSavedUriString?.let(Uri::parse) }
    var previewViewportSize by remember { mutableStateOf<Size?>(null) }
    var activeControlName by rememberSaveable { mutableStateOf(CameraControlStripMode.PRESETS.name) }
    val activeControl = remember(activeControlName) { CameraControlStripMode.valueOf(activeControlName) }
    var selectedPresetLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var showInfoDialog by rememberSaveable { mutableStateOf(false) }
    var isExposureSupported by remember { mutableStateOf(false) }
    var hasFlashUnit by remember { mutableStateOf(false) }
    var minZoomRatio by remember { mutableFloatStateOf(1.0f) }
    var maxZoomRatio by remember { mutableFloatStateOf(1.0f) }
    var selectedZoomRatio by rememberSaveable { mutableFloatStateOf(1.0f) }
    var flashModeName by rememberSaveable { mutableStateOf(FlashModeOption.OFF.name) }
    val flashMode = remember(flashModeName) { FlashModeOption.valueOf(flashModeName) }

    val settings by rememberUpdatedState(
        DitherSettings(
            pixelSize = pixelSize,
            pattern = pattern,
            contrast = contrast,
            colorProfile = colorProfile,
            detail = detail,
        ),
    )
    val processingOutputSize by rememberUpdatedState(calculateProcessingOutputSize(previewViewportSize, detail))

    fun applyPreset(preset: DitherPreset) {
        pixelSize = preset.settings.pixelSize
        patternName = preset.settings.pattern.name
        contrast = preset.settings.contrast
        colorProfileName = preset.settings.colorProfile.name
        detail = preset.settings.detail
        selectedPresetLabel = preset.label
    }

    fun applyFlashMode(mode: FlashModeOption) {
        if (!hasFlashUnit) return
        when (mode) {
            FlashModeOption.OFF -> {
                cameraController.imageCaptureFlashMode = ImageCapture.FLASH_MODE_OFF
                cameraController.cameraControl?.enableTorch(false)
            }

            FlashModeOption.ON_CAPTURE -> {
                cameraController.cameraControl?.enableTorch(false)
                cameraController.imageCaptureFlashMode = ImageCapture.FLASH_MODE_ON
            }

            FlashModeOption.ALWAYS_ON -> {
                cameraController.imageCaptureFlashMode = ImageCapture.FLASH_MODE_OFF
                cameraController.cameraControl?.enableTorch(true)
            }
        }
    }

    fun refreshCameraState() {
        val exposureState = cameraController.cameraInfo?.exposureState
        isExposureSupported = exposureState?.isExposureCompensationSupported == true
        exposureRange = exposureState?.exposureCompensationRange ?: Range(0, 0)
        hasFlashUnit = cameraController.cameraInfo?.hasFlashUnit() == true
        val zoomState = cameraController.cameraInfo?.zoomState?.value
        minZoomRatio = zoomState?.minZoomRatio ?: 1.0f
        maxZoomRatio = zoomState?.maxZoomRatio ?: 1.0f
        if (selectedZoomRatio == 1.0f && zoomState != null) {
            selectedZoomRatio = zoomState.zoomRatio
        }
    }

    fun restoreCameraState() {
        refreshCameraState()
        val restoredZoom = selectedZoomRatio.coerceIn(minZoomRatio, maxZoomRatio)
        cameraController.cameraControl?.setZoomRatio(restoredZoom)
        selectedZoomRatio = restoredZoom
        if (isExposureSupported) {
            val restoredExposure = exposureIndex.coerceIn(exposureRange.lower, exposureRange.upper)
            cameraController.cameraControl?.setExposureCompensationIndex(restoredExposure)
            exposureIndex = restoredExposure
        } else {
            exposureIndex = 0
        }
        applyFlashMode(flashMode)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            Toast.makeText(
                context,
                "Necesitamos permiso de cámara para la preview en tiempo real.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        lastSavedUriString = BitmapStorage.findLatestSavedImageUri(context)?.toString()
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(hasPermission, lifecycleOwner) {
        if (!hasPermission) return@LaunchedEffect
        previewView.controller = cameraController
        cameraController.bindToLifecycle(lifecycleOwner)
        refreshCameraState()
        cameraController.initializationFuture.addListener(
            { restoreCameraState() },
            ContextCompat.getMainExecutor(context),
        )
        cameraController.setImageAnalysisAnalyzer(analyzerExecutor) { image ->
            val processed = runCatching { DitherProcessor.process(image, settings, processingOutputSize) }
                .onFailure { image.close() }
                .getOrNull()
            if (processed != null) {
                scope.launch(Dispatchers.Main.immediate) {
                    previewBitmap = processed
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            analyzerExecutor.shutdown()
        }
    }

    DisposableEffect(lifecycleOwner, hasPermission, selectedZoomRatio, exposureIndex, flashModeName) {
        if (!hasPermission) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    restoreCameraState()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF04080D)),
    ) {
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                CameraPreviewPane(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                        .padding(start = 10.dp, top = 10.dp, bottom = 10.dp),
                    previewView = previewView,
                    previewBitmap = previewBitmap,
                    hasPermission = hasPermission,
                    focusPoint = focusPoint,
                    density = density,
                    onFocus = { x, y ->
                        focusPoint = x to y
                        val meteringPoint = previewView.meteringPointFactory.createPoint(x, y)
                        val action = FocusMeteringAction.Builder(
                            meteringPoint,
                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
                        )
                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                            .build()
                        cameraController.cameraControl?.startFocusAndMetering(action)
                    },
                    onSizeChanged = {
                        previewViewportSize = Size(it.width, it.height)
                        if (focusPoint == null) {
                            focusPoint = it.width / 2f to it.height / 2f
                        }
                    },
                    onShowInfo = { showInfoDialog = true },
                    minZoomRatio = minZoomRatio,
                    maxZoomRatio = maxZoomRatio,
                    selectedZoomRatio = selectedZoomRatio,
                    onZoomSelected = { zoom ->
                        cameraController.cameraControl?.setZoomRatio(zoom)
                        selectedZoomRatio = zoom
                    },
                    hasFlashUnit = hasFlashUnit,
                    flashMode = flashMode,
                    onFlashModeSelected = {
                        flashModeName = it.name
                        applyFlashMode(it)
                    },
                    contrast = contrast,
                    onContrastChange = {
                        contrast = it
                        selectedPresetLabel = null
                    },
                    exposureIndex = exposureIndex,
                    exposureRange = exposureRange,
                    isExposureSupported = isExposureSupported,
                    onExposureChange = {
                        updateExposure(cameraController, exposureRange, it) { updated ->
                            exposureIndex = updated
                        }
                        selectedPresetLabel = null
                    },
                )

                CameraControlsPane(
                    modifier = Modifier
                        .width(340.dp)
                        .fillMaxSize()
                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
                    activeControl = activeControl,
                    onControlSelected = { activeControlName = it.name },
                    selectedPresetLabel = selectedPresetLabel,
                    pattern = pattern,
                    colorProfile = colorProfile,
                    pixelSize = pixelSize,
                    detail = detail,
                    lastSavedUri = lastSavedUri,
                    isCapturing = isCapturing,
                    hasPermission = hasPermission,
                    onApplyPreset = { applyPreset(it) },
                    onPatternSelected = {
                        patternName = it.name
                        selectedPresetLabel = null
                    },
                    onPaletteSelected = {
                        colorProfileName = it.name
                        selectedPresetLabel = null
                    },
                    onPixelChange = {
                        pixelSize = it
                        selectedPresetLabel = null
                    },
                    onDetailChange = {
                        detail = it
                        selectedPresetLabel = null
                    },
                    onOpenGallery = { openSavedPhotosShortcut(context, lastSavedUri) },
                    onCapture = {
                        if (isCapturing) return@CameraControlsPane
                        isCapturing = true
                        cameraController.takePicture(
                            analyzerExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val processed = DitherProcessor.process(
                                        image = image,
                                        settings = settings,
                                        targetSize = processingOutputSize,
                                    )
                                    scope.launch(Dispatchers.IO) {
                                        val savedUri = BitmapStorage.save(context, processed)
                                        scope.launch(Dispatchers.Main) {
                                            isCapturing = false
                                            lastSavedUriString = savedUri?.toString()
                                            showSavedMessage(context, savedUri)
                                        }
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    scope.launch(Dispatchers.Main) {
                                        isCapturing = false
                                        Toast.makeText(
                                            context,
                                            "Error al capturar: ${exception.message ?: "desconocido"}",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                            },
                        )
                    },
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                CameraPreviewPane(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    previewView = previewView,
                    previewBitmap = previewBitmap,
                    hasPermission = hasPermission,
                    focusPoint = focusPoint,
                    density = density,
                    onFocus = { x, y ->
                        focusPoint = x to y
                        val meteringPoint = previewView.meteringPointFactory.createPoint(x, y)
                        val action = FocusMeteringAction.Builder(
                            meteringPoint,
                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
                        )
                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                            .build()
                        cameraController.cameraControl?.startFocusAndMetering(action)
                    },
                    onSizeChanged = {
                        previewViewportSize = Size(it.width, it.height)
                        if (focusPoint == null) {
                            focusPoint = it.width / 2f to it.height / 2f
                        }
                    },
                    onShowInfo = { showInfoDialog = true },
                    minZoomRatio = minZoomRatio,
                    maxZoomRatio = maxZoomRatio,
                    selectedZoomRatio = selectedZoomRatio,
                    onZoomSelected = { zoom ->
                        cameraController.cameraControl?.setZoomRatio(zoom)
                        selectedZoomRatio = zoom
                    },
                    hasFlashUnit = hasFlashUnit,
                    flashMode = flashMode,
                    onFlashModeSelected = {
                        flashModeName = it.name
                        applyFlashMode(it)
                    },
                    contrast = contrast,
                    onContrastChange = {
                        contrast = it
                        selectedPresetLabel = null
                    },
                    exposureIndex = exposureIndex,
                    exposureRange = exposureRange,
                    isExposureSupported = isExposureSupported,
                    onExposureChange = {
                        updateExposure(cameraController, exposureRange, it) { updated ->
                            exposureIndex = updated
                        }
                        selectedPresetLabel = null
                    },
                )

                CameraControlsPane(
                    modifier = Modifier.fillMaxWidth(),
                    activeControl = activeControl,
                    onControlSelected = { activeControlName = it.name },
                    selectedPresetLabel = selectedPresetLabel,
                    pattern = pattern,
                    colorProfile = colorProfile,
                    pixelSize = pixelSize,
                    detail = detail,
                    lastSavedUri = lastSavedUri,
                    isCapturing = isCapturing,
                    hasPermission = hasPermission,
                    onApplyPreset = { applyPreset(it) },
                    onPatternSelected = {
                        patternName = it.name
                        selectedPresetLabel = null
                    },
                    onPaletteSelected = {
                        colorProfileName = it.name
                        selectedPresetLabel = null
                    },
                    onPixelChange = {
                        pixelSize = it
                        selectedPresetLabel = null
                    },
                    onDetailChange = {
                        detail = it
                        selectedPresetLabel = null
                    },
                    onOpenGallery = { openSavedPhotosShortcut(context, lastSavedUri) },
                    onCapture = {
                        if (isCapturing) return@CameraControlsPane
                        isCapturing = true
                        cameraController.takePicture(
                            analyzerExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val processed = DitherProcessor.process(
                                        image = image,
                                        settings = settings,
                                        targetSize = processingOutputSize,
                                    )
                                    scope.launch(Dispatchers.IO) {
                                        val savedUri = BitmapStorage.save(context, processed)
                                        scope.launch(Dispatchers.Main) {
                                            isCapturing = false
                                            lastSavedUriString = savedUri?.toString()
                                            showSavedMessage(context, savedUri)
                                        }
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    scope.launch(Dispatchers.Main) {
                                        isCapturing = false
                                        Toast.makeText(
                                            context,
                                            "Error al capturar: ${exception.message ?: "desconocido"}",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                            },
                        )
                    },
                )
            }
        }

        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text("Cerrar")
                    }
                },
                title = { Text("Que hace cada parametro") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        InfoLine("Preset", "Aplica un look completo: paleta, patron, pixel, detalle y contraste.")
                        InfoLine("Paleta", "Cambia solo los colores usados en la imagen final.")
                        InfoLine("Patron", "Cambia la trama de dither. Afecta al grano y al reparto de puntos.")
                        InfoLine("Pixel", "Hace mas grandes o pequenos los bloques visibles del efecto.")
                        InfoLine("Detalle", "Cambia la resolucion interna del procesado. Menos detalle da un look mas retro; mas detalle conserva mas informacion.")
                        InfoLine("Contraste", "Se ajusta con la barra izquierda del reticulo al tocar para enfocar.")
                        InfoLine("Exposicion", "Se ajusta con la barra derecha del reticulo. Aclara u oscurece antes del procesado.")
                        InfoLine("Zoom", "La barra inferior usa el rango real del movil, en vez de valores fijos inventados.")
                        InfoLine("Flash", "Arriba a la derecha puedes elegir apagado, flash al disparar o luz continua.")
                    }
                },
            )
        }
    }
}

@Composable
private fun CameraPreviewPane(
    modifier: Modifier,
    previewView: PreviewView,
    previewBitmap: Bitmap?,
    hasPermission: Boolean,
    focusPoint: Pair<Float, Float>?,
    density: Density,
    onFocus: (Float, Float) -> Unit,
    onSizeChanged: (androidx.compose.ui.unit.IntSize) -> Unit,
    onShowInfo: () -> Unit,
    minZoomRatio: Float,
    maxZoomRatio: Float,
    selectedZoomRatio: Float,
    onZoomSelected: (Float) -> Unit,
    hasFlashUnit: Boolean,
    flashMode: FlashModeOption,
    onFlashModeSelected: (FlashModeOption) -> Unit,
    contrast: Float,
    onContrastChange: (Float) -> Unit,
    exposureIndex: Int,
    exposureRange: Range<Int>,
    isExposureSupported: Boolean,
    onExposureChange: (Int) -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black)
            .onSizeChanged(onSizeChanged),
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        previewBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Preview procesada",
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(hasPermission) {
                    detectTapGestures { offset ->
                        if (!hasPermission) return@detectTapGestures
                        onFocus(offset.x, offset.y)
                    }
                },
        )

        FocusAssistOverlay(
            focusPoint = focusPoint,
            density = density,
            contrast = contrast,
            onContrastChange = onContrastChange,
            exposureIndex = exposureIndex,
            exposureRange = exposureRange,
            isExposureSupported = isExposureSupported,
            onExposureChange = onExposureChange,
        )

        Surface(
            color = Color(0x6608111A),
            tonalElevation = 0.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "PocketDither",
                    color = Color(0xFFE9F0EA),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Tap to focus",
                    color = Color(0xFFB7C7BD),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = onShowInfo,
                    modifier = Modifier.height(30.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = "? Ayuda",
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }

        if (hasFlashUnit) {
            Surface(
                color = Color(0x6608111A),
                tonalElevation = 0.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(22.dp)),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    FlashModeOption.entries.forEach { entry ->
                        FilterChip(
                            selected = flashMode == entry,
                            onClick = { onFlashModeSelected(entry) },
                            label = {
                                Text(
                                    text = entry.shortLabel,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            },
                        )
                    }
                }
            }
        }

        Surface(
            color = Color(0x6608111A),
            tonalElevation = 0.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(22.dp)),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Zoom",
                        color = Color(0xFFB7C7BD),
                        fontSize = 11.sp,
                    )
                    Text(
                        text = formatZoomLabel(selectedZoomRatio),
                        color = Color(0xFFE9F0EA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Slider(
                    value = selectedZoomRatio.coerceIn(minZoomRatio, maxZoomRatio),
                    onValueChange = onZoomSelected,
                    valueRange = minZoomRatio..maxZoomRatio,
                    modifier = Modifier.width(220.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = formatZoomLabel(minZoomRatio),
                        color = Color(0xFF8FA198),
                        fontSize = 10.sp,
                    )
                    Text(
                        text = formatZoomLabel(maxZoomRatio),
                        color = Color(0xFF8FA198),
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusAssistOverlay(
    focusPoint: Pair<Float, Float>?,
    density: Density,
    contrast: Float,
    onContrastChange: (Float) -> Unit,
    exposureIndex: Int,
    exposureRange: Range<Int>,
    isExposureSupported: Boolean,
    onExposureChange: (Int) -> Unit,
) {
    focusPoint ?: return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val (x, y) = focusPoint
        val maxWidthPx = constraints.maxWidth.toFloat()
        val maxHeightPx = constraints.maxHeight.toFloat()
        val ringSizePx = with(density) { 48.dp.toPx() }
        val sliderWidthPx = with(density) { 46.dp.toPx() }
        val sliderHeightPx = with(density) { 170.dp.toPx() }
        val gapPx = with(density) { 14.dp.toPx() }
        val edgePaddingPx = with(density) { 10.dp.toPx() }

        val ringOffsetX = (x - ringSizePx / 2f).coerceIn(edgePaddingPx, maxWidthPx - ringSizePx - edgePaddingPx)
        val ringOffsetY = (y - ringSizePx / 2f).coerceIn(edgePaddingPx, maxHeightPx - ringSizePx - edgePaddingPx)
        val sliderOffsetY = (y - sliderHeightPx / 2f).coerceIn(edgePaddingPx, maxHeightPx - sliderHeightPx - edgePaddingPx)
        val leftSliderX = (ringOffsetX - sliderWidthPx - gapPx).coerceIn(edgePaddingPx, maxWidthPx - sliderWidthPx - edgePaddingPx)
        val rightSliderX = (ringOffsetX + ringSizePx + gapPx).coerceIn(edgePaddingPx, maxWidthPx - sliderWidthPx - edgePaddingPx)

        Box(
            modifier = Modifier
                .offset { IntOffset(ringOffsetX.roundToInt(), ringOffsetY.roundToInt()) }
                .size(48.dp)
                .border(2.dp, Color(0xFFF4A261), CircleShape),
        )

        FocusSideSlider(
            modifier = Modifier.offset { IntOffset(leftSliderX.roundToInt(), sliderOffsetY.roundToInt()) },
            label = "CON",
            valueLabel = "%.1f".format(contrast),
            value = contrast,
            range = 0.6f..1.8f,
            steps = 11,
            accent = Color(0xFFDCC7A1),
            onValueChange = { onContrastChange(it.coerceIn(0.6f, 1.8f)) },
        )

        if (isExposureSupported) {
            FocusSideSlider(
                modifier = Modifier.offset { IntOffset(rightSliderX.roundToInt(), sliderOffsetY.roundToInt()) },
                label = "EXP",
                valueLabel = exposureIndex.toString(),
                value = exposureIndex.toFloat(),
                range = exposureRange.lower.toFloat()..exposureRange.upper.toFloat(),
                steps = (exposureRange.upper - exposureRange.lower - 1).coerceAtLeast(0),
                accent = Color(0xFFF4A261),
                onValueChange = { onExposureChange(it.roundToInt()) },
            )
        }
    }
}

@Composable
private fun FocusSideSlider(
    modifier: Modifier,
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    accent: Color,
    onValueChange: (Float) -> Unit,
) {
    Surface(
        color = Color(0x6608111A),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
        ) {
            Text(
                text = label,
                color = Color(0xFFE9F0EA),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(36.dp)
                    .height(130.dp),
            ) {
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = range,
                    steps = steps,
                    modifier = Modifier
                        .width(130.dp)
                        .rotate(-90f),
                )
            }
            Text(
                text = valueLabel,
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun CameraControlsPane(
    modifier: Modifier,
    activeControl: CameraControlStripMode,
    onControlSelected: (CameraControlStripMode) -> Unit,
    selectedPresetLabel: String?,
    pattern: DitherPattern,
    colorProfile: ColorProfile,
    pixelSize: Int,
    detail: Float,
    lastSavedUri: Uri?,
    isCapturing: Boolean,
    hasPermission: Boolean,
    onApplyPreset: (DitherPreset) -> Unit,
    onPatternSelected: (DitherPattern) -> Unit,
    onPaletteSelected: (ColorProfile) -> Unit,
    onPixelChange: (Int) -> Unit,
    onDetailChange: (Float) -> Unit,
    onOpenGallery: () -> Unit,
    onCapture: () -> Unit,
) {
    Surface(
        color = Color(0xFF08111A),
        modifier = modifier.wrapContentHeight(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CameraControlStripMode.entries) { control ->
                    FilterChip(
                        selected = control == activeControl,
                        onClick = { onControlSelected(control) },
                        label = {
                            Text(
                                text = control.shortLabel,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                    )
                }
            }

            when (activeControl) {
                CameraControlStripMode.PRESETS -> {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(DitherPresets.presets) { preset ->
                            FilterChip(
                                selected = preset.label == selectedPresetLabel,
                                onClick = { onApplyPreset(preset) },
                                label = {
                                    Text(
                                        text = preset.label,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }

                CameraControlStripMode.PATTERN -> {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(DitherPattern.entries) { entry ->
                            FilterChip(
                                selected = entry == pattern,
                                onClick = { onPatternSelected(entry) },
                                label = {
                                    Text(
                                        text = entry.label,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }

                CameraControlStripMode.PIXEL -> {
                    CompactSliderRow(
                        label = "Pixel",
                        valueLabel = "${pixelSize}px",
                        value = pixelSize.toFloat(),
                        range = 2f..16f,
                        steps = 13,
                        onValueChange = { onPixelChange(it.roundToInt().coerceIn(2, 16)) },
                        onDecrease = { onPixelChange((pixelSize - 1).coerceAtLeast(2)) },
                        onIncrease = { onPixelChange((pixelSize + 1).coerceAtMost(16)) },
                        decreaseEnabled = pixelSize > 2,
                        increaseEnabled = pixelSize < 16,
                    )
                }

                CameraControlStripMode.DETAIL -> {
                    CompactSliderRow(
                        label = "Detalle",
                        valueLabel = "%.2f".format(detail),
                        value = detail,
                        range = 0.35f..1.20f,
                        steps = 16,
                        onValueChange = { onDetailChange(it.coerceIn(0.35f, 1.20f)) },
                        onDecrease = { onDetailChange((detail - 0.05f).coerceAtLeast(0.35f)) },
                        onIncrease = { onDetailChange((detail + 0.05f).coerceAtMost(1.20f)) },
                        decreaseEnabled = detail > 0.35f,
                        increaseEnabled = detail < 1.20f,
                    )
                }

                CameraControlStripMode.PALETTE -> {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ColorProfile.entries) { entry ->
                            FilterChip(
                                selected = entry == colorProfile,
                                onClick = { onPaletteSelected(entry) },
                                label = {
                                    Text(
                                        text = entry.label,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }

            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    GalleryShortcutButton(
                        lastSavedUri = lastSavedUri,
                        onClick = onOpenGallery,
                    )
                }

                ShutterButton(
                    capturing = isCapturing,
                    enabled = hasPermission && !isCapturing,
                    onClick = onCapture,
                )

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Surface(
                        color = Color(0x22000000),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            text = selectedPresetLabel ?: colorProfile.label,
                            color = Color(0xFFB7C7BD),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseEnabled: Boolean = true,
    increaseEnabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            color = Color(0xFFE9F0EA),
            fontSize = 12.sp,
            modifier = Modifier.width(56.dp),
            maxLines = 1,
            softWrap = false,
        )
        OutlinedButton(
            onClick = onDecrease,
            enabled = decreaseEnabled,
            modifier = Modifier.size(36.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(text = "-", fontSize = 14.sp)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            onClick = onIncrease,
            enabled = increaseEnabled,
            modifier = Modifier.size(36.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(text = "+", fontSize = 14.sp)
        }
        Text(
            text = valueLabel,
            color = Color(0xFFB7C7BD),
            fontSize = 12.sp,
            modifier = Modifier.width(52.dp),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun InfoLine(
    title: String,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            color = Color(0xFFE9F0EA),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
        Text(
            text = description,
            color = Color(0xFFB7C7BD),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun GalleryShortcutButton(
    lastSavedUri: Uri?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(initialValue = null, key1 = lastSavedUri) {
        value = if (lastSavedUri == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.loadThumbnail(lastSavedUri, Size(160, 160), null)
                }.getOrNull()
            }
        }
    }

    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(56.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail!!.asImageBitmap(),
                contentDescription = "Fotos guardadas",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = "Fotos",
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun ShutterButton(
    capturing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFF4A261),
            contentColor = Color(0xFF08111A),
        ),
        modifier = Modifier
            .width(148.dp)
            .height(58.dp),
        shape = RoundedCornerShape(28.dp),
    ) {
        Text(
            text = if (capturing) "Procesando..." else "Capturar",
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private enum class CameraControlStripMode(val shortLabel: String) {
    PRESETS("Presets"),
    PALETTE("Paleta"),
    PATTERN("Patron"),
    PIXEL("Pixel"),
    DETAIL("Detalle"),
}

private enum class FlashModeOption(val shortLabel: String) {
    OFF("Off"),
    ON_CAPTURE("Flash"),
    ALWAYS_ON("On"),
}

private fun formatZoomLabel(
    zoom: Float,
): String {
    val rounded = (zoom * 10f).roundToInt() / 10f
    return if (rounded % 1f == 0f) "${rounded.toInt()}x" else "${rounded}x"
}

private fun updateExposure(
    controller: LifecycleCameraController,
    range: Range<Int>,
    requested: Int,
    onUpdated: (Int) -> Unit,
) {
    val newValue = requested.coerceIn(range.lower, range.upper)
    controller.cameraControl?.setExposureCompensationIndex(newValue)
    onUpdated(newValue)
}

private fun showSavedMessage(context: Context, savedUri: Uri?) {
    val message = if (savedUri != null) {
        "Foto guardada en galería."
    } else {
        "No se pudo guardar la foto procesada."
    }
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

private fun openSavedPhotosShortcut(context: Context, lastSavedUri: Uri?) {
    val photosPackage = "com.google.android.apps.photos"

    if (lastSavedUri != null) {
        val photosImageIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(lastSavedUri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage(photosPackage)
        }
        if (tryStartActivity(context, photosImageIntent)) {
            return
        }
    }

    val folderUri = Uri.parse(
        "content://com.android.externalstorage.documents/document/primary%3APictures%2FDitherCamera",
    )
    val folderIntent = Intent(Intent.ACTION_VIEW).apply {
        data = folderUri
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        setPackage("com.google.android.documentsui")
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri)
    }

    if (tryStartActivity(context, folderIntent)) {
        return
    }

    val photosLaunchIntent = context.packageManager.getLaunchIntentForPackage(photosPackage)
    if (photosLaunchIntent != null && tryStartActivity(context, photosLaunchIntent)) {
        return
    }

    val intent = if (lastSavedUri != null) {
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(lastSavedUri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
        }
    }

    if (tryStartActivity(context, intent)) {
        return
    } else {
        Toast.makeText(context, "No hay una app de galeria disponible.", Toast.LENGTH_LONG).show()
    }
}

private fun tryStartActivity(
    context: Context,
    intent: Intent,
): Boolean {
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

private fun calculateProcessingOutputSize(
    viewportSize: Size?,
    detail: Float,
): Size? {
    viewportSize ?: return null
    val width = viewportSize.width.toFloat()
    val height = viewportSize.height.toFloat()
    val longEdge = maxOf(width, height)
    if (longEdge <= 0f) return null

    val targetLongEdge = 960f * detail.coerceIn(0.35f, 1.20f)
    val scale = minOf(1f, targetLongEdge / longEdge)
    val scaledWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val scaledHeight = (height * scale).roundToInt().coerceAtLeast(1)
    return Size(scaledWidth, scaledHeight)
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED
