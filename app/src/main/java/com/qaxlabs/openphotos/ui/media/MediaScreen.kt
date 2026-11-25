package com.qaxlabs.openphotos.ui.media

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qaxlabs.openphotos.ui.components.BackupStatusCard
import com.qaxlabs.openphotos.ui.components.BottomNavCapsule
import com.qaxlabs.openphotos.ui.components.GlassBox
import com.qaxlabs.openphotos.ui.components.GlassCard
import com.qaxlabs.openphotos.ui.components.NavDestination
import com.qaxlabs.openphotos.ui.theme.Ink
import com.qaxlabs.openphotos.ui.theme.Signal
import com.qaxlabs.openphotos.ui.upload.UploadViewModel
import com.qaxlabs.openphotos.ui.util.formatFileSize

/**
 * Device media browser screen (FR-MEDIA-2 + FR-MEDIA-3 & §10.5).
 */
@Composable
fun MediaScreen(
    onNavigateDestination: (NavDestination) -> Unit,
    onNavigateToUpload: () -> Unit,
    vm: MediaViewModel = hiltViewModel(),
    uploadVm: UploadViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mediaItems by vm.mediaItems.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val selectedIds by vm.selectedIds.collectAsStateWithLifecycle()

    val activeSession by uploadVm.activeSession.collectAsStateWithLifecycle()

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    var hasPermission by remember {
        mutableStateOf(
            requiredPermissions.any {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    var hasPermissionBeenRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissionBeenRequested = true
        val granted = results.values.any { it }
        hasPermission = granted
        if (granted) {
            vm.loadMedia()
        }
    }

    // Automatically re-check permission when returning from System Settings or Picker
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = requiredPermissions.any {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
                hasPermission = granted
                if (granted) {
                    vm.loadMedia()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) vm.loadMedia()
    }

    val activity = context as? android.app.Activity
    val shouldShowRationale = remember(hasPermission, hasPermissionBeenRequested) {
        activity != null && requiredPermissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
    }
    val isPermanentlyDenied = hasPermissionBeenRequested && !hasPermission && !shouldShowRationale

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        if (!hasPermission) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassCard(modifier = Modifier.padding(24.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Photo & Video Access Needed",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "OpenPhotos requires permission to access your device photos and videos to back them up securely to Telegram.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(20.dp))
                        if (isPermanentlyDenied) {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                },
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = ButtonDefaults.buttonColors(containerColor = Signal),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                            ) {
                                Text("Open System Settings", color = Ink, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { permissionLauncher.launch(requiredPermissions) },
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = ButtonDefaults.buttonColors(containerColor = Signal),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                            ) {
                                Text("Grant Permission", color = Ink, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            val hasAnySelection = selectedIds.isNotEmpty()

            Column(modifier = Modifier.fillMaxSize()) {
                // Compact Backup Status Card
                if (activeSession != null && activeSession!!.status in listOf("ACTIVE", "PAUSED")) {
                    BackupStatusCard(
                        session = activeSession!!,
                        onPause = { uploadVm.pauseBackup() },
                        onResume = { uploadVm.resumeBackup() },
                        onOpenDetails = onNavigateToUpload,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(
                        start = 2.dp,
                        top = 2.dp,
                        end = 2.dp,
                        bottom = 90.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(mediaItems, key = { it.id }) { item ->
                        MediaGridItem(
                            item = item,
                            isSelected = item.id in selectedIds,
                            hasAnySelection = hasAnySelection,
                            onToggle = { vm.toggleSelection(item.id) },
                        )
                    }
                }
            }

            if (mediaItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No photos or videos found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Selection capsule (FR-MEDIA-3)
            AnimatedVisibility(
                visible = hasAnySelection,
                enter = slideInVertically(animationSpec = tween(200), initialOffsetY = { it }),
                exit = slideOutVertically(animationSpec = tween(150), targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    GlassBox(
                        shape = RoundedCornerShape(32.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = "${vm.selectionCount} item${if (vm.selectionCount != 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Ink,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = vm.selectionSizeBytes.formatFileSize(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Ink.copy(alpha = 0.65f),
                                )
                            }

                            Spacer(Modifier.weight(1f))

                            Button(
                                onClick = {
                                    uploadVm.startUserBackupSession(vm.selectedItems)
                                    vm.clearSelection()
                                },
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = ButtonDefaults.buttonColors(containerColor = Signal),
                            ) {
                                Text("Back up", color = Ink, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // Floating glass bottom nav capsule when no items are selected
            AnimatedVisibility(
                visible = !hasAnySelection,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                BottomNavCapsule(
                    currentDestination = NavDestination.BACKUP,
                    onNavigate = onNavigateDestination,
                )
            }
        }
    }
}
