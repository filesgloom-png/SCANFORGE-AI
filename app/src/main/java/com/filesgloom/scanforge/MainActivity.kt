package com.filesgloom.scanforge

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16, 20, 18)
        window.navigationBarColor = android.graphics.Color.rgb(16, 20, 18)
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
                ScannerHome(
                    selectedCount = selectedUris.size,
                    recognizedCount = recognizedText.size,
                    status = status,
                    query = query,
                    onQueryChange = { query = it },
                    documentName = documentName,
                    onNameChange = { documentName = it },
                    results = recognizedText.filter {
                        query.isBlank() || it.second.contains(query, ignoreCase = true) || it.first.contains(query, ignoreCase = true)
                    },
                    onPickImages = { imagePicker.launch("image/*") },
                    onExportPdf = { exportPicker.launch(safeFileName(documentName) + ".pdf") },
                    onClear = {
                        selectedUris.clear()
                        recognizedText.clear()
                        status = "Выбери изображения для нового документа"
                    }
                )
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
                val bitmap = contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                    ?: throw IllegalStateException("Не удалось прочитать страницу ${index + 1}")
                try {
                    val pageWidth = 595
                    val pageHeight = 842
                    val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                    val page = pdf.startPage(info)
                    val canvas = page.canvas
                    canvas.drawColor(AndroidColor.WHITE)
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
private fun ScannerHome(
    selectedCount: Int,
    recognizedCount: Int,
    status: String,
    query: String,
    onQueryChange: (String) -> Unit,
    documentName: String,
    onNameChange: (String) -> Unit,
    results: List<Pair<String, String>>,
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
                        onClick = onPickImages,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Graphite)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Добавить страницы", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onExportPdf,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
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
                Spacer(Modifier.weight(1f))
                Text(status, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            if (results.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                        Text("Добавь фото или изображения документов — SCANFORGE обработает их локально.", color = Muted, fontSize = 12.sp)
                    }
                }
            } else {
                Column(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    results.forEachIndexed { index, item ->
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
                OutlinedButton(onClick = onClear, contentPadding = ButtonDefaults.TextButtonContentPadding) {
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
