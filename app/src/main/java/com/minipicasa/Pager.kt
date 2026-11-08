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

// ---------- Compartir ----------

fun shareImage(context: Context, uri: Uri) {
    try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Compartir foto")
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "No se pudo compartir esta foto", Toast.LENGTH_SHORT).show()
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
            .background(Color(0x77000000), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
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

@Composable
private fun PhotoPagerScreen(photos: List<Uri>, start: Uri, onClose: () -> Unit) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = photos.indexOf(start).coerceAtLeast(0)
    ) { photos.size }
    var mode by remember { mutableStateOf(0) } // 0 = visor, 1 = editor, 2 = recorte
    var pendingDelete by remember { mutableStateOf<Uri?>(null) }

    val uri = photos[pagerState.currentPage.coerceIn(0, photos.size - 1)]
    val edited = remember(uri, mode) { hasRecipe(context, uri) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingDelete?.let { saveRecipe(context, it, Recipe()) }
            Toast.makeText(context, "Foto enviada a la papelera", Toast.LENGTH_SHORT).show()
            pendingDelete = null
            onClose()
        } else {
            pendingDelete = null
        }
    }

    fun askDelete() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(context, "Borrar requiere Android 11 o superior", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val request = MediaStore.createTrashRequest(context.contentResolver, listOf(uri), true)
            pendingDelete = uri
            deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "No se puede borrar esta foto desde MiniPicasa",
                Toast.LENGTH_LONG
            ).show()
        }
    }

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
                    Pill("Editada", Modifier.align(Alignment.TopEnd).padding(8.dp))
                }
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SmallIconButton("Recortar", Icons.Rounded.ContentCut, Modifier.weight(1f)) { mode = 2 }
                    SmallIconButton("Editar", Icons.Rounded.Edit, Modifier.weight(1f)) { mode = 1 }
                    SmallIconButton("Compartir", Icons.Rounded.Share, Modifier.weight(1f)) { shareImage(context, uri) }
                    DangerIconButton("Borrar", Icons.Rounded.Delete, Modifier.weight(1f)) { askDelete() }
                }
            }
        }
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
    PhotoPagerScreen(photos, start, onClose)
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
