package kr.neptune.simplebook.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kr.neptune.simplebook.core.BookKind
import kr.neptune.simplebook.core.BookState
import kr.neptune.simplebook.core.ShelfItem
import kr.neptune.simplebook.core.SortMode
import kr.neptune.simplebook.core.ViewMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(vm: MainViewModel, onOpenSettings: () -> Unit) {

    val path by vm.path.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val states by vm.store.states.collectAsStateWithLifecycle()
    val viewMode by vm.prefs.viewMode.collectAsStateWithLifecycle()
    val sortMode by vm.prefs.sortMode.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    var sortOpen by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<ShelfItem?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { vm.registerFolder(it) }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data ?: return@rememberLauncherForActivityResult
        val uris = buildList {
            data.clipData?.let { clip -> repeat(clip.itemCount) { add(clip.getItemAt(it).uri) } }
            data.data?.let { if (isEmpty()) add(it) }
        }
        if (uris.isNotEmpty()) vm.registerFiles(uris)
    }

    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            vm.consumeNotice()
        }
    }

    val sortedItems = remember(items, sortMode, states) { vm.sorted(items, sortMode) }
    val here = path.lastOrNull()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (path.isNotEmpty()) {
                        IconButton(onClick = { vm.back() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            here?.title ?: "책장",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (path.size > 1) {
                            Text(
                                path.dropLast(1).joinToString(" / ") { it.title },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                    Box {
                        IconButton(onClick = { sortOpen = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "정렬")
                        }
                        DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                            SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    leadingIcon = {
                                        if (mode == sortMode) Icon(Icons.Default.Check, null)
                                        else Spacer(Modifier.size(24.dp))
                                    },
                                    onClick = {
                                        vm.prefs.setSortMode(mode)
                                        sortOpen = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        vm.prefs.setViewMode(
                            if (viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
                        )
                    }) {
                        Icon(
                            if (viewMode == ViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "보기 전환",
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
                    }
                },
            )
        },
        floatingActionButton = {
            if (path.isEmpty()) {
                Box {
                    FloatingActionButton(onClick = { addOpen = true }) {
                        Icon(Icons.Default.Add, contentDescription = "추가")
                    }
                    DropdownMenu(expanded = addOpen, onDismissRequest = { addOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("폴더 등록") },
                            leadingIcon = { Icon(Icons.Default.Folder, null) },
                            onClick = {
                                addOpen = false
                                folderPicker.launch(vm.openTreeIntent())
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("파일 추가") },
                            leadingIcon = { Icon(Icons.Default.MenuBook, null) },
                            onClick = {
                                addOpen = false
                                filePicker.launch(vm.openFileIntent())
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {

            when {
                sortedItems.isEmpty() && busy ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                sortedItems.isEmpty() && here == null -> EmptyShelf(
                    onFolder = { folderPicker.launch(vm.openTreeIntent()) },
                    onFile = { filePicker.launch(vm.openFileIntent()) },
                )

                sortedItems.isEmpty() -> Text(
                    "볼 수 있는 파일이 없습니다",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )

                viewMode == ViewMode.GRID -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 132.dp),
                    contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (here?.folderHasImages == true) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            ReadThisFolder { vm.openFolderAsBook(here) }
                        }
                    }
                    items(sortedItems, key = { it.id }) { item ->
                        GridCell(
                            item = item,
                            state = states[item.id],
                            onClick = { onItemClick(vm, item) },
                            onLongClick = { menuFor = item },
                        )
                    }
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (here?.folderHasImages == true) {
                        item { ReadThisFolder { vm.openFolderAsBook(here) } }
                    }
                    items(sortedItems, key = { it.id }) { item ->
                        ListRow(
                            item = item,
                            state = states[item.id],
                            onClick = { onItemClick(vm, item) },
                            onLongClick = { menuFor = item },
                        )
                    }
                }
            }

            if (busy && sortedItems.isNotEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }

    menuFor?.let { item ->
        ItemMenu(
            item = item,
            state = states[item.id],
            onDismiss = { menuFor = null },
            onRemove = {
                vm.removeRoot(item)
                menuFor = null
            },
            onMarkUnread = {
                vm.markUnread(item)
                menuFor = null
            },
        )
    }
}

private fun onItemClick(vm: MainViewModel, item: ShelfItem) {
    if (item.isFolder) vm.enter(item) else vm.openBook(item)
}

@Composable
private fun ReadThisFolder(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(16.dp, 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.AutoStories, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("이 폴더를 한 권으로 읽기", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridCell(
    item: ShelfItem,
    state: BookState?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (item.isFolder) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
            } else {
                AsyncImage(
                    model = item,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (item.kind == BookKind.TXT) {
                    Icon(
                        Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
            KindBadge(item, Modifier.align(Alignment.TopEnd).padding(4.dp))
            if (state != null && state.started) {
                ProgressBadge(state, Modifier.align(Alignment.BottomStart).padding(4.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.isFolder && item.childCount > 0) {
            Text(
                "${item.childCount}개",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(
    item: ShelfItem,
    state: BookState?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(44.dp)
                .height(62.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (item.isFolder) {
                Icon(
                    Icons.Default.Folder,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                AsyncImage(
                    model = item,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append(if (item.isFolder) "폴더 · ${item.childCount}개" else item.kind?.label ?: "")
                    if (state != null && state.started) {
                        append(" · ")
                        append(if (state.finished) "다 읽음" else "${state.percent}%")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KindBadge(item: ShelfItem, modifier: Modifier = Modifier) {
    val label = when {
        item.isFolder -> return
        item.kind == null -> return
        else -> item.kind.label
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = Color(0xCC000000),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(4.dp, 1.dp),
        )
    }
}

@Composable
private fun ProgressBadge(state: BookState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = if (state.finished) MaterialTheme.colorScheme.primary else Color(0xCC000000),
    ) {
        Text(
            if (state.finished) "완독" else "${state.percent}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (state.finished) MaterialTheme.colorScheme.onPrimary else Color.White,
            modifier = Modifier.padding(4.dp, 1.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemMenu(
    item: ShelfItem,
    state: BookState?,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onMarkUnread: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text(
                    if (item.isFolder) "폴더 · ${item.childCount}개"
                    else "${item.kind?.label ?: "파일"}" +
                        (if (state?.started == true) " · ${state.page + 1}/${state.pageCount}쪽" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.isRoot) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "책장에서 빼도 폰에 있는 원본은 지워지지 않습니다.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (item.isRoot) {
                TextButton(onClick = onRemove) { Text("책장에서 빼기") }
            } else {
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
        },
        dismissButton = {
            if (state?.started == true) {
                TextButton(onClick = onMarkUnread) { Text("안 읽음으로") }
            }
        },
    )
}

@Composable
private fun EmptyShelf(onFolder: () -> Unit, onFile: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.AutoStories,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("책장이 비어 있습니다", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "폰에 있는 폴더를 등록하면 그 안의 책이 그대로 올라옵니다.\n" +
                "ZIP·CBZ, RAR·CBR, PDF, TXT, 이미지 폴더를 읽습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.Button(onClick = onFolder) {
                Icon(Icons.Default.Folder, null)
                Spacer(Modifier.width(8.dp))
                Text("폴더 등록")
            }
            androidx.compose.material3.OutlinedButton(onClick = onFile) {
                Text("파일 추가")
            }
        }
    }
}
