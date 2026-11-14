// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 oracevedo86
package com.minipicasa

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Recuadro de recorte, en fracciones (0 a 1) de la foto ya girada.
data class CropRect(val l: Float, val t: Float, val r: Float, val b: Float)

fun rotateOnly(src: Bitmap, turns: Int): Bitmap {
    val k = ((turns % 4) + 4) % 4
    if (k == 0) return src
    val m = Matrix().apply { postRotate(90f * k) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

// Refleja la imagen horizontalmente (efecto espejo).
fun flipHorizontal(src: Bitmap): Bitmap {
    val matrix = Matrix().apply { preScale(-1f, 1f) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
}

// Primero gira, luego refleja y finalmente recorta.
fun applyGeometry(src: Bitmap, recipe: Recipe): Bitmap {
    var bmp = rotateOnly(src, recipe.turns)
    try {
        if (recipe.flipHorizontal) {
            val flipped = flipHorizontal(bmp)
            if (bmp !== src && !bmp.isRecycled) bmp.recycle()
            bmp = flipped
        }
        val full = recipe.cropL <= 0.001f && recipe.cropT <= 0.001f &&
            recipe.cropR >= 0.999f && recipe.cropB >= 0.999f
        if (!full) {
            val l = (recipe.cropL * bmp.width).toInt().coerceIn(0, bmp.width - 1)
            val t = (recipe.cropT * bmp.height).toInt().coerceIn(0, bmp.height - 1)
            val r = (recipe.cropR * bmp.width).toInt().coerceIn(l + 1, bmp.width)
            val b = (recipe.cropB * bmp.height).toInt().coerceIn(t + 1, bmp.height)
            val cropped = Bitmap.createBitmap(bmp, l, t, r - l, b - t)
            if (bmp !== src && cropped !== bmp && !bmp.isRecycled) bmp.recycle()
            bmp = cropped
        }
        return bmp
    } catch (error: Throwable) {
        if (bmp !== src && !bmp.isRecycled) bmp.recycle()
        throw error
    }
}

// La receta completa: geometría primero, colores y efectos después.
// inPlaceColor permite que la exportación modifique el bitmap geométrico
// directamente, evitando una copia adicional de una foto de alta resolución.
fun applyRecipe(
    src: Bitmap,
    recipe: Recipe,
    inPlaceColor: Boolean = false,
    cancellationCheck: (() -> Unit)? = null
): Bitmap {
    val geometric = applyGeometry(src, recipe)
    return try {
        val colored = applyColor(
            geometric,
            recipe,
            inPlace = inPlaceColor,
            cancellationCheck = cancellationCheck
        )
        // When color processing copied the geometric result, release the intermediate.
        if (geometric !== src && colored !== geometric && !geometric.isRecycled) {
            geometric.recycle()
        }
        colored
    } catch (error: Throwable) {
        if (geometric !== src && !geometric.isRecycled) geometric.recycle()
        throw error
    }
}

// corner: 0 = sup. izq., 1 = sup. der., 2 = inf. izq., 3 = inf. der.
fun dragCorner(
    c: CropRect,
    corner: Int,
    dx: Float,
    dy: Float,
    ratio: Float?,
    bw: Float,
    bh: Float
): CropRect {
    val left = corner == 0 || corner == 2
    val top = corner == 0 || corner == 1
    val ax = if (left) c.r else c.l
    val ay = if (top) c.b else c.t
    var mx = (if (left) c.l else c.r) + dx
    var my = (if (top) c.t else c.b) + dy
    val minF = 0.08f

    if (ratio == null) {
        mx = if (left) mx.coerceIn(0f, ax - minF) else mx.coerceIn(ax + minF, 1f)
        my = if (top) my.coerceIn(0f, ay - minF) else my.coerceIn(ay + minF, 1f)
    } else {
        val k = ratio * bh / bw
        val availW = if (left) ax else 1f - ax
        val availH = if (top) ay else 1f - ay
        var w = maxOf(abs(mx - ax), abs(my - ay) * k)
        w = minOf(w, availW, availH * k)
        w = maxOf(w, minF)
        val h = w / k
        mx = if (left) ax - w else ax + w
        my = if (top) ay - h else ay + h
    }
    return CropRect(
        minOf(ax, mx).coerceIn(0f, 1f),
        minOf(ay, my).coerceIn(0f, 1f),
        maxOf(ax, mx).coerceIn(0f, 1f),
        maxOf(ay, my).coerceIn(0f, 1f)
    )
}

fun moveRect(c: CropRect, dx: Float, dy: Float): CropRect {
    val w = c.r - c.l
    val h = c.b - c.t
    val l = (c.l + dx).coerceIn(0f, 1f - w)
    val t = (c.t + dy).coerceIn(0f, 1f - h)
    return CropRect(l, t, l + w, t + h)
}

// Recuadro centrado y lo más grande posible con esa proporción (ancho/alto).
fun rectForRatio(ratio: Float, bw: Float, bh: Float): CropRect {
    val ia = bw / bh
    val wf: Float
    val hf: Float
    if (ratio >= ia) {
        wf = 1f
        hf = ia / ratio
    } else {
        hf = 1f
        wf = ratio / ia
    }
    val l = (1f - wf) / 2f
    val t = (1f - hf) / 2f
    return CropRect(l, t, l + wf, t + hf)
}

@Composable
fun CropScreen(uri: Uri, onDone: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onDone)

    var recipe by remember(uri) { mutableStateOf(loadRecipe(context, uri)) }
    var base by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var crop by remember(uri) {
        mutableStateOf(CropRect(recipe.cropL, recipe.cropT, recipe.cropR, recipe.cropB))
    }
    var ratio by remember(uri) { mutableStateOf<Float?>(null) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(uri) {
        base = withContext(Dispatchers.IO) { loadScaledBitmap(context, uri, 1200) }
    }
    val shown = remember(base, recipe.turns, recipe.flipHorizontal) {
        base?.let { source ->
            val rotated = rotateOnly(source, recipe.turns)
            if (recipe.flipHorizontal) flipHorizontal(rotated) else rotated
        }
    }

    fun pickRatio(v: Float) {
        val s = shown ?: return
        val rr = if (s.height > s.width && v > 1f) 1f / v else v
        ratio = rr
        crop = rectForRatio(rr, s.width.toFloat(), s.height.toFloat())
    }

    val cropControls: @Composable (Modifier) -> Unit = { panelModifier ->
        Column(
            panelModifier.background(Color(0xFF222222)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val r = ratio
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TabButton("Libre", r == null, Modifier.weight(1f)) { ratio = null }
                listOf(
                    "1:1" to 1f,
                    "4:3" to 4f / 3f,
                    "3:2" to 3f / 2f,
                    "16:9" to 16f / 9f
                ).forEach { (name, v) ->
                    val active = r != null && (abs(r - v) < 0.01f || abs(r - 1f / v) < 0.01f)
                    TabButton(name, active, Modifier.weight(1f)) { pickRatio(v) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallIconButton("Girar", Icons.Rounded.RotateLeft, Modifier.weight(1f)) {
                    recipe = recipe.copy(turns = (recipe.turns + 3) % 4)
                    crop = CropRect(crop.t, 1f - crop.r, crop.b, 1f - crop.l)
                    ratio = ratio?.let { 1f / it }
                }
                SmallIconButton("Girar", Icons.Rounded.RotateRight, Modifier.weight(1f)) {
                    recipe = recipe.copy(turns = (recipe.turns + 1) % 4)
                    crop = CropRect(1f - crop.b, crop.l, 1f - crop.t, crop.r)
                    ratio = ratio?.let { 1f / it }
                }
                SmallIconButton("Invertir", Icons.Rounded.Flip, Modifier.weight(1f)) {
                    recipe = recipe.copy(flipHorizontal = !recipe.flipHorizontal)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallIconButton("Restablecer", Icons.Rounded.Refresh, Modifier.weight(1f)) {
                    recipe = recipe.copy(turns = 0, flipHorizontal = false)
                    crop = CropRect(0f, 0f, 1f, 1f)
                    ratio = null
                }
                SmallIconButton("Cancelar", Icons.Rounded.Close, Modifier.weight(1f)) { onDone() }
                SmallIconButton("Listo", Icons.Rounded.Done, Modifier.weight(1f)) {
                    saveRecipe(
                        context, uri,
                        recipe.copy(
                            cropL = crop.l, cropT = crop.t,
                            cropR = crop.r, cropB = crop.b
                        )
                    )
                    onDone()
                }
            }
        }
    }

    val cropPhoto: @Composable (Modifier) -> Unit = { photoModifier ->
        Box(
            photoModifier
                .onSizeChanged { boxSize = it }
        ) {
            val bmp = shown
            if (bmp != null && boxSize.width > 0) {
                val bw = bmp.width.toFloat()
                val bh = bmp.height.toFloat()
                val boxW = boxSize.width.toFloat()
                val boxH = boxSize.height.toFloat()
                val scale = minOf(boxW / bw, boxH / bh)
                val dw = bw * scale
                val dh = bh * scale
                val ox = (boxW - dw) / 2f
                val oy = (boxH - dh) / 2f

                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(bmp, boxSize) {
                            var mode = -1
                            detectDragGestures(
                                onDragStart = { p ->
                                    val c = crop
                                    val corners = listOf(
                                        Offset(ox + c.l * dw, oy + c.t * dh),
                                        Offset(ox + c.r * dw, oy + c.t * dh),
                                        Offset(ox + c.l * dw, oy + c.b * dh),
                                        Offset(ox + c.r * dw, oy + c.b * dh)
                                    )
                                    val reach = 56.dp.toPx()
                                    var best = -1
                                    var bestD = Float.MAX_VALUE
                                    corners.forEachIndexed { i, o ->
                                        val d = (o - p).getDistance()
                                        if (d < bestD) {
                                            bestD = d
                                            best = i
                                        }
                                    }
                                    val inside = p.x >= ox + c.l * dw && p.x <= ox + c.r * dw &&
                                        p.y >= oy + c.t * dh && p.y <= oy + c.b * dh
                                    mode = if (bestD <= reach) best else if (inside) 4 else -1
                                },
                                onDrag = { change, drag ->
                                    change.consume()
                                    val fx = drag.x / dw
                                    val fy = drag.y / dh
                                    crop = when (mode) {
                                        0, 1, 2, 3 -> dragCorner(crop, mode, fx, fy, ratio, bw, bh)
                                        4 -> moveRect(crop, fx, fy)
                                        else -> crop
                                    }
                                }
                            )
                        }
                ) {
                    val c = crop
                    val l = ox + c.l * dw
                    val t = oy + c.t * dh
                    val r = ox + c.r * dw
                    val b = oy + c.b * dh
                    val shade = Color(0xAA000000)
                    drawRect(shade, Offset(ox, oy), Size(dw, t - oy))
                    drawRect(shade, Offset(ox, b), Size(dw, oy + dh - b))
                    drawRect(shade, Offset(ox, t), Size(l - ox, b - t))
                    drawRect(shade, Offset(r, t), Size(ox + dw - r, b - t))
                    drawRect(
                        Color.White, Offset(l, t), Size(r - l, b - t),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    for (i in 1..2) {
                        val x = l + (r - l) * i / 3f
                        val y = t + (b - t) * i / 3f
                        drawLine(Color(0x88FFFFFF), Offset(x, t), Offset(x, b), 1.dp.toPx())
                        drawLine(Color(0x88FFFFFF), Offset(l, y), Offset(r, y), 1.dp.toPx())
                    }
                    listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach {
                        drawCircle(Color.White, 8.dp.toPx(), it)
                    }
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        if (maxWidth >= 840.dp) {
            Row(Modifier.fillMaxSize()) {
                cropPhoto(Modifier.weight(1f).fillMaxHeight())
                cropControls(Modifier.width(360.dp).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                cropPhoto(Modifier.weight(1f).fillMaxWidth())
                cropControls(Modifier.fillMaxWidth())
            }
        }
    }
}
