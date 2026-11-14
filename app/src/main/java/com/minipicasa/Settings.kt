// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 oracevedo86
package com.minipicasa

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Nombre para la pantalla "Acerca de".
const val AUTOR = "Orlando Romero (@oracevedo86)"

@Composable
fun TopBar(
    title: String,
    onSettings: () -> Unit,
    onAbout: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Más opciones",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Ajustes") },
                    leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    onClick = { menuOpen = false; onSettings() }
                )
                DropdownMenuItem(
                    text = { Text("Acerca de") },
                    leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                    onClick = { menuOpen = false; onAbout() }
                )
            }
        }
    }
}

@Composable
fun OptionRow(text: String, selected: Boolean, description: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text)
            if (description != null) {
                Text(
                    description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    appearance: Int,
    folderView: Int,
    onAppearance: (Int) -> Unit,
    onFolderView: (Int) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text("Volver")
        }
        Text("Ajustes", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Text("Apariencia", fontWeight = FontWeight.Bold)
        OptionRow(
            "Graphite", appearance == 0,
            "Fondo grafito y acentos violetas, siempre oscuro"
        ) { onAppearance(0) }
        OptionRow(
            "Adaptive", appearance == 1,
            "Usa los colores de tu teléfono (Android 12 o superior) y el modo claro u oscuro del sistema"
        ) { onAppearance(1) }

        Spacer(Modifier.height(24.dp))
        Text("Vista de las carpetas", fontWeight = FontWeight.Bold)
        OptionRow("Cuadrícula", folderView == 0, null) { onFolderView(0) }
        OptionRow("Lista", folderView == 1, null) { onFolderView(1) }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text("Volver")
        }
        Spacer(Modifier.height(16.dp))
        Text("Nuvora", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Tu fotografía, tu estilo")
        Spacer(Modifier.height(4.dp))
        Text(
            "Edita · Crea · Revela",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        Text("Versión ${BuildConfig.VERSION_NAME}")
        Spacer(Modifier.height(8.dp))
        Text("Galería y editor de fotos inspirado en Picasa.")
        Spacer(Modifier.height(8.dp))
        Text("Creada por $AUTOR, con la ayuda de Claude (Anthropic).")
        Spacer(Modifier.height(8.dp))
        Text("Compilada en la nube con GitHub Actions.")
    }
}
