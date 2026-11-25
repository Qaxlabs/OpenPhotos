package com.qaxlabs.openphotos.ui.gallery

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qaxlabs.openphotos.data.util.ScanProgress
import com.qaxlabs.openphotos.ui.components.BottomNavCapsule
import com.qaxlabs.openphotos.ui.components.GlassBox
import com.qaxlabs.openphotos.ui.components.GlassCard
import com.qaxlabs.openphotos.ui.components.NavDestination
import com.qaxlabs.openphotos.ui.theme.Aurora
import com.qaxlabs.openphotos.ui.theme.Ink
import com.qaxlabs.openphotos.ui.theme.Mist
import com.qaxlabs.openphotos.ui.theme.Panel
import com.qaxlabs.openphotos.ui.theme.Void

/**
 * Vault Gallery Screen with Strict Chronological Date Headers & Fast-Scroll Indicator.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryScreen(
    onNavigateDestination: (NavDestination) -> Unit,
    onItemClick: (indexId: String) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    vm: GalleryViewModel = hiltViewModel(),
) {
    val items by vm.filteredCombinedItems.collectAsStateWithLifecycle()
    val dateGroupList by vm.dateGroupList.collectAsStateWithLifecycle()
    val scanProgress by vm.scanProgress.collectAsStateWithLifecycle()
    val activeFilter by vm.activeFilter.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val selectedIds by vm.selectedItemIds.collectAsStateWithLifecycle()
    val isMultiSelect by vm.isMultiSelectMode.collectAsStateWithLifecycle()

    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val scope = rememberCoroutineScope()

    var showSearchInput by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showDatePickerMenu by remember { mutableStateOf(false) }
    var showAlbumDialog by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }

    var hasPermissions by remember { mutableStateOf(vm.hasMediaPermissions()) }
    var hasScrolledToTopOnLoad by remember { mutableStateOf(false) }

    // Reset filter to ALL and scroll to top (Today) on entry
    LaunchedEffect(Unit) {
        vm.setFilter(GalleryFilter.ALL)
        gridState.scrollToItem(0)
    }

    LaunchedEffect(dateGroupList.isNotEmpty()) {
        if (dateGroupList.isNotEmpty() && !hasScrolledToTopOnLoad) {
            gridState.scrollToItem(0)
            hasScrolledToTopOnLoad = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.values.all { it }
        hasPermissions = isGranted
        if (isGranted) {
            vm.startMediaScan()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    android.Manifest.permission.READ_MEDIA_IMAGES,
                    android.Manifest.permission.READ_MEDIA_VIDEO,
                )
            } else {
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 10
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            vm.loadNextPage()
        }
    }

    // Active date string tracking visible item during fast scrolling
    val activeScrollDateText by remember {
        derivedStateOf {
            if (dateGroupList.isEmpty()) return@derivedStateOf ""
            var count = 0
            for (group in dateGroupList) {
                count += 1 // date header item
                count += group.items.size // group items
                if (gridState.firstVisibleItemIndex < count) {
                    return@derivedStateOf group.headerTitle
                }
            }
            dateGroupList.lastOrNull()?.headerTitle ?: ""
        }
    }

    // Map of header titles to grid item indices for interactive date jumping
    val dateHeaderIndices by remember(dateGroupList) {
        derivedStateOf {
            val map = LinkedHashMap<String, Int>()
            var count = 0
            for (group in dateGroupList) {
                map[group.headerTitle] = count
                count += 1 + group.items.size
            }
            map
        }
    }

    if (showAlbumDialog) {
        AlertDialog(
            onDismissRequest = { showAlbumDialog = false },
            title = { Text("Add Selected to Album", color = Ink) },
            text = {
                OutlinedTextField(
                    value = newAlbumName,
                    onValueChange = { newAlbumName = it },
                    label = { Text("Album Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newAlbumName.isNotBlank()) {
                            vm.createAlbum(newAlbumName)
                            newAlbumName = ""
                            showAlbumDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Aurora, contentColor = Void),
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAlbumDialog = false }) {
                    Text("Cancel", color = Mist)
                }
            },
            containerColor = Panel,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Void)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        // ── Main Content Grid / Permission Card / Empty State ───────────────
        if (!hasPermissions) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 150.dp, bottom = 90.dp),
                contentAlignment = Alignment.Center,
            ) {
                GlassCard(
                    modifier = Modifier.padding(horizontal = 24.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PermMedia,
                            contentDescription = null,
                            tint = Aurora,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = "Media Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "OpenPhotos needs access to your device photos and videos to display your local camera roll.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Mist,
                        )
                        Button(
                            onClick = {
                                val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    arrayOf(
                                        android.Manifest.permission.READ_MEDIA_IMAGES,
                                        android.Manifest.permission.READ_MEDIA_VIDEO,
                                    )
                                } else {
                                    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                                permissionLauncher.launch(permissionsToRequest)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Aurora, contentColor = Void),
                        ) {
                            Text("Grant Access")
                        }
                    }
                }
            }
        } else if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 150.dp, bottom = 90.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = Mist,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = "No matching media found",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Try clearing search or changing active filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mist,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    top = 110.dp,
                    bottom = 90.dp,
                    start = 2.dp,
                    end = 2.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                dateGroupList.forEach { group ->
                    item(
                        key = "header_${group.headerTitle}_${group.dateKey}",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Text(
                            text = group.headerTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Ink,
                            modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 8.dp),
                        )
                    }

                    items(
                        items = group.items,
                        key = { it.indexId },
                    ) { item ->
                        val isSelected = selectedIds.contains(item.indexId)
                        GalleryItemCell(
                            indexId = item.indexId,
                            uriString = item.localUri,
                            thumbnailPath = item.thumbnailPath,
                            displayName = item.displayName,
                            isVideo = item.isVideo,
                            isUploading = item.isUploading,
                            isBackedUp = item.isBackedUp,
                            backupStatus = item.backupStatus,
                            onClick = {
                                if (isMultiSelect) {
                                    vm.toggleItemSelection(item.indexId)
                                } else {
                                    onItemClick(item.indexId)
                                }
                            },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            modifier = if (isSelected) Modifier.background(Aurora.copy(alpha = 0.3f)) else Modifier,
                        )
                    }
                }
            }
        }

        // ── Fast-Scroll Floating Date Indicator Pill ───────────────────────
        AnimatedVisibility(
            visible = (gridState.isScrollInProgress || showDatePickerMenu) && activeScrollDateText.isNotBlank(),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
        ) {
            Box {
                Surface(
                    color = Aurora,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier.clickable { showDatePickerMenu = true },
                ) {
                    Text(
                        text = activeScrollDateText,
                        style = MaterialTheme.typography.labelMedium,
                        color = Void,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }

                DropdownMenu(
                    expanded = showDatePickerMenu,
                    onDismissRequest = { showDatePickerMenu = false },
                    modifier = Modifier
                        .background(Panel)
                        .width(220.dp),
                ) {
                    dateHeaderIndices.forEach { (headerTitle, targetIndex) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = headerTitle,
                                    fontWeight = if (headerTitle == activeScrollDateText) FontWeight.Bold else FontWeight.Normal,
                                    color = if (headerTitle == activeScrollDateText) Aurora else Ink,
                                )
                            },
                            onClick = {
                                showDatePickerMenu = false
                                scope.launch {
                                    gridState.animateScrollToItem(targetIndex)
                                }
                            },
                            leadingIcon = {
                                if (headerTitle == activeScrollDateText) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Aurora)
                                }
                            }
                        )
                    }
                }
            }
        }

        // ── Floating Glass Top Bar with Filter Dropdown & Search ───────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            GlassBox(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Vault",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium,
                            color = Ink,
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Filter Button
                            Box {
                                IconButton(onClick = { showFilterMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.FilterList,
                                        contentDescription = "Filter",
                                        tint = if (activeFilter != GalleryFilter.ALL) Aurora else Mist,
                                    )
                                }

                                DropdownMenu(
                                    expanded = showFilterMenu,
                                    onDismissRequest = { showFilterMenu = false },
                                    modifier = Modifier
                                        .background(Panel)
                                        .width(260.dp),
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("All", fontWeight = FontWeight.Bold, color = Ink)
                                                Text("All local & cloud media", style = MaterialTheme.typography.labelSmall, color = Mist)
                                            }
                                        },
                                        onClick = {
                                            vm.setFilter(GalleryFilter.ALL)
                                            showFilterMenu = false
                                        },
                                        leadingIcon = {
                                            if (activeFilter == GalleryFilter.ALL) {
                                                Icon(Icons.Filled.Check, contentDescription = null, tint = Aurora)
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Photos", color = Ink) },
                                        onClick = {
                                            vm.setFilter(GalleryFilter.PHOTOS)
                                            showFilterMenu = false
                                        },
                                        leadingIcon = {
                                            if (activeFilter == GalleryFilter.PHOTOS) {
                                                Icon(Icons.Filled.Check, contentDescription = null, tint = Aurora)
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Videos", color = Ink) },
                                        onClick = {
                                            vm.setFilter(GalleryFilter.VIDEOS)
                                            showFilterMenu = false
                                        },
                                        leadingIcon = {
                                            if (activeFilter == GalleryFilter.VIDEOS) {
                                                Icon(Icons.Filled.Check, contentDescription = null, tint = Aurora)
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("Local Only", color = Ink)
                                                Text("Media stored only on device", style = MaterialTheme.typography.labelSmall, color = Mist)
                                            }
                                        },
                                        onClick = {
                                            vm.setFilter(GalleryFilter.LOCAL_ONLY)
                                            showFilterMenu = false
                                        },
                                        leadingIcon = {
                                            if (activeFilter == GalleryFilter.LOCAL_ONLY) {
                                                Icon(Icons.Filled.Check, contentDescription = null, tint = Aurora)
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("Backed Up", color = Ink)
                                                Text("Media saved in Telegram cloud vault", style = MaterialTheme.typography.labelSmall, color = Mist)
                                            }
                                        },
                                        onClick = {
                                            vm.setFilter(GalleryFilter.BACKED_UP)
                                            showFilterMenu = false
                                        },
                                        leadingIcon = {
                                            if (activeFilter == GalleryFilter.BACKED_UP) {
                                                Icon(Icons.Filled.Check, contentDescription = null, tint = Aurora)
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("Cloud Only", color = Ink)
                                                Text("Vault media whose local file was deleted", style = MaterialTheme.typography.labelSmall, color = Mist)
                                            }
                                        },
                                        onClick = {
                                            vm.setFilter(GalleryFilter.CLOUD_ONLY)
                                            showFilterMenu = false
                                        },
                                        leadingIcon = {
                                            if (activeFilter == GalleryFilter.CLOUD_ONLY) {
                                                Icon(Icons.Filled.Check, contentDescription = null, tint = Aurora)
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Pending Backup", color = Ink) },
                                        onClick = {
                                            vm.setFilter(GalleryFilter.PENDING_BACKUP)
                                            showFilterMenu = false
                                        },
                                        leadingIcon = {
                                            if (activeFilter == GalleryFilter.PENDING_BACKUP) {
                                                Icon(Icons.Filled.Check, contentDescription = null, tint = Aurora)
                                            }
                                        }
                                    )
                                }
                            }

                            // Search Button
                            IconButton(onClick = { showSearchInput = !showSearchInput }) {
                                Icon(imageVector = Icons.Filled.Search, contentDescription = "Search", tint = Mist)
                            }

                            if (items.isNotEmpty()) {
                                Text(
                                    text = "${items.size} item${if (items.size == 1) "" else "s"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Mist,
                                )
                            }
                        }
                    }

                    // Expandable search query input
                    AnimatedVisibility(visible = showSearchInput) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { vm.setSearchQuery(it) },
                            placeholder = { Text("Search filename or date...", color = Mist) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { vm.setSearchQuery("") }) {
                                        Icon(imageVector = Icons.Filled.Clear, contentDescription = "Clear", tint = Mist)
                                    }
                                }
                            }
                        )
                    }

                    // Scanning progress banner
                    AnimatedVisibility(
                        visible = scanProgress is ScanProgress.Scanning,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        val currentProgress = scanProgress as? ScanProgress.Scanning
                        val text = if (currentProgress != null && currentProgress.totalEstimate > 0) {
                            "Indexing media: ${currentProgress.scannedCount} / ${currentProgress.totalEstimate} items..."
                        } else {
                            "Scanning device media..."
                        }
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            Text(text = text, style = MaterialTheme.typography.bodySmall, color = Aurora)
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                color = Aurora,
                                trackColor = Void,
                            )
                        }
                    }
                }
            }
        }

        // ── Multi-Selection Action Bar ─────────────────────────────────────
        AnimatedVisibility(
            visible = isMultiSelect,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
        ) {
            Surface(
                color = Panel,
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 8.dp,
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${selectedIds.size} selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink,
                    )
                    IconButton(onClick = { vm.selectAll() }) {
                        Icon(imageVector = Icons.Filled.SelectAll, contentDescription = "Select All", tint = Aurora)
                    }
                    IconButton(onClick = { showAlbumDialog = true }) {
                        Icon(imageVector = Icons.Filled.FolderSpecial, contentDescription = "Add to Album", tint = Aurora)
                    }
                    IconButton(onClick = { vm.clearSelection() }) {
                        Icon(imageVector = Icons.Filled.Clear, contentDescription = "Clear Selection", tint = Mist)
                    }
                }
            }
        }

        // ── Floating Glass Bottom Navigation Capsule ────────────────────────
        BottomNavCapsule(
            currentDestination = NavDestination.VAULT,
            onNavigate = onNavigateDestination,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
