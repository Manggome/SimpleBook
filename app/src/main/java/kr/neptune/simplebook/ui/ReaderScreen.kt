package kr.neptune.simplebook.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
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
import kr.neptune.simplebook.core.PageEffect
import kr.neptune.simplebook.core.ReadDirection
import kr.neptune.simplebook.core.ShelfItem
import kr.neptune.simplebook.core.SpreadMode

private val PageText = Color(0xFFE6E0D6)

/** 좌 25% / 가운데 50% / 우 25%. 가운데는 메뉴 */
private const val TAP_EDGE = 0.25f

/**
 * 뷰어 안에서 페이지를 옮기는 손잡이.
 *
 * 탭·슬라이더·검색·마지막 쪽 안내가 전부 이걸 통해 움직인다. 실제 동작은 표시 방식
 * (페이저 / 세로 스크롤 / 효과 없음)마다 달라서 각 구현이 여기에 자기 함수를 꽂는다.
 */
@Stable
class ReaderNav {
    /** 상대 이동. +1 이 다음 펼침면 */
    var turn: (Int) -> Unit = {}

    /** 절대 이동. 슬라이더와 검색이 쓴다 */
    var seekPage: (Int) -> Unit = {}

    /** 마지막 쪽에서 한 번 더 넘기려 했다 */
    var onPastEnd: () -> Unit = {}
}

@Composable
fun ReaderScreen(
    vm: MainViewModel,
    item: ShelfItem,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val reader = remember(item.id) { ReaderState(context, item) }
    val nav = remember(item.id) { ReaderNav() }

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
    val pageEffect by vm.prefs.pageEffect.collectAsStateWithLifecycle()

    val direction = bookState.direction ?: defaultDirection
    val spreadMode = bookState.spread ?: defaultSpread

    // 화면 켜두기. 만화 한 장을 오래 보면 금방 꺼져서 필요하다
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    var chromeVisible by remember(item.id) { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var endPanelVisible by remember(item.id) { mutableStateOf(false) }
    var searchVisible by remember(item.id) { mutableStateOf(false) }
    var highlight by remember(item.id) { mutableStateOf<String?>(null) }
    var textStarts by remember(item.id) { mutableStateOf<List<Int>?>(null) }

    val pageNumber = remember(item.id) { mutableIntStateOf(bookState.page) }
    var totalPages by remember(item.id) { mutableIntStateOf(0) }

    nav.onPastEnd = { endPanelVisible = true }

    val nextBook = remember(item.id) { vm.nextBook(item) }

    // 페이지가 멈춘 뒤에 저장한다. 빠르게 넘길 때마다 쓰면 디스크가 쉬지 못한다
    LaunchedEffect(pageNumber.intValue, totalPages) {
        if (totalPages <= 0) return@LaunchedEffect
        delay(500)
        vm.saveProgress(item.id, pageNumber.intValue, totalPages)
    }

    val onMenu = { chromeVisible = !chromeVisible }

    // 뒤로가기는 열려 있는 것부터 하나씩 닫는다. 바로 책장으로 튕기면 답답하다
    BackHandler(enabled = searchVisible || endPanelVisible || chromeVisible) {
        when {
            searchVisible -> searchVisible = false
            endPanelVisible -> endPanelVisible = false
            else -> chromeVisible = false
        }
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

                // 폴드를 펴거나 가로로 돌리면 가로÷세로가 커진다. 그 값으로 2쪽을 결정한다.
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
                        effect = pageEffect,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        fontSizeSp = textSizeSp,
                        startPage = bookState.page,
                        pageNumber = pageNumber,
                        nav = nav,
                        tapToTurn = tapToTurn,
                        onMenu = onMenu,
                        onTotal = { totalPages = it },
                        highlight = highlight,
                        onPages = { textStarts = it },
                    )
                } else {
                    ImageContent(
                        reader = reader,
                        direction = direction,
                        double = double,
                        coverAlone = coverAlone,
                        effect = pageEffect,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        startPage = bookState.page,
                        pageNumber = pageNumber,
                        nav = nav,
                        tapToTurn = tapToTurn,
                        onMenu = onMenu,
                        onTotal = { totalPages = it },
                    )
                }
            }
        }

        ReaderChrome(
            visible = chromeVisible,
            title = item.title,
            page = pageNumber.intValue,
            total = totalPages,
            canSearch = reader.isText,
            onBack = onClose,
            onSeek = { nav.seekPage(it) },
            onSearch = { searchVisible = true },
            onSettings = { settingsVisible = true },
        )

        if (searchVisible) {
            TextSearch(
                text = reader.text.orEmpty(),
                starts = textStarts,
                initialQuery = highlight.orEmpty(),
                onJump = { page, query ->
                    highlight = query
                    searchVisible = false
                    chromeVisible = false
                    nav.seekPage(page)
                },
                onClear = { highlight = null },
                onDismiss = { searchVisible = false },
            )
        }

        if (endPanelVisible) {
            EndPanel(
                nextBook = nextBook,
                onNext = {
                    endPanelVisible = false
                    nextBook?.let { vm.openBook(it) }
                },
                onShelf = {
                    endPanelVisible = false
                    onClose()
                },
                onStay = { endPanelVisible = false },
            )
        }
    }

    if (settingsVisible) {
        ReaderSettings(
            direction = direction,
            spread = spreadMode,
            effect = pageEffect,
            isText = reader.isText,
            textSize = textSizeSp,
            onDirection = { vm.setBookState(item.id) { s -> s.copy(direction = it) } },
            onSpread = { vm.setBookState(item.id) { s -> s.copy(spread = it) } },
            onEffect = { vm.prefs.setPageEffect(it) },
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

// ---------------------------------------------------------------- 손동작

/**
 * 좌 25% / 가운데 50% / 우 25%.
 *
 * 페이저나 스크롤 위에 투명 레이어를 덮으면 드래그가 전부 그 레이어에 먹힌다.
 * 그래서 이 감지기는 콘텐츠와 같은 modifier 사슬에 붙인다. 안쪽(스크롤)이 먼저
 * 이벤트를 보고 드래그를 가져가고, 탭만 여기까지 내려온다.
 */
private fun Modifier.readerTaps(
    tapToTurn: Boolean,
    rtl: Boolean,
    nav: ReaderNav,
    onMenu: () -> Unit,
): Modifier = pointerInput(tapToTurn, rtl) {
    detectTapGestures { offset ->
        val x = if (size.width > 0) offset.x / size.width else 0.5f
        val left = x < TAP_EDGE
        val right = x > 1f - TAP_EDGE
        when {
            !tapToTurn || (!left && !right) -> onMenu()
            rtl -> nav.turn(if (left) 1 else -1)
            else -> nav.turn(if (right) 1 else -1)
        }
    }
}

/** 효과 없음 모드의 스와이프. 페이지가 손가락을 따라오지 않고 놓는 순간 바뀐다 */
private fun Modifier.swipeToTurn(
    enabled: Boolean,
    rtl: Boolean,
    nav: ReaderNav,
): Modifier = pointerInput(enabled, rtl) {
    if (!enabled) return@pointerInput
    var travelled = 0f
    detectHorizontalDragGestures(
        onDragStart = { travelled = 0f },
        onDragEnd = {
            val threshold = size.width * 0.12f
            val forward = if (rtl) travelled > threshold else travelled < -threshold
            val backward = if (rtl) travelled < -threshold else travelled > threshold
            when {
                forward -> nav.turn(1)
                backward -> nav.turn(-1)
            }
        },
    ) { change, dragAmount ->
        travelled += dragAmount
        change.consume()
    }
}

// ---------------------------------------------------------------- 펼침면 표시기

/**
 * 펼침면 하나를 화면 하나에 담아 넘긴다. 슬라이드/효과없음 두 방식을 여기서 흡수한다.
 *
 * @param page 한 쪽을 그리는 함수. (페이지 번호, 이 화면에 놓인 쪽 수)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpreadPager(
    spreads: List<IntArray>,
    rtl: Boolean,
    effect: PageEffect,
    zoomable: Boolean,
    startPage: Int,
    pageNumber: MutableIntState,
    nav: ReaderNav,
    tapToTurn: Boolean,
    onMenu: () -> Unit,
    page: @Composable RowScope.(Int, Int) -> Unit,
) {
    if (spreads.isEmpty()) return
    val scope = rememberCoroutineScope()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val zoomed = scale > 1.01f

    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }
    // canPan 을 걸어야 확대 전 한 손가락 드래그가 페이저로 내려간다
    val zoomModifier = if (zoomable) {
        Modifier.transformable(state = transform, canPan = { scale > 1f })
    } else {
        Modifier
    }

    if (effect == PageEffect.SLIDE) {
        val pagerState = rememberPagerState(
            initialPage = spreadIndexOf(spreads, startPage)
        ) { spreads.size }

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

        nav.turn = { delta ->
            val target = pagerState.currentPage + delta
            when {
                target >= spreads.size -> nav.onPastEnd()
                target >= 0 -> scope.launch { pagerState.animateScrollToPage(target) }
            }
        }
        nav.seekPage = { p ->
            scope.launch { pagerState.animateScrollToPage(spreadIndexOf(spreads, p)) }
        }

        HorizontalPager(
            state = pagerState,
            reverseLayout = rtl,
            beyondViewportPageCount = 1,
            userScrollEnabled = !zoomed,
            modifier = Modifier
                .fillMaxSize()
                .readerTaps(tapToTurn, rtl, nav, onMenu)
                .then(zoomModifier),
        ) { index ->
            Spread(spreads[index], rtl, scale, offset, page)
        }
        return
    }

    // ---- 효과 없음: 현재 펼침면만 그리고 스와이프/탭으로 즉시 교체한다
    var index by remember(spreads.size) {
        mutableIntStateOf(spreadIndexOf(spreads, startPage))
    }
    LaunchedEffect(spreads) {
        index = spreadIndexOf(spreads, pageNumber.intValue)
    }
    LaunchedEffect(index) {
        spreads.getOrNull(index)?.firstOrNull()?.let { pageNumber.intValue = it }
        scale = 1f
        offset = Offset.Zero
    }

    nav.turn = { delta ->
        val target = index + delta
        when {
            target >= spreads.size -> nav.onPastEnd()
            target >= 0 -> index = target
        }
    }
    nav.seekPage = { p -> index = spreadIndexOf(spreads, p) }

    Box(
        Modifier
            .fillMaxSize()
            .readerTaps(tapToTurn, rtl, nav, onMenu)
            .swipeToTurn(!zoomed, rtl, nav)
            .then(zoomModifier)
    ) {
        Spread(spreads[index.coerceIn(0, spreads.size - 1)], rtl, scale, offset, page)
    }
}

@Composable
private fun Spread(
    group: IntArray,
    rtl: Boolean,
    scale: Float,
    offset: Offset,
    page: @Composable RowScope.(Int, Int) -> Unit,
) {
    // 일본식은 먼저 나오는 쪽이 오른쪽에 붙는다
    val ordered = if (rtl) group.reversed() else group.toList()
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
        ordered.forEach { p -> page(p, ordered.size) }
    }
}

// ---------------------------------------------------------------- 이미지 책

@Composable
private fun ImageContent(
    reader: ReaderState,
    direction: ReadDirection,
    double: Boolean,
    coverAlone: Boolean,
    effect: PageEffect,
    widthPx: Int,
    heightPx: Int,
    startPage: Int,
    pageNumber: MutableIntState,
    nav: ReaderNav,
    tapToTurn: Boolean,
    onMenu: () -> Unit,
    onTotal: (Int) -> Unit,
) {
    val count = reader.pageCount
    LaunchedEffect(count) { onTotal(count) }
    if (count <= 0) return
    val start = startPage.coerceIn(0, count - 1)

    if (direction == ReadDirection.VERTICAL) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)
        val scope = rememberCoroutineScope()

        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex }.collect { pageNumber.intValue = it }
        }
        nav.turn = { delta ->
            val target = listState.firstVisibleItemIndex + delta
            when {
                target >= count -> nav.onPastEnd()
                target >= 0 -> scope.launch { listState.animateScrollToItem(target) }
            }
        }
        nav.seekPage = { p ->
            scope.launch { listState.animateScrollToItem(p.coerceIn(0, count - 1)) }
        }
        AtEndWatcher(listState, count, nav)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // 세로 스크롤에서 좌우 탭은 의미가 없다. 아무 데나 누르면 메뉴
                .readerTaps(tapToTurn = false, rtl = false, nav = nav, onMenu = onMenu),
        ) {
            items(count) { index -> VerticalPage(reader, index, widthPx, heightPx) }
        }
        return
    }

    val spreads = remember(count, double, coverAlone) { buildSpreads(count, double, coverAlone) }

    SpreadPager(
        spreads = spreads,
        rtl = direction == ReadDirection.RTL,
        effect = effect,
        zoomable = true,
        startPage = start,
        pageNumber = pageNumber,
        nav = nav,
        tapToTurn = tapToTurn,
        onMenu = onMenu,
    ) { pageIndex, columns ->
        PageImage(
            reader = reader,
            index = pageIndex,
            widthPx = widthPx / columns,
            heightPx = heightPx,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

/** 끝까지 내려오면 다음 화 안내를 띄운다 */
@Composable
private fun AtEndWatcher(
    listState: androidx.compose.foundation.lazy.LazyListState,
    count: Int,
    nav: ReaderNav,
) {
    // 스크롤이 "멈춘 순간" 만 본다. 그냥 canScrollForward 를 보면 화면보다 짧은 책이나
    // 아직 측정 전인 첫 프레임에서 열자마자 안내가 떠 버린다.
    LaunchedEffect(listState, count) {
        var wasScrolling = false
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (wasScrolling && !scrolling) {
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                if (last >= count - 1 && !listState.canScrollForward) nav.onPastEnd()
            }
            wasScrolling = scrolling
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
    effect: PageEffect,
    widthPx: Int,
    heightPx: Int,
    fontSizeSp: Float,
    startPage: Int,
    pageNumber: MutableIntState,
    nav: ReaderNav,
    tapToTurn: Boolean,
    onMenu: () -> Unit,
    onTotal: (Int) -> Unit,
    highlight: String?,
    onPages: (List<Int>) -> Unit,
) {
    val density = LocalDensity.current
    val style = TextStyle(
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * 1.75f).sp,
        color = PageText,
    )

    val padH = with(density) { 22.dp.roundToPx() }
    val padV = with(density) { 20.dp.roundToPx() }
    val scrolling = direction == ReadDirection.VERTICAL
    val columns = if (double && !scrolling) 2 else 1
    val columnWidth = (widthPx / columns - padH * 2).coerceAtLeast(80)
    val columnHeight = (heightPx - padV * 2).coerceAtLeast(80)

    // 스크롤 모드도 같은 분할을 쓴다. 쪽 경계가 있어야 검색 결과로 정확히 뛴다
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

    LaunchedEffect(starts) {
        onTotal(starts.size)
        onPages(starts)
    }
    val lastPage = (starts.size - 1).coerceAtLeast(0)

    if (scrolling) {
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = startPage.coerceIn(0, lastPage)
        )
        val scope = rememberCoroutineScope()

        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex }.collect { pageNumber.intValue = it }
        }
        nav.turn = { delta ->
            val target = listState.firstVisibleItemIndex + delta
            when {
                target > lastPage -> nav.onPastEnd()
                target >= 0 -> scope.launch { listState.animateScrollToItem(target) }
            }
        }
        nav.seekPage = { p ->
            scope.launch { listState.animateScrollToItem(p.coerceIn(0, lastPage)) }
        }
        AtEndWatcher(listState, starts.size, nav)

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(22.dp, 20.dp),
            modifier = Modifier
                .fillMaxSize()
                .readerTaps(tapToTurn = false, rtl = false, nav = nav, onMenu = onMenu),
        ) {
            items(starts.size) { index ->
                TextChunk(text, starts, index, style, highlight)
            }
        }
        return
    }

    val spreads = remember(starts, double) { buildSpreads(starts.size, double, false) }

    SpreadPager(
        spreads = spreads,
        rtl = direction == ReadDirection.RTL,
        effect = effect,
        zoomable = false,
        startPage = startPage.coerceIn(0, lastPage),
        pageNumber = pageNumber,
        nav = nav,
        tapToTurn = tapToTurn,
        onMenu = onMenu,
    ) { pageIndex, _ ->
        TextPage(
            text = text,
            starts = starts,
            index = pageIndex,
            style = style,
            highlight = highlight,
            modifier = Modifier.weight(1f).fillMaxHeight().padding(22.dp, 20.dp),
        )
    }
}

/** TXT 본문 검색. 결과를 누르면 그 쪽으로 뛰고 본문에 형광펜이 남는다 */
@Composable
private fun TextSearch(
    text: String,
    starts: List<Int>?,
    initialQuery: String,
    onJump: (Int, String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(initialQuery) }
    var hits by remember { mutableStateOf<List<Int>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.length < 2) {
            hits = emptyList()
            return@LaunchedEffect
        }
        searching = true
        delay(250) // 타이핑이 멈추면 찾는다
        hits = findAll(text, query)
        searching = false
    }

    Surface(
        color = Color(0xF5141210),
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.statusBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp, 8.dp, 12.dp, 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "닫기", tint = PageText)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("본문에서 찾기 (두 글자 이상)") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(20.dp, 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        query.length < 2 -> "찾을 말을 넣어 주세요"
                        searching -> "찾는 중…"
                        hits.isEmpty() -> "결과가 없습니다"
                        else -> "${hits.size}곳" + if (hits.size >= 400) " (앞의 400곳만)" else ""
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = PageText.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )
                if (initialQuery.isNotEmpty()) {
                    OutlinedButton(onClick = {
                        onClear()
                        onDismiss()
                    }) { Text("형광펜 지우기") }
                }
            }

            HorizontalDivider(color = PageText.copy(alpha = 0.15f))

            LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
                items(hits.size) { i ->
                    val offset = hits[i]
                    val page = starts?.let { pageOfOffset(it, offset) } ?: 0
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onJump(page, query) }
                            .padding(20.dp, 12.dp)
                    ) {
                        Text(
                            "${page + 1}쪽",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            snippetAt(text, offset, query.length),
                            style = MaterialTheme.typography.bodySmall,
                            color = PageText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HorizontalDivider(color = PageText.copy(alpha = 0.08f))
                }
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
    canSearch: Boolean,
    onBack: () -> Unit,
    onSeek: (Int) -> Unit,
    onSearch: () -> Unit,
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
            if (canSearch) {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Default.Search, "본문 검색", tint = PageText)
                }
            }
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
                        value = page.coerceIn(0, total - 1).toFloat(),
                        onValueChange = { onSeek(it.toInt()) },
                        valueRange = 0f..(total - 1).toFloat(),
                    )
                }
            }
        }
    }
}

/** 마지막 쪽에서 한 번 더 넘기면 나온다 */
@Composable
private fun EndPanel(
    nextBook: ShelfItem?,
    onNext: () -> Unit,
    onShelf: () -> Unit,
    onStay: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF00D0C0B))
            .pointerInput(Unit) { detectTapGestures { onStay() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("마지막 쪽입니다", color = PageText, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(20.dp))

            if (nextBook != null) {
                Button(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "다음 화 · ${nextBook.title}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    "같은 폴더에 다음 파일이 없습니다",
                    color = PageText.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onShelf) {
                Icon(Icons.Default.ArrowBack, null)
                Spacer(Modifier.width(8.dp))
                Text("책장으로")
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "화면을 누르면 계속 봅니다",
                color = PageText.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettings(
    direction: ReadDirection,
    spread: SpreadMode,
    effect: PageEffect,
    isText: Boolean,
    textSize: Float,
    onDirection: (ReadDirection) -> Unit,
    onSpread: (SpreadMode) -> Unit,
    onEffect: (PageEffect) -> Unit,
    onTextSize: (Float) -> Unit,
    onMakeDefault: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scrolling = direction == ReadDirection.VERTICAL

    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp, 0.dp, 20.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isText) {
                // 소설에 "일본식/한국식" 은 와닿지 않는다. 먼저 방식부터 고르게 한다
                Text("읽기 방식", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = scrolling,
                        onClick = { if (!scrolling) onDirection(ReadDirection.VERTICAL) },
                        label = { Text("스크롤") },
                    )
                    FilterChip(
                        selected = !scrolling,
                        onClick = { if (scrolling) onDirection(ReadDirection.LTR) },
                        label = { Text("책 넘김") },
                    )
                }
                if (!scrolling) {
                    Spacer(Modifier.height(4.dp))
                    Text("넘기는 방향", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ReadDirection.LTR, ReadDirection.RTL).forEach { d ->
                            FilterChip(
                                selected = d == direction,
                                onClick = { onDirection(d) },
                                label = { Text(d.label) },
                            )
                        }
                    }
                }
            } else {
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
            }

            Spacer(Modifier.height(8.dp))
            Text("한 화면에 몇 쪽", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpreadMode.entries.forEach { s ->
                    FilterChip(
                        selected = s == spread,
                        onClick = { onSpread(s) },
                        label = { Text(s.label) },
                        enabled = !scrolling,
                    )
                }
            }
            Text(
                if (scrolling) "스크롤에서는 항상 1쪽입니다"
                else "자동: 화면 가로÷세로가 기준값 이상이면 2쪽 — 기본값에서는 폴드를 펴면 2쪽이 됩니다",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))
            Text("넘김 효과", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PageEffect.entries.forEach { e ->
                    FilterChip(
                        selected = e == effect,
                        onClick = { onEffect(e) },
                        label = { Text(e.label) },
                        enabled = !scrolling,
                    )
                }
            }

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
                "읽기 방식과 쪽 수는 이 책에만 적용됩니다. 넘김 효과와 글자 크기는 전체 공통입니다.",
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
