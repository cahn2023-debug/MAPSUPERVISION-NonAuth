package com.mapsupervision.app.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mapsupervision.domain.model.SitePhoto
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.outlined.PlayCircle
import com.mapsupervision.domain.model.MediaType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceRefreshContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
    ) {
        content()
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

@Composable
fun SitePhotoThumb(
    photo: SitePhoto,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val thumbFile = File(photo.thumbnailPath.ifBlank { photo.filePath })
    val context = LocalContext.current
    val theme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(theme.surfaceVariant)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
    ) {
        if (thumbFile.exists()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(thumbFile)
                    .crossfade(true)
                    .build(),
                contentDescription = photo.objectCode,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = null
            )
        } else {
            Icon(
                Icons.Outlined.CameraAlt,
                contentDescription = null,
                tint = theme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp)
            )
        }
        val ts = SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date(photo.capturedAtEpochMs))
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(theme.scrim.copy(alpha = 0.68f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(ts, color = theme.onSurface, fontSize = 9.sp)
        }

        if (photo.mediaType == MediaType.VIDEO) {
            Icon(
                Icons.Outlined.PlayCircle,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
            )
            val durationText = formatDuration(photo.durationMs)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(theme.scrim.copy(alpha = 0.68f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(durationText, color = Color.White, fontSize = 9.sp)
            }
        }
    }
}

@Composable
fun WorkspaceEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified
) {
    val resolvedTextColor = if (textColor == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        textColor
    }
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = resolvedTextColor,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

// Neon Cyber Gradients
val NeonCyberOrangeGradient = androidx.compose.ui.graphics.Brush.linearGradient(
    colors = listOf(
        Color(0xFFFFB074), // PrimaryPeach
        Color(0xFFFF8F00)  // PrimaryContainer
    )
)

val NeonCyberCyanGradient = androidx.compose.ui.graphics.Brush.linearGradient(
    colors = listOf(
        Color(0xFF00FFCC), // SecondaryMint
        Color(0xFF00E5FF)  // TertiaryCyan
    )
)

@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.foundation.shape.CornerBasedShape = RoundedCornerShape(12.dp),
    borderAlpha: Float = 0.15f,
    backgroundAlpha: Float = 0.5f, // Glass trans
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val theme = MaterialTheme.colorScheme
    val borderBrush = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = borderAlpha),
            Color.White.copy(alpha = borderAlpha * 0.3f),
            theme.primary.copy(alpha = borderAlpha)
        )
    )
    val cardBgColor = theme.surfaceVariant.copy(alpha = backgroundAlpha)

    if (onClick != null) {
        androidx.compose.material3.Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = cardBgColor),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderBrush),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            content()
        }
    } else {
        androidx.compose.material3.Card(
            modifier = modifier,
            shape = shape,
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = cardBgColor),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderBrush),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            content()
        }
    }
}

