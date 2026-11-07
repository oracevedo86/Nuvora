package com.minipicasa

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Cambia aquí tu nombre para la pantalla "Acerca de".
const val AUTOR = "Tu nombre"

@Composable
fun TopBar(title: String, onSettings: () -> Unit, onAbout: () -> Unit) {
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
            TextButton(onClick = { menuOpen = true }) {
                Text("⋮", fontSize = 26.sp)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Ajustes") },
                    onClick = { menuOpen = false; onSettings() }
                )
                DropdownMenuItem(
                    text = { Text("Acerca de") },
                    onClick = { menuOpen = false; onAbout() }
                )
            }
        }
    }
}

@Composable
fun OptionRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(text)
    }
}

@Composable
fun SettingsScreen(
    themeMode: Int,
    folderView: Int,
    onTheme: (Int) -> Unit,
    onFolderView: (Int) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← Volver") }
        Text("Ajustes", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Text("Vista de las carpetas", fontWeight = FontWeight.Bold)
        OptionRow("Cuadrícula", folderView == 0) { onFolderView(0) }
        OptionRow("Lista", folderView == 1) { onFolderView(1) }

        Spacer(Modifier.height(24.dp))
        Text("Tema", fontWeight = FontWeight.Bold)
        OptionRow("Igual que el dispositivo", themeMode == 0) { onTheme(0) }
        OptionRow("Claro", themeMode == 1) { onTheme(1) }
        OptionRow("Oscuro", themeMode == 2) { onTheme(2) }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← Volver") }
        Spacer(Modifier.height(16.dp))
        Text("MiniPicasa", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Versión 1.0")
        Spacer(Modifier.height(16.dp))
        Text("Galería y editor de fotos inspirado en Picasa.")
        Spacer(Modifier.height(8.dp))
        Text("Creada por $AUTOR, con la ayuda de Claude (Anthropic).")
        Spacer(Modifier.height(8.dp))
        Text("Compilada en la nube con GitHub Actions.")
    }
}
