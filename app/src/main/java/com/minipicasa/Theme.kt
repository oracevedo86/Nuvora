// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 oracevedo86
package com.minipicasa

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Colores de la marca Nuvora.
object Brand {
    val Violet = Color(0xFF5B2CA4)
    val Magenta = Color(0xFFEC4899)
    val Orange = Color(0xFFFF8A00)
    val Yellow = Color(0xFFFFC837)
    val Turquoise = Color(0xFF00B3A7)
    val Blue = Color(0xFF3F86FF)
    val Cream = Color(0xFFF8F4ED)
    val Graphite = Color(0xFF2A2A37)
}

// Tema Graphite: siempre oscuro, fondo grafito y acentos violetas.
val GraphiteColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFBFA6FF),
    onPrimary = Color(0xFF2A1260),
    primaryContainer = Brand.Violet,
    onPrimaryContainer = Brand.Cream,
    secondary = Color(0xFFF58BC1),
    onSecondary = Color(0xFF4A0B2B),
    secondaryContainer = Color(0xFF6B2150),
    onSecondaryContainer = Brand.Cream,
    tertiary = Color(0xFF3FD6CA),
    onTertiary = Color(0xFF00322E),
    background = Brand.Graphite,
    onBackground = Brand.Cream,
    surface = Brand.Graphite,
    onSurface = Brand.Cream,
    surfaceVariant = Color(0xFF3A3A4B),
    onSurfaceVariant = Color(0xFFD0CADB),
    outline = Color(0xFF908BA0)
)

// Paletas de reserva del tema Adaptive (cuando no hay colores dinámicos).
private val AdaptiveLightFallback = lightColorScheme(
    primary = Brand.Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF22005D),
    secondary = Color(0xFFB4236F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD8E8),
    onSecondaryContainer = Color(0xFF3E0022),
    tertiary = Color(0xFF00796F),
    onTertiary = Color.White,
    background = Brand.Cream,
    onBackground = Brand.Graphite,
    surface = Brand.Cream,
    onSurface = Brand.Graphite,
    surfaceVariant = Color(0xFFE8E0EC),
    onSurfaceVariant = Color(0xFF4A4553),
    outline = Color(0xFF7B7485)
)

private val AdaptiveDarkFallback = GraphiteColors.copy(
    background = Color(0xFF1C1B22),
    surface = Color(0xFF1C1B22)
)

// appearance: 0 = Graphite, 1 = Adaptive
@Composable
fun NuvoraTheme(appearance: Int, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = appearance == 0 || systemDark

    val scheme = if (appearance == 0) {
        GraphiteColors
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (systemDark) AdaptiveDarkFallback else AdaptiveLightFallback
    }

    // Barras del sistema con el color del tema.
    val view = LocalView.current
    DisposableEffect(scheme.background, dark) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            window.statusBarColor = scheme.background.toArgb()
            window.navigationBarColor = scheme.background.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
        onDispose { }
    }

    MaterialTheme(colorScheme = scheme, content = content)
}
