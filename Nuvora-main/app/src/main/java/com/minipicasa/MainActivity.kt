package com.minipicasa

import android.Manifest
import android.content.ContentUris
import android.content.Context
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

    // Recarga al abrir la app y cada vez que se cierra una foto.
    LaunchedEffect(granted, selected) {
        if (granted && selected == null) {
            folders = withContext(Dispatchers.IO) { loadFolders(context) }
        }
    }

    BackHandler(enabled = openFolder != null && selected == null && page == "") {
        openFolder = null
    }

    val current = folders.firstOrNull { it.name == openFolder }

    NuvoraTheme(appearance = prefs.appearance) {
        Surface(Modifier.fillMaxSize()) {
            when {
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
                    onPick = { selected = it }
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
                contentPadding = PaddingValues(8.dp)
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
                            contentDescription = null,
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
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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

@Composable
fun PhotoGrid(
    folder: Folder,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onPick: (Uri) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        TopBar("${folder.name} (${folder.photos.size})", onSettings, onAbout)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            items(folder.photos) { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable { onPick(uri) }
                )
            }
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
