package teikengstudio.xmpro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import teikengstudio.xmpro.ui.theme.XmproShapes
import teikengstudio.xmpro.ui.theme.XmproSpacing
import kotlin.math.roundToInt
import teikengstudio.xmpro.model.Track
import teikengstudio.xmpro.ui.theme.ThemedContent

/**
 * Full-screen "now playing" page: artwork, transport controls, speed, sleep
 * timer and the play queue.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(
    state: UiState,
    positionMs: StateFlow<Long>,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkip: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onEffect: (String) -> Unit = {},
    onEffectStrength: (Int) -> Unit = {},
    onFavorite: () -> Unit,
    onBookmark: () -> Unit,
    isFavorite: Boolean,
    onSleepTimer: (Int?) -> Unit,
    onSleepAtTrackEnd: () -> Unit = {},
    onVolume: (Float) -> Unit = {},
    onSelectQueue: (Int) -> Unit,
    onQueueRange: (Int?, Int?) -> Unit = { _, _ -> },
    onOpenAlbum: (String) -> Unit = {},
    onSkipTrim: (Boolean, Int, Int, String) -> Unit = { _, _, _, _ -> },
    onDownload: (String?) -> Unit = {},
    commentsOpen: Boolean = false,
    comments: List<teikengstudio.xmpro.model.Comment> = emptyList(),
    commentsLoading: Boolean = false,
    commentsError: String? = null,
    commentsTotal: Int = 0,
    commentsHasMore: Boolean = false,
    onOpenComments: () -> Unit = {},
    onCloseComments: () -> Unit = {},
    onLoadMoreComments: () -> Unit = {},
    onRetryComments: () -> Unit = {},
) {
    val track = state.playing
    var queueOpen by remember { mutableStateOf(false) }
    var timerOpen by remember { mutableStateOf(false) }
    var timerCustom by remember { mutableStateOf(false) }
    var timerCustomText by remember { mutableStateOf("") }
    var trimOpen by remember { mutableStateOf(false) }
    var speedOpen by remember { mutableStateOf(false) }
    var effectOpen by remember { mutableStateOf(false) }
    var downloadOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    BackHandler {
        when {
            queueOpen -> queueOpen = false
            timerOpen -> timerOpen = false
            trimOpen -> trimOpen = false
            speedOpen -> speedOpen = false
            effectOpen -> effectOpen = false
            downloadOpen -> downloadOpen = false
            else -> onDismiss()
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // long: 按主题缩放后的实际可用空间计算封面，横屏和窄屏不会挤出底部主控。
        val artworkSize = minOf(260.dp, maxWidth - 48.dp, maxHeight * 0.31f).coerceAtLeast(96.dp)
        ThemedContent {
            Column(Modifier.fillMaxSize().padding(horizontal = XmproSpacing.pageHorizontal)) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "收起")
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "正在收听",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { queueOpen = true }) {
                        Icon(Icons.Filled.PlaylistPlay, contentDescription = "播放列表")
                    }
                }

                // long: 内容不足一屏时居中排布，超出时仅滚动内容；进度和主控始终留在底部。
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val contentHeight = maxHeight
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = contentHeight),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            val cover = track?.coverUrl
                            if (cover != null) {
                                Box(
                                    Modifier
                                        .size(artworkSize)
                                        .shadow(elevation = 18.dp, shape = RoundedCornerShape(28.dp), clip = false),
                                ) {
                                    Cover(cover, artworkSize.value.toInt())
                                }
                            } else {
                                Box(
                                    Modifier
                                        .size(artworkSize)
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(artworkSize * 0.3f),
                                    )
                                }
                            }
                        }

                        Text(
                            track?.title ?: "尚未选择声音",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = XmproSpacing.sectionSpacing),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            listOfNotNull(track?.album?.takeIf { it.isNotEmpty() }, track?.artist?.takeIf { it.isNotEmpty() })
                                .joinToString(" · "),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = XmproSpacing.compactSpacing)
                                .then(
                                    if (track?.albumKey?.isNotEmpty() == true) {
                                        Modifier.clickable { onOpenAlbum(track.albumKey) }
                                    } else {
                                        Modifier
                                    },
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = XmproShapes.card,
                            modifier = Modifier.fillMaxWidth().padding(top = XmproSpacing.sectionSpacing),
                        ) {
                            Column(Modifier.padding(horizontal = XmproSpacing.componentSpacing)) {
                                // long: 音质和音效与辅助操作集中展示，底部只保留五项主要播放控制。
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = XmproSpacing.minimumTouchTarget)
                                        .clip(XmproShapes.compactCard)
                                        .clickable { effectOpen = true }
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val quality = formatQuality(state.playingQuality).ifEmpty { state.playingQuality }
                                    Text(
                                        quality.ifEmpty { "音质" },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    val preset = teikengstudio.xmpro.player.EffectPreset.of(state.effectPreset)
                                    Text(
                                        if (preset == teikengstudio.xmpro.player.EffectPreset.Off) "音效" else "音效 · ${preset.label}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                val savedState = state.downloads.firstOrNull { it.id == track?.id }
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PlayerActionButton(
                                        label = speedChipLabel(state.speed),
                                        active = state.speed != 1f,
                                        onClick = { speedOpen = true },
                                    )
                                    PlayerActionIcon(
                                        icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        label = if (isFavorite) "已收藏" else "收藏",
                                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        enabled = track != null,
                                        onClick = onFavorite,
                                    )
                                    PlayerActionIcon(
                                        icon = when (savedState?.state) {
                                            null -> Icons.Filled.FileDownload
                                            teikengstudio.xmpro.download.DownloadState.DONE -> Icons.Filled.DownloadDone
                                            teikengstudio.xmpro.download.DownloadState.FAILED -> Icons.Filled.Refresh
                                            else -> Icons.Filled.FileDownload
                                        },
                                        label = when (savedState?.state) {
                                            null -> "下载"
                                            teikengstudio.xmpro.download.DownloadState.DONE -> "已下载"
                                            teikengstudio.xmpro.download.DownloadState.FAILED -> "重试下载"
                                            teikengstudio.xmpro.download.DownloadState.PAUSED -> "已暂停"
                                            else -> "下载中"
                                        },
                                        tint = if (savedState == null) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.primary,
                                        // A finished file needs no second tap; the download page owns deletion.
                                        enabled = track != null && savedState?.state != teikengstudio.xmpro.download.DownloadState.DONE,
                                        onClick = { if (savedState == null) downloadOpen = true else onDownload(null) },
                                    )
                                    PlayerActionIcon(
                                        icon = Icons.Filled.ChatBubbleOutline,
                                        label = "评论",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        enabled = track != null,
                                        onClick = onOpenComments,
                                    )
                                }
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PlayerActionIcon(
                                        icon = Icons.Filled.BookmarkBorder,
                                        label = "书签",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        enabled = track != null,
                                        onClick = onBookmark,
                                    )
                                    PlayerActionIcon(
                                        icon = Icons.Filled.Timer,
                                        label = when {
                                            state.sleepAtTrackEnd -> "播完停"
                                            state.sleepAtMillis != null -> "已定时"
                                            else -> "定时"
                                        },
                                        tint = if (state.sleepAtMillis != null || state.sleepAtTrackEnd) {
                                            MaterialTheme.colorScheme.primary
                                        } else MaterialTheme.colorScheme.onSurfaceVariant,
                                        enabled = true,
                                        onClick = { timerOpen = true },
                                    )
                                    // long: 开启头尾跳过时直接展示范围设置，避免用户不清楚实际跳过多少。
                                    PlayerActionIcon(
                                        icon = if (state.skipTrimEnabled) Icons.Filled.TaskAlt else Icons.Filled.SkipNext,
                                        label = "跳过头尾",
                                        tint = if (state.skipTrimEnabled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        enabled = track != null,
                                        onClick = {
                                            val turningOn = !state.skipTrimEnabled
                                            onSkipTrim(
                                                turningOn,
                                                state.introSkipPermille,
                                                state.outroSkipPermille,
                                                state.skipTrimMode,
                                            )
                                            if (turningOn) trimOpen = true
                                        },
                                    )
                                }

                                // long: 应用内音量与系统媒体音量分开，归入辅助区以保持主播放控件连贯。
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            when {
                                                state.playVolume <= 0.01f -> Icons.Filled.VolumeMute
                                                state.playVolume < 0.5f -> Icons.Filled.VolumeDown
                                                else -> Icons.Filled.VolumeUp
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        PlayerSlider(
                                            value = state.playVolume,
                                            onValueChange = onVolume,
                                            semanticsLabel = "应用内播放音量",
                                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = CircleShape,
                                        ) {
                                            Text(
                                                "${(state.playVolume * 100).roundToInt()}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(XmproSpacing.sectionSpacing))
                                Surface(
                                    onClick = { queueOpen = true },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = XmproSpacing.pageHorizontal, vertical = XmproSpacing.componentSpacing),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Filled.PlaylistPlay,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            "待播列表",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            "${state.queue.size} 集",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                PlayerTransportControls(
                    trackId = track?.id,
                    durationMs = state.durationMs,
                    positionMs = positionMs,
                    isPlaying = state.isPlaying,
                    onSeek = onSeek,
                    onSkip = onSkip,
                    onPrevious = onPrevious,
                    onToggle = onToggle,
                    onNext = onNext,
                )
            }
        }
    }

    if (queueOpen) {
        var rangeOpen by remember { mutableStateOf(false) }
        ModalBottomSheet(onDismissRequest = { queueOpen = false }, sheetState = sheetState) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = XmproSpacing.pageHorizontal),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "待播列表",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                // Same slice picker as the album page, applied to the queue.
                Surface(
                    onClick = { rangeOpen = !rangeOpen },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        if (state.queueFrom != null && state.queueTo != null) {
                            "${state.queueFrom}-${state.queueTo}"
                        } else {
                            "共 ${state.queue.size} 集"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                TextButton(onClick = onStop) { Text("清空") }
            }
            AnimatedVisibility(visible = rangeOpen) {
                val full = state.queueAll.ifEmpty { state.queue }
                Column(
                    Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = XmproSpacing.pageHorizontal, vertical = XmproSpacing.componentSpacing),
                ) {
                    TierGrid(
                        total = full.size,
                        selectedStart = state.queueFrom,
                        selectedEnd = state.queueTo,
                        onPick = { tier ->
                            onQueueRange(tier.from, tier.to)
                            rangeOpen = false
                        },
                    )
                    TextButton(
                        onClick = {
                            onQueueRange(null, null)
                            rangeOpen = false
                        },
                        modifier = Modifier.padding(top = 4.dp),
                    ) { Text("显示全部") }
                }
            }
            LazyColumn(Modifier.navigationBarsPadding()) {
                itemsIndexed(state.queue) { index, item ->
                    val active = index == state.queueIndex
                    Surface(
                        onClick = {
                            onSelectQueue(index)
                            queueOpen = false
                        },
                        shape = XmproShapes.card,
                        color = if (active) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        // The tinted colour above matches no scheme role, so the
                        // content colour has to be supplied explicitly.
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = XmproSpacing.pageHorizontal, vertical = XmproSpacing.compactRowVertical),
                    ) {
                        Row(
                            Modifier.padding(XmproSpacing.compactCardContent),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Cover(item.coverUrl, 44)
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = XmproSpacing.componentSpacing),
                            ) {
                                Text(
                                    item.title.ifEmpty { item.number },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (active) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Text(
                                    listOfNotNull(
                                        item.album.takeIf { it.isNotEmpty() },
                                        formatDuration(item.duration),
                                    ).joinToString(" · "),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            if (active) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "正在播放",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (trimOpen) {
        ModalBottomSheet(
            onDismissRequest = { trimOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            TrimSheet(
                enabled = state.skipTrimEnabled,
                mode = state.skipTrimMode,
                intro = state.introSkipPermille,
                outro = state.outroSkipPermille,
                onChange = { on, head, tail, next -> onSkipTrim(on, head, tail, next) },
            )
        }
    }

    if (speedOpen) {
        SpeedSheet(
            speed = state.speed,
            onChange = onSpeedChange,
            onDismiss = { speedOpen = false },
        )
    }

    if (downloadOpen) {
        DownloadQualitySheet(
            onPick = { quality ->
                downloadOpen = false
                onDownload(quality)
            },
            onDismiss = { downloadOpen = false },
        )
    }

    if (effectOpen) {
        EffectSheet(
            preset = state.effectPreset,
            strength = state.effectStrength,
            onPreset = onEffect,
            onStrength = onEffectStrength,
            onDismiss = { effectOpen = false },
        )
    }

    if (commentsOpen) {
        CommentsSheet(
            total = commentsTotal,
            comments = comments,
            loading = commentsLoading,
            error = commentsError,
            hasMore = commentsHasMore,
            onLoadMore = onLoadMoreComments,
            onRetry = onRetryComments,
            onDismiss = onCloseComments,
        )
    }

    if (timerOpen) {
        val remaining = state.sleepAtMillis?.let { deadline ->
            ((deadline - System.currentTimeMillis()) / 60_000L).coerceAtLeast(1L)
        }
        AlertDialog(
            onDismissRequest = { timerOpen = false },
            title = { Text("定时关闭") },
            text = {
                Column {
                    Text(
                        when {
                            state.sleepAtTrackEnd -> "当前会在一集播完后停止播放。"
                            remaining != null -> "还有约 $remaining 分钟停止播放。"
                            else -> "到达时间后停止播放。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        15 to "15 分钟",
                        30 to "30 分钟",
                        45 to "45 分钟",
                        60 to "60 分钟",
                    ).forEach { (minutes, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onSleepTimer(minutes)
                                    timerOpen = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onSleepAtTrackEnd()
                                timerOpen = false
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("本集播完后", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { timerCustom = !timerCustom }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("自定义分钟…", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (timerCustom) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = timerCustomText,
                                onValueChange = { input ->
                                    timerCustomText = input.filter(Char::isDigit).take(4)
                                },
                                singleLine = true,
                                placeholder = { Text("分钟") },
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = {
                                    val minutes = timerCustomText.toIntOrNull() ?: 0
                                    if (minutes > 0) {
                                        onSleepTimer(minutes)
                                        timerCustomText = ""
                                        timerOpen = false
                                    }
                                },
                                enabled = (timerCustomText.toIntOrNull() ?: 0) > 0,
                            ) { Text("确定") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSleepTimer(null)
                    timerOpen = false
                }) { Text("关闭定时") }
            },
            dismissButton = {
                TextButton(onClick = { timerOpen = false }) { Text("取消") }
            },
        )
    }
}

/** long: 进度更新只重组底部控制区；五个主控固定排列，所有尺寸由全局主题统一缩放。 */
@Composable
private fun PlayerTransportControls(
    trackId: String?,
    durationMs: Long,
    positionMs: StateFlow<Long>,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
) {
    var dragPosition by remember(trackId) { mutableFloatStateOf(-1f) }
    val livePosition by positionMs.collectAsState()
    val total = durationMs.coerceAtLeast(0)
    val shown = if (dragPosition >= 0f) dragPosition.toLong() else livePosition
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        // long: 抬高固定主控卡以平衡上方留白，同时为底部手势区保留足够间距。
        modifier = Modifier.fillMaxWidth().padding(bottom = 36.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            PlayerSlider(
                value = if (total > 0) (shown.toFloat() / total).coerceIn(0f, 1f) else 0f,
                onValueChange = { value -> if (total > 0) dragPosition = value * total },
                semanticsLabel = "播放进度",
                onValueChangeFinished = {
                    if (dragPosition >= 0f) {
                        onSeek(dragPosition.toLong())
                        dragPosition = -1f
                    }
                },
                enabled = total > 0,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(
                    formatDuration(shown / 1000.0),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatDuration(total / 1000.0),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onSkip(-10_000) }, modifier = Modifier.size(XmproSpacing.minimumTouchTarget)) {
                    Icon(Icons.Filled.Replay10, contentDescription = "快退 10 秒", modifier = Modifier.size(26.dp))
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(XmproSpacing.minimumTouchTarget)) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "上一集", modifier = Modifier.size(30.dp))
                }
                FilledIconButton(onClick = onToggle, modifier = Modifier.size(74.dp), shape = CircleShape) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(38.dp),
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(XmproSpacing.minimumTouchTarget)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "下一集", modifier = Modifier.size(30.dp))
                }
                IconButton(onClick = { onSkip(30_000) }, modifier = Modifier.size(XmproSpacing.minimumTouchTarget)) {
                    Icon(Icons.Filled.Forward30, contentDescription = "快进 30 秒", modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun PlayerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    semanticsLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
    )
    val interactionSource = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics { contentDescription = semanticsLabel },
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        interactionSource = interactionSource,
        // long: 用圆形小滑块和细轨道替代播放器默认的大号胶囊滑块，保留完整拖动及触控范围。
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                colors = colors,
                enabled = enabled,
                thumbSize = DpSize(12.dp, 12.dp),
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(4.dp),
                colors = colors,
                enabled = enabled,
                drawStopIndicator = null,
                thumbTrackGapSize = 0.dp,
                trackInsideCornerSize = 0.dp,
            )
        },
    )
}

@Composable
private fun PlayerActionButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (active) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * Picks the quality for one offline episode.
 *
 * Following the global setting is the default; the other three match the
 * settings page so the same words mean the same bitrates everywhere.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DownloadQualitySheet(onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                "下载音质",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            Text(
                "只影响这一集；批量下载仍使用播放设置里的音质。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
            listOf(
                null to "跟随播放设置",
                "m4a_128" to "高清 · AAC 128k",
                "m4a_64" to "标准 · AAC 64k",
                "mp3_64" to "省流 · MP3 64k",
            ).forEach { (quality, label) ->
                androidx.compose.material3.Surface(
                    onClick = { onPick(quality) },
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = XmproShapes.compactCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PlayerActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(min = 64.dp)
            .heightIn(min = 72.dp)
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .alpha(if (enabled) 1f else 0.45f),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Compact preview used by tests/previews. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun QueueRow(track: Track, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = XmproShapes.compactCard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    track.title.ifEmpty { track.number },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatDuration(track.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (active) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "正在播放",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Head/tail trim editor: automatic silence skipping, or manual percentages. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TrimSheet(
    enabled: Boolean,
    mode: String,
    intro: Int,
    outro: Int,
    onChange: (Boolean, Int, Int, String) -> Unit,
) {
    val smart = mode == teikengstudio.xmpro.player.SmartSkip.MODE_SMART
    Column(Modifier.padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "跳过头尾",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.Switch(
                checked = enabled,
                onCheckedChange = { onChange(it, intro, outro, mode) },
            )
        }
        Text(
            if (smart) {
                "自动识别开头的空白与结尾的静音并跳过，无需设置。"
            } else {
                "按每集时长比例裁剪，百分比会自动换算成本集的秒数。"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(3.dp),
        ) {
            listOf(
                teikengstudio.xmpro.player.SmartSkip.MODE_SMART to "智能",
                teikengstudio.xmpro.player.SmartSkip.MODE_MANUAL to "手动",
            ).forEach { (value, label) ->
                val active = mode == value
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                        )
                        .clickable { onChange(enabled, intro, outro, value) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
        if (!smart) {
            TrimSlider(
                title = "片头",
                permille = intro,
                enabled = enabled,
                onChange = { onChange(enabled, it, outro, mode) },
            )
            TrimSlider(
                title = "片尾",
                permille = outro,
                enabled = enabled,
                onChange = { onChange(enabled, intro, it, mode) },
            )
        }
        TextButton(
            onClick = { onChange(false, intro, outro, mode) },
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            Text("关闭跳过头尾")
        }
    }
}

@Composable
private fun TrimSlider(
    title: String,
    permille: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(
                if (permille == 0) {
                    "不跳过"
                } else {
                    "%.1f%%".format(permille / 10.0)
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = permille.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..teikengstudio.xmpro.data.MAX_SKIP_PERMILLE.toFloat(),
            enabled = enabled,
        )
    }
}

/** `1.0×`, or `1.25×` when the step is not a half. */
private fun speedChipLabel(value: Float): String {
    val text = if (value * 2f == kotlin.math.round(value * 2f)) "%.1f".format(value) else "%.2f".format(value)
    return "${text}×"
}
