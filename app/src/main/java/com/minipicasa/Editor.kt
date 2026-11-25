// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 oracevedo86
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material3.ExperimentalMaterial3Api
import kotlin.math.roundToInt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Wallpaper
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// La "receta": lo único que guardamos. La foto original no se toca.
data class Recipe(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val warmth: Float = 0f,     // -100 frío, 0 neutro, 100 cálido
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
fun applyColor(
    src: Bitmap,
    recipe: Recipe,
    inPlace: Boolean = false,
    cancellationCheck: (() -> Unit)? = null
): Bitmap {
    // Procesa una fila a la vez: evita reservar un IntArray del tamaño completo
    // de la fotografía, algo especialmente costoso en imágenes de alta resolución.
    val bmp = if (inPlace && src.isMutable && src.config == Bitmap.Config.ARGB_8888) src
              else src.copy(Bitmap.Config.ARGB_8888, true)

    try {
        val width = bmp.width
        val height = bmp.height
        val row = IntArray(width)
        val m = recipe.toColorMatrix().values
        val sh = recipe.shadows / 100f
        val effect = recipe.effect
        val v = recipe.vignette / 100f
        val cx = (width - 1) / 2f
        val cy = (height - 1) / 2f
        val maxD = sqrt(cx * cx + cy * cy).let { if (it == 0f) 1f else it }

        for (y in 0 until height) {
            cancellationCheck?.invoke()
            bmp.getPixels(row, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                val p = row[x]
                var r = ((p shr 16) and 0xFF).toFloat()
                var g = ((p shr 8) and 0xFF).toFloat()
                var b = (p and 0xFF).toFloat()
                if (sh != 0f) {
                    val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                    val d = sh * (1f - lum) * (1f - lum) * 70f
                    r += d; g += d; b += d
                }
                var nr = (m[0] * r + m[1] * g + m[2] * b + m[4]).coerceIn(0f, 255f)
                var ng = (m[5] * r + m[6] * g + m[7] * b + m[9]).coerceIn(0f, 255f)
                var nb = (m[10] * r + m[11] * g + m[12] * b + m[14]).coerceIn(0f, 255f)
                if (recipe.warmth != 0f) {
                    val w = recipe.warmth / 100f
                    nr = (nr + 38f * w).coerceIn(0f, 255f)
                    ng = (ng + 7f * w).coerceIn(0f, 255f)
                    nb = (nb - 30f * w).coerceIn(0f, 255f)
                }
                // Apply warmth before monochrome conversion so B/N always stays neutral.
                when (effect) {
                    1 -> {
                        val rr = 0.393f * nr + 0.769f * ng + 0.189f * nb
                        val gg = 0.349f * nr + 0.686f * ng + 0.168f * nb
                        val bb = 0.272f * nr + 0.534f * ng + 0.131f * nb
                        nr = rr.coerceAtMost(255f); ng = gg.coerceAtMost(255f); nb = bb.coerceAtMost(255f)
                    }
                    2 -> {
                        val gray = 0.299f * nr + 0.587f * ng + 0.114f * nb
                        nr = gray; ng = gray; nb = gray
                    }
                }
                if (v > 0f) {
                    val dx = x - cx
                    val dy = y - cy
                    val dist = sqrt(dx * dx + dy * dy) / maxD
                    val t = ((dist - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    val f = 1f - v * t * t
                    nr *= f; ng *= f; nb *= f
                }
                row[x] = (p and 0xFF000000.toInt()) or
                    (clamp255(nr) shl 16) or (clamp255(ng) shl 8) or clamp255(nb)
            }
            bmp.setPixels(row, 0, width, 0, y, width, 1)
        }
        return bmp
    } catch (error: Throwable) {
        if (bmp !== src && !bmp.isRecycled) bmp.recycle()
        throw error
    }
}

// "Retoque de un toque": calcula una receta a partir de la propia foto.
// Lee una fila a la vez para no reservar un IntArray del tamaño completo de la foto.
fun autoRecipe(src: Bitmap): Recipe {
    val width = src.width
    val height = src.height
    val row = IntArray(width)
    val hist = IntArray(256)
    var sum = 0L
    for (y in 0 until height) {
        src.getPixels(row, 0, width, 0, y, width, 1)
        for (p in row) {
            val lum = (0.299f * ((p shr 16) and 0xFF) +
                0.587f * ((p shr 8) and 0xFF) +
                0.114f * (p and 0xFF)).toInt().coerceIn(0, 255)
            hist[lum]++
            sum += lum
        }
    }
    val total = width.toLong() * height.toLong()
    val mean = sum.toFloat() / total

    val cut = total * 0.01f
    var acc = 0L
    var lo = 0
    var hi = 255
    for (i in 0..255) {
        acc += hist[i].toLong()
        if (acc >= cut) { lo = i; break }
    }
    acc = 0L
    for (i in 255 downTo 0) {
        acc += hist[i].toLong()
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

    val opts = BitmapFactory.Options().apply { inSampleSize = sample; inMutable = true }
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
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.preScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.preScale(-1f, 1f)
        }
    }
    val oriented = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    if (oriented !== bmp && !bmp.isRecycled) bmp.recycle()
    return oriented
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
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) { Text(text, fontSize = 12.sp, maxLines = 1) }
}

private data class RecipePreset(
    val icon: String,
    val title: String,
    val description: String,
    val recipe: Recipe
)

@Composable
private fun RecipePresetRow(
    icon: String,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(icon, fontSize = 25.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.apply_recipe), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun Editor(uri: Uri, onClose: () -> Unit, onWallpaper: (Recipe) -> Unit, initialRecipe: Recipe? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var recipe by remember(uri, initialRecipe) { mutableStateOf(initialRecipe ?: loadRecipe(context, uri)) }
LaunchedEffect(recipe) { saveRecipe(context, uri, recipe) }
    var jpegQuality by remember(uri) { mutableFloatStateOf(95f) }
    var saving by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var comparing by remember(uri) { mutableStateOf(false) }
    var recipePreview by remember { mutableStateOf<Recipe?>(null) }
    var recipePreviewName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uri) {
        original = withContext(Dispatchers.IO) { loadScaledBitmap(context, uri, 900) }
    }
    LaunchedEffect(original, recipe, recipePreview) {
        val src = original ?: return@LaunchedEffect
        val activeRecipe = recipePreview ?: recipe
        val previewJob = coroutineContext[Job]
        preview = withContext(Dispatchers.Default) {
            applyRecipe(src, activeRecipe, cancellationCheck = { previewJob?.ensureActive() })
                .asImageBitmap()
        }
    }

    var exportDialog by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val categories = listOf(R.string.studio_light, R.string.studio_color, R.string.menu_effects, R.string.menu_recipes)
    val categoryIcons = listOf(Icons.Rounded.WbSunny, Icons.Rounded.Palette, Icons.Rounded.AutoAwesome, Icons.Rounded.Collections)
    if (exportDialog) {
        AlertDialog(
            onDismissRequest = { exportDialog = false },
            title = { Text(stringResource(R.string.studio_export)) },
            text = { SliderRow(stringResource(R.string.jpeg_quality), jpegQuality, 50f..100f) { jpegQuality = it } },
            confirmButton = {
                Button(onClick = {
                    exportDialog = false
                    val snapshot = recipe
                    val quality = jpegQuality.toInt()
                    saving = true
                    scope.launch {
                        try {
                            val ok = withContext(Dispatchers.Default) { saveEdited(context, uri, snapshot, quality) }
                            Toast.makeText(context, if (ok) context.getString(R.string.copy_saved) else context.getString(R.string.copy_save_failed), Toast.LENGTH_LONG).show()
                        } finally { saving = false }
                    }
                }) { Text(stringResource(R.string.studio_export)) }
            },
            dismissButton = { TextButton(onClick = { exportDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.close_editor), tint = colors.onBackground) }
            Text(stringResource(R.string.studio_title), modifier = Modifier.weight(1f), color = colors.onBackground, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Button(onClick = { exportDialog = true }, enabled = !saving && original != null && recipePreview == null) {
                Text(stringResource(if (saving) R.string.saving else R.string.studio_export))
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 720.dp && maxWidth > maxHeight
            val panelWidth = (maxWidth * 0.42f).coerceIn(300.dp, 420.dp)
            val photoWeight = if (maxHeight < 600.dp) 0.43f else 0.52f
            val photoPane: @Composable (Modifier) -> Unit = { m ->
                Box(m.background(Color(0xFF101013))) {
                    (if (comparing) original?.asImageBitmap() else preview)?.let {
                        Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                    TextButton(onClick = { comparing = !comparing }, enabled = original != null, modifier = Modifier.align(Alignment.BottomEnd)) {
                        Text(stringResource(if (comparing) R.string.edited else R.string.original), color = Color.White, modifier = Modifier.background(Color(0xCC202026), RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
            val controls: @Composable (Modifier) -> Unit = { m ->
                Column(m.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(colors.surface)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(categories[tab]), modifier = Modifier.weight(1f), color = colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = {
                            original?.let {
                                recipe = autoRecipe(it).copy(turns = recipe.turns, cropL = recipe.cropL, cropT = recipe.cropT, cropR = recipe.cropR, cropB = recipe.cropB, flipHorizontal = recipe.flipHorizontal, effect = recipe.effect, vignette = recipe.vignette)
                            }
                            recipePreview = null
                        }) { Icon(Icons.Rounded.AutoFixHigh, stringResource(R.string.one_tap_enhance), tint = colors.primary) }
                        IconButton(onClick = { recipe = Recipe(); recipePreview = null }) { Icon(Icons.Rounded.Refresh, stringResource(R.string.reset), tint = colors.onSurfaceVariant) }
                        IconButton(onClick = { onWallpaper(recipe) }, enabled = recipePreview == null) { Icon(Icons.Rounded.Wallpaper, stringResource(R.string.wallpaper), tint = colors.onSurfaceVariant) }
                    }
                    HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        when (tab) {
                            0 -> {
                                SliderRow(stringResource(R.string.brightness), recipe.brightness, -100f..100f) { recipe = recipe.copy(brightness = it) }
                                SliderRow(stringResource(R.string.contrast), ((recipe.contrast - 1f) * 200f).coerceIn(-100f, 100f), -100f..100f) { recipe = recipe.copy(contrast = 1f + it / 200f) }
                                SliderRow(stringResource(R.string.shadows), recipe.shadows, -100f..100f) { recipe = recipe.copy(shadows = it) }
                            }
                            1 -> {
                                SliderRow(stringResource(R.string.temperature), recipe.warmth, -100f..100f) { recipe = recipe.copy(warmth = it) }
                                SliderRow(stringResource(R.string.saturation), ((recipe.saturation - 1f) * 100f).coerceIn(-100f, 100f), -100f..100f) { recipe = recipe.copy(saturation = 1f + it / 100f) }
                            }
                            2 -> {
                                Text(stringResource(R.string.color_effect), color = colors.onSurface)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TabButton(stringResource(R.string.original), recipe.effect == 0, Modifier.weight(1f).height(48.dp)) { recipe = recipe.copy(effect = 0) }
                                    TabButton(stringResource(R.string.sepia), recipe.effect == 1, Modifier.weight(1f).height(48.dp)) { recipe = recipe.copy(effect = 1) }
                                    TabButton(stringResource(R.string.black_white_short), recipe.effect == 2, Modifier.weight(1f).height(48.dp)) { recipe = recipe.copy(effect = 2) }
                                }
                                SliderRow(stringResource(R.string.vignette), recipe.vignette, 0f..100f) { recipe = recipe.copy(vignette = it) }
                            }
                            3 -> {
                        val presets = listOf(
                            RecipePreset("☀️", stringResource(R.string.recipe_warm_light), stringResource(R.string.recipe_warm_light_desc), Recipe(brightness = 5f, contrast = 1.04f, saturation = 1.08f, warmth = 28f, shadows = 12f)),
                            RecipePreset("🎨", stringResource(R.string.recipe_vivid), stringResource(R.string.recipe_vivid_desc), Recipe(brightness = 3f, contrast = 1.10f, saturation = 1.32f, shadows = 8f)),
                            RecipePreset("🖤", stringResource(R.string.recipe_black_white), stringResource(R.string.recipe_black_white_desc), Recipe(brightness = 3f, contrast = 1.12f, shadows = 10f, effect = 2, vignette = 8f)),
                            RecipePreset("✨", stringResource(R.string.recipe_soft_portrait), stringResource(R.string.recipe_soft_portrait_desc), Recipe(brightness = 5f, contrast = 0.96f, saturation = 0.94f, warmth = 5f, shadows = 18f, vignette = 3f)),
                            RecipePreset("📷", stringResource(R.string.recipe_analog), stringResource(R.string.recipe_analog_desc), Recipe(brightness = 4f, contrast = 0.92f, saturation = 0.82f, warmth = 12f, shadows = 8f, vignette = 12f)),
                            RecipePreset("🎬", stringResource(R.string.recipe_cinematic), stringResource(R.string.recipe_cinematic_desc), Recipe(brightness = -2f, contrast = 1.18f, saturation = 1.08f, warmth = 9f, shadows = 5f, vignette = 18f)),
                            RecipePreset("🍽️", stringResource(R.string.recipe_food), stringResource(R.string.recipe_food_desc), Recipe(brightness = 6f, contrast = 1.08f, saturation = 1.18f, warmth = 14f, shadows = 10f))
                        )
                        presets.forEach { (icon, title, description, preset) ->
                            RecipePresetRow(icon, title, description) {
                                recipePreview = preset.copy(turns = recipe.turns, cropL = recipe.cropL, cropT = recipe.cropT, cropR = recipe.cropR, cropB = recipe.cropB, flipHorizontal = recipe.flipHorizontal)
                                recipePreviewName = title
                            }
                        }
                        if (recipePreview != null) {
                            Text(stringResource(R.string.recipe_preview, recipePreviewName ?: stringResource(R.string.menu_recipes)), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        recipe = recipePreview ?: recipe
                                        recipePreview = null
                                        recipePreviewName = null
                                        tab = 0
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors()
                                ) { Text(stringResource(R.string.apply_recipe)) }
                                TextButton(onClick = {
                                    recipePreview = null
                                    recipePreviewName = null
                                }, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                            }
                        }
                    }
                    HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        categories.forEachIndexed { index, label ->
                            Surface(
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).clickable { tab = index; recipePreview = null; recipePreviewName = null },
                                color = if (tab == index) colors.primaryContainer else colors.surface,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    val tint = if (tab == index) colors.onPrimaryContainer else colors.onSurfaceVariant
                                    Icon(categoryIcons[index], contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.height(3.dp))
                                    Text(stringResource(label), color = tint, fontSize = 11.sp, fontWeight = if (tab == index) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    photoPane(Modifier.weight(1f).fillMaxHeight())
                    controls(Modifier.width(panelWidth).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    photoPane(Modifier.weight(photoWeight).fillMaxWidth())
                    controls(Modifier.weight(1f - photoWeight).fillMaxWidth())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    val displayValue = if (range.start % 1f == 0f && range.endInclusive % 1f == 0f) {
        value.roundToInt().toString()
    } else {
        String.format(java.util.Locale.getDefault(), "%.2f", value)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            Text(displayValue, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
        val accent = MaterialTheme.colorScheme.primary
        val trackColor = MaterialTheme.colorScheme.surfaceVariant
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
            thumb = {
                Box(Modifier.size(16.dp).background(accent, androidx.compose.foundation.shape.CircleShape))
            },
            track = { state ->
                Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                    val fraction = ((state.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                    val neutral = if (range.start < 0f && range.endInclusive > 0f) (-range.start / (range.endInclusive - range.start)) else 0f
                    val y = size.height / 2f
                    drawLine(trackColor, Offset(0f, y), Offset(size.width, y), 3.dp.toPx(), StrokeCap.Round)
                    drawLine(accent, Offset(size.width * neutral, y), Offset(size.width * fraction, y), 3.dp.toPx(), StrokeCap.Round)
                    if (neutral > 0f) drawCircle(accent.copy(alpha = 0.65f), 2.dp.toPx(), Offset(size.width * neutral, y))
                }
            }
        )
    }
}
