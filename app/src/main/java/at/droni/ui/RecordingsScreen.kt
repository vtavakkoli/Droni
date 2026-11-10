package at.droni.ui

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.format.Formatter
import android.util.Size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private data class MovieItem(val uri: Uri, val name: String, val size: Long, val durationMs: Long, val timestamp: Long)
private data class MovieLibrary(val movies: List<MovieItem> = emptyList(), val error: Boolean = false)

/** Queries and thumbnails run off the main thread, even for a large local collection. */
private fun readMovies(context: Context): MovieLibrary = try {
    val movies = mutableListOf<MovieItem>()
    if (Build.VERSION.SDK_INT >= 29) {
        val columns = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.DATE_ADDED)
        context.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, columns,
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Video.Media.IS_PENDING} = 0",
            arrayOf("%/Droni/%"), "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { cursor ->
            while (cursor.moveToNext()) movies += MovieItem(
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0)),
                cursor.getString(1) ?: "Droni movie", cursor.getLong(2), cursor.getLong(3), cursor.getLong(4) * 1000L)
        }
    } else {
        File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Droni")
            .listFiles { file -> file.extension.equals("mp4", true) }?.sortedByDescending { it.lastModified() }
            ?.forEach { movies += MovieItem(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it),
                it.name, it.length(), 0L, it.lastModified()) }
    }
    MovieLibrary(movies)
} catch (_: Exception) { MovieLibrary(error = true) }

@Composable
fun RecordingsScreen(onBack: () -> Unit, onOpenFlight: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val library by produceState<MovieLibrary?>(null, refresh) {
        value = null
        value = withContext(Dispatchers.IO) { readMovies(context) }
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun openMovie(movie: MovieItem, share: Boolean) {
        try {
            val intent = if (share) Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"; putExtra(Intent.EXTRA_STREAM, movie.uri)
                clipData = android.content.ClipData.newRawUri(movie.name, movie.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(movie.uri, "video/mp4"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(if (share) Intent.createChooser(intent, "Share recording") else intent)
        } catch (_: Exception) {
            scope.launch { snackbar.showSnackbar(if (share) "No app available to share this video" else "Install a video player to open this recording") }
        }
    }
    PageFrame("Recordings", "Your moments, captured from above", onBack) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (library == null) "Loading your videos…" else "${library!!.movies.size} recordings · saved on this device",
                        style = MaterialTheme.typography.bodyMedium, color = DroniColors.Muted,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { refresh++ }) {
                        DroniIcon(DroniSymbol.Refresh, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp)); Text("Refresh")
                    }
                }
                when {
                    library == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                    library!!.error -> EmptyMovies("Couldn’t load recordings", "Refresh to try reading your saved videos again.",
                        "Try again", { refresh++ })
                    library!!.movies.isEmpty() -> EmptyMovies("Your next memory starts here",
                        "Open the flight deck, connect your Elfie, and tap REC MP4.\nYour saved videos will appear here.",
                        "Open flight deck", onOpenFlight)
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                        items(library!!.movies, key = { it.uri.toString() }) { movie ->
                            Surface(onClick = { openMovie(movie, false) }, shape = RoundedCornerShape(16.dp),
                                color = DroniColors.Surface, border = BorderStroke(1.dp, DroniColors.Outline.copy(alpha = .7f))) {
                                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    MovieThumbnail(movie)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(movie.name, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Text("${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(movie.timestamp))}  ·  ${Formatter.formatShortFileSize(context, movie.size)}",
                                            style = MaterialTheme.typography.bodySmall, color = DroniColors.Muted)
                                    }
                                    IconButton(onClick = { openMovie(movie, true) }) {
                                        DroniIcon(DroniSymbol.Share, description = "Share ${movie.name}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun EmptyMovies(title: String, subtitle: String, button: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(60.dp).background(DroniColors.Cyan.copy(alpha = .07f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center) { DroniIcon(DroniSymbol.Video, Modifier.size(28.dp)) }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(7.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = DroniColors.Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick, shape = RoundedCornerShape(12.dp)) { Text(button) }
    }
}

@Composable
private fun MovieThumbnail(movie: MovieItem) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, movie.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(movie.uri, Size(224, 144), null)
                else {
                    val retriever = MediaMetadataRetriever()
                    try { retriever.setDataSource(context, movie.uri); retriever.getFrameAtTime(0) }
                    finally { retriever.release() }
                }
            }.getOrNull()
        }
    }
    Box(Modifier.size(112.dp, 72.dp).clip(RoundedCornerShape(10.dp)).background(DroniColors.Raised),
        contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        DroniIcon(DroniSymbol.Play, Modifier.size(24.dp), androidx.compose.ui.graphics.Color.White)
        if (movie.durationMs > 0L) {
            val seconds = movie.durationMs / 1000
            Text(String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60),
                color = DroniColors.Text, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .background(DroniColors.Background.copy(alpha = .8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp))
        }
    }
}
