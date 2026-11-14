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
import androidx.compose.ui.res.stringResource
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
                    contentDescription = stringResource(R.string.menu_more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_settings)) },
                    leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    onClick = { menuOpen = false; onSettings() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_about)) },
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
            Text(stringResource(R.string.action_back))
        }
        Text(stringResource(R.string.settings_title), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Text(stringResource(R.string.settings_appearance), fontWeight = FontWeight.Bold)
        OptionRow(
            stringResource(R.string.appearance_graphite), appearance == 0,
            stringResource(R.string.appearance_graphite_description)
        ) { onAppearance(0) }
        OptionRow(
            stringResource(R.string.appearance_adaptive), appearance == 1,
            stringResource(R.string.appearance_adaptive_description)
        ) { onAppearance(1) }

        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.settings_folder_view), fontWeight = FontWeight.Bold)
        OptionRow(stringResource(R.string.folder_view_grid), folderView == 0, null) { onFolderView(0) }
        OptionRow(stringResource(R.string.folder_view_list), folderView == 1, null) { onFolderView(1) }
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
            Text(stringResource(R.string.action_back))
        }
        Spacer(Modifier.height(16.dp))
        Text("Nuvora", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.about_tagline))
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.about_slogan),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.about_description))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.about_credits, AUTOR))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.about_build))
    }
}
