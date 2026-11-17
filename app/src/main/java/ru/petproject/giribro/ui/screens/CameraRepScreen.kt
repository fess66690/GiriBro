package ru.petproject.giribro.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.petproject.giribro.GiriBroApp
import ru.petproject.giribro.data.SetCaptureMode
import ru.petproject.giribro.vision.FixationPreset
import ru.petproject.giribro.vision.PoseFrame
import ru.petproject.giribro.vision.PoseIndex
import ru.petproject.giribro.vision.RepExercise
import ru.petproject.giribro.vision.Side
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min

private const val TAG = "GiriBroCAM"

@Composable
fun CameraRepScreen(
    onBack: () -> Unit,
    initialExercise: RepExercise? = null,
    initialPreset: FixationPreset? = null,
    initialCaptureMode: SetCaptureMode = SetCaptureMode.NONE,
    initialDurationSec: Int = 0
) {
    val vm: CameraRepViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settingsRepository = remember(context.applicationContext) {
        (context.applicationContext as GiriBroApp).settings
    }

    var videoFolderUri by remember {
        mutableStateOf(
            settingsRepository.videoFolderUri?.let { stored ->
                runCatching { Uri.parse(stored) }.getOrNull()
            }
        )
    }
    var isRecording by remember { mutableStateOf(false) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var videoStatus by remember { mutableStateOf<String?>(null) }
    var cameraReady by remember { mutableStateOf(false) }
    val recorder = remember { Recorder.Builder().build() }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }
    val currentRecording = rememberUpdatedState(activeRecording)

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            settingsRepository.videoFolderUri = uri.toString()
            videoFolderUri = uri
            videoStatus = "Папка для видео выбрана"
        }.onFailure { error ->
            Log.e(TAG, "Не удалось сохранить доступ к папке видео", error)
            videoStatus = "Не удалось выбрать папку: ${error.message.orEmpty()}"
        }
    }

    fun startVideoRecording() {
        if (isRecording || activeRecording != null) return
        val folder = videoFolderUri
        if (folder == null) {
            videoStatus = "Сначала выберите папку для видео"
            return
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "GiriBro_${state.exercise.name}_$stamp.mp4"
        val document = runCatching {
            DocumentFile.fromTreeUri(context, folder)?.createFile("video/mp4", fileName)
        }.onFailure { error ->
            Log.e(TAG, "Не удалось создать файл видео", error)
        }.getOrNull()
        if (document == null) {
            videoStatus = "Не удалось создать файл в выбранной папке"
            return
        }
        val descriptor: ParcelFileDescriptor = runCatching {
            context.contentResolver.openFileDescriptor(document.uri, "w")
        }.getOrNull() ?: run {
            videoStatus = "Нет доступа к файлу видео"
            return
        }
        try {
            val options = FileDescriptorOutputOptions.Builder(descriptor).build()
            activeRecording = recorder.prepareRecording(context, options)
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            isRecording = true
                            videoStatus = null
                        }
                        is VideoRecordEvent.Finalize -> {
                            isRecording = false
                            activeRecording = null
                            runCatching { descriptor.close() }
                            videoStatus = if (event.hasError()) {
                                Log.e(
                                    TAG,
                                    "Ошибка записи видео, код ${event.error}",
                                    event.cause
                                )
                                "Не удалось сохранить видео (код ${event.error})"
                            } else {
                                "Видео сохранено: $fileName"
                            }
                        }
                    }
                }
        } catch (error: Throwable) {
            runCatching { descriptor.close() }
            Log.e(TAG, "Не удалось начать запись видео", error)
            videoStatus = "Не удалось начать запись: ${error.message.orEmpty()}"
        }
    }

    fun stopVideoRecording() {
        activeRecording?.stop()
    }

    LaunchedEffect(cameraReady, state.running, state.captureMode) {
        if (!cameraReady) return@LaunchedEffect
        if (state.captureMode == SetCaptureMode.RECORD && state.running) {
            startVideoRecording()
        } else if (!state.running && activeRecording != null) {
            stopVideoRecording()
        }
    }

    DisposableEffect(Unit) {
        onDispose { currentRecording.value?.stop() }
    }

    LaunchedEffect(initialExercise) {
        if (initialExercise != null) vm.setExercise(initialExercise)
    }

    LaunchedEffect(initialPreset) {
        if (initialPreset != null) vm.setPreset(initialPreset)
    }

    LaunchedEffect(initialCaptureMode, initialDurationSec) {
        if (initialCaptureMode != SetCaptureMode.NONE) {
            vm.startSet(initialCaptureMode, initialDurationSec)
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var showRationale by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        showRationale = !granted
    }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            CameraRepHeader(
                exercise = state.exercise,
                preset = state.preset,
                captureMode = state.captureMode,
                durationSec = state.setDurationSec,
                remainingSec = state.setRemainingSec,
                onBack = onBack
            )
        }

        item {
            CameraCard(
                state = state,
                hasPermission = hasPermission,
                showRationale = showRationale,
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onCameraReady = { cameraReady = it },
                videoCapture = videoCapture,
                vm = vm
            )
        }

        if (state.captureMode == SetCaptureMode.RECORD) {
            item {
                VideoCard(
                    folderUri = videoFolderUri,
                    isRecording = isRecording,
                    status = videoStatus,
                    onPickFolder = { folderPicker.launch(null) },
                    onToggleRecording = {
                        if (isRecording) stopVideoRecording() else startVideoRecording()
                    }
                )
            }
        }

        item {
            FixationCard(state = state)
        }

        item {
            Text(
                "Упражнение",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RepExercise.entries.forEach { exercise ->
                    FilterChip(
                        selected = state.exercise == exercise,
                        onClick = { vm.setExercise(exercise) },
                        label = { Text(exercise.short) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (state.exercise) {
                    RepExercise.JERK ->
                        "Толчок: повтор только в полном замке — кисти выше головы, локти прямые."

                    RepExercise.LONG_CYCLE ->
                        "ДЦ: замок → сброс в вис → заброс на грудь. Следующий повтор " +
                            "считается только после виса."

                    RepExercise.SNATCH ->
                        "Рывок: руки поочерёдно. Пока одна вверху, вторая не считается."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Text(
                "Порог фиксации",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FixationPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = state.preset == preset,
                        onClick = { vm.setPreset(preset) },
                        label = { Text("${preset.label} · ${preset.seconds} с") }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (state.exercise == RepExercise.LONG_CYCLE) {
                    "В ДЦ замок подтверждается коротко (${state.fixationSec} с): " +
                        "гири сразу уходят в вис."
                } else {
                    state.preset.hint
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            ControlsCard(state = state, vm = vm)
        }

        if (state.exercise == RepExercise.SNATCH) {
            item {
                Text(
                    text = "Левая ${state.left} · правая ${state.right}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.error != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Камера-счётчик недоступен",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            state.error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = { vm.clearError() }) {
                            Text("Скрыть")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = if (state.captureMode == SetCaptureMode.RECORD) {
                    "Счёт тренировочный и не заменяет судью. Видео сохраняется только " +
                        "в выбранную вами папку и никуда не отправляется."
                } else {
                    "Счёт тренировочный и не заменяет судью. Видео не записывается " +
                        "и не отправляется: кадры обрабатываются на устройстве."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CameraRepHeader(
    exercise: RepExercise,
    preset: FixationPreset,
    captureMode: SetCaptureMode,
    durationSec: Int,
    remainingSec: Int,
    onBack: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
        }
        Spacer(Modifier.width(4.dp))
        Column {
            Text(
                text = "СЧЁТЧИК",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = buildString {
                    append(exercise.label).append(" · ").append(preset.label.lowercase())
                    if (captureMode != SetCaptureMode.NONE) {
                        append(" · ").append(
                            if (captureMode == SetCaptureMode.RECORD) "Запись" else "Счёт"
                        )
                    }
                    if (captureMode != SetCaptureMode.NONE && durationSec > 0) {
                        append(" · ").append(remainingSec).append(" с")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CameraCard(
    state: CameraRepUiState,
    hasPermission: Boolean,
    showRationale: Boolean,
    onRequestPermission: () -> Unit,
    onCameraReady: (Boolean) -> Unit,
    videoCapture: VideoCapture<Recorder>,
    vm: CameraRepViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val analysisExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val analyzer = remember(hasPermission) {
        if (hasPermission) vm.poseAnalyzer() else null
    }

    LaunchedEffect(hasPermission, analyzer, state.captureMode) {
        if (!hasPermission || analyzer == null) {
            onCameraReady(false)
            return@LaunchedEffect
        }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder()
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(640, 480),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                )
                            )
                            .build()
                    )
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, analyzer) }
                val useCases = mutableListOf<UseCase>(preview, analysis)
                if (state.captureMode == SetCaptureMode.RECORD) {
                    useCases += videoCapture
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    *useCases.toTypedArray()
                )
                cameraError = null
                onCameraReady(true)
            } catch (error: Throwable) {
                onCameraReady(false)
                Log.e(TAG, "Не удалось открыть камеру", error)
                cameraError = buildString {
                    append("Не удалось открыть камеру: ").append(error.javaClass.name)
                    error.message?.let { append(": ").append(it) }
                    error.cause?.let { append("\n← ").append(it.javaClass.name) }
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            onCameraReady(false)
            analysisExecutor.shutdown()
        }
    }

    val borderColor = MaterialTheme.colorScheme.primary
    val wristColor = MaterialTheme.colorScheme.primary
    val boneColor = Color.White.copy(alpha = 0.75f)
    val lineWidth = with(density) { 2.dp.toPx() }
    val jointRadius = with(density) { 3.5.dp.toPx() }
    val wristRadius = with(density) { 6.dp.toPx() }
    val captureMode = state.captureMode
    val previewHeight = if (captureMode != SetCaptureMode.NONE) {
        (LocalConfiguration.current.screenHeightDp * 0.62f).dp
    } else {
        null
    }

    Box(
        Modifier
            .fillMaxWidth()
            .then(
                if (previewHeight != null) Modifier.height(previewHeight)
                else Modifier.aspectRatio(3f / 4f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black)
    ) {
        if (hasPermission) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
            PoseOverlay(
                frame = state.frame,
                mirrored = true,
                boneColor = boneColor,
                jointColor = borderColor,
                wristColor = wristColor,
                lineWidth = lineWidth,
                jointRadius = jointRadius,
                wristRadius = wristRadius,
                activeSides = state.activeSides,
                modifier = Modifier.fillMaxSize()
            )

            CounterBadge(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
            )
            StatusBadge(
                state = state,
                cameraError = cameraError,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
            )
            if (state.fixationProgress > 0f) {
                LinearProgressIndicator(
                    progress = { state.fixationProgress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp),
                    color = wristColor,
                    trackColor = Color.White.copy(alpha = 0.18f)
                )
            }
        } else {
            PermissionCard(
                showRationale = showRationale,
                captureMode = captureMode,
                onRequestPermission = onRequestPermission,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
        }
    }
}

@Composable
private fun VideoCard(
    folderUri: Uri?,
    isRecording: Boolean,
    status: String?,
    onPickFolder: () -> Unit,
    onToggleRecording: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Видео подхода", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (folderUri == null) {
                    "Папка для видео не выбрана"
                } else {
                    "Папка для видео выбрана"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (status != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status.startsWith("Не удалось")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickFolder, modifier = Modifier.weight(1f)) {
                    Text("Папка")
                }
                Button(
                    onClick = onToggleRecording,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                ) {
                    Text(if (isRecording) "Стоп" else "Запись")
                }
            }
        }
    }
}

@Composable
private fun CounterBadge(state: CameraRepUiState, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = state.total.toString(),
            fontSize = 54.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Text(
            text = "повторов",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f)
        )
        Text(
            text = "${state.elapsedSec / 60}:${(state.elapsedSec % 60).toString().padStart(2, '0')}" +
                " · ${state.rpm} rpm",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun StatusBadge(
    state: CameraRepUiState,
    cameraError: String?,
    modifier: Modifier = Modifier
) {
    val (text, color) = when {
        cameraError != null -> "Камера недоступна" to MaterialTheme.colorScheme.error
        !state.tracking -> "Нет позы" to Color(0xFFFFC46B)
        state.running -> "Идёт счёт" to MaterialTheme.colorScheme.primary
        else -> "Поза найдена" to Color(0xFF9CCC65)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.Black,
        modifier = modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun PermissionCard(
    showRationale: Boolean,
    captureMode: SetCaptureMode,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Нужна камера",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = buildString {
                    if (showRationale) {
                        append("Без доступа к камере счётчик не работает. Разрешите камеру ")
                        append("в настройках приложения. ")
                    } else {
                        append("Камера нужна, чтобы считать повторы по скелету. ")
                    }
                    if (captureMode == SetCaptureMode.RECORD) {
                        append("Видео сохраняется только в выбранную вами папку.")
                    } else {
                        append("Видео не записывается и не отправляется.")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRequestPermission) {
                Text(if (showRationale) "Открыть доступ" else "Разрешить камеру")
            }
        }
    }
}

@Composable
private fun FixationCard(state: CameraRepUiState) {
    val progress = state.fixationProgress
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Фиксация", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (state.fixationProgress > 0f) {
                        "${(state.fixationProgress * state.fixationSec).coerceAtMost(state.fixationSec)
                            .let { "%.1f".format(it) }} / ${state.fixationSec} с"
                    } else {
                        "ждём полный замок"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            if (state.shortFixations > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Коротких фиксаций: ${state.shortFixations} — в счёт не вошли",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ControlsCard(state: CameraRepUiState, vm: CameraRepViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Button(
                onClick = { if (state.running) vm.stop() else vm.start() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.running) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                Text(if (state.running) "Стоп" else "Старт", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { vm.removeRep() },
                    modifier = Modifier.weight(1f)
                ) { Text("−1") }
                OutlinedButton(
                    onClick = { vm.addRep() },
                    modifier = Modifier.weight(1f)
                ) { Text("+1") }
                OutlinedButton(
                    onClick = { vm.reset() },
                    modifier = Modifier.weight(1f)
                ) { Text("Сброс") }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Metric("Время", "${state.elapsedSec / 60}:${(state.elapsedSec % 60).toString().padStart(2, '0')}")
                Metric("Темп", "${state.rpm} rpm")
                Metric("Коротких", state.shortFixations.toString())
                if (state.exercise == RepExercise.SNATCH) {
                    Metric("Л/П", "${state.left}/${state.right}")
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PoseOverlay(
    frame: PoseFrame?,
    mirrored: Boolean,
    boneColor: Color,
    jointColor: Color,
    wristColor: Color,
    lineWidth: Float,
    jointRadius: Float,
    wristRadius: Float,
    activeSides: Set<Side>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        if (frame == null || frame.landmarks.size < PoseFrame.LANDMARK_COUNT) return@Canvas
        val sourceWidth = frame.imageWidth.toFloat()
        val sourceHeight = frame.imageHeight.toFloat()
        if (sourceWidth <= 0f || sourceHeight <= 0f) return@Canvas

        val scale = min(size.width / sourceWidth, size.height / sourceHeight)
        val drawWidth = sourceWidth * scale
        val drawHeight = sourceHeight * scale
        val offsetX = (size.width - drawWidth) / 2f
        val offsetY = (size.height - drawHeight) / 2f

        fun map(index: Int): Offset? {
            val landmark = frame.landmarks.getOrNull(index) ?: return null
            val x = if (mirrored) 1f - landmark.x else landmark.x
            return Offset(offsetX + x * drawWidth, offsetY + landmark.y * drawHeight)
        }

        val connections = listOf(
            PoseIndex.LEFT_SHOULDER to PoseIndex.RIGHT_SHOULDER,
            PoseIndex.LEFT_SHOULDER to PoseIndex.LEFT_ELBOW,
            PoseIndex.LEFT_ELBOW to PoseIndex.LEFT_WRIST,
            PoseIndex.RIGHT_SHOULDER to PoseIndex.RIGHT_ELBOW,
            PoseIndex.RIGHT_ELBOW to PoseIndex.RIGHT_WRIST,
            PoseIndex.LEFT_SHOULDER to PoseIndex.LEFT_HIP,
            PoseIndex.RIGHT_SHOULDER to PoseIndex.RIGHT_HIP,
            PoseIndex.LEFT_HIP to PoseIndex.RIGHT_HIP,
            PoseIndex.LEFT_HIP to PoseIndex.LEFT_KNEE,
            PoseIndex.LEFT_KNEE to PoseIndex.LEFT_ANKLE,
            PoseIndex.RIGHT_HIP to PoseIndex.RIGHT_KNEE,
            PoseIndex.RIGHT_KNEE to PoseIndex.RIGHT_ANKLE
        )
        connections.forEach { (from, to) ->
            val a = map(from) ?: return@forEach
            val b = map(to) ?: return@forEach
            drawLine(boneColor, a, b, strokeWidth = lineWidth, cap = StrokeCap.Round)
        }

        val leftActive = Side.LEFT in activeSides || Side.BOTH in activeSides
        val rightActive = Side.RIGHT in activeSides || Side.BOTH in activeSides
        val joints = listOf(
            PoseIndex.NOSE,
            PoseIndex.LEFT_SHOULDER,
            PoseIndex.RIGHT_SHOULDER,
            PoseIndex.LEFT_ELBOW,
            PoseIndex.RIGHT_ELBOW,
            PoseIndex.LEFT_HIP,
            PoseIndex.RIGHT_HIP,
            PoseIndex.LEFT_KNEE,
            PoseIndex.RIGHT_KNEE,
            PoseIndex.LEFT_ANKLE,
            PoseIndex.RIGHT_ANKLE
        )
        joints.forEach { index ->
            map(index)?.let { drawCircle(jointColor, jointRadius, it) }
        }
        map(PoseIndex.LEFT_WRIST)?.let {
            drawCircle(
                color = if (leftActive) wristColor else Color.White,
                radius = wristRadius,
                center = it
            )
        }
        map(PoseIndex.RIGHT_WRIST)?.let {
            drawCircle(
                color = if (rightActive) wristColor else Color.White,
                radius = wristRadius,
                center = it
            )
        }
    }
}
