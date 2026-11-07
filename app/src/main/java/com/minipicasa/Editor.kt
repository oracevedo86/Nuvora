package com.minipicasa

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

// La "receta": lo único que guardamos. La foto original no se toca.
data class Recipe(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f
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
fun Editor(uri: Uri, onClose: () -> Unit) {
    var recipe by remember { mutableStateOf(Recipe()) }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.colorMatrix(recipe.toColorMatrix()),
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
        Column(Modifier.background(Color(0xFF222222)).padding(16.dp)) {
            SliderRow("Brillo", recipe.brightness, -100f..100f) {
                recipe = recipe.copy(brightness = it)
            }
            SliderRow("Contraste", recipe.contrast, 0.5f..1.5f) {
                recipe = recipe.copy(contrast = it)
            }
            SliderRow("Saturación", recipe.saturation, 0f..2f) {
                recipe = recipe.copy(saturation = it)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { recipe = Recipe() }) { Text("Restablecer") }
                Button(onClick = onClose) { Text("Cerrar") }
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
