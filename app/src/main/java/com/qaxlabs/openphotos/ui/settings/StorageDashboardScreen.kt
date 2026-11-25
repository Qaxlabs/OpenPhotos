package com.qaxlabs.openphotos.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qaxlabs.openphotos.ui.components.GlassCard
import com.qaxlabs.openphotos.ui.theme.Aurora
import com.qaxlabs.openphotos.ui.theme.Ink
import com.qaxlabs.openphotos.ui.theme.Mist
import com.qaxlabs.openphotos.ui.theme.Panel
import com.qaxlabs.openphotos.ui.theme.Void
import com.qaxlabs.openphotos.ui.util.formatFileSize

/**
 * Storage Dashboard Screen.
 */
@Composable
fun StorageDashboardScreen(
    vm: StorageInsightsViewModel = hiltViewModel(),
) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val isFreeingSpace by vm.isFreeingSpace.collectAsStateWithLifecycle()
    var showFreeUpDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    if (showFreeUpDialog) {
        AlertDialog(
            onDismissRequest = { showFreeUpDialog = false },
            title = { Text("Free Up Local Device Space?", color = Ink) },
            text = {
                Text(
                    text = "This will safely delete ${stats.recoverableBytes.formatFileSize()} of local files that are fully backed up to Telegram. Your files remain safely accessible in your cloud vault.",
                    color = Mist,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.freeUpSpace()
                        showFreeUpDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Aurora, contentColor = Void),
                ) {
                    Text("Free Up Space")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFreeUpDialog = false }) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Storage Dashboard",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = Ink,
            )

            // Free Up Space Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = Icons.Filled.DeleteSweep, contentDescription = null, tint = Aurora, modifier = Modifier.size(28.dp))
                        Column {
                            Text(text = "Recoverable Storage", style = MaterialTheme.typography.titleMedium, color = Ink)
                            Text(text = "${stats.recoverableBytes.formatFileSize()} can be freed", style = MaterialTheme.typography.bodyMedium, color = Mist)
                        }
                    }

                    Button(
                        onClick = { showFreeUpDialog = true },
                        enabled = stats.recoverableBytes > 0 && !isFreeingSpace,
                        colors = ButtonDefaults.buttonColors(containerColor = Aurora, contentColor = Void),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isFreeingSpace) {
                            CircularProgressIndicator(color = Void, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Free Up ${stats.recoverableBytes.formatFileSize()}")
                        }
                    }
                }
            }

            // Local Media Stats Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = Icons.Filled.Smartphone, contentDescription = null, tint = Mist, modifier = Modifier.size(20.dp))
                        Text(text = "Local Device Storage", style = MaterialTheme.typography.titleMedium, color = Ink)
                    }
                    Text(text = "Local Items: ${stats.localCount}", style = MaterialTheme.typography.bodyMedium, color = Mist)
                    Text(text = "Total Local Size: ${stats.totalLocalBytes.formatFileSize()}", style = MaterialTheme.typography.bodyMedium, color = Mist)
                }
            }

            // Cloud Media Stats Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = Icons.Filled.Cloud, contentDescription = null, tint = Aurora, modifier = Modifier.size(20.dp))
                        Text(text = "Telegram Vault Cloud Storage", style = MaterialTheme.typography.titleMedium, color = Ink)
                    }
                    Text(text = "Backed Up Items: ${stats.backedUpCount}", style = MaterialTheme.typography.bodyMedium, color = Mist)
                    Text(text = "Pending Uploads: ${stats.pendingCount}", style = MaterialTheme.typography.bodyMedium, color = Mist)
                    Text(text = "Failed Uploads: ${stats.failedCount}", style = MaterialTheme.typography.bodyMedium, color = Mist)
                }
            }

            // Cache Usage Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = Icons.Filled.CleaningServices, contentDescription = null, tint = Mist, modifier = Modifier.size(20.dp))
                        Text(text = "App Cache Usage", style = MaterialTheme.typography.titleMedium, color = Ink)
                    }
                    Text(text = "Thumbnail Cache: ${stats.thumbnailCacheBytes.formatFileSize()}", style = MaterialTheme.typography.bodyMedium, color = Mist)
                    Text(text = "Downloaded Media Cache: ${stats.fullresCacheBytes.formatFileSize()}", style = MaterialTheme.typography.bodyMedium, color = Mist)
                }
            }
        }
    }
}
