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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
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
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// La "receta": lo único que guardamos. La foto original no se toca.
data class Recipe(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val shadows: Float = 0f,
    val effect: Int = 0,        // 0 = ninguno, 1 = sepia, 2 = blanco y negro
    val vignette: Float = 0f,   // 0 a 100
    val turns: Int = 0,         // giros de 90° a la derecha (0 a 3)
    val cropL: Float = 0f,      // recorte, en fracciones (0 a 1)
    val cropT: Float = 0f,
    val cropR: Float = 1f,
    val cropB: Float = 1f,
    val flipHorizontal: Boolean = false
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
fun applyColor(src: Bitmap, recipe: Recipe): Bitmap {
    val width = src.width
    val height = src.height
    val px = IntArray(width * height)
    src.getPixels(px, 0, width, 0, 0, width, height)
    val m = recipe.toColorMatrix().values
    val sh = recipe.shadows / 100f
    val effect = recipe.effect
    val v = recipe.vignette / 100f
    val cx = (width - 1) / 2f
    val cy = (height - 1) / 2f
    val maxD = sqrt(cx * cx + cy * cy).let { if (it == 0f) 1f else it }

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

        var nr = (m[0] * r + m[1] * g + m[2] * b + m[4]).coerceIn(0f, 255f)
        var ng = (m[5] * r + m[6] * g + m[7] * b + m[9]).coerceIn(0f, 255f)
        var nb = (m[10] * r + m[11] * g + m[12] * b + m[14]).coerceIn(0f, 255f)

        when (effect) {
            1 -> {
                val sr = 0.393f * nr + 0.769f * ng + 0.189f * nb
                val sg = 0.349f * nr + 0.686f * ng + 0.168f * nb
                val sb = 0.272f * nr + 0.534f * ng + 0.131f * nb
                nr = sr.coerceAtMost(255f)
                ng = sg.coerceAtMost(255f)
                nb = sb.coerceAtMost(255f)
            }
            2 -> {
                val gray = 0.299f * nr + 0.587f * ng + 0.114f * nb
                nr = gray
                ng = gray
                nb = gray
            }
        }

        if (v > 0f) {
            val dx = (i % width) - cx
            val dy = (i / width) - cy
            val dist = sqrt(dx * dx + dy * dy) / maxD
            val t = ((dist - 0.35f) / 0.65f).coerceIn(0f, 1f)
            val f = 1f - v * t * t
            nr *= f
            ng *= f
            nb *= f
        }

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
fun SmallIconButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = Brand.Violet,
            contentColor = Color.White,
            disabledContainerColor = Brand.Violet.copy(alpha = 0.38f),
            disabledContentColor = Color.White.copy(alpha = 0.70f)
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun TabButton(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Brand.Violet else Color(0xFF444444),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) { Text(text, fontSize = 12.sp, maxLines = 1) }
}

@Composable
fun Editor(uri: Uri, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var recipe by remember(uri) { mutableStateOf(loadRecipe(context, uri)) }
LaunchedEffect(recipe) { saveRecipe(context, uri, recipe) }
    var saving by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(uri) {
        original = withContext(Dispatchers.IO) { loadScaledBitmap(context, uri, 900) }
    }
    LaunchedEffect(original, recipe) {
        val src = original ?: return@LaunchedEffect
        preview = withContext(Dispatchers.Default) {
            applyRecipe(src, recipe).asImageBitmap()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val tabletLayout = maxWidth >= 840.dp
        val panelPadding = if (tabletLayout) 20.dp else 10.dp
        val panelHorizontalPadding = if (tabletLayout) 18.dp else 14.dp
        val panelSpacing = if (tabletLayout) 16.dp else 8.dp
        val tabHeight = if (tabletLayout) 48.dp else 40.dp
        val actionHeight = if (tabletLayout) 48.dp else 40.dp
        val editorControls: @Composable (Modifier) -> Unit = { panelModifier ->
            Column(
                panelModifier
                    .background(Color(0xFF1E1D24))
                    .padding(horizontal = panelHorizontalPadding, vertical = panelPadding),
                verticalArrangement = Arrangement.spacedBy(panelSpacing)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TabButton("Ajustes", tab == 0, Modifier.weight(1f).height(tabHeight)) { tab = 0 }
                    TabButton("Efectos", tab == 1, Modifier.weight(1f).height(tabHeight)) { tab = 1 }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (tab == 0) {
                        Text(
                            "LUZ Y COLOR",
                            color = Color(0xFFBDB6C9),
                            fontSize = 11.sp,
                            letterSpacing = 1.2.sp
                        )
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
                    } else {
                        Text(
                            "ESTILO FOTOGRÁFICO",
                            color = Color(0xFFBDB6C9),
                            fontSize = 11.sp,
                            letterSpacing = 1.2.sp
                        )
                        Text("Efecto de color", color = Color.White, fontSize = 14.sp)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TabButton("Original", recipe.effect == 0, Modifier.weight(1f).height(44.dp)) {
                                recipe = recipe.copy(effect = 0)
                            }
                            TabButton("Sepia", recipe.effect == 1, Modifier.weight(1f).height(44.dp)) {
                                recipe = recipe.copy(effect = 1)
                            }
                            TabButton("B/N", recipe.effect == 2, Modifier.weight(1f).height(44.dp)) {
                                recipe = recipe.copy(effect = 2)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        SliderRow("Viñeta", recipe.vignette, 0f..100f) {
                            recipe = recipe.copy(vignette = it)
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                Button(
                    onClick = {
                        Toast.makeText(
                            context,
                            "La biblioteca de Recetas llegará próximamente ✨",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(if (tabletLayout) 54.dp else 46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6330A9),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.AutoFixHigh, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Explorar Recetas", fontSize = 14.sp)
                        Text(
                            "Descubre nuevos estilos para tus fotografías",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 1
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Abrir Recetas")
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                if (tabletLayout) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallIconButton("Retoque", Icons.Rounded.AutoFixHigh, Modifier.weight(1f).height(48.dp)) {
                                original?.let {
                                    recipe = autoRecipe(it).copy(
                                        turns = recipe.turns, cropL = recipe.cropL, cropT = recipe.cropT,
                                        cropR = recipe.cropR, cropB = recipe.cropB,
                                        flipHorizontal = recipe.flipHorizontal,
                                        effect = recipe.effect, vignette = recipe.vignette
                                    )
                                }
                            }
                            SmallIconButton("Restablecer", Icons.Rounded.Refresh, Modifier.weight(1f).height(48.dp)) {
                                recipe = Recipe()
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallIconButton(
                                if (saving) "Guardando…" else "Guardar",
                                Icons.Rounded.Save,
                                Modifier.weight(1f).height(48.dp),
                                enabled = !saving
                            ) {
                                scope.launch {
                                    saving = true
                                    val ok = withContext(Dispatchers.Default) { saveEdited(context, uri, recipe) }
                                    saving = false
                                    Toast.makeText(
                                        context,
                                        if (ok) "Copia guardada en Pictures/Nuvora" else "No se pudo guardar la copia",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            SmallIconButton("Cerrar", Icons.Rounded.Close, Modifier.weight(1f).height(48.dp)) {
                                onClose()
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallIconButton("Retoque", Icons.Rounded.AutoFixHigh, Modifier.weight(1f).height(actionHeight)) {
                                original?.let {
                                    recipe = autoRecipe(it).copy(
                                        turns = recipe.turns, cropL = recipe.cropL, cropT = recipe.cropT,
                                        cropR = recipe.cropR, cropB = recipe.cropB,
                                        flipHorizontal = recipe.flipHorizontal,
                                        effect = recipe.effect, vignette = recipe.vignette
                                    )
                                }
                            }
                            SmallIconButton("Restablecer", Icons.Rounded.Refresh, Modifier.weight(1f).height(actionHeight)) {
                                recipe = Recipe()
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallIconButton(
                                if (saving) "Guardando…" else "Guardar",
                                Icons.Rounded.Save,
                                Modifier.weight(1f).height(actionHeight),
                                enabled = !saving
                            ) {
                                scope.launch {
                                    saving = true
                                    val ok = withContext(Dispatchers.Default) { saveEdited(context, uri, recipe) }
                                    saving = false
                                    Toast.makeText(
                                        context,
                                        if (ok) "Copia guardada en Pictures/Nuvora" else "No se pudo guardar la copia",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            SmallIconButton("Cerrar", Icons.Rounded.Close, Modifier.weight(1f).height(actionHeight)) {
                                onClose()
                            }
                        }
                    }
                }
            }
        }
        val photoPane: @Composable (Modifier) -> Unit = { paneModifier ->
            Box(paneModifier) {
                preview?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        if (tabletLayout) {
            Row(Modifier.fillMaxSize()) {
                photoPane(Modifier.weight(1f).fillMaxHeight())
                editorControls(Modifier.width(400.dp).fillMaxHeight())
            }
        } else {
            // En teléfonos reservamos espacio real para la fotografía y limitamos
            // el panel para que los controles no la expulsen fuera de pantalla.
            Column(Modifier.fillMaxSize()) {
                photoPane(Modifier.weight(0.42f).fillMaxWidth())
                editorControls(Modifier.weight(0.58f).fillMaxWidth())
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
    val displayValue = if (range.start % 1f == 0f && range.endInclusive % 1f == 0f) {
        value.toInt().toString()
    } else {
        String.format(java.util.Locale.getDefault(), "%.2f", value)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color(0xFFF2EFF7), fontSize = 14.sp)
            Text(displayValue, color = Color(0xFFBFA1F5), fontSize = 12.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
