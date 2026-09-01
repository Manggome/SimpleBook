package kr.neptune.simplebook.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.neptune.simplebook.core.BookState
import kr.neptune.simplebook.core.ReadDirection
import kr.neptune.simplebook.core.ShelfItem
import kr.neptune.simplebook.core.SpreadMode

private val PageText = Color(0xFFE6E0D6)

@Composable
fun ReaderScreen(vm: MainViewModel, item: ShelfItem, onClose: () -> Unit) {

    val context = LocalContext.current
    val reader = remember(item.id) { ReaderState(context, item) }

    LaunchedEffect(reader) { reader.open() }
    DisposableEffect(reader) { onDispose { reader.close() } }

    val states by vm.store.states.collectAsStateWithLifecycle()
    val bookState = states[item.id] ?: BookState()

    val defaultDirection by vm.prefs.direction.collectAsStateWithLifecycle()
    val defaultSpread by vm.prefs.spread.collectAsStateWithLifecycle()
    val threshold by vm.prefs.spreadThreshold.collectAsStateWithLifecycle()
    val coverAlone by vm.prefs.coverAlone.collectAsStateWithLifecycle()
    val tapToTurn by vm.prefs.tapToTurn.collectAsStateWithLifecycle()
    val keepScreenOn by vm.prefs.keepScreenOn.collectAsStateWithLifecycle()
    val textSizeSp by vm.prefs.textSize.collectAsStateWithLifecycle()

    val direction = bookState.direction ?: defaultDirection
    val spreadMode = bookState.spread ?: defaultSpread

    // 화면 켜두기. 만화 한 장을 오래 보면 금방 꺼져서 필요하다
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    var chromeVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    val pageNumber = remember { mutableIntStateOf(bookState.page) }
    var totalPages by remember { mutableIntStateOf(0) }
    var seek by remember { mutableStateOf<(Int) -> Unit>({}) }

    // 페이지가 멈춘 뒤에 저장한다. 빠르게 넘길 때마다 쓰면 디스크가 쉬지 못한다
    LaunchedEffect(pageNumber.intValue, totalPages) {
        if (totalPages <= 0) return@LaunchedEffect
        delay(500)
        vm.saveProgress(item.id, pageNumber.intValue, totalPages)
    }

    Box(Modifier.fillMaxSize().background(ReaderBackground)) {

        when {
            reader.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            reader.error != null -> ReaderError(reader.error!!, onClose)

            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.roundToPx() }
                val heightPx = with(density) { maxHeight.roundToPx() }
                val ratio = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 1f

                // 폴드를 펴거나 가로로 돌리면 가로가 세로보다 길어진다. 그때 2쪽을 편다.
                val double = direction != ReadDirection.VERTICAL && when (spreadMode) {
                    SpreadMode.SINGLE -> false
                    SpreadMode.DOUBLE -> true
                    SpreadMode.AUTO -> ratio >= threshold
                }

                if (reader.isText) {
                    TextContent(
                        text = reader.text.orEmpty(),
                        direction = direction,
                        double = double,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        fontSizeSp = textSizeSp,
                        startPage = bookState.page,
                        pageNumber = pageNumber,
                        onTotal = { totalPages = it },
                        registerSeek = { seek = it },
                    )
                } else {
                    ImageContent(
                        reader = reader,
                        direction = direction,
                        double = double,
                        coverAlone = coverAlone,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        startPage = bookState.page,
                        pageNumber = pageNumber,
                        onTotal = { totalPages = it },
                        registerSeek = { seek = it },
                    )
                }

                // 탭 영역. 자식(페이저)이 드래그를 먼저 가져가므로 탭만 여기서 받는다
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(direction, tapToTurn, totalPages) {
                            detectTapGestures { offset ->
                                val x = if (size.width > 0) offset.x / size.width else 0.5f
                                val forward = when (direction) {
                                    ReadDirection.RTL -> x < 0.32f
                                    else -> x > 0.68f
                                }
                                val backward = when (direction) {
                                    ReadDirection.RTL -> x > 0.68f
                                    else -> x < 0.32f
                                }
                                when {
                                    !tapToTurn || direction == ReadDirection.VERTICAL ->
                                        chromeVisible = !chromeVisible
                                    forward -> seek(pageNumber.intValue + 1)
                                    backward -> seek(pageNumber.intValue - 1)
                                    else -> chromeVisible = !chromeVisible
                                }
                            }
                        }
                )
            }
        }

        ReaderChrome(
            visible = chromeVisible,
            title = item.title,
            page = pageNumber.intValue,
            total = totalPages,
            onBack = onClose,
            onSeek = { seek(it) },
            onSettings = { settingsVisible = true },
        )
    }

    if (settingsVisible) {
        ReaderSettings(
            direction = direction,
            spread = spreadMode,
            isText = reader.isText,
            textSize = textSizeSp,
            onDirection = { vm.setBookState(item.id) { s -> s.copy(direction = it) } },
            onSpread = { vm.setBookState(item.id) { s -> s.copy(spread = it) } },
            onTextSize = { vm.prefs.setTextSize(it) },
            onMakeDefault = {
                vm.prefs.setDirection(direction)
                vm.prefs.setSpread(spreadMode)
                vm.say("지금 설정을 기본값으로 저장했습니다")
            },
            onDismiss = { settingsVisible = false },
        )
    }
}

// ---------------------------------------------------------------- 이미지 책

@Composable
private fun ImageContent(
    reader: ReaderState,
    direction: ReadDirection,
    double: Boolean,
    coverAlone: Boolean,
    widthPx: Int,
    heightPx: Int,
    startPage: Int,
    pageNumber: androidx.compose.runtime.MutableIntState,
    onTotal: (Int) -> Unit,
    registerSeek: ((Int) -> Unit) -> Unit,
) {
    val count = reader.pageCount
    LaunchedEffect(count) { onTotal(count) }
    if (count <= 0) return

    if (direction == ReadDirection.VERTICAL) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage.coerceIn(0, count - 1))
        val scope = rememberCoroutineScope()
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex }.collect { pageNumber.intValue = it }
        }
        LaunchedEffect(Unit) {
            registerSeek { target ->
                scope.launch { listState.animateScrollToItem(target.coerceIn(0, count - 1)) }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(count) { index ->
                VerticalPage(reader, index, widthPx, heightPx)
            }
        }
        return
    }

    val spreads = remember(count, double, coverAlone) { buildSpreads(count, double, coverAlone) }
    val pagerState = rememberPagerState(
        initialPage = spreadIndexOf(spreads, startPage.coerceIn(0, count - 1))
    ) { spreads.size }
    val scope = rememberCoroutineScope()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { index ->
            spreads.getOrNull(index)?.firstOrNull()?.let { pageNumber.intValue = it }
            scale = 1f
            offset = Offset.Zero
        }
    }

    // 접었다 펴서 펼침 방식이 바뀌어도 보고 있던 페이지를 놓치지 않는다
    LaunchedEffect(spreads) {
        val target = spreadIndexOf(spreads, pageNumber.intValue)
        if (target != pagerState.currentPage) pagerState.scrollToPage(target)
    }

    LaunchedEffect(spreads) {
        registerSeek { target ->
            val clamped = target.coerceIn(0, count - 1)
            scope.launch { pagerState.animateScrollToPage(spreadIndexOf(spreads, clamped)) }
        }
    }

    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }

    HorizontalPager(
        state = pagerState,
        reverseLayout = direction == ReadDirection.RTL,
        beyondViewportPageCount = 1,
        userScrollEnabled = scale <= 1.01f,
        modifier = Modifier
            .fillMaxSize()
            .transformable(state = transform, canPan = { scale > 1f }),
    ) { index ->
        val group = spreads[index]
        // 일본식은 먼저 나오는 쪽이 오른쪽에 붙는다
        val ordered = if (direction == ReadDirection.RTL) group.reversed() else group.toList()
        val perPageWidth = if (ordered.isEmpty()) widthPx else widthPx / ordered.size

        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        ) {
            ordered.forEach { page ->
                PageImage(
                    reader = reader,
                    index = page,
                    widthPx = perPageWidth,
                    heightPx = heightPx,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun PageImage(
    reader: ReaderState,
    index: Int,
    widthPx: Int,
    heightPx: Int,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(index, widthPx, heightPx) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }
    LaunchedEffect(index, widthPx, heightPx) {
        bitmap = reader.page(index, widthPx, heightPx)
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "${index + 1}쪽",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun VerticalPage(reader: ReaderState, index: Int, widthPx: Int, heightPx: Int) {
    var bitmap by remember(index, widthPx) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(index, widthPx) {
        // 세로 스크롤은 가로 폭에 맞추므로 높이 제한을 넉넉히 준다
        bitmap = reader.page(index, widthPx, heightPx * 3)
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "${index + 1}쪽",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.7f),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
        }
    }
}

// ---------------------------------------------------------------- 텍스트 책

@Composable
private fun TextContent(
    text: String,
    direction: ReadDirection,
    double: Boolean,
    widthPx: Int,
    heightPx: Int,
    fontSizeSp: Float,
    startPage: Int,
    pageNumber: androidx.compose.runtime.MutableIntState,
    onTotal: (Int) -> Unit,
    registerSeek: ((Int) -> Unit) -> Unit,
) {
    val density = LocalDensity.current
    val style = TextStyle(
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * 1.75f).sp,
        color = PageText,
    )

    if (direction == ReadDirection.VERTICAL) {
        LaunchedEffect(Unit) {
            onTotal(1)
            registerSeek { }
        }
        TextScroll(text, style, Modifier.padding(20.dp, 16.dp))
        return
    }

    val padH = with(density) { 22.dp.roundToPx() }
    val padV = with(density) { 20.dp.roundToPx() }
    val columns = if (double) 2 else 1
    val columnWidth = (widthPx / columns - padH * 2).coerceAtLeast(80)
    val columnHeight = (heightPx - padV * 2).coerceAtLeast(80)

    val starts = rememberTextPages(text, style, columnWidth, columnHeight)

    if (starts == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("쪽을 나누는 중…", color = PageText, style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }

    val spreads = remember(starts, double) { buildSpreads(starts.size, double, false) }
    LaunchedEffect(starts) { onTotal(starts.size) }

    val pagerState = rememberPagerState(
        initialPage = spreadIndexOf(spreads, startPage.coerceIn(0, (starts.size - 1).coerceAtLeast(0)))
    ) { spreads.size }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { index ->
            spreads.getOrNull(index)?.firstOrNull()?.let { pageNumber.intValue = it }
        }
    }
    LaunchedEffect(spreads) {
        val target = spreadIndexOf(spreads, pageNumber.intValue)
        if (target != pagerState.currentPage) pagerState.scrollToPage(target)
        registerSeek { requested ->
            val clamped = requested.coerceIn(0, (starts.size - 1).coerceAtLeast(0))
            scope.launch { pagerState.animateScrollToPage(spreadIndexOf(spreads, clamped)) }
        }
    }

    HorizontalPager(
        state = pagerState,
        reverseLayout = direction == ReadDirection.RTL,
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize(),
    ) { index ->
        val group = spreads[index]
        val ordered = if (direction == ReadDirection.RTL) group.reversed() else group.toList()
        Row(Modifier.fillMaxSize()) {
            ordered.forEach { p ->
                TextPage(
                    text = text,
                    starts = starts,
                    index = p,
                    style = style,
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(22.dp, 20.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 겉 UI

@Composable
private fun ReaderChrome(
    visible: Boolean,
    title: String,
    page: Int,
    total: Int,
    onBack: () -> Unit,
    onSeek: (Int) -> Unit,
    onSettings: () -> Unit,
) {
    val scrim = Color(0xE6141210)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(scrim)
                .statusBarsPadding()
                .padding(4.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "책장으로", tint = PageText)
            }
            Text(
                title,
                color = PageText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Tune, "보기 설정", tint = PageText)
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(scrim)
                    .navigationBarsPadding()
                    .padding(20.dp, 10.dp),
            ) {
                Text(
                    if (total > 0) "${page + 1} / $total" else "—",
                    color = PageText,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                if (total > 1) {
                    Slider(
                        value = page.toFloat(),
                        onValueChange = { onSeek(it.toInt()) },
                        valueRange = 0f..(total - 1).toFloat(),
                        steps = 0,
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettings(
    direction: ReadDirection,
    spread: SpreadMode,
    isText: Boolean,
    textSize: Float,
    onDirection: (ReadDirection) -> Unit,
    onSpread: (SpreadMode) -> Unit,
    onTextSize: (Float) -> Unit,
    onMakeDefault: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp, 0.dp, 20.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("넘기는 방향", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadDirection.entries.forEach { d ->
                    FilterChip(
                        selected = d == direction,
                        onClick = { onDirection(d) },
                        label = { Text(d.label) },
                    )
                }
            }
            Text(
                direction.hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))
            Text("한 화면에 몇 쪽", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpreadMode.entries.forEach { s ->
                    FilterChip(
                        selected = s == spread,
                        onClick = { onSpread(s) },
                        label = { Text(s.label) },
                        enabled = direction != ReadDirection.VERTICAL,
                    )
                }
            }
            Text(
                if (direction == ReadDirection.VERTICAL) "세로 스크롤에서는 항상 1쪽입니다"
                else "자동: 화면 가로가 세로보다 길면 2쪽 — 폴드를 펴거나 가로로 돌리면 바뀝니다",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (isText) {
                Spacer(Modifier.height(8.dp))
                Text("글자 크기  ${textSize.toInt()}sp", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = textSize,
                    onValueChange = onTextSize,
                    valueRange = 12f..34f,
                    steps = 21,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "여기서 바꾼 것은 이 책에만 적용됩니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onMakeDefault, modifier = Modifier.fillMaxWidth()) {
                Text("모든 책의 기본값으로 저장")
            }
        }
    }
}

@Composable
private fun ReaderError(message: String, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            color = PageText,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onClose) { Text("책장으로") }
    }
}
