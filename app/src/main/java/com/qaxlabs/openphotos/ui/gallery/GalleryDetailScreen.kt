package com.qaxlabs.openphotos.ui.gallery

import android.net.Uri
import kotlin.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem as Media3MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.qaxlabs.openphotos.data.ViewerFileState
import com.qaxlabs.openphotos.ui.components.GlassBox
import com.qaxlabs.openphotos.ui.theme.DataTextStyle
import com.qaxlabs.openphotos.ui.theme.Mist
import com.qaxlabs.openphotos.ui.theme.Signal
import com.qaxlabs.openphotos.ui.util.formatFileSize
import kotlinx.coroutines.delay
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val MAX_ZOOM = 5f
private const val MIN_ZOOM = 1f

/**
 * Full-screen Vault Viewer (design_system.md §10.4).
 *
 * - Pure black background.
 * - Swipeable HorizontalPager.
 * - Full-resolution fetch on page settle.
 * - Pinch-to-zoom + double-tap-to-zoom on images.
 * - In-app video playback via Media3 ExoPlayer.
 * - Explicit Download action -> MediaStore save.
 * - Glass control bars top and bottom, fade on tap.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryDetailScreen(
    initialIndexId: String,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    vm: GalleryViewModel = hiltViewModel(),
) {
    val items by vm.combinedItems.collectAsStateWithLifecycle()
    val viewerStateMap by vm.viewerStateMap.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Consume download results
    LaunchedEffect(Unit) {
        vm.downloadResult.collect { result ->
            when (result) {
                is DownloadResult.Success ->
                    snackbarHostState.showSnackbar("Saved to gallery ✓")
                is DownloadResult.Failure ->
                    snackbarHostState.showSnackbar("Download failed: ${result.reason}")
            }
        }
    }

    val targetPage = remember(items, initialIndexId) {
        items.indexOfFirst { it.indexId == initialIndexId }
    }

    if (items.isEmpty() || targetPage < 0) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Signal)
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = targetPage) { items.size }
    val currentItem = items.getOrNull(pagerState.currentPage)
    var controlsVisible by remember { mutableStateOf(true) }

    // Auto-hide controls after 3 s
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(3_000)
            controlsVisible = false
        }
    }

    // Fetch full-res when pager settles on a new page
    LaunchedEffect(pagerState.currentPage) {
        if (!pagerState.isScrollInProgress) {
            items.getOrNull(pagerState.currentPage)?.let { item ->
                if (item.entity != null) {
                    vm.fetchFullResolution(item.indexId)
                }
            }
        }
    }

    val currentViewerState = currentItem?.indexId?.let { viewerStateMap[it] } ?: ViewerFileState.Idle

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.systemBars)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { controlsVisible = !controlsVisible },
                ),
        ) {
            // ── Swipeable Full-resolution Pager ──────────────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { items.getOrNull(it)?.indexId ?: it },
            ) { page ->
                val item = items.getOrNull(page) ?: return@HorizontalPager
                val fileState = item.indexId.let { viewerStateMap[it] } ?: ViewerFileState.Idle
                if (item.isVideo) {
                    VideoPage(
                        item = item,
                        fileState = fileState,
                        onFetchRequested = { vm.fetchFullResolution(item.indexId) },
                    )
                } else {
                    ImagePage(
                        item = item,
                        fileState = fileState,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onTap = { controlsVisible = !controlsVisible },
                    )
                }
            }

            // ── Download loading overlay (shown while full-res is loading) ───
            if (currentViewerState is ViewerFileState.Loading) {
                LinearProgressIndicator(
                    progress = { currentViewerState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp),
                    color = Signal,
                    trackColor = Color.White.copy(alpha = 0.1f),
                )
            }

            // ── Floating Glass Top Control Bar ───────────────────────────────
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    GlassBox(
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                )
                            }
                            Text(
                                text = currentItem?.displayName ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${pagerState.currentPage + 1} / ${items.size}",
                                style = DataTextStyle,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        }
                    }
                }
            }

            // ── Floating Glass Bottom Metadata + Download Bar ────────────────
            AnimatedVisibility(
                visible = controlsVisible && currentItem != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                currentItem?.let { item ->
                    val itemViewerState = viewerStateMap[item.indexId] ?: ViewerFileState.Idle
                    DetailBottomBar(
                        item = item,
                        viewerState = itemViewerState,
                        onDownload = { vm.downloadToGallery(item.indexId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

// ── Image page with pinch + double-tap zoom ───────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ImagePage(
    item: VaultGalleryItem,
    fileState: ViewerFileState,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onTap: () -> Unit,
) {
    var scale by remember(item.indexId) { mutableFloatStateOf(1f) }
    var offset by remember(item.indexId) { mutableStateOf(Offset.Zero) }
    var imageState by remember(item.indexId) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

    val imageSource = remember(item.indexId, fileState, item.thumbnailPath, item.localUri) {
        when {
            fileState is ViewerFileState.Ready -> File(fileState.localPath)
            item.localUri.startsWith("file://") -> {
                val f = File(item.localUri.removePrefix("file://"))
                if (f.exists() && f.length() > 0) f
                else item.thumbnailPath?.let { File(it) } ?: Uri.parse(item.localUri)
            }
            !item.thumbnailPath.isNullOrEmpty() -> File(item.thumbnailPath)
            else -> Uri.parse(item.localUri)
        }
    }

    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                rememberSharedContentState(key = "photo_${item.indexId}"),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else Modifier

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(item.indexId) {
                var lastDoubleTapTime = 0L
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    var pointersDown = 1

                    val now = System.currentTimeMillis()
                    val isDoubleTap = (now - lastDoubleTapTime) < 300L
                    lastDoubleTapTime = now

                    if (isDoubleTap) {
                        scale = if (scale > 1.5f) 1f else 2.5f
                        offset = Offset.Zero
                        firstDown.consume()
                        return@awaitEachGesture
                    }

                    do {
                        val event = awaitPointerEvent()
                        pointersDown = event.changes.count { it.pressed }

                        if (pointersDown >= 2) {
                            val zoomFactor = event.calculateZoom()
                            val pan = event.calculatePan()

                            val newScale = (scale * zoomFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                            scale = newScale

                            if (newScale > 1f) {
                                offset = Offset(
                                    x = offset.x + pan.x,
                                    y = offset.y + pan.y,
                                )
                            } else {
                                offset = Offset.Zero
                            }
                            event.changes.forEach { it.consume() }
                        } else if (pointersDown == 1 && scale > 1f) {
                            val pan = event.calculatePan()
                            offset = Offset(
                                x = offset.x + pan.x,
                                y = offset.y + pan.y,
                            )
                            event.changes.forEach { it.consume() }
                        }
                    } while (pointersDown > 0 &&
                        event.changes.any { it.pressed })

                    if (scale < MIN_ZOOM + 0.05f) {
                        scale = MIN_ZOOM
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageSource)
                .crossfade(true)
                .build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .then(sharedModifier),
            onState = { imageState = it },
        )

        if (imageState is AsyncImagePainter.State.Error) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ImageNotSupported,
                    contentDescription = null,
                    tint = Mist,
                    modifier = Modifier.size(64.dp),
                )
                Text(
                    text = "Preview unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mist,
                )
            }
        }

        if (fileState is ViewerFileState.Loading && imageState !is AsyncImagePainter.State.Error) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = "Loading full resolution…",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ── In-App Video Player using Media3 ExoPlayer ─────────────────────────────

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerView(
    videoUri: Uri,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(Media3MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = modifier,
    )
}

// ── Video page with In-App Media3 Playback & Cloud Download ───────────────

@Composable
private fun VideoPage(
    item: VaultGalleryItem,
    fileState: ViewerFileState,
    onFetchRequested: () -> Unit,
) {
    var isPlayingInApp by remember(item.indexId) { mutableStateOf(false) }

    val localVideoUri = remember(item.localUri) {
        when {
            item.localUri.startsWith("content://") -> Uri.parse(item.localUri)
            item.localUri.startsWith("file://") -> {
                val f = File(item.localUri.removePrefix("file://"))
                if (f.exists() && f.length() > 0) Uri.fromFile(f) else null
            }
            item.localUri.isNotBlank() && File(item.localUri).exists() -> Uri.fromFile(File(item.localUri))
            else -> null
        }
    }

    val readyVideoUri = remember(fileState) {
        if (fileState is ViewerFileState.Ready) {
            val f = File(fileState.localPath)
            if (f.exists() && f.length() > 0) Uri.fromFile(f) else null
        } else null
    }

    val playUri = readyVideoUri ?: localVideoUri

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (isPlayingInApp && playUri != null) {
            VideoPlayerView(
                videoUri = playUri,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val thumbnailSource = remember(item.thumbnailPath, item.localUri) {
                when {
                    !item.thumbnailPath.isNullOrEmpty() -> File(item.thumbnailPath)
                    item.localUri.startsWith("file://") -> File(item.localUri.removePrefix("file://"))
                    else -> Uri.parse(item.localUri)
                }
            }

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(thumbnailSource)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.6f },
            )

            when {
                fileState is ViewerFileState.Loading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator(
                            color = Signal,
                            modifier = Modifier.size(56.dp),
                        )
                        if (fileState.progress > 0f) {
                            Text(
                                text = "Downloading video... ${(fileState.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                            )
                        }
                    }
                }
                playUri != null -> {
                    IconButton(
                        onClick = { isPlayingInApp = true },
                        modifier = Modifier.size(80.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play video",
                            tint = Color.White,
                            modifier = Modifier.size(80.dp),
                        )
                    }
                }
                fileState is ViewerFileState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ImageNotSupported,
                            contentDescription = null,
                            tint = Mist,
                            modifier = Modifier.size(56.dp),
                        )
                        Text(
                            text = fileState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = Mist,
                        )
                        Button(
                            onClick = onFetchRequested,
                            colors = ButtonDefaults.buttonColors(containerColor = Signal, contentColor = Color.White),
                        ) {
                            Text("Retry Download")
                        }
                    }
                }
                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconButton(
                            onClick = onFetchRequested,
                            modifier = Modifier.size(80.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Download & Play Video",
                                tint = Color.White,
                                modifier = Modifier.size(80.dp),
                            )
                        }
                        Text(
                            text = "Tap to download & play cloud video",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}

// ── Bottom glass bar: metadata + Download button ───────────────────────────────

private val dateFormatter = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withZone(ZoneId.systemDefault())

@Composable
private fun DetailBottomBar(
    item: VaultGalleryItem,
    viewerState: ViewerFileState,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val takenDate = remember(item.entity?.takenAt) {
        val millis = item.entity?.takenAt ?: 0L
        if (millis > 0) {
            runCatching {
                dateFormatter.format(Instant.ofEpochMilli(millis))
            }.getOrDefault("—")
        } else "—"
    }

    val statusBadgeText = remember(item.isBackedUp, item.localUri) {
        val isLocalFileExisting = item.localUri.isNotBlank() && (item.localUri.startsWith("content://") || File(item.localUri.removePrefix("file://")).exists())
        when {
            item.isBackedUp && isLocalFileExisting -> "Backed Up • Local + Cloud"
            item.isBackedUp -> "Cloud Only"
            else -> "Local Only"
        }
    }

    val canDownload = viewerState is ViewerFileState.Ready && item.entity != null
    val isDownloading = viewerState is ViewerFileState.Loading

    GlassBox(
        shape = RoundedCornerShape(24.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = takenDate,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                )
                Text(
                    text = "${item.mimeType}  •  ${item.sizeBytes.formatFileSize()}",
                    style = DataTextStyle,
                    color = Mist,
                )
                Text(
                    text = statusBadgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Signal,
                )
            }

            IconButton(
                onClick = onDownload,
                enabled = canDownload,
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        color = Signal,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Download to device gallery",
                        tint = if (canDownload) Color.White else Mist,
                    )
                }
            }
        }
    }
}
