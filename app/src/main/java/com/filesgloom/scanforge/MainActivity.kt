package com.filesgloom.scanforge

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.ImageCapture.OutputFileOptions
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private val Graphite = Color(0xFF101412)
private val Panel = Color(0xFF1A211D)
private val Emerald = Color(0xFF19C37D)
private val Gold = Color(0xFFD9B56D)
private val Muted = Color(0xFF9BA8A0)
private val Paper = Color(0xFFE8F0EA)

class MainActivity : ComponentActivity() {
    private val selectedUris = mutableStateListOf<Uri>()
    private val recognizedText = mutableStateListOf<Pair<String, String>>()
    private var status by mutableStateOf("Готов к сканированию")
    private var query by mutableStateOf("")
    private var documentName by mutableStateOf("Scanforge_Document")
    private var cameraMode by mutableStateOf(false)

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) cameraMode = true
            else status = "Разреши доступ к камере в настройках Android"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16, 20, 18)
        window.navigationBarColor = android.graphics.Color.rgb(16, 20, 18)
        DocumentImageProcessor.initialize()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Emerald,
                secondary = Gold,
                background = Graphite,
                surface = Panel,
                onPrimary = Graphite,
                onBackground = Paper,
                onSurface = Paper
            )) {
                if (cameraMode) {
                    CameraCaptureScreen(
                        onPhotoCaptured = { file ->
                            cameraMode = false
                            importCapturedPhoto(file)
                        },
                        onCancel = { cameraMode = false },
                        onCameraError = { message -> status = message }
                    )
                } else {
                    ScannerHome(
                        selectedCount = selectedUris.size,
                        pageUris = selectedUris.toList(),
                        onMovePage = { from, to ->
                            if (from in selectedUris.indices && to in selectedUris.indices && from != to) {
                                val item = selectedUris.removeAt(from)
                                selectedUris.add(to, item)
                            }
                        },
                        onRemovePage = { index ->
                            if (index in selectedUris.indices) {
                                val removed = selectedUris.removeAt(index)
                                recognizedText.removeAll { it.first == removed.toString() }
                                status = "Страница удалена • осталось: ${selectedUris.size}"
                            }
                        },
                        recognizedCount = recognizedText.size,
                        recognizedText = recognizedText.toList(),
                        status = status,
                        query = query,
                        onQueryChange = { query = it },
                        onOpenCamera = {
                            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                cameraMode = true
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        onPickImages = { imagePicker.launch("image/*") },
                        onExportPdf = { exportPicker.launch(safeFileName(documentName) + ".pdf") },
                        onClear = {
                            selectedUris.clear()
                            recognizedText.clear()
                            status = "Выбери изображения или сфотографируй документ"
                        }
                    )
                }
            }
        }
    }

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        val fresh = uris.filterNot { it in selectedUris }
        selectedUris.addAll(fresh)
        status = "Добавлено страниц: ${selectedUris.size}. Распознаём текст…"
        fresh.forEach { uri -> recognize(uri) }
    }

    private val exportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { destination ->
        if (destination == null) {
            status = "Сохранение отменено"
            return@registerForActivityResult
        }
        val pages = selectedUris.toList()
        if (pages.isEmpty()) {
            status = "Сначала добавь изображения"
            return@registerForActivityResult
        }
        status = "Создаём PDF…"
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { writePdf(pages, destination) }
            runOnUiThread {
                status = if (result.isSuccess) {
                    "PDF сохранён • ${pages.size} стр."
                } else {
                    "Не удалось создать PDF: ${result.exceptionOrNull()?.localizedMessage ?: "ошибка"}"
                }
            }
        }
    }

    private fun importCapturedPhoto(photo: File) {
        status = "Ищем границы листа и исправляем перспективу…"
        lifecycleScope.launch {
            val finalFile = try {
                withContext(Dispatchers.Default) {
                    val source = BitmapFactory.decodeFile(photo.absolutePath)
                        ?: throw IllegalStateException("Не удалось прочитать снимок")
                    try {
                        val corrected = DocumentImageProcessor.autoCorrect(source)
                        if (corrected == null) {
                            photo
                        } else {
                            val output = File(photo.parentFile, "corrected_${photo.nameWithoutExtension}.jpg")
                            try {
                                FileOutputStream(output).use { stream ->
                                    if (!corrected.compress(Bitmap.CompressFormat.JPEG, 94, stream)) {
                                        throw java.io.IOException("Не удалось сохранить исправленный снимок")
                                    }
                                }
                                output
                            } catch (error: Exception) {
                                output.delete()
                                photo
                            } finally {
                                corrected.recycle()
                            }
                        }
                    } finally {
                        source.recycle()
                    }
                }
            } catch (error: Exception) {
                photo.delete()
                status = "Не удалось обработать снимок: ${error.localizedMessage ?: "ошибка чтения"}"
                return@launch
            }
            val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", finalFile)
            if (uri !in selectedUris) selectedUris.add(uri)
            status = if (finalFile.name.startsWith("corrected_")) {
                photo.delete()
                "Лист найден • перспектива исправлена • OCR…"
            } else {
                "Снимок сохранён • границы листа не найдены, оставлен оригинал • OCR…"
            }
            recognize(uri)
        }
    }

    private fun recognize(uri: Uri) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val image = InputImage.fromFilePath(this, uri)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    recognizedText.removeAll { it.first == uri.toString() }
                    recognizedText.add(uri.toString() to result.text)
                    status = "OCR готов • распознано страниц: ${recognizedText.size}"
                    recognizer.close()
                }
                .addOnFailureListener { error ->
                    status = "OCR не удался: ${error.localizedMessage ?: "ошибка распознавания"}"
                    recognizer.close()
                }
        } catch (error: Exception) {
            status = "Не удалось открыть изображение"
            recognizer.close()
        }
    }

    private fun writePdf(pages: List<Uri>, destination: Uri) {
        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { index, uri ->
                val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?: throw IllegalStateException("Не удалось прочитать страницу ${index + 1}")
                try {
                    // Keep every selected image as its own PDF page and match page orientation
                    // to the source image so landscape scans are not needlessly shrunk.
                    val landscape = bitmap.width > bitmap.height
                    val pageWidth = if (landscape) 842 else 595
                    val pageHeight = if (landscape) 595 else 842
                    val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                    val page = pdf.startPage(info)
                    val canvas = page.canvas
                    canvas.drawColor(android.graphics.Color.WHITE)
                    val inset = 24f
                    val availableWidth = pageWidth - inset * 2f
                    val availableHeight = pageHeight - inset * 2f
                    val scale = minOf(availableWidth / bitmap.width.toFloat(), availableHeight / bitmap.height.toFloat())
                    val width = bitmap.width * scale
                    val height = bitmap.height * scale
                    val left = (pageWidth - width) / 2f
                    val top = (pageHeight - height) / 2f
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(bitmap, null, RectF(left, top, left + width, top + height), paint)
                    pdf.finishPage(page)
                } finally {
                    bitmap.recycle()
                }
            }
            contentResolver.openOutputStream(destination)?.use { pdf.writeTo(it) }
                ?: throw IllegalStateException("Не удалось открыть файл для записи")
        } finally {
            pdf.close()
        }
    }

    private fun safeFileName(value: String): String =
        value.trim().replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifBlank { "Scanforge_Document" }
}

@Composable
private fun CameraCaptureScreen(
    onPhotoCaptured: (File) -> Unit,
    onCancel: () -> Unit,
    onCameraError: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var takingPhoto by remember { mutableStateOf(false) }
    var liveCorners by remember { mutableStateOf<List<Pair<Float, Float>>?>(null) }
    var detectionLabel by remember { mutableStateOf("Ищем края листа…") }
    var autoCaptureEnabled by remember { mutableStateOf(true) }

    val capturePhoto: (Boolean) -> Unit = { automatic ->
        val capture = imageCapture
        if (capture != null && !takingPhoto) {
            takingPhoto = true
            detectionLabel = if (automatic) "Лист стабилен • снимаем…" else "Сохраняем снимок…"
            val directory = File(context.cacheDir, "documents").apply { mkdirs() }
            val output = File(directory, "scan_${System.currentTimeMillis()}.jpg")
            val options = OutputFileOptions.Builder(output).build()
            try {
                capture.takePicture(options, ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                            takingPhoto = false
                            onPhotoCaptured(output)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            takingPhoto = false
                            output.delete()
                            detectionLabel = "Не удалось снять • попробуй ещё раз"
                            onCameraError("Не удалось сделать снимок: ${exception.message ?: "ошибка камеры"}")
                        }
                    })
            } catch (error: Exception) {
                takingPhoto = false
                output.delete()
                detectionLabel = "Ошибка съёмки"
                onCameraError("Не удалось запустить съёмку: ${error.localizedMessage ?: "ошибка камеры"}")
            }
        }
    }

    DisposableEffect(activity, previewView, autoCaptureEnabled) {
        if (activity == null) {
            onCameraError("Не удалось открыть камеру")
            onDispose { }
        } else {
            val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
            val tracker = DetectionStabilityTracker(requiredFrames = 5, minimumConfidence = 0.75)
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setJpegQuality(96)
                        .setTargetRotation(previewView.display?.rotation ?: android.view.Surface.ROTATION_0)
                        .build()
                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(android.util.Size(640, 480))
                        .setTargetRotation(previewView.display?.rotation ?: android.view.Surface.ROTATION_0)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(analysisExecutor) { image ->
                        var frame: Bitmap? = null
                        try {
                            frame = lumaFrameToBitmap(image)
                            val detection = DocumentImageProcessor.detectDocumentCorners(frame)
                            val rotation = image.imageInfo.rotationDegrees
                            val displayCorners = detection?.corners
                                ?.map { it.x.toFloat() to it.y.toFloat() }
                                ?.let { transformDisplayCorners(it, rotation) }

                            ContextCompat.getMainExecutor(context).execute {
                                liveCorners = displayCorners
                                detectionLabel = when {
                                    detection == null -> "Лист не найден • можно снять вручную"
                                    detection.confidence < 0.75 -> "Границы неуверенные • держи камеру ровнее"
                                    else -> "Лист найден • ${(detection.confidence * 100).toInt()}% • не двигай телефон"
                                }
                                if (autoCaptureEnabled && !takingPhoto && tracker.observe(detection)) {
                                    capturePhoto(true)
                                } else if (!autoCaptureEnabled) {
                                    tracker.reset()
                                }
                            }
                        } catch (_: Exception) {
                            ContextCompat.getMainExecutor(context).execute {
                                liveCorners = null
                                detectionLabel = "Не удалось распознать края • ручная съёмка доступна"
                            }
                            tracker.observe(null)
                        } finally {
                            frame?.recycle()
                            image.close()
                        }
                    }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        activity,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture,
                        analysis
                    )
                    imageCapture = capture
                } catch (error: Exception) {
                    onCameraError("Ошибка запуска камеры: ${error.localizedMessage ?: "неизвестная ошибка"}")
                }
            }, ContextCompat.getMainExecutor(context))
            onDispose {
                provider?.unbindAll()
                imageCapture = null
                analysisExecutor.shutdown()
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Graphite) {
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("СКАНИРОВАНИЕ", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Наведи камеру на лист", color = Paper, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onCancel) { Text("Назад", color = Gold) }
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val stroke = 3.dp.toPx()
                    val corners = liveCorners
                    if (corners == null) {
                        val marginX = size.width * 0.09f
                        val marginY = size.height * 0.10f
                        val length = 28.dp.toPx()
                        val frame = listOf(
                            Offset(marginX, marginY) to Offset(marginX + length, marginY),
                            Offset(marginX, marginY) to Offset(marginX, marginY + length),
                            Offset(size.width - marginX, marginY) to Offset(size.width - marginX - length, marginY),
                            Offset(size.width - marginX, marginY) to Offset(size.width - marginX, marginY + length),
                            Offset(marginX, size.height - marginY) to Offset(marginX + length, size.height - marginY),
                            Offset(marginX, size.height - marginY) to Offset(marginX, size.height - marginY - length),
                            Offset(size.width - marginX, size.height - marginY) to Offset(size.width - marginX - length, size.height - marginY),
                            Offset(size.width - marginX, size.height - marginY) to Offset(size.width - marginX, size.height - marginY - length)
                        )
                        frame.forEach { (from, to) -> drawLine(Gold, from, to, strokeWidth = stroke) }
                    } else {
                        val orientedWidth = previewView.width.toFloat().coerceAtLeast(1f)
                        val orientedHeight = previewView.height.toFloat().coerceAtLeast(1f)
                        val points = corners.map { (x, y) -> Offset(x * orientedWidth, y * orientedHeight) }
                        for (index in points.indices) {
                            drawLine(Emerald, points[index], points[(index + 1) % points.size], strokeWidth = stroke)
                            drawCircle(Emerald, radius = 5.dp.toPx(), center = points[index])
                        }
                    }
                }
            }
            Text(detectionLabel, color = if (liveCorners == null) Muted else Emerald, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Автосъёмка при 5 стабильных кадрах", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(
                    checked = autoCaptureEnabled,
                    onCheckedChange = { autoCaptureEnabled = it },
                    enabled = !takingPhoto
                )
            }
            Text(
                "Если края не распознаны, снимок вручную сохранится без автоматической обрезки.",
                color = Muted, fontSize = 12.sp
            )
            Button(
                onClick = { capturePhoto(false) },
                enabled = imageCapture != null && !takingPhoto,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Graphite)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(if (takingPhoto) "Сохраняем снимок…" else "Сфотографировать вручную", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun lumaFrameToBitmap(image: ImageProxy): Bitmap {
    val plane = image.planes.first()
    val buffer = plane.buffer.duplicate()
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val pixels = IntArray(image.width * image.height)
    for (y in 0 until image.height) {
        val rowStart = y * rowStride
        for (x in 0 until image.width) {
            val index = rowStart + x * pixelStride
            val luma = (bytes.getOrNull(index)?.toInt()?.and(0xFF) ?: 0)
            pixels[y * image.width + x] = android.graphics.Color.rgb(luma, luma, luma)
        }
    }
    return Bitmap.createBitmap(pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
}

internal fun transformDisplayCorners(
    points: List<Pair<Float, Float>>,
    rotationDegrees: Int
): List<Pair<Float, Float>> {
    if (points.size != 4) return emptyList()
    val rotated = points.map { (x, y) ->
        when (rotationDegrees) {
            90 -> (1f - y) to x
            180 -> (1f - x) to (1f - y)
            270 -> y to (1f - x)
            else -> x to y
        }
    }
    return orderDisplayCorners(rotated)
}

internal fun orderDisplayCorners(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
    if (points.size != 4) return emptyList()
    val topLeft = points.minBy { it.first + it.second }
    val bottomRight = points.maxBy { it.first + it.second }
    val topRight = points.minBy { it.second - it.first }
    val bottomLeft = points.maxBy { it.second - it.first }
    return listOf(topLeft, topRight, bottomRight, bottomLeft)
}

@Composable
private fun ScannerHome(
    selectedCount: Int,
    pageUris: List<Uri>,
    onMovePage: (Int, Int) -> Unit,
    onRemovePage: (Int) -> Unit,
    recognizedCount: Int,
    recognizedText: List<Pair<String, String>>,
    status: String,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenCamera: () -> Unit,
    onPickImages: () -> Unit,
    onExportPdf: () -> Unit,
    onClear: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Graphite) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(48.dp).background(Emerald.copy(alpha = 0.14f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = Emerald, modifier = Modifier.size(27.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("SCANFORGE", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Paper, letterSpacing = 1.2.sp)
                    Text("AI DOCUMENT STUDIO", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Gold, letterSpacing = 2.sp)
                }
                Box(modifier = Modifier.size(10.dp).background(Emerald, CircleShape))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Panel)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ТВОЯ ДОКУМЕНТ-СТАНЦИЯ", fontSize = 10.sp, color = Muted, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("Бумага. В цифру.", fontSize = 25.sp, color = Paper, fontWeight = FontWeight.Bold)
                            Text("Сканируй, распознавай, сохраняй.", fontSize = 13.sp, color = Muted)
                        }
                        Icon(Icons.Default.Description, contentDescription = null, tint = Gold, modifier = Modifier.size(48.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile("СТРАНИЦЫ", selectedCount.toString(), Modifier.weight(1f))
                        MetricTile("OCR ГОТОВ", recognizedCount.toString(), Modifier.weight(1f))
                    }
                    Button(
                        onClick = onOpenCamera,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Graphite)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Сканировать камерой", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onPickImages,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Добавить изображения")
                    }
                    OutlinedButton(
                        onClick = onExportPdf,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Собрать PDF • ${selectedCount} стр.", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ПОИСК ПО OCR", color = Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp)
                Spacer(Modifier.width(8.dp))
                Text(status, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Muted)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    cursorBrush = SolidColor(Emerald),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Paper, fontSize = 14.sp),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) Text("Найти текст в распознанных страницах…", color = Muted, fontSize = 13.sp)
                            inner()
                        }
                    }
                )
            }

            if (pageUris.isNotEmpty()) {
                Text("СТРАНИЦЫ PDF • ПОРЯДОК ЭКСПОРТА", color = Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp)
                Column(
                    modifier = Modifier.fillMaxWidth().weight(0.45f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pageUris.forEachIndexed { index, uri ->
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(String.format("%02d", index + 1), color = Emerald, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                            Text(uri.lastPathSegment?.substringAfterLast('/') ?: "Скан ${index + 1}", color = Paper, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onMovePage(index, index - 1) }, enabled = index > 0) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Выше", tint = if (index > 0) Gold else Muted)
                            }
                            IconButton(onClick = { onMovePage(index, index + 1) }, enabled = index < pageUris.lastIndex) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Ниже", tint = if (index < pageUris.lastIndex) Gold else Muted)
                            }
                            IconButton(onClick = { onRemovePage(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить страницу", tint = Color(0xFFFF7B72))
                            }
                        }
                    }
                }
            }
            Text("НАЙДЕННЫЕ ДОКУМЕНТЫ", color = Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.4.sp)
            if (recognizedText.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.TextSnippet, contentDescription = null, tint = Emerald, modifier = Modifier.size(30.dp))
                        Text(if (selectedCount == 0) "Здесь появятся твои сканы" else "Текст пока распознаётся", color = Paper, fontWeight = FontWeight.SemiBold)
                        Text("Сфотографируй документ или добавь изображения — обработка выполняется на устройстве.", color = Muted, fontSize = 12.sp)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recognizedText.filter {
                        query.isBlank() || it.second.contains(query, ignoreCase = true) || it.first.contains(query, ignoreCase = true)
                    }.forEachIndexed { index, item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Panel),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text("СТРАНИЦА ${index + 1}", color = Emerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(5.dp))
                                Text(item.second.ifBlank { "Текст не найден на этой странице" }, color = Paper, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LOCAL-FIRST • ТВОИ ФАЙЛЫ ОСТАЮТСЯ У ТЕБЯ", color = Muted, fontSize = 9.sp, letterSpacing = 0.7.sp, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onClear) {
                    Text("Очистить", color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(Graphite, RoundedCornerShape(14.dp)).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
        Text(value, color = Emerald, fontSize = 23.sp, fontWeight = FontWeight.Bold)
    }
}
