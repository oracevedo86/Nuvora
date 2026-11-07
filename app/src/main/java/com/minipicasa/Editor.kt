package com.minipicasa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// La "receta": lo único que guardamos. La foto original no se toca.
data class Recipe(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val shadows: Float = 0f
)

fun Recipe.toColorMatrix(): ColorMatrix {
    val s = saturation
    val c = contrast
    val t = 128f * (1f - c) + brightness
    val r = 0.213f
    val g = 0.715f
    val b = 0.072f
    val a = floatArrayOf(
        r * (1 - s) + s, g * (1 - s), b * (1 - s),
        r * (1 - s), g * (1 - s) + s, b * (1 - s),
        r * (1 - s), g * (1 - s), b * (1 - s) + s
    )
    return ColorMatrix(
        floatArrayOf(
            a[0] * c, a[1] * c, a[2] * c, 0f, t,
            a[3] * c, a[4] * c, a[5] * c, 0f, t,
            a[6] * c, a[7] * c, a[8] * c, 0f, t,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

private fun clamp255(v: Float): Int =
    if (v < 0f) 0 else if (v > 255f) 255 else v.toInt()

// Aplica la receta a un bitmap y devuelve uno nuevo.
fun applyRecipe(src: Bitmap, recipe: Recipe): Bitmap {
    val width = src.width
    val height = src.height
    val px = IntArray(width * height)
    src.getPixels(px, 0, width, 0, 0, width, height)
    val m = recipe.toColorMatrix().values
    val sh = recipe.shadows / 100f

    for (i in px.indices) {
        val p = px[i]
        var r = ((p shr 16) and 0xFF).toFloat()
        var g = ((p shr 8) and 0xFF).toFloat()
        var b = (p and 0xFF).toFloat()

        if (sh != 0f) {
            val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            val weight = (1f - lum) * (1f - lum)
            val d = sh * weight * 70f
            r += d
            g += d
            b += d
        }

        val nr = m[0] * r + m[1] * g + m[2] * b + m[4]
        val ng = m[5] * r + m[6] * g + m[7] * b + m[9]
        val nb = m[10] * r + m[11] * g + m[12] * b + m[14]

        px[i] = (p and 0xFF000000.toInt()) or
            (clamp255(nr) shl 16) or (clamp255(ng) shl 8) or clamp255(nb)
    }
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    out.setPixels(px, 0, width, 0, 0, width, height)
    return out
}

// "Retoque de un toque": calcula una receta a partir de la propia foto.
fun autoRecipe(src: Bitmap): Recipe {
    val width = src.width
    val height = src.height
    val px = IntArray(width * height)
    src.getPixels(px, 0, width, 0, 0, width, height)

    val hist = IntArray(256)
    var sum = 0L
    for (p in px) {
        val lum = (0.299f * ((p shr 16) and 0xFF) +
            0.587f * ((p shr 8) and 0xFF) +
            0.114f * (p and 0xFF)).toInt().coerceIn(0, 255)
        hist[lum]++
        sum += lum
    }
    val total = px.size
    val mean = sum.toFloat() / total

    val cut = total * 0.01f
    var acc = 0
    var lo = 0
    var hi = 255
    for (i in 0..255) {
        acc += hist[i]
        if (acc >= cut) { lo = i; break }
    }
    acc = 0
    for (i in 255 downTo 0) {
        acc += hist[i]
        if (acc >= cut) { hi = i; break }
    }
    if (hi - lo < 30) { lo = 0; hi = 255 }

    val c = (255f / (hi - lo)).coerceIn(0.8f, 1.5f)
    val b = (-c * lo - 128f * (1f - c)).coerceIn(-100f, 100f)
    val shadows = ((110f - mean) / 110f * 100f).coerceIn(0f, 60f)
    return Recipe(brightness = b, contrast = c, saturation = 1.15f, shadows = shadows)
}

// Carga la foto reducida y bien girada.
fun loadScaledBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2

    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    var bmp = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, opts)
    } ?: return null

    val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
    val matrix = Matrix()
    if (scale < 1f) matrix.postScale(scale, scale)

    val orientation = context.contentResolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
    } ?: ExifInterface.ORIENTATION_NORMAL
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
    }
    bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    return bmp
}

@Composable
fun PhotoScreen(uri: Uri, onClose: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        BackHandler { editing = false }
        Editor(uri) { editing = false }
    } else {
        Box(Modifier.fillMaxSize()) {
            Viewer(uri, onClose)
            Button(
                onClick = { editing = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) { Text("Editar") }
        }
    }
}

@Composable
fun SmallButton(
    text: String,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) { Text(text, fontSize = 12.sp, maxLines = 1) }
}

@Composable
fun Editor(uri: Uri, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var recipe by remember { mutableStateOf(Recipe()) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        original = withContext(Dispatchers.IO) { loadScaledBitmap(context, uri, 900) }
    }
    LaunchedEffect(original, recipe) {
        val src = original ?: return@LaunchedEffect
        preview = withContext(Dispatchers.Default) {
            applyRecipe(src, recipe).asImageBitmap()
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            preview?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(Modifier.background(Color(0xFF222222)).padding(16.dp)) {
            SliderRow("Brillo", recipe.brightness, -100f..100f) {
                recipe = recipe.copy(brightness = it)
            }
            SliderRow("Sombras", recipe.shadows, -100f..100f) {
                recipe = recipe.copy(shadows = it)
            }
            SliderRow("Contraste", recipe.contrast, 0.5f..1.5f) {
                recipe = recipe.copy(contrast = it)
            }
            SliderRow("Saturación", recipe.saturation, 0f..2f) {
                recipe = recipe.copy(saturation = it)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SmallButton("✨ Retoque", Modifier.weight(1f)) {
                    original?.let { recipe = autoRecipe(it) }
                }
                SmallButton("Restablecer", Modifier.weight(1.2f)) {
                    recipe = Recipe()
                }
                SmallButton(
                    if (saving) "Guardando…" else "💾 Guardar",
                    Modifier.weight(1.2f),
                    enabled = !saving
                ) {
                    scope.launch {
                        saving = true
                        val ok = withContext(Dispatchers.Default) {
                            saveEdited(context, uri, recipe)
                        }
                        saving = false
                        Toast.makeText(
                            context,
                            if (ok) "Copia guardada en Pictures/MiniPicasa"
                            else "No se pudo guardar la copia",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                SmallButton("Cerrar", Modifier.weight(1f)) { onClose() }
            }
        }
    }
}

@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Text(label, color = Color.White)
    Slider(value = value, onValueChange = onChange, valueRange = range)
}
