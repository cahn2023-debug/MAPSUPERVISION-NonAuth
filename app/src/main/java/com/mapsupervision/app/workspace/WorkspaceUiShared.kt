package com.mapsupervision.app.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import com.mapsupervision.app.ui.theme.extendedColors

data class ScreenUiMessage(
    val title: String,
    val detail: String? = null,
    val actionLabel: String? = null
)

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

@Composable
fun WorkspaceLoadingState(
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun WorkspaceErrorState(
    message: ScreenUiMessage,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AppPanelCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = message.title,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleSmall
            )
            if (!message.detail.isNullOrBlank()) {
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = message.detail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (onRetry != null) {
                Spacer(modifier = Modifier.size(12.dp))
                AppButton(
                    text = message.actionLabel ?: "Thử lại",
                    onClick = onRetry,
                    isPrimary = false
                )
            }
        }
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
    shape: androidx.compose.foundation.shape.CornerBasedShape = RoundedCornerShape(6.dp),
    borderAlpha: Float = 0.15f,
    backgroundAlpha: Float = 1.0f, // No glass trans
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    AppPanelCard(
        modifier = modifier,
        shape = shape,
        onClick = onClick,
        content = content
    )
}

@Composable
fun AppPanelCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.foundation.shape.CornerBasedShape = MaterialTheme.shapes.medium,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val theme = MaterialTheme.colorScheme
    val borderStroke = androidx.compose.foundation.BorderStroke(1.dp, theme.outlineVariant)
    val cardBgColor = MaterialTheme.extendedColors.panelBackground

    if (onClick != null) {
        androidx.compose.material3.Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = cardBgColor),
            border = borderStroke,
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            content()
        }
    } else {
        androidx.compose.material3.Card(
            modifier = modifier,
            shape = shape,
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = cardBgColor),
            border = borderStroke,
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            content()
        }
    }
}

@Composable
fun AppOverlayCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.foundation.shape.CornerBasedShape = MaterialTheme.shapes.large,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val theme = MaterialTheme.colorScheme
    val borderStroke = androidx.compose.foundation.BorderStroke(1.dp, theme.outline)
    val cardBgColor = MaterialTheme.extendedColors.panelBackgroundOverlay

    if (onClick != null) {
        androidx.compose.material3.Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = cardBgColor),
            border = borderStroke,
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            content()
        }
    } else {
        androidx.compose.material3.Card(
            modifier = modifier,
            shape = shape,
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = cardBgColor),
            border = borderStroke,
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            content()
        }
    }
}

@Composable
fun AppScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .background(Color.Black)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (navigationIcon != null) {
            navigationIcon()
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
        }
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (actions != null) {
            actions()
        }
    }
}

@Composable
fun AppSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        if (action != null) {
            action()
        }
    }
}

@Composable
fun AppSectionContainer(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    AppPanelCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (action != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    action()
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
            content()
        }
    }
}

@Composable
fun AppStatusBadge(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(backgroundColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}

@Composable
fun AppMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    AppPanelCard(modifier = modifier) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accentColor ?: MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun AppFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        modifier = modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = true
) {
    val buttonModifier = modifier
        .heightIn(min = 48.dp)
        .widthIn(min = 48.dp)
        .semantics {
            role = Role.Button
            contentDescription = text
        }
    if (isPrimary) {
        Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = MaterialTheme.shapes.small,
            contentPadding = ButtonDefaults.ContentPadding
        ) {
            Text(text = text, maxLines = 1)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = MaterialTheme.shapes.small,
            contentPadding = ButtonDefaults.ContentPadding
        ) {
            Text(text = text, maxLines = 1)
        }
    }
}

@Composable
fun AppIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(48.dp)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            }
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}

@Composable
fun AppRetryAction(
    label: String = "Làm mới",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppButton(
        text = label,
        onClick = onClick,
        modifier = modifier,
        isPrimary = false
    )
}
