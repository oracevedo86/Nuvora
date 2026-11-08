package com.minipicasa

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun buildWallpaperBitmap(
    context: Context,
    uri: Uri,
    recipe: Recipe,
    portrait: Boolean,
    fill: Boolean,
    zoom: Float,
    panX: Float,
    panY: Float
): Bitmap? {
    val metrics = context.resources.displayMetrics
    val screenShort = min(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
    val screenLong = max(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
    var outputWidth = if (portrait) screenShort else screenLong
    var outputHeight = if (portrait) screenLong else screenShort

    // Avoid creating an unnecessarily large output bitmap on high-resolution devices.
    val reduction = min(1f, 4096f / max(outputWidth, outputHeight).toFloat())
    outputWidth = (outputWidth * reduction).roundToInt().coerceAtLeast(1)
    outputHeight = (outputHeight * reduction).roundToInt().coerceAtLeast(1)

    val source = loadScaledBitmap(context, uri, 4096) ?: return null
    var processed: Bitmap? = null
    var output: Bitmap? = null
    try {
        processed = applyRecipe(source, recipe, inPlaceColor = true)
        output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output!!)
        canvas.drawColor(android.graphics.Color.BLACK)

        val baseScale = if (fill) {
            max(outputWidth.toFloat() / processed!!.width, outputHeight.toFloat() / processed!!.height)
        } else {
            min(outputWidth.toFloat() / processed!!.width, outputHeight.toFloat() / processed!!.height)
        }
        val scale = baseScale * zoom.coerceIn(1f, 4f)
        val drawnWidth = processed!!.width * scale
        val drawnHeight = processed!!.height * scale
        val maxPanX = max(0f, (drawnWidth - outputWidth) / 2f)
        val maxPanY = max(0f, (drawnHeight - outputHeight) / 2f)
        val left = (outputWidth - drawnWidth) / 2f + panX.coerceIn(-1f, 1f) * maxPanX
        val top = (outputHeight - drawnHeight) / 2f + panY.coerceIn(-1f, 1f) * maxPanY
        val destination = RectF(left, top, left + drawnWidth, top + drawnHeight)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(processed!!, null, destination, paint)

        val result = output
        output = null
        return result
    } finally {
        if (processed != null && processed !== source && !processed!!.isRecycled) processed!!.recycle()
        if (!source.isRecycled) source.recycle()
        if (output != null && !output!!.isRecycled) output!!.recycle()
    }
}

@Composable
fun WallpaperStudio(uri: Uri, recipe: Recipe, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val portraitAspect = (min(configuration.screenWidthDp, configuration.screenHeightDp).toFloat() /
        max(configuration.screenWidthDp, configuration.screenHeightDp).coerceAtLeast(1).toFloat())
        .coerceIn(0.50f, 0.70f)

    var portrait by remember(uri) { mutableStateOf(true) }
    var fill by remember(uri) { mutableStateOf(true) }
    var destination by remember(uri) { mutableIntStateOf(WallpaperManager.FLAG_SYSTEM) }
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var pan by remember(uri) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var applying by remember { mutableStateOf(false) }
    var preview by remember(uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri, recipe) {
        val loaded = withContext(Dispatchers.IO) {
            val source = loadScaledBitmap(context, uri, 1600) ?: return@withContext null
            try {
                val result = applyRecipe(source, recipe, inPlaceColor = true)
                if (result !== source && !source.isRecycled) source.recycle()
                result
            } catch (error: Throwable) {
                if (!source.isRecycled) source.recycle()
                throw error
            }
        }
        preview = loaded
    }
    DisposableEffect(preview) {
        val ownedBitmap = preview
        onDispose {
            if (ownedBitmap != null && !ownedBitmap.isRecycled) ownedBitmap.recycle()
        }
    }

    val targetRatio = if (portrait) portraitAspect else 1f / portraitAspect
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Wallpaper Studio", color = Color.White, fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("Prepara tu fondo de pantalla", color = Color(0xFFBDB6C9))
            }
            androidx.compose.material3.TextButton(onClick = onClose) {
                androidx.compose.material3.Icon(Icons.Rounded.Close, contentDescription = null)
                Text("Cerrar", color = Color(0xFFC7A5FF))
            }
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(360.dp),
            contentAlignment = Alignment.Center
        ) {
            val previewHeight = if (portrait) maxHeight else minOf(maxHeight, maxWidth / targetRatio)
            val previewWidth = previewHeight * targetRatio
            Box(
                modifier = Modifier
                    .size(previewWidth, previewHeight)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, panChange, zoomChange, _ ->
                            zoom = (zoom * zoomChange).coerceIn(1f, 4f)
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            pan = androidx.compose.ui.geometry.Offset(
                                (pan.x + panChange.x / (w / 2f)).coerceIn(-1f, 1f),
                                (pan.y + panChange.y / (h / 2f)).coerceIn(-1f, 1f)
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val image = preview
                if (image == null) {
                    CircularProgressIndicator(color = Color(0xFFC7A5FF))
                } else {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = "Vista previa del fondo de pantalla",
                        contentScale = if (fill) ContentScale.Crop else ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            val baseScale = if (fill) {
                                maxOf(size.width / image.width, size.height / image.height)
                            } else {
                                minOf(size.width / image.width, size.height / image.height)
                            }
                            val drawnWidth = image.width * baseScale * zoom
                            val drawnHeight = image.height * baseScale * zoom
                            translationX = pan.x * max(0f, (drawnWidth - size.width) / 2f)
                            translationY = pan.y * max(0f, (drawnHeight - size.height) / 2f)
                            scaleX = zoom
                            scaleY = zoom
                        }
                    )
                }
            }
        }

        Text(
            "Pellizca para ampliar y arrastra para acomodar la fotografía.",
            color = Color(0xFFBDB6C9),
            fontSize = 12.sp
        )

        Text("Orientación", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabButton("Vertical", portrait, Modifier.weight(1f).height(44.dp)) {
                portrait = true
                zoom = 1f
                pan = androidx.compose.ui.geometry.Offset.Zero
            }
            TabButton("Horizontal", !portrait, Modifier.weight(1f).height(44.dp)) {
                portrait = false
                zoom = 1f
                pan = androidx.compose.ui.geometry.Offset.Zero
            }
        }

        Text("Encuadre", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabButton("Rellenar", fill, Modifier.weight(1f).height(44.dp)) {
                fill = true
                zoom = 1f
                pan = androidx.compose.ui.geometry.Offset.Zero
            }
            TabButton("Ajustar", !fill, Modifier.weight(1f).height(44.dp)) {
                fill = false
                zoom = 1f
                pan = androidx.compose.ui.geometry.Offset.Zero
            }
        }
        Text(
            "Rellenar ocupa todo el espacio y recorta lo que sobra. Ajustar conserva la foto completa y utiliza un fondo negro cuando sea necesario.",
            color = Color(0xFFBDB6C9),
            fontSize = 12.sp
        )

        Text("Aplicar en", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabButton("Inicio", destination == WallpaperManager.FLAG_SYSTEM, Modifier.weight(1f).height(44.dp)) {
                destination = WallpaperManager.FLAG_SYSTEM
            }
            TabButton("Bloqueo", destination == WallpaperManager.FLAG_LOCK, Modifier.weight(1f).height(44.dp)) {
                destination = WallpaperManager.FLAG_LOCK
            }
            TabButton("Ambas", destination == (WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK), Modifier.weight(1f).height(44.dp)) {
                destination = WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }
        }

        SmallIconButton(
            if (applying) "Aplicando…" else "Aplicar fondo",
            Icons.Rounded.Wallpaper,
            Modifier.fillMaxWidth().height(52.dp),
            enabled = !applying && preview != null
        ) {
            scope.launch {
                applying = true
                val result = try {
                    withContext(Dispatchers.IO) {
                        val bitmap = buildWallpaperBitmap(
                            context, uri, recipe, portrait, fill, zoom, pan.x, pan.y
                        ) ?: throw IllegalStateException("No se pudo preparar la imagen")
                        try {
                            WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, destination)
                        } finally {
                            if (!bitmap.isRecycled) bitmap.recycle()
                        }
                    }
                    true
                } catch (_: OutOfMemoryError) {
                    false
                } catch (_: Exception) {
                    false
                }
                applying = false
                Toast.makeText(
                    context,
                    if (result) "Fondo de pantalla actualizado" else "No se pudo aplicar el fondo",
                    Toast.LENGTH_LONG
                ).show()
                if (result) onClose()
            }
        }

        SmallIconButton("Volver al editor", Icons.Rounded.Close, Modifier.fillMaxWidth().height(48.dp)) {
            onClose()
        }
        Spacer(Modifier.height(12.dp))
    }
}
