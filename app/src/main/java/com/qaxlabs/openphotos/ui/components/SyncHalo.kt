package com.qaxlabs.openphotos.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.qaxlabs.openphotos.data.BackupStatus
import com.qaxlabs.openphotos.ui.theme.Aurora

/**
 * Signature Element: The Sync Halo (design_system.md §8 & §9).
 *
 * Rotating & pulsing Aurora-colored gradient ring during active upload.
 * On completion, contracts into a small Aurora dot with a checkmark in the bottom-right corner.
 * Also supports displaying status badges for [BackupStatus].
 */
@Composable
fun SyncHalo(
    isUploading: Boolean = false,
    isBackedUp: Boolean = false,
    backupStatus: BackupStatus? = null,
    modifier: Modifier = Modifier,
) {
    val activeStatus = backupStatus ?: when {
        isUploading -> BackupStatus.UPLOADING
        isBackedUp -> BackupStatus.BACKED_UP
        else -> BackupStatus.LOCAL_ONLY
    }

    when (activeStatus) {
        BackupStatus.UPLOADING -> {
            val infiniteTransition = rememberInfiniteTransition(label = "SyncHaloTransition")

            val rotationDegrees by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 3000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "Rotation",
            )

            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "Pulse",
            )

            val haloGradient = Brush.sweepGradient(
                colors = listOf(
                    Aurora.copy(alpha = 0.1f * pulseAlpha),
                    Aurora.copy(alpha = pulseAlpha),
                    Aurora.copy(alpha = 0.3f * pulseAlpha),
                    Aurora.copy(alpha = 0.9f * pulseAlpha),
                    Aurora.copy(alpha = 0.1f * pulseAlpha),
                )
            )

            Canvas(
                modifier = modifier
                    .fillMaxSize()
                    .rotate(rotationDegrees)
                    .padding(2.dp)
            ) {
                val strokeWidth = 2.5.dp.toPx()
                drawCircle(
                    brush = haloGradient,
                    style = Stroke(width = strokeWidth),
                )
            }
        }
        BackupStatus.BACKED_UP -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(6.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(Aurora, CircleShape)
                        .border(1.dp, Color.Black.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Backed up",
                        tint = Color.Black,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
        BackupStatus.REMOTE_ONLY -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(6.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cloud,
                        contentDescription = "Cloud only",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
        BackupStatus.LOCAL_ONLY -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(6.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Smartphone,
                        contentDescription = "Local only",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
        BackupStatus.FAILED, BackupStatus.OVERSIZED -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(6.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(Color.Red.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = "Error",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
        BackupStatus.PENDING_BACKUP -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(6.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .border(1.5.dp, Aurora, CircleShape),
                )
            }
        }
    }
}
