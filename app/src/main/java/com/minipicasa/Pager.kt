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
            vignette = p[5].toFloat()
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
                "${recipe.shadows}|${recipe.effect}|${recipe.vignette}"
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

// ---------- Visor con deslizar entre fotos ----------

@Composable
fun PhotoPager(photos: List<Uri>, start: Uri, onClose: () -> Unit) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = photos.indexOf(start).coerceAtLeast(0)
    ) { photos.size }
    var editing by remember { mutableStateOf(false) }

    val uri = photos[pagerState.currentPage.coerceIn(0, photos.size - 1)]
    val edited = remember(uri, editing) { hasRecipe(context, uri) }

    if (editing) {
        BackHandler { editing = false }
        Editor(uri) { editing = false }
    } else {
        BackHandler(onBack = onClose)
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { photos[it].toString() }
            ) { page ->
                ZoomImage(photos[page])
            }
            Text(
                "${pagerState.currentPage + 1} / ${photos.size}",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
            )
            if (edited) {
                Text(
                    "✎ Editada",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                )
            }
            Button(
                onClick = { editing = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) { Text("Editar") }
        }
    }
}
