@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.minipicasa

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

// ---------- Recordar la receta de cada foto ----------

private fun recipePrefs(context: Context) =
    context.getSharedPreferences("recipes", Context.MODE_PRIVATE)

fun loadRecipe(context: Context, uri: Uri): Recipe {
    val s = recipePrefs(context).getString(uri.toString(), null) ?: return Recipe()
    return try {
        val p = s.split("|")
        Recipe(
            brightness = p[0].toFloat(),
            contrast = p[1].toFloat(),
            saturation = p[2].toFloat(),
            shadows = p[3].toFloat(),
            effect = p[4].toInt(),
            vignette = p[5].toFloat(),
            turns = p.getOrNull(6)?.toInt() ?: 0,
            cropL = p.getOrNull(7)?.toFloat() ?: 0f,
            cropT = p.getOrNull(8)?.toFloat() ?: 0f,
            cropR = p.getOrNull(9)?.toFloat() ?: 1f,
            cropB = p.getOrNull(10)?.toFloat() ?: 1f
        )
    } catch (e: Exception) {
        Recipe()
    }
}

fun saveRecipe(context: Context, uri: Uri, recipe: Recipe) {
    val edit = recipePrefs(context).edit()
    if (recipe == Recipe()) {
        edit.remove(uri.toString())
    } else {
        edit.putString(
            uri.toString(),
            "${recipe.brightness}|${recipe.contrast}|${recipe.saturation}|" +
                "${recipe.shadows}|${recipe.effect}|${recipe.vignette}|" +
                "${recipe.turns}|${recipe.cropL}|${recipe.cropT}|" +
                "${recipe.cropR}|${recipe.cropB}"
        )
    }
    edit.apply()
}

fun hasRecipe(context: Context, uri: Uri): Boolean =
    recipePrefs(context).contains(uri.toString())

// ---------- Foto con zoom (no bloquea el deslizar entre fotos) ----------

@Composable
fun ZoomImage(uri: Uri) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}

@Composable
fun Pill(text: String, modifier: Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 12.sp,
        modifier = modifier
            .background(Color(0x77000000), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

// ---------- Visor con deslizar entre fotos ----------

@Composable
fun PhotoPager(photos: List<Uri>, start: Uri, onClose: () -> Unit) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = photos.indexOf(start).coerceAtLeast(0)
    ) { photos.size }
    var mode by remember { mutableStateOf(0) } // 0 = visor, 1 = editor, 2 = recorte

    val uri = photos[pagerState.currentPage.coerceIn(0, photos.size - 1)]
    val edited = remember(uri, mode) { hasRecipe(context, uri) }

    when (mode) {
        1 -> {
            BackHandler { mode = 0 }
            Editor(uri) { mode = 0 }
        }
        2 -> CropScreen(uri) { mode = 0 }
        else -> {
            BackHandler(onBack = onClose)
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { photos[it].toString() }
                ) { page ->
                    ZoomImage(photos[page])
                }
                Pill(
                    "${pagerState.currentPage + 1} / ${photos.size}",
                    Modifier.align(Alignment.TopCenter).padding(8.dp)
                )
                if (edited) {
                    Pill("✎ Editada", Modifier.align(Alignment.TopEnd).padding(8.dp))
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = { mode = 2 }) { Text("✂ Recortar") }
                    Button(onClick = { mode = 1 }) { Text("Editar") }
                }
            }
        }
    }
}
