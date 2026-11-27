// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 oracevedo86
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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // Decode to a bounded working size before filtering. A 3072 px cap
    // avoids briefly holding a near-50 MP bitmap for common 8K phone photos.
    val source = loadScaledBitmap(context, uri, 3072) ?: return null
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
    // Match the exact display ratio used when generating the output bitmap.
    val displayMetrics = context.resources.displayMetrics
    val portraitAspect = min(displayMetrics.widthPixels, displayMetrics.heightPixels).toFloat() /
        max(displayMetrics.widthPixels, displayMetrics.heightPixels).coerceAtLeast(1).toFloat()

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
    val colors = MaterialTheme.colorScheme
    val previewPane: @Composable (Modifier) -> Unit = { modifier ->
        BoxWithConstraints(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            val previewHeight = minOf(maxHeight, maxWidth / targetRatio)
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
                        contentDescription = stringResource(R.string.wallpaper_preview_description),
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


    }
    val controlsPane: @Composable () -> Unit = {
        Text(stringResource(R.string.orientation), color = colors.onSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant).selectableGroup().padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            WallpaperSegment(stringResource(R.string.vertical), portrait, Modifier.weight(1f).height(42.dp)) {
                portrait = true
                zoom = 1f
                pan = androidx.compose.ui.geometry.Offset.Zero
            }
            WallpaperSegment(stringResource(R.string.horizontal), !portrait, Modifier.weight(1f).height(42.dp)) {
                portrait = false
                zoom = 1f
                pan = androidx.compose.ui.geometry.Offset.Zero
            }
        }

        Text(stringResource(R.string.framing), color = colors.onSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant).selectableGroup().padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            WallpaperSegment(stringResource(R.string.fill), fill, Modifier.weight(1f).height(42.dp)) {
                fill = true
                zoom = 1f
                pan = androidx.compose.ui.geometry.Offset.Zero
            }
            WallpaperSegment(stringResource(R.string.fit), !fill, Modifier.weight(1f).height(42.dp)) {
                fill = false
                zoom = 1f
                pan = androidx.compose.ui.geometry.Offset.Zero
            }
        }
        Text(
            stringResource(R.string.framing_description),
            color = colors.onSurfaceVariant,
            fontSize = 12.sp
        )

        Text(stringResource(R.string.apply_to), color = colors.onSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant).selectableGroup().padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            WallpaperSegment(stringResource(R.string.home_screen), destination == WallpaperManager.FLAG_SYSTEM, Modifier.weight(1f).height(42.dp)) {
                destination = WallpaperManager.FLAG_SYSTEM
            }
            WallpaperSegment(stringResource(R.string.lock_screen), destination == WallpaperManager.FLAG_LOCK, Modifier.weight(1f).height(42.dp)) {
                destination = WallpaperManager.FLAG_LOCK
            }
            WallpaperSegment(stringResource(R.string.both_screens), destination == (WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK), Modifier.weight(1f).height(42.dp)) {
                destination = WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }
        }

    }
    val applyAction: @Composable () -> Unit = {
        Button(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(48.dp),
            shape = RoundedCornerShape(14.dp),
            enabled = !applying && preview != null,
            onClick = {
            // Snapshot the visible settings so the output matches the preview.
            val selectedPortrait = portrait
            val selectedFill = fill
            val selectedZoom = zoom
            val selectedPan = pan
            val selectedDestination = destination
            scope.launch {
                applying = true
                val result = try {
                    val wallpaperId = withContext(Dispatchers.IO) {
                        val bitmap = buildWallpaperBitmap(
                            context, uri, recipe, selectedPortrait, selectedFill,
                            selectedZoom, selectedPan.x, selectedPan.y
                        ) ?: throw IllegalStateException(context.getString(R.string.wallpaper_prepare_failed))
                        try {
                            WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, selectedDestination)
                        } finally {
                            if (!bitmap.isRecycled) bitmap.recycle()
                        }
                    }
                    wallpaperId > 0
                } catch (_: OutOfMemoryError) {
                    false
                } catch (_: Exception) {
                    false
                }
                applying = false
                Toast.makeText(
                    context,
                    if (result) context.getString(R.string.wallpaper_updated) else context.getString(R.string.wallpaper_apply_failed),
                    Toast.LENGTH_LONG
                ).show()
                if (result) onClose()
            }
        }) {
            Icon(Icons.Rounded.Wallpaper, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (applying) R.string.applying else R.string.apply_wallpaper))
        }
    }
    Column(Modifier.fillMaxSize().background(colors.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, enabled = !applying) {
                Icon(Icons.Rounded.ArrowBack, stringResource(R.string.wallpaper_back))
            }
            Text(stringResource(R.string.wallpaper_studio_title), style = MaterialTheme.typography.titleLarge)
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val availableWidth = maxWidth
            val availableHeight = maxHeight
            if (availableWidth >= 720.dp && availableWidth > availableHeight) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF101013)).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        previewPane(Modifier.weight(1f).fillMaxWidth())
        Text(
            stringResource(R.string.wallpaper_gesture_hint),
            color = colors.onSurfaceVariant,
            fontSize = 12.sp
        )


                    }
                    Column(Modifier.width((availableWidth * 0.40f).coerceIn(300.dp, 420.dp)).fillMaxHeight()) {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) { controlsPane() }
                        applyAction()
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        previewPane(Modifier.fillMaxWidth().height(280.dp))
        Text(
            stringResource(R.string.wallpaper_gesture_hint),
            color = colors.onSurfaceVariant,
            fontSize = 12.sp
        )


                        controlsPane()
                    }
                    applyAction()
                }
            }
        }
    }
}


@Composable
private fun WallpaperSegment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.clip(RoundedCornerShape(9.dp))
            .background(if (selected) colors.primary.copy(alpha = 0.18f) else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) colors.primary else colors.onSurfaceVariant,
            fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}
