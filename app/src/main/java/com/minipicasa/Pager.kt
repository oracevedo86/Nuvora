// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 oracevedo86
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.minipicasa
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.res.stringResource
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
            warmth = p.getOrNull(12)?.toFloat() ?: 0f,
            shadows = p[3].toFloat(),
            effect = p[4].toInt(),
            vignette = p[5].toFloat(),
            turns = p.getOrNull(6)?.toInt() ?: 0,
            cropL = p.getOrNull(7)?.toFloat() ?: 0f,
            cropT = p.getOrNull(8)?.toFloat() ?: 0f,
            cropR = p.getOrNull(9)?.toFloat() ?: 1f,
            cropB = p.getOrNull(10)?.toFloat() ?: 1f,
            flipHorizontal = p.getOrNull(11)?.toBooleanStrictOrNull() ?: false
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
                "${recipe.cropR}|${recipe.cropB}|${recipe.flipHorizontal}|${recipe.warmth}"
        )
    }
    edit.apply()
}

fun hasRecipe(context: Context, uri: Uri): Boolean =
    recipePrefs(context).contains(uri.toString())

// ---------- Compartir ----------

fun shareImage(context: Context, uri: Uri) {
    try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.share_photo))
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.share_photo_failed), Toast.LENGTH_SHORT).show()
    }
}

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
            .background(Color(0x99000000), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
fun DangerIconButton(iconText: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFB3261E),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(iconText, fontSize = 12.sp, maxLines = 1)
    }
}

// ---------- Visor con deslizar entre fotos ----------

private fun photoDateLabel(context: Context, uri: Uri): Pair<String, String>? {
    return try {
        val projection = arrayOf(
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val takenIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
            val modifiedIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
            val taken = if (takenIndex >= 0 && !cursor.isNull(takenIndex)) cursor.getLong(takenIndex) else 0L
            val modified = if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) cursor.getLong(modifiedIndex) * 1000L else 0L
            val timestamp = if (taken > 0L) taken else modified
            if (timestamp <= 0L) return null
            val date = Date(timestamp)
            DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault()).format(date) to
                DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(date)
        }
    } catch (_: Exception) {
        null
    }
}

private data class PhotoDetails(
    val name: String,
    val size: String,
    val resolution: String,
    val dateLabel: String,
    val dateValue: String
)

private fun readPhotoDetails(context: Context, uri: Uri): PhotoDetails? {
    return try {
        val columns = arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED
        )
        context.contentResolver.query(uri, columns, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            fun value(column: String): Long {
                val i = c.getColumnIndex(column)
                return if (i >= 0 && !c.isNull(i)) c.getLong(i) else 0L
            }
            val nameIndex = c.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val name = if (nameIndex >= 0) c.getString(nameIndex) ?: context.getString(R.string.unnamed_photo) else context.getString(R.string.unnamed_photo)
            val bytes = value(MediaStore.Images.Media.SIZE)
            val width = value(MediaStore.Images.Media.WIDTH)
            val height = value(MediaStore.Images.Media.HEIGHT)
            val taken = value(MediaStore.Images.Media.DATE_TAKEN)
            val added = value(MediaStore.Images.Media.DATE_ADDED) * 1000L
            val stamp = if (taken > 0L) taken else added
            val date = if (stamp > 0L)
                DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT, Locale.getDefault()).format(Date(stamp))
            else context.getString(R.string.not_available)
            PhotoDetails(
                name,
                if (bytes > 0L) String.format(Locale.getDefault(), "%.2f MB", bytes / 1048576.0) else context.getString(R.string.not_available),
                if (width > 0L && height > 0L) context.getString(R.string.photo_resolution, width, height) else context.getString(R.string.not_available),
                if (taken > 0L) context.getString(if (taken > 0L) R.string.date_taken else R.string.date_added),
                date
            )
        }
    } catch (_: Exception) { null }
}

@Composable
private fun PhotoPagerScreen(photos: List<Uri>, start: Uri, onClose: () -> Unit) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = photos.indexOf(start).coerceAtLeast(0)
    ) { photos.size }
    var mode by remember { mutableStateOf(0) } // 0 = visor, 1 = editor, 2 = recorte, 3 = fondo
    var wallpaperRecipe by remember { mutableStateOf(Recipe()) }
    var wallpaperReturnsToEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Uri?>(null) }
    var showDetails by remember { mutableStateOf(false) }

    val uri = photos[pagerState.currentPage.coerceIn(0, photos.size - 1)]
    val edited = remember(uri, mode) { hasRecipe(context, uri) }
    val photoDate = remember(uri) { photoDateLabel(context, uri) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingDelete?.let { saveRecipe(context, it, Recipe()) }
            Toast.makeText(context, context.getString(R.string.photo_trashed), Toast.LENGTH_SHORT).show()
            pendingDelete = null
            onClose()
        } else {
            pendingDelete = null
        }
    }

    fun askDelete() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(context, context.getString(R.string.delete_requires_android_11), Toast.LENGTH_LONG).show()
            return
        }
        try {
            val request = MediaStore.createTrashRequest(context.contentResolver, listOf(uri), true)
            pendingDelete = uri
            deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } catch (e: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.cannot_delete_photo),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    if (showDetails) {
        val details = remember(uri, showDetails) { readPhotoDetails(context, uri) }
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(stringResource(R.string.photo_details)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.name), color = Color(0xFFBFA1F5), fontSize = 12.sp)
                    Text(details?.name ?: stringResource(R.string.not_available))
                    Text(stringResource(R.string.size), color = Color(0xFFBFA1F5), fontSize = 12.sp)
                    Text(details?.size ?: stringResource(R.string.not_available))
                    Text(stringResource(R.string.resolution), color = Color(0xFFBFA1F5), fontSize = 12.sp)
                    Text(details?.resolution ?: stringResource(R.string.not_available))
                    Text(details?.dateLabel ?: stringResource(R.string.date), color = Color(0xFFBFA1F5), fontSize = 12.sp)
                    Text(details?.dateValue ?: stringResource(R.string.not_available))
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showDetails = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    when (mode) {
        1 -> {
            BackHandler { mode = 0 }
            Editor(
                uri = uri,
                onClose = {
                    wallpaperReturnsToEditor = false
                    mode = 0
                },
                onWallpaper = { currentRecipe ->
                    wallpaperRecipe = currentRecipe
                    wallpaperReturnsToEditor = true
                    mode = 3
                },
                initialRecipe = if (wallpaperReturnsToEditor) wallpaperRecipe else null
            )
        }
        2 -> CropScreen(uri) { mode = 0 }
        3 -> {
            BackHandler { mode = if (wallpaperReturnsToEditor) 1 else 0 }
            WallpaperStudio(uri, wallpaperRecipe) {
                mode = if (wallpaperReturnsToEditor) 1 else 0
            }
        }
        else -> {
            BackHandler(onBack = onClose)
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().padding(bottom = 132.dp),
                    key = { photos[it].toString() }
                ) { page ->
                    ZoomImage(photos[page])
                }

                // Encabezado discreto: fecha/hora y posición dentro de la carpeta.
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier
                            .background(Color(0x990D0D12), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(
                            photoDate?.first ?: stringResource(R.string.photo),
                            color = Color.White,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                        Text(
                            photoDate?.second ?: "",
                            color = Color(0xFFBDB8C8),
                            fontSize = 12.sp
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill("${pagerState.currentPage + 1} / ${photos.size}", Modifier)
                        if (edited) Pill(stringResource(R.string.edited), Modifier)
                    }
                }

                // Acciones distribuidas en dos filas para incluir Wallpaper Studio.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xEE19191F))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallIconButton(stringResource(R.string.info), Icons.Rounded.Info, Modifier.weight(1f)) { showDetails = true }
                        SmallIconButton(stringResource(R.string.crop), Icons.Rounded.ContentCut, Modifier.weight(1f)) { mode = 2 }
                        SmallIconButton(stringResource(R.string.edit), Icons.Rounded.Edit, Modifier.weight(1f)) { wallpaperReturnsToEditor = false; mode = 1 }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallIconButton(stringResource(R.string.wallpaper), Icons.Rounded.Wallpaper, Modifier.weight(1f)) {
                            wallpaperRecipe = loadRecipe(context, uri)
                            wallpaperReturnsToEditor = false
                            mode = 3
                        }
                        SmallIconButton(stringResource(R.string.share), Icons.Rounded.Share, Modifier.weight(1f)) {
                            shareImage(context, uri)
                        }
                        DangerIconButton(stringResource(R.string.delete), Icons.Rounded.Delete, Modifier.weight(1f)) { askDelete() }
                    }
                }
            }        }
    }
}

// ---------- Pantallas de foto: siempre oscuras, con barras negras ----------

@Composable
fun ImmersiveBars() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val oldStatus = window.statusBarColor
            val oldNav = window.navigationBarColor
            val oldLightStatus = controller.isAppearanceLightStatusBars
            val oldLightNav = controller.isAppearanceLightNavigationBars
            window.statusBarColor = Color.Black.toArgb()
            window.navigationBarColor = Color.Black.toArgb()
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            onDispose {
                window.statusBarColor = oldStatus
                window.navigationBarColor = oldNav
                controller.isAppearanceLightStatusBars = oldLightStatus
                controller.isAppearanceLightNavigationBars = oldLightNav
            }
        }
    }
}

@Composable
fun PhotoPagerContent(
    photos: List<Uri>,
    start: Uri,
    onClose: () -> Unit
) {
    ImmersiveBars()
    MaterialTheme(colorScheme = GraphiteColors) {
        PhotoPagerScreen(photos, start, onClose)
    }
}

// ---------- Cuando otra app abre una foto con Nuvora ----------

@Composable
fun Root(openUri: Uri?) {
    val context = LocalContext.current
    if (openUri == null) {
        App()
    } else {
        val prefs by rememberAppPrefs()
        NuvoraTheme(appearance = prefs.appearance) {
            Surface(Modifier.fillMaxSize()) {
                PhotoPagerContent(listOf(openUri), openUri) { (context as? Activity)?.finish() }
            }
        }
    }
}
