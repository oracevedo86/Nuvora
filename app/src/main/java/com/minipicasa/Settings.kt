// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 oracevedo86
package com.minipicasa

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
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
    onAbout: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, stringResource(R.string.action_back)) }
        Text(
            title,
            fontSize = 26.sp,
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
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text, fontWeight = FontWeight.Medium)
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
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SettingsHeader(stringResource(R.string.settings_title), onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppearanceCard(stringResource(R.string.appearance_graphite), appearance == 0, GraphiteColors.primary, GraphiteColors.surface, Modifier.weight(1f)) { onAppearance(0) }
                AppearanceCard(stringResource(R.string.appearance_adaptive), appearance == 1, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceVariant, Modifier.weight(1f)) { onAppearance(1) }
            }
            Text(stringResource(if (appearance == 0) R.string.appearance_graphite_description else R.string.appearance_adaptive_description), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.settings_folder_view), style = MaterialTheme.typography.titleMedium)
            OptionRow(stringResource(R.string.folder_view_grid), folderView == 0, null) { onFolderView(0) }
            OptionRow(stringResource(R.string.folder_view_list), folderView == 1, null) { onFolderView(1) }
        }
    }
}

@Composable
private fun SettingsHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, stringResource(R.string.action_back)) }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun AppearanceCard(label: String, selected: Boolean, accent: Color, panel: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(12.dp)).background(panel).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(0.65f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(accent))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) { Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(accent.copy(alpha = 0.25f))) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.size(28.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SettingsHeader(stringResource(R.string.menu_about), onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Nuvora", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.about_tagline))
                Text(stringResource(R.string.about_slogan), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelMedium)
            }
            Text(stringResource(R.string.about_description))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            Text(stringResource(R.string.about_credits, AUTOR))
            Text(stringResource(R.string.about_build), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
