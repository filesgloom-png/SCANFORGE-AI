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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
                    val pageWidth = 595
                    val pageHeight = 842
                    val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                    val page = pdf.startPage(info)
                    val canvas = page.canvas
                    canvas.drawColor(android.graphics.Color.WHITE)
                    val inset = 24f
                    val scale = minOf((pageWidth - inset * 2) / bitmap.width, (pageHeight - inset * 2) / bitmap.height)
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

    DisposableEffect(activity, previewView) {
        if (activity == null) {
            onCameraError("Не удалось открыть камеру")
            onDispose { }
        } else {
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
                        .build()
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        activity,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture
                    )
                    imageCapture = capture
                } catch (error: Exception) {
                    onCameraError("Ошибка запуска камеры: ${error.localizedMessage ?: "неизвестная ошибка"}")
                }
            }, ContextCompat.getMainExecutor(context))
            onDispose {
                provider?.unbindAll()
                imageCapture = null
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
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier.fillMaxWidth(0.82f).fillMaxSize(0.78f)
                        .background(Color.Transparent, RoundedCornerShape(12.dp))
                ) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val stroke = 3.dp.toPx()
                        val length = 30.dp.toPx()
                        val color = Emerald
                        val corners = listOf(
                            androidx.compose.ui.geometry.Offset(0f, 0f) to androidx.compose.ui.geometry.Offset(length, 0f),
                            androidx.compose.ui.geometry.Offset(0f, 0f) to androidx.compose.ui.geometry.Offset(0f, length),
                            androidx.compose.ui.geometry.Offset(size.width, 0f) to androidx.compose.ui.geometry.Offset(size.width - length, 0f),
                            androidx.compose.ui.geometry.Offset(size.width, 0f) to androidx.compose.ui.geometry.Offset(size.width, length),
                            androidx.compose.ui.geometry.Offset(0f, size.height) to androidx.compose.ui.geometry.Offset(length, size.height),
                            androidx.compose.ui.geometry.Offset(0f, size.height) to androidx.compose.ui.geometry.Offset(0f, size.height - length),
                            androidx.compose.ui.geometry.Offset(size.width, size.height) to androidx.compose.ui.geometry.Offset(size.width - length, size.height),
                            androidx.compose.ui.geometry.Offset(size.width, size.height) to androidx.compose.ui.geometry.Offset(size.width, size.height - length)
                        )
                        corners.forEach { (start, end) ->
                            drawLine(color, start, end, strokeWidth = stroke)
                        }
                    }
                }
            }
            Text(
                "Держи телефон параллельно листу и постарайся захватить все четыре угла.",
                color = Muted, fontSize = 12.sp
            )
            Button(
                onClick = {
                    val capture = imageCapture
                    if (capture == null || takingPhoto) return@Button
                    takingPhoto = true
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
                                    onCameraError("Не удалось сделать снимок: ${exception.message ?: "ошибка камеры"}")
                                }
                            })
                    } catch (error: Exception) {
                        takingPhoto = false
                        output.delete()
                        onCameraError("Не удалось запустить съёмку: ${error.localizedMessage ?: "ошибка камеры"}")
                    }
                },
                enabled = imageCapture != null && !takingPhoto,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Graphite)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(if (takingPhoto) "Сохраняем снимок…" else "Сфотографировать документ", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ScannerHome(
    selectedCount: Int,
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
                        Text("Экспортировать в PDF", fontWeight = FontWeight.SemiBold)
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
