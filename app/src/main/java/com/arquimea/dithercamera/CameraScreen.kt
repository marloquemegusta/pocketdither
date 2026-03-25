package com.arquimea.dithercamera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ActivityNotFoundException
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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
    var pixelSize by remember { mutableIntStateOf(6) }
    var contrast by remember { mutableFloatStateOf(1.0f) }
    var detail by remember { mutableFloatStateOf(1.0f) }
    var pattern by remember { mutableStateOf(DitherPattern.BAYER_4X4) }
    var colorProfile by remember { mutableStateOf(ColorProfile.FULL_COLOR) }
    var exposureIndex by remember { mutableIntStateOf(0) }
    var exposureRange by remember { mutableStateOf(Range(0, 0)) }
    var isCapturing by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    var focusPoint by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var lastSavedUri by remember { mutableStateOf<Uri?>(null) }
    var previewViewportSize by remember { mutableStateOf<Size?>(null) }
    var activeControl by remember { mutableStateOf(CameraControlStripMode.PRESETS) }
    var selectedPresetLabel by remember { mutableStateOf<String?>(null) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var isExposureSupported by remember { mutableStateOf(false) }
    var minZoomRatio by remember { mutableFloatStateOf(1.0f) }
    var maxZoomRatio by remember { mutableFloatStateOf(1.0f) }
    var selectedZoomRatio by remember { mutableFloatStateOf(1.0f) }

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
        pattern = preset.settings.pattern
        contrast = preset.settings.contrast
        colorProfile = preset.settings.colorProfile
        detail = preset.settings.detail
        selectedPresetLabel = preset.label
    }

    fun refreshExposureState() {
        val exposureState = cameraController.cameraInfo?.exposureState
        isExposureSupported = exposureState?.isExposureCompensationSupported == true
        exposureRange = exposureState?.exposureCompensationRange ?: Range(0, 0)
        exposureIndex = exposureState?.exposureCompensationIndex ?: 0
        val zoomState = cameraController.cameraInfo?.zoomState?.value
        minZoomRatio = zoomState?.minZoomRatio ?: 1.0f
        maxZoomRatio = zoomState?.maxZoomRatio ?: 1.0f
        selectedZoomRatio = zoomState?.zoomRatio ?: 1.0f
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
        lastSavedUri = BitmapStorage.findLatestSavedImageUri(context)
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(hasPermission, lifecycleOwner) {
        if (!hasPermission) return@LaunchedEffect
        previewView.controller = cameraController
        cameraController.bindToLifecycle(lifecycleOwner)
        refreshExposureState()
        cameraController.initializationFuture.addListener(
            { refreshExposureState() },
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF04080D)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .onSizeChanged { previewViewportSize = Size(it.width, it.height) }
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.Black),
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
                                focusPoint = offset.x to offset.y
                                val meteringPoint =
                                    previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                                val action = FocusMeteringAction.Builder(
                                    meteringPoint,
                                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
                                )
                                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                    .build()
                                cameraController.cameraControl?.startFocusAndMetering(action)
                            }
                        },
                )

                focusPoint?.let { (x, y) ->
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (x - with(density) { 24.dp.toPx() }).roundToInt(),
                                    (y - with(density) { 24.dp.toPx() }).roundToInt(),
                                )
                            }
                            .size(48.dp)
                            .border(2.dp, Color(0xFFF4A261), CircleShape),
                    )
                }

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
                            onClick = { showInfoDialog = true },
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

                val zoomOptions = remember(minZoomRatio, maxZoomRatio) {
                    buildZoomOptions(minZoomRatio, maxZoomRatio)
                }
                if (zoomOptions.size > 1) {
                    Surface(
                        color = Color(0x6608111A),
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(22.dp)),
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            items(zoomOptions) { zoom ->
                                FilterChip(
                                    selected = isZoomSelected(selectedZoomRatio, zoom),
                                    onClick = {
                                        cameraController.cameraControl?.setZoomRatio(zoom)
                                        selectedZoomRatio = zoom
                                    },
                                    label = {
                                        Text(
                                            text = formatZoomLabel(zoom),
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
            }

            Surface(
                color = Color(0xFF08111A),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
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
                                onClick = { activeControl = control },
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
                                        onClick = { applyPreset(preset) },
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
                                        onClick = {
                                            pattern = entry
                                            selectedPresetLabel = null
                                        },
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
                                onValueChange = {
                                    pixelSize = it.roundToInt().coerceIn(2, 16)
                                    selectedPresetLabel = null
                                },
                            )
                        }

                        CameraControlStripMode.DETAIL -> {
                            CompactSliderRow(
                                label = "Detalle",
                                valueLabel = "%.2f".format(detail),
                                value = detail,
                                range = 0.35f..1.20f,
                                onValueChange = {
                                    detail = it.coerceIn(0.35f, 1.20f)
                                    selectedPresetLabel = null
                                },
                            )
                        }

                        CameraControlStripMode.CONTRAST -> {
                            CompactSliderRow(
                                label = "Contraste",
                                valueLabel = "%.2f".format(contrast),
                                value = contrast,
                                range = 0.6f..1.8f,
                                onValueChange = {
                                    contrast = it.coerceIn(0.6f, 1.8f)
                                    selectedPresetLabel = null
                                },
                            )
                        }

                        CameraControlStripMode.PALETTE -> {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(ColorProfile.entries) { entry ->
                                    FilterChip(
                                        selected = entry == colorProfile,
                                        onClick = {
                                            colorProfile = entry
                                            selectedPresetLabel = null
                                        },
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

                        CameraControlStripMode.EXPOSURE -> {
                            if (isExposureSupported) {
                                ExposureControlRow(
                                    exposureIndex = exposureIndex,
                                    exposureRange = exposureRange,
                                    onDecrease = {
                                        updateExposure(cameraController, exposureRange, exposureIndex - 1) {
                                            exposureIndex = it
                                        }
                                        selectedPresetLabel = null
                                    },
                                    onIncrease = {
                                        updateExposure(cameraController, exposureRange, exposureIndex + 1) {
                                            exposureIndex = it
                                        }
                                        selectedPresetLabel = null
                                    },
                                )
                            } else {
                                Text(
                                    text = "La camara aun no ha expuesto controles manuales.",
                                    color = Color(0xFFB7C7BD),
                                    fontSize = 12.sp,
                                )
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
                                onClick = { openSavedPhotosShortcut(context, lastSavedUri) },
                            )
                        }

                        ShutterButton(
                            capturing = isCapturing,
                            enabled = hasPermission && !isCapturing,
                            onClick = {
                                if (isCapturing) return@ShutterButton
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
                                                    lastSavedUri = savedUri
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
                        InfoLine("Contraste", "Empuja sombras y luces antes del dither.")
                        InfoLine("Exposicion", "Aclara u oscurece la camara antes del procesado.")
                        InfoLine("Zoom", "Usa el zoom del sistema y el movil cambia de lente automaticamente cuando corresponde.")
                    }
                },
            )
        }
    }
}

@Composable
private fun CompactSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
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
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valueLabel,
            color = Color(0xFFB7C7BD),
            fontSize = 12.sp,
            modifier = Modifier.width(46.dp),
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
private fun ExposureControlRow(
    exposureIndex: Int,
    exposureRange: Range<Int>,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Exp",
            color = Color(0xFFE9F0EA),
            fontSize = 12.sp,
            modifier = Modifier.width(40.dp),
        )
        OutlinedButton(
            onClick = onDecrease,
            enabled = exposureIndex > exposureRange.lower,
            modifier = Modifier.height(34.dp),
        ) {
            Text("-")
        }
        Text(
            text = exposureIndex.toString(),
            color = Color(0xFFB7C7BD),
            fontSize = 12.sp,
            modifier = Modifier.width(24.dp),
        )
        OutlinedButton(
            onClick = onIncrease,
            enabled = exposureIndex < exposureRange.upper,
            modifier = Modifier.height(34.dp),
        ) {
            Text("+")
        }
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
    CONTRAST("Contraste"),
    EXPOSURE("Exposicion"),
}

private fun buildZoomOptions(
    minZoomRatio: Float,
    maxZoomRatio: Float,
): List<Float> {
    val candidates = listOf(0.5f, 1.0f, 2.0f, 5.0f)
    return candidates
        .filter { it in minZoomRatio..maxZoomRatio }
        .ifEmpty { listOf(minZoomRatio.coerceAtLeast(1.0f)) }
}

private fun isZoomSelected(
    current: Float,
    candidate: Float,
): Boolean = kotlin.math.abs(current - candidate) < 0.08f

private fun formatZoomLabel(
    zoom: Float,
): String = if (zoom % 1f == 0f) "${zoom.toInt()}x" else "${zoom}x"

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
