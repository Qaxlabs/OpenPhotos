package com.qaxlabs.openphotos.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qaxlabs.openphotos.data.db.BackupSessionEntity
import com.qaxlabs.openphotos.ui.theme.Aurora
import com.qaxlabs.openphotos.ui.theme.Ink
import com.qaxlabs.openphotos.ui.theme.Mist
import com.qaxlabs.openphotos.ui.theme.Void

/**
 * Compact Google Photos-Style Backup Status Card.
 *
 * Appears as a floating glass card displaying real-time upload progress.
 */
@Composable
fun BackupStatusCard(
    session: BackupSessionEntity,
    currentFileName: String? = null,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressFraction = if (session.totalBytes > 0) {
        (session.uploadedBytes.toFloat() / session.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val percentage = (progressFraction * 100).toInt()

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetails),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (session.status == "PAUSED") "Backup Paused" else "Backing up media…",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                    )
                    Text(
                        text = "${session.completedItems}/${session.totalItems} ($percentage%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Aurora,
                    )
                }

                if (!currentFileName.isNullOrEmpty()) {
                    Text(
                        text = currentFileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Mist,
                        maxLines = 1,
                    )
                }

                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    color = Aurora,
                    trackColor = Void,
                )
            }

            Spacer(Modifier.width(12.dp))

            IconButton(
                onClick = {
                    if (session.status == "PAUSED") onResume() else onPause()
                },
            ) {
                Icon(
                    imageVector = if (session.status == "PAUSED") Icons.Filled.PlayArrow else Icons.Filled.HourglassBottom,
                    contentDescription = if (session.status == "PAUSED") "Resume Backup" else "Pause Backup",
                    tint = Aurora,
                )
            }
        }
    }
}
