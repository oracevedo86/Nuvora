package com.minipicasa

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent { Root(if (intent?.action == android.content.Intent.ACTION_VIEW) intent.data else null) }
    }
}

data class Folder(val name: String, val photos: List<Uri>)

// Lee las fotos del teléfono y las agrupa por carpeta (la más reciente primero).
fun loadFolders(context: Context): List<Folder> {
    val map = LinkedHashMap<String, MutableList<Uri>>()
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME
    )
    val order = "${MediaStore.Images.Media.DATE_ADDED} DESC"
    context.contentResolver.query(collection, projection, null, null, order)?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        while (c.moveToNext()) {
            val name = c.getString(nameCol) ?: "Otras"
            val uri = ContentUris.withAppendedId(collection, c.getLong(idCol))
            map.getOrPut(name) { mutableListOf() }.add(uri)
        }
    }
    return map.map { Folder(it.key, it.value) }
}

@Composable
fun App() {
    val context = LocalContext.current
    val prefs by rememberAppPrefs()
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf("") }

    val permission = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_IMAGES
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    var folders by remember { mutableStateOf<List<Folder>>(emptyList()) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Uri?>(null) }
    val selectedPhotos = remember { mutableStateListOf<Uri>() }

    fun togglePhotoSelection(uri: Uri) {
        if (selectedPhotos.contains(uri)) selectedPhotos.remove(uri)
        else selectedPhotos.add(uri)
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            selectedPhotos.clear()
            scope.launch {
                folders = withContext(Dispatchers.IO) { loadFolders(context) }
            }
        }
    }

    fun shareSelectedPhotos() {
        if (selectedPhotos.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(selectedPhotos))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir fotografías"))
    }

    fun deleteSelectedPhotos() {
        if (selectedPhotos.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val request = MediaStore.createDeleteRequest(context.contentResolver, selectedPhotos.toList())
            deleteLauncher.launch(IntentSenderRequest.Builder(request).build())
        } else {
            Toast.makeText(context, "La eliminación múltiple requiere Android 11 o superior", Toast.LENGTH_LONG).show()
        }
    }

    // Recarga al abrir la app y cada vez que se cierra una foto.
    LaunchedEffect(granted, selected) {
        if (granted && selected == null) {
            folders = withContext(Dispatchers.IO) { loadFolders(context) }
        }
    }

    BackHandler(enabled = selectedPhotos.isNotEmpty() && selected == null && page == "") {
        selectedPhotos.clear()
    }

    BackHandler(enabled = openFolder != null && selected == null && page == "" && selectedPhotos.isEmpty()) {
        openFolder = null
    }

    val current = folders.firstOrNull { it.name == openFolder }

    NuvoraTheme(appearance = prefs.appearance) {
        Surface(Modifier.fillMaxSize()) {
            val tabletLayout = LocalConfiguration.current.screenWidthDp >= 840
            if (tabletLayout && granted && page == "" && selected == null) {
                TabletGalleryShell(
                    folders = folders,
                    currentFolder = openFolder,
                    onAllFolders = { openFolder = null },
                    onOpenFolder = { openFolder = it },
                    onSettings = { page = "settings" },
                    onAbout = { page = "about" },
                    onPick = { selected = it },
                    selectedPhotos = selectedPhotos,
                    onToggleSelection = ::togglePhotoSelection,
                    onClearSelection = { selectedPhotos.clear() },
                    onShareSelection = ::shareSelectedPhotos,
                    onDeleteSelection = ::deleteSelectedPhotos
                )
            } else when {
                !granted -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Necesito permiso para ver tus fotos")
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { launcher.launch(permission) }) { Text("Dar permiso") }
                }
                page == "settings" -> SettingsScreen(
                    appearance = prefs.appearance,
                    folderView = prefs.folderView,
                    onAppearance = { v -> scope.launch { setAppearance(context, v) } },
                    onFolderView = { v -> scope.launch { setFolderView(context, v) } },
                    onBack = { page = "" }
                )
                page == "about" -> AboutScreen { page = "" }
                selected != null -> PhotoPagerContent(
                    current?.photos ?: listOf(selected!!),
                    selected!!
                ) { selected = null }
                current != null -> PhotoGrid(
                    folder = current,
                    onSettings = { page = "settings" },
                    onAbout = { page = "about" },
                    onPick = { selected = it },
                    selectedPhotos = selectedPhotos,
                    onToggleSelection = ::togglePhotoSelection,
                    onClearSelection = { selectedPhotos.clear() },
                    onShareSelection = ::shareSelectedPhotos,
                    onDeleteSelection = ::deleteSelectedPhotos
                )
                else -> FolderList(
                    folders = folders,
                    listMode = prefs.folderView == 1,
                    onSettings = { page = "settings" },
                    onAbout = { page = "about" },
                    onOpen = { openFolder = it }
                )
            }
        }
    }
}

@Composable
private fun TabletGalleryShell(
    folders: List<Folder>,
    currentFolder: String?,
    onAllFolders: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onPick: (Uri) -> Unit,
    selectedPhotos: List<Uri>,
    onToggleSelection: (Uri) -> Unit,
    onClearSelection: () -> Unit,
    onShareSelection: () -> Unit,
    onDeleteSelection: () -> Unit
) {
    Row(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(224.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                .padding(horizontal = 12.dp, vertical = 16.dp)
        ) {
            Column(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 28.dp)) {
                Text(
                    "Nuvora",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Tu fotografía, tu estilo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TabletNavItem(
                icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                label = "Todas las carpetas",
                selected = currentFolder == null,
                onClick = onAllFolders
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "BIBLIOTECA",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, bottom = 8.dp)
            )
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                folders.forEach { folder ->
                    TabletNavItem(
                        icon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                        label = folder.name,
                        selected = currentFolder == folder.name,
                        supporting = "${folder.photos.size} fotos",
                        onClick = { onOpenFolder(folder.name) }
                    )
                }
            }
            androidx.compose.material3.HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                modifier = Modifier.padding(vertical = 10.dp)
            )
            TabletNavItem(
                icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                label = "Ajustes",
                selected = false,
                onClick = onSettings
            )
            TabletNavItem(
                icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                label = "Acerca de",
                selected = false,
                onClick = onAbout
            )
        }

        androidx.compose.material3.VerticalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f),
            modifier = Modifier.fillMaxHeight()
        )

        Box(Modifier.weight(1f).fillMaxHeight()) {
            val folder = folders.firstOrNull { it.name == currentFolder }
            if (folder == null) {
                TabletFolderGrid(folders = folders, onOpen = onOpenFolder)
            } else {
                TabletPhotoGrid(
                    folder = folder,
                    onPick = onPick,
                    selectedPhotos = selectedPhotos,
                    onToggleSelection = onToggleSelection,
                    onClearSelection = onClearSelection,
                    onShareSelection = onShareSelection,
                    onDeleteSelection = onDeleteSelection
                )
            }
        }
    }
}

@Composable
private fun TabletNavItem(
    icon: @Composable () -> Unit,
    label: String,
    selected: Boolean,
    supporting: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else Color.Transparent
            )
            .then(
                if (selected) Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                    RoundedCornerShape(16.dp)
                ) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            icon()
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                maxLines = 1,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TabletFolderGrid(folders: List<Folder>, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Text(
                "Todas las carpetas",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp
            )
            Text(
                "Explora tus fotografías por álbum",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (folders.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No encontré fotos todavía")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 190.dp),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(folders) { folder ->
                    Column(Modifier.clickable { onOpen(folder.name) }) {
                        AsyncImage(
                            model = folder.photos.firstOrNull(),
                            contentDescription = "Carpeta ${folder.name}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1.18f)
                                .clip(RoundedCornerShape(18.dp))
                        )
                        Text(folder.name, fontWeight = FontWeight.SemiBold, maxLines = 1,
                            modifier = Modifier.padding(top = 8.dp))
                        Text("${folder.photos.size} fotos", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabletPhotoGrid(
    folder: Folder,
    onPick: (Uri) -> Unit,
    selectedPhotos: List<Uri>,
    onToggleSelection: (Uri) -> Unit,
    onClearSelection: () -> Unit,
    onShareSelection: () -> Unit,
    onDeleteSelection: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        if (selectedPhotos.isNotEmpty()) {
            SelectionToolbar(selectedPhotos.size, onClearSelection, onShareSelection, onDeleteSelection)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    folder.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "${folder.photos.size} fotos",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(folder.photos) { uri ->
                PhotoGridTile(
                    uri = uri,
                    selected = uri in selectedPhotos,
                    onClick = { if (selectedPhotos.isNotEmpty()) onToggleSelection(uri) else onPick(uri) },
                    onLongClick = { onToggleSelection(uri) }
                )
            }
        }
    }
}

@Composable
fun FolderList(
    folders: List<Folder>,
    listMode: Boolean,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onOpen: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Nuvora", onSettings, onAbout)
        if (folders.isEmpty()) {
            Text("No encontré fotos todavía", modifier = Modifier.padding(16.dp))
        }
        if (listMode) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(folders) { folder ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(folder.name) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = folder.photos.first(),
                            contentDescription = "Carpeta ${folder.name}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(folder.name, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("${folder.photos.size} fotos", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (LocalConfiguration.current.screenWidthDp >= 600) 4 else 2),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(folders) { folder ->
                    Column(Modifier.clickable { onOpen(folder.name) }) {
                        AsyncImage(
                            model = folder.photos.first(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Text(
                            folder.name,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text("${folder.photos.size} fotos", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoGrid(
    folder: Folder,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onPick: (Uri) -> Unit,
    selectedPhotos: List<Uri>,
    onToggleSelection: (Uri) -> Unit,
    onClearSelection: () -> Unit,
    onShareSelection: () -> Unit,
    onDeleteSelection: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        if (selectedPhotos.isNotEmpty()) {
            SelectionToolbar(selectedPhotos.size, onClearSelection, onShareSelection, onDeleteSelection)
        } else {
            TopBar("${folder.name} (${folder.photos.size})", onSettings, onAbout)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (LocalConfiguration.current.screenWidthDp >= 600) 5 else 3),
            contentPadding = PaddingValues(top = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            items(folder.photos) { uri ->
                PhotoGridTile(
                    uri = uri,
                    selected = uri in selectedPhotos,
                    onClick = { if (selectedPhotos.isNotEmpty()) onToggleSelection(uri) else onPick(uri) },
                    onLongClick = { onToggleSelection(uri) }
                )
            }
        }
    }
}

@Composable
private fun SelectionToolbar(
    count: Int,
    onClear: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClear) {
            Icon(Icons.Rounded.Close, contentDescription = "Salir de selección")
        }
        Text("$count seleccionadas", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onShare) {
            Icon(Icons.Rounded.Share, contentDescription = null)
            Spacer(Modifier.width(5.dp))
            Text("Compartir")
        }
        TextButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(5.dp))
            Text("Borrar", color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGridTile(
    uri: Uri,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "Fotografía",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (selected) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)))
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = "Seleccionada",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
            )
        }
    }
}

// (Visor antiguo: ya no se usa, pero Editor.kt aún lo referencia. Se limpia en otra fase.)
@Composable
fun Viewer(uri: Uri, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
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
