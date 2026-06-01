package com.youtube.mini.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.youtube.mini.data.VideoItem
import com.youtube.mini.ui.BottomTab
import com.youtube.mini.ui.ContinueWatchingItem
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniTubeScreen(
    hasMediaPermission: Boolean,
    permission: String,
    onPermissionResult: (Boolean) -> Unit,
    vm: MiniTubeViewModel,
) {
    val ui by vm.uiState.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onPermissionResult(it)
    }

    var pinInput by remember { mutableStateOf("") }
    var setPinInput by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(FeedFilter.All) }

    val visibleVideos = remember(
        ui.videos,
        ui.blockedIds,
        ui.allowedIds,
        ui.useAllowListMode,
        ui.searchQuery,
        selectedFilter,
    ) {
        val allowed = if (ui.useAllowListMode) {
            ui.videos.filter { ui.allowedIds.contains(it.id) }
        } else {
            ui.videos.filterNot { ui.blockedIds.contains(it.id) }
        }
        val searched = if (ui.searchQuery.isBlank()) {
            allowed
        } else {
            val q = ui.searchQuery.trim().lowercase()
            allowed.filter { it.title.lowercase().contains(q) }
        }
        when (selectedFilter) {
            FeedFilter.All -> searched
            FeedFilter.Recent -> searched.sortedByDescending { it.dateAddedSec }
            FeedFilter.ShortClips -> searched.filter { it.durationMs in 1..(4 * 60 * 1000L) }
        }
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(Color(0xFFE61C1C), shape = RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("MiniTube Family", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.selectTab(BottomTab.Parent) }) {
                        Icon(Icons.Default.Lock, contentDescription = "Parent Controls")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, modifier = Modifier.navigationBarsPadding()) {
                BottomItem(
                    label = "Home",
                    icon = Icons.Default.Home,
                    selected = ui.activeTab == BottomTab.Home,
                    onClick = { vm.selectTab(BottomTab.Home) },
                )
                BottomItem(
                    label = "Library",
                    icon = Icons.Default.PlayArrow,
                    selected = ui.activeTab == BottomTab.Library,
                    onClick = { vm.selectTab(BottomTab.Library) },
                )
                BottomItem(
                    label = "Parent",
                    icon = Icons.Default.Person,
                    selected = ui.activeTab == BottomTab.Parent,
                    onClick = { vm.selectTab(BottomTab.Parent) },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFFFFFFF), Color(0xFFF9F9F9)),
                    ),
                )
                .padding(padding),
        ) {
            if (!hasMediaPermission) {
                PermissionView(
                    onRequest = { launcher.launch(permission) },
                )
            } else {
                when (ui.activeTab) {
                    BottomTab.Home -> {
                        if (visibleVideos.isEmpty()) {
                            EmptyView()
                        } else {
                            SearchBar(
                                query = ui.searchQuery,
                                onChange = { vm.setSearchQuery(it) },
                            )
                            ContinueWatchingRail(
                                items = ui.continueWatching,
                                onOpen = { vm.play(it) },
                            )
                            FeedFilters(selected = selectedFilter, onSelected = { selectedFilter = it })
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            ) {
                                items(visibleVideos, key = { it.id }) { video ->
                                    VideoRow(video = video, onOpen = { vm.play(video) })
                                }
                            }
                        }
                    }

                    BottomTab.Library -> {
                        if (visibleVideos.isEmpty()) {
                            EmptyView()
                        } else {
                            SearchBar(
                                query = ui.searchQuery,
                                onChange = { vm.setSearchQuery(it) },
                            )
                            LibraryGrid(videos = visibleVideos, onOpen = { vm.play(it) })
                        }
                    }

                    BottomTab.Parent -> {
                        if (visibleVideos.isEmpty()) {
                            EmptyView()
                        } else {
                            Text(
                                text = "Parent controls",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Tap the lock icon to manage blocked videos",
                                modifier = Modifier.padding(horizontal = 12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF666666),
                            )
                            SearchBar(
                                query = ui.searchQuery,
                                onChange = { vm.setSearchQuery(it) },
                            )
                        }
                    }
                }
            }
        }
    }

    ui.parentAuthError?.let {
        AlertDialog(
            onDismissRequest = { vm.setParentPanelVisible(false) },
            title = { Text("Parent lock") },
            text = { Text(it) },
            confirmButton = {
                TextButton(onClick = { vm.setParentPanelVisible(false) }) { Text("OK") }
            },
        )
    }

    if (ui.pinPromptVisible && ui.hasPin) {
        AlertDialog(
            onDismissRequest = {
                vm.setPinPromptVisible(false)
                pinInput = ""
            },
            title = { Text("Enter parent PIN") },
            text = {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it.filter(Char::isDigit).take(6) },
                    label = { Text("PIN") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.verifyParentPin(pinInput)
                    pinInput = ""
                }) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.setPinPromptVisible(false)
                    pinInput = ""
                }) { Text("Cancel") }
            },
        )
    }

    if (!ui.hasPin && ui.parentPanelVisible) {
        AlertDialog(
            onDismissRequest = { vm.setParentPanelVisible(false) },
            title = { Text("Set parent PIN") },
            text = {
                OutlinedTextField(
                    value = setPinInput,
                    onValueChange = { setPinInput = it.filter(Char::isDigit).take(6) },
                    label = { Text("New PIN (4-6 digits)") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (setPinInput.length in 4..6) {
                        vm.createPin(setPinInput)
                        setPinInput = ""
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.setParentPanelVisible(false) }) { Text("Cancel") }
            },
        )
    }

    if (ui.parentPanelVisible && ui.hasPin) {
        ModalBottomSheet(onDismissRequest = { vm.setParentPanelVisible(false) }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Parent Controls", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Manage access by folder")
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (ui.useAllowListMode) "Allowlist mode" else "Blocklist mode",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Switch(
                        checked = ui.useAllowListMode,
                        onCheckedChange = { vm.setAllowListMode(it) },
                    )
                }
                Text(
                    text = if (ui.useAllowListMode) {
                        "Only enabled folders will be visible in child view."
                    } else {
                        "All folders visible except blocked ones."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF666666),
                )
                Spacer(modifier = Modifier.height(12.dp))
                val folderMap = ui.videos.groupBy { video ->
                    video.uri.substringBeforeLast("/").substringAfterLast("/")
                        .takeIf { it.isNotEmpty() } ?: "Device Storage"
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(folderMap.keys.sorted()) { folderName ->
                        val videosInFolder = folderMap[folderName] ?: emptyList()
                        val isAllowed = videosInFolder.all { video ->
                            if (ui.useAllowListMode) {
                                ui.allowedIds.contains(video.id)
                            } else {
                                !ui.blockedIds.contains(video.id)
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(folderName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = "${videosInFolder.size} video${if (videosInFolder.size != 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF666666),
                                )
                            }
                            Switch(
                                checked = isAllowed,
                                onCheckedChange = { checked ->
                                    videosInFolder.forEach { video ->
                                        if (ui.useAllowListMode) {
                                            vm.setAllowed(video.id, allowed = checked)
                                        } else {
                                            vm.setBlocked(video.id, blocked = !checked)
                                        }
                                    }
                                },
                            )
                        }
                        HorizontalDivider(color = Color(0xFFEAEAEA))
                    }
                }
            }
        }
    }

    ui.selectedVideo?.let { selected ->
        FullscreenPlayerDialog(video = selected, onClose = { vm.closePlayer(it) })
    }
}

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        singleLine = true,
        label = { Text("Search videos") },
    )
}

@Composable
private fun ContinueWatchingRail(items: List<ContinueWatchingItem>, onOpen: (VideoItem) -> Unit) {
    if (items.isEmpty()) return

    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = "Continue watching",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Text(
            text = "← Scroll to see more →",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF999999),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            items(items, key = { it.video.id }) { item ->
                Card(
                    modifier = Modifier
                        .size(width = 220.dp, height = 170.dp)
                        .clickable { onOpen(item.video) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    val context = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.video.uri)
                            .decoderFactory(VideoFrameDecoder.Factory())
                            .videoFrameMillis(1000)
                            .build(),
                        contentDescription = item.video.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        contentScale = ContentScale.Crop,
                    )
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(item.video.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = "${formatDuration(item.positionMs)} watched",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF666666),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionView(onRequest: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Allow video access to show local videos")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRequest) {
                Text("Allow Access")
            }
        }
    }
}

@Composable
private fun EmptyView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No local videos found", color = Color(0xFF666666))
    }
}

@Composable
private fun VideoRow(video: VideoItem, onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(video.uri)
                    .decoderFactory(VideoFrameDecoder.Factory())
                    .videoFrameMillis(1000)
                    .crossfade(true)
                    .build(),
                contentDescription = video.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .border(1.dp, Color(0xFFE6E6E6), shape = RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop,
            )
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color(0xFFE61C1C), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Local Family Channel",
                        color = Color(0xFF707070),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "${formatDuration(video.durationMs)}  •  ${formatAge(video.dateAddedSec)}",
                        color = Color(0xFF707070),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun FullscreenPlayerDialog(video: VideoItem, onClose: (Long) -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember(video.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(video.uri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Dialog(
        onDismissRequest = { onClose(exoPlayer.currentPosition) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        useController = true
                        player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            TextButton(
                onClick = { onClose(exoPlayer.currentPosition) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Text("Close", color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryGrid(videos: List<VideoItem>, onOpen: (VideoItem) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val minCell = 170.dp
        val columns = max(2, (maxWidth / minCell).toInt())
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(videos, key = { it.id }) { video ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(video) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    val context = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(video.uri)
                            .decoderFactory(VideoFrameDecoder.Factory())
                            .videoFrameMillis(1000)
                            .crossfade(true)
                            .build(),
                        contentDescription = video.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        contentScale = ContentScale.Crop,
                    )
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = video.title,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = formatDuration(video.durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF707070),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedFilters(selected: FeedFilter, onSelected: (FeedFilter) -> Unit) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChipItem(label = "All", selected = selected == FeedFilter.All) {
            onSelected(FeedFilter.All)
        }
        FilterChipItem(label = "Recent", selected = selected == FeedFilter.Recent) {
            onSelected(FeedFilter.Recent)
        }
        FilterChipItem(label = "Short clips", selected = selected == FeedFilter.ShortClips) {
            onSelected(FeedFilter.ShortClips)
        }
    }
}

@Composable
private fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) Color(0xFF121212) else Color(0xFFF1F1F1)
    val foreground = if (selected) Color.White else Color(0xFF1E1E1E)
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, color = foreground, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RowScope.BottomItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) Color(0xFFE61C1C) else Color(0xFF666666)
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Text(label, color = tint, style = MaterialTheme.typography.labelSmall)
    }
}

private enum class FeedFilter {
    All,
    Recent,
    ShortClips,
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}

private fun formatAge(dateAddedSec: Long): String {
    val ageDays = max(0, ((System.currentTimeMillis() / 1000L) - dateAddedSec) / 86_400L)
    return when {
        ageDays <= 0 -> "today"
        ageDays == 1L -> "1 day ago"
        ageDays < 30L -> "$ageDays days ago"
        ageDays < 365L -> "${ageDays / 30} months ago"
        else -> "${ageDays / 365} years ago"
    }
}
