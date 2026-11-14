package com.auraplayer

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.auraplayer.playback.AudioLibrary
import com.auraplayer.playback.AudioTagWriter
import com.auraplayer.playback.MusicPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException

 data class Track(val uri: Uri, val title: String, val artist: String, val album: String)

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AuraApp() }
    }

    @Composable
    private fun AuraApp() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var controller by remember { mutableStateOf<MediaController?>(null) }
        var tracks by remember { mutableStateOf(emptyList<Track>()) }
        var thai by remember { mutableStateOf(false) }
        var permissionGranted by remember { mutableStateOf(hasAudioPermission()) }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionGranted = granted
            if (granted) scope.launch { tracks = withContext(Dispatchers.IO) { AudioLibrary.query(context) } }
        }
        val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                controller?.setMediaItem(mediaItem(uri, "Selected audio", ""))
                controller?.prepare()
                controller?.play()
            }
        }

        DisposableEffect(Unit) {
            val token = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            controllerFuture = future
            future.addListener({
                try { controller = future.get() } catch (_: ExecutionException) { }
                catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            }, ContextCompat.getMainExecutor(context))
            onDispose {
                controller = null
                controllerFuture?.let { MediaController.releaseFuture(it) }
                controllerFuture = null
            }
        }

        LaunchedEffect(permissionGranted) {
            if (permissionGranted) tracks = withContext(Dispatchers.IO) { AudioLibrary.query(context) }
        }

        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFB7A7FF), background = Color(0xFF100D2B))) {
            AuraPlayerScreen(
                thai = thai,
                onLanguage = { thai = !thai },
                tracks = tracks,
                controller = controller,
                onRequestPermission = { permissionLauncher.launch(audioPermission()) },
                onPickFile = { audioPicker.launch(arrayOf("audio/*")) }
            )
        }
    }

    private fun hasAudioPermission(): Boolean = ContextCompat.checkSelfPermission(this, audioPermission()) == PackageManager.PERMISSION_GRANTED
    private fun audioPermission(): String = if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
}

private fun mediaItem(uri: Uri, title: String, artist: String) = MediaItem.Builder()
    .setUri(uri)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).build())
    .build()

@Composable
private fun AuraPlayerScreen(
    thai: Boolean,
    onLanguage: () -> Unit,
    tracks: List<Track>,
    controller: MediaController?,
    onRequestPermission: () -> Unit,
    onPickFile: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentTitle by remember { mutableStateOf("") }
    var currentArtist by remember { mutableStateOf("") }
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var selectedForEdit by remember { mutableStateOf<Track?>(null) }
    var artworkUri by remember { mutableStateOf<Uri?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { artworkUri = it }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                currentTitle = metadata.title?.toString().orEmpty()
                currentArtist = metadata.artist?.toString().orEmpty()
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) { duration = controller?.duration?.coerceAtLeast(0L) ?: 0L }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }
    LaunchedEffect(controller) {
        while (true) {
            position = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
            duration = controller?.duration?.coerceAtLeast(0L) ?: 0L
            delay(400)
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF17113F), Color(0xFF343A82), Color(0xFF17122D))))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(if (thai) "ฟังเพลง" else "Aura Player", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text(if (thai) "คลังเสียงส่วนตัว" else "Your personal library", color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = onLanguage) { Text(if (thai) "EN" else "ไทย") }
            }
            Spacer(Modifier.height(18.dp))
            GlassCard(Modifier.fillMaxWidth().height(238.dp)) {
                Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                    Text(if (thai) "กำลังเล่น" else "NOW PLAYING", color = Color.White.copy(alpha = .68f), style = MaterialTheme.typography.labelMedium)
                    RingProgress(progress = if (duration > 0) position.toFloat() / duration else 0f, modifier = Modifier.size(112.dp)) {
                        Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(42.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(currentTitle.ifBlank { if (thai) "เลือกเสียงเพื่อเริ่ม" else "Choose audio to begin" }, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(currentArtist, color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = { controller?.seekToPreviousMediaItem() }) { Icon(Icons.Default.SkipPrevious, null, tint = Color.White) }
                        FilledIconButton(onClick = { if (isPlaying) controller?.pause() else controller?.play() }, modifier = Modifier.size(54.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFB46BFF))) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                        }
                        IconButton(onClick = { controller?.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, null, tint = Color.White) }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (thai) "เสียงของคุณ" else "YOUR AUDIO", color = Color.White, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onPickFile) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text(if (thai) "เลือกไฟล์" else "Open file") }
            }
            if (tracks.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth().weight(1f)) {
                    Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(if (thai) "ยังไม่มีเสียงในคลัง" else "No audio found", color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onRequestPermission) { Text(if (thai) "อนุญาตเข้าถึงเสียง" else "Allow audio access") }
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 10.dp)) {
                    items(tracks, key = { it.uri.toString() }) { track ->
                        TrackRow(track, thai, onClick = {
                            val index = tracks.indexOf(track)
                            val items = tracks.map { mediaItem(it.uri, it.title, it.artist) }
                            controller?.setMediaItems(items, index, 0L)
                            controller?.prepare(); controller?.play()
                        }, onLongClick = { selectedForEdit = track; artworkUri = null })
                    }
                }
            }
        }
        errorMessage?.let { message ->
            Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp), action = { TextButton(onClick = { errorMessage = null }) { Text("OK") } }) { Text(message) }
        }
    }

    selectedForEdit?.let { track ->
        TagEditorDialog(track, artworkUri, thai, onPickArtwork = { imagePicker.launch("image/*") }, onDismiss = { selectedForEdit = null }, onSave = { title, artist, album ->
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { AudioTagWriter.update(context, track.uri, title, artist, album, artworkUri) }
                    selectedForEdit = null
                } catch (error: Exception) {
                    errorMessage = error.message ?: "Unable to save tags"
                }
            }
        })
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(26.dp)).background(Color.White.copy(alpha = .11f)).then(Modifier), content = content)
}

@Composable
private fun RingProgress(progress: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = .16f), style = Stroke(width = 7.dp.toPx()))
            drawArc(Color(0xFFC19AFF), -90f, 360f * progress.coerceIn(0f, 1f), false, style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round))
        }
        content()
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(track: Track, thai: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .09f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFF7863B9)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, tint = Color.White)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(track.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Color.White.copy(alpha = .6f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        IconButton(onClick = onClick) { Icon(Icons.Default.PlayArrow, null, tint = Color.White) }
    }
}

@Composable
private fun TagEditorDialog(track: Track, image: Uri?, thai: Boolean, onPickArtwork: () -> Unit, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var title by remember(track) { mutableStateOf(track.title) }
    var artist by remember(track) { mutableStateOf(track.artist) }
    var album by remember(track) { mutableStateOf(track.album) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (thai) "แก้ไขข้อมูลเสียง" else "Edit audio tags") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(if (thai) "ชื่อ" else "Title") }, singleLine = true)
                OutlinedTextField(artist, { artist = it }, label = { Text(if (thai) "ศิลปิน" else "Artist") }, singleLine = true)
                OutlinedTextField(album, { album = it }, label = { Text(if (thai) "อัลบั้ม" else "Album") }, singleLine = true)
                TextButton(onClick = onPickArtwork) { Text(if (image != null) { if (thai) "เลือกรูปแล้ว" else "Artwork selected" } else { if (thai) "เลือกรูปปก" else "Choose artwork" }) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, artist, album) }) { Text(if (thai) "บันทึก" else "Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (thai) "ยกเลิก" else "Cancel") } }
    )
}
