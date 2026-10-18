package io.github.akrishna87.jotter.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoFixNormal
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import io.github.akrishna87.jotter.Files
import io.github.akrishna87.jotter.Recorder
import io.github.akrishna87.jotter.model.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Ways to add something to a note. Each ends with [onAdd] getting the new attachments. */
class Attacher internal constructor(
    private val gallery: ManagedActivityResultLauncher<PickVisualMediaRequest, List<android.net.Uri>>,
    private val files: ManagedActivityResultLauncher<Array<String>, List<android.net.Uri>>,
    private val camera: () -> Unit,
    private val scan: () -> Unit,
    private val micPermission: () -> Unit,
) {
    var recording by mutableStateOf(false)
    var sketching by mutableStateOf(false)

    fun photo() = gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    fun camera() = camera.invoke()
    fun scan() = scan.invoke()
    fun file() = files.launch(arrayOf("*/*"))
    fun audio() = micPermission()
    fun sketch() { sketching = true }

    fun start(kind: String?) {
        when (kind) {
            "photo" -> camera()
            "scan" -> scan()
            "audio" -> audio()
            "sketch" -> sketch()
            "file" -> file()
        }
    }
}

@Composable
fun rememberAttacher(onAdd: (List<Attachment>) -> Unit): Attacher {
    val context = LocalContext.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    var pendingPhoto by remember { mutableStateOf<Pair<Attachment, Boolean>?>(null) }
    fun copyAll(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            val atts = withContext(Dispatchers.IO) { uris.mapNotNull { Files.copy(context, it) } }
            if (atts.size < uris.size) actions.message("Some files couldn't be read")
            if (atts.isNotEmpty()) onAdd(atts)
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { copyAll(it) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { copyAll(it) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val (a, isScan) = pendingPhoto ?: return@rememberLauncherForActivityResult
        pendingPhoto = null
        val done = if (ok) Files.finished(context, a) else null.also { Files.delete(context, a) }
        if (done != null) {
            scope.launch {
                val result = if (isScan) withContext(Dispatchers.Default) { Files.enhanceScan(context, done) } else done
                onAdd(listOf(result))
            }
        }
    }
    fun shoot(scan: Boolean) {
        val (a, uri) = Files.newPhoto(context, scan)
        pendingPhoto = a to scan
        try {
            takePicture.launch(uri)
        } catch (e: android.content.ActivityNotFoundException) {
            Files.delete(context, a)
            pendingPhoto = null
            actions.message("There's no camera app on this phone")
        }
    }
    lateinit var attacher: Attacher
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) attacher.recording = true else actions.message("Recording needs the microphone permission")
    }
    attacher = remember {
        Attacher(
            gallery, files,
            camera = { shoot(false) },
            scan = { shoot(true) },
            micPermission = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) attacher.recording = true
                else mic.launch(Manifest.permission.RECORD_AUDIO)
            },
        )
    }
    if (attacher.recording) RecorderDialog(onDone = { a -> attacher.recording = false; if (a != null) onAdd(listOf(a)) })
    if (attacher.sketching) SketchDialog(onDone = { a -> attacher.sketching = false; if (a != null) onAdd(listOf(a)) })
    return attacher
}

/** The attach menu: from the gallery, the camera, a scan, a recording, a sketch or any file. */
@Composable
fun AttachMenu(expanded: Boolean, attacher: Attacher, onDismiss: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Photo from gallery") }, leadingIcon = { Icon(Icons.Rounded.Image, null) }, onClick = { onDismiss(); attacher.photo() })
        DropdownMenuItem(text = { Text("Take a photo") }, leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) }, onClick = { onDismiss(); attacher.camera() })
        DropdownMenuItem(text = { Text("Scan a document") }, leadingIcon = { Icon(Icons.Rounded.DocumentScanner, null) }, onClick = { onDismiss(); attacher.scan() })
        DropdownMenuItem(text = { Text("Record audio") }, leadingIcon = { Icon(Icons.Rounded.Mic, null) }, onClick = { onDismiss(); attacher.audio() })
        DropdownMenuItem(text = { Text("Sketch") }, leadingIcon = { Icon(Icons.Rounded.Brush, null) }, onClick = { onDismiss(); attacher.sketch() })
        DropdownMenuItem(text = { Text("File") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, null) }, onClick = { onDismiss(); attacher.file() })
    }
}

/** Records until Stop; the recording goes into the note. */
@Composable
private fun RecorderDialog(onDone: (Attachment?) -> Unit) {
    val context = LocalContext.current
    val actions = LocalActions.current
    var started by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }
    var level by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        started = Recorder.start(context)
        if (!started) {
            actions.message("Couldn't start recording")
            onDone(null)
            return@LaunchedEffect
        }
        val begin = System.currentTimeMillis()
        while (true) {
            delay(100)
            elapsed = (System.currentTimeMillis() - begin).toInt()
            level = level * 0.6f + Recorder.level() * 0.4f
        }
    }
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Rounded.Mic, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(if (started) "Recording" else "Starting…") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Recorder.clock(elapsed), style = MaterialTheme.typography.displaySmall)
                LinearProgressIndicator(progress = { level }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(Recorder.stop()) }, Modifier.semantics { contentDescription = "Stop recording" }) {
                Icon(Icons.Rounded.Stop, null)
                Text("Stop and save", Modifier.padding(start = 6.dp))
            }
        },
        dismissButton = { TextButton(onClick = { Recorder.cancel(); onDone(null) }) { Text("Discard") } },
    )
}

private class PenStroke(val color: Color, val width: Float, val points: MutableList<Offset> = mutableListOf())

/** A full-screen sketch pad: pens in a few colours and widths, an eraser, undo and clear. Saved as a PNG. */
@Composable
private fun SketchDialog(onDone: (Attachment?) -> Unit) {
    val context = LocalContext.current
    val strokes = remember { mutableStateListOf<PenStroke>() }
    var current by remember { mutableStateOf<PenStroke?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val palette = listOf(Color(0xFF1F1B16), Color(0xFFC62828), Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFF9A825), Color(0xFF6A1B9A), Color(0xFFEF6C00), Color(0xFF8D6E63))
    val paletteNames = listOf("black", "red", "blue", "green", "yellow", "purple", "orange", "brown")
    var color by remember { mutableStateOf(palette[0]) }
    var width by remember { mutableFloatStateOf(6f) }
    var eraser by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val paper = Color.White

    fun save(): Attachment? {
        if (strokes.isEmpty() || size.width == 0) return null
        val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawColor(paper.toArgb())
        for (s in strokes) {
            val p = AndroidPaint().apply {
                this.color = s.color.toArgb(); strokeWidth = s.width; style = AndroidPaint.Style.STROKE
                strokeCap = AndroidPaint.Cap.ROUND; strokeJoin = AndroidPaint.Join.ROUND; isAntiAlias = true
            }
            if (s.points.size == 1) {
                c.drawCircle(s.points[0].x, s.points[0].y, s.width / 2, p.apply { style = AndroidPaint.Style.FILL })
            } else {
                val path = AndroidPath()
                s.points.forEachIndexed { i, pt -> if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y) }
                c.drawPath(path, p)
            }
        }
        val name = "Sketch " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm").format(LocalDateTime.now()) + ".png"
        return Files.saveBitmap(context, bmp, name)
    }

    // Drawn over the note in the app's own window rather than in a dialog window: a dialog doesn't
    // always learn where the phone's navigation buttons are, and the tools ended up under them.
    BackHandler { onDone(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { keyboard?.hide() }
    run {
        Surface(Modifier.fillMaxSize().zIndex(10f), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onDone(null) }, Modifier.semantics { contentDescription = "Discard sketch" }) { Icon(Icons.Rounded.Close, null) }
                    Text("Sketch", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }, Modifier.semantics { contentDescription = "Undo stroke" }) { Icon(Icons.AutoMirrored.Rounded.Undo, null) }
                    IconButton(onClick = { strokes.clear() }, Modifier.semantics { contentDescription = "Clear sketch" }) { Icon(Icons.Rounded.Delete, null) }
                    TextButton(onClick = { onDone(save()) }, enabled = strokes.isNotEmpty()) { Text("Save") }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .background(paper)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .onSizeChanged { size = it }
                        .semantics { contentDescription = "Sketch pad" }
                        .pointerInput(eraser, color, width) {
                            detectTapGestures { pt -> strokes += PenStroke(if (eraser) paper else color, if (eraser) width * 4 else width, mutableListOf(pt)) }
                        }
                        .pointerInput(eraser, color, width) {
                            detectDragGestures(
                                onDragStart = { pt -> current = PenStroke(if (eraser) paper else color, if (eraser) width * 4 else width, mutableListOf(pt)).also { strokes += it } },
                                onDragEnd = { current = null },
                                onDragCancel = { current = null },
                            ) { change, _ ->
                                current?.points?.add(change.position)
                                tick++
                            }
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        if (tick < 0) return@Canvas // Redraw as a stroke grows.
                        for (s in strokes) {
                            if (s.points.size == 1) {
                                drawCircle(s.color, s.width / 2, s.points[0])
                            } else {
                                val path = Path()
                                s.points.forEachIndexed { i, pt -> if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y) }
                                drawPath(path, s.color, style = Stroke(width = s.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        }
                    }
                }
                // Colours on one row, spread over the width so they fit any screen; pen sizes and the eraser below.
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    palette.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(c)
                                .border(if (c == color && !eraser) 3.dp else 1.dp, if (c == color && !eraser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable { color = c; eraser = false }
                                .semantics { contentDescription = "Pen colour ${paletteNames[i]}" },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (w in listOf(4f, 8f, 16f)) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape)
                                .background(if (w == width) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .clickable { width = w }
                                .semantics { contentDescription = "Pen width ${w.toInt()}" },
                            contentAlignment = Alignment.Center,
                        ) { Box(Modifier.size((w * 1.2f).dp.coerceAtMost(20.dp)).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface)) }
                    }
                    Box(Modifier.weight(1f))
                    FilledIconButton(onClick = { eraser = !eraser }, Modifier.semantics { contentDescription = if (eraser) "Pen" else "Eraser" }) {
                        Icon(if (eraser) Icons.Rounded.Brush else Icons.Rounded.AutoFixNormal, null)
                    }
                }
            }
        }
    }
}
