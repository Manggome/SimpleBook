package kr.neptune.simplebook.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.neptune.simplebook.core.BookState
import kr.neptune.simplebook.core.FormatGroup
import kr.neptune.simplebook.core.OrientationMode
import kr.neptune.simplebook.core.PageEffect
import kr.neptune.simplebook.core.ReadDirection
import kr.neptune.simplebook.core.ShelfItem
import kr.neptune.simplebook.core.SpreadMode
import kr.neptune.simplebook.core.TextBackground

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

    /**
     * true 를 돌려주면 그 탭은 삼킨다.
     * 자동으로 넘어가는 중에 화면을 누르면 "멈추려고 눌렀는데 한 장 넘어가는" 일이
     * 없도록, 그 탭은 멈추는 데만 쓴다.
     */
    var interceptTap: () -> Boolean = { false }

    /**
     * 마지막으로 손가락이 닿은 세로 위치 (0 위 ~ 1 아래).
     * 종이가 여기서부터 들려 기울어진 채로 넘어간다.
     */
    var grabY: Float = 0.72f
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

    val group = remember(item.id) { FormatGroup.of(item.kind) }
    val directions by vm.prefs.directions.collectAsStateWithLifecycle()
    val spreads by vm.prefs.spreads.collectAsStateWithLifecycle()
    val defaultDirection = directions[group] ?: group.defaultDirection
    val defaultSpread = spreads[group] ?: group.defaultSpread
    val threshold by vm.prefs.spreadThreshold.collectAsStateWithLifecycle()
    val coverAlone by vm.prefs.coverAlone.collectAsStateWithLifecycle()
    val tapToTurn by vm.prefs.tapToTurn.collectAsStateWithLifecycle()
    val keepScreenOn by vm.prefs.keepScreenOn.collectAsStateWithLifecycle()
    val textSizeSp by vm.prefs.textSize.collectAsStateWithLifecycle()
    val pageEffect by vm.prefs.pageEffect.collectAsStateWithLifecycle()
    val textBackground by vm.prefs.textBackground.collectAsStateWithLifecycle()
    val letterSpacing by vm.prefs.letterSpacing.collectAsStateWithLifecycle()
    val orientation by vm.prefs.orientation.collectAsStateWithLifecycle()
    val systemBrightness by vm.prefs.systemBrightness.collectAsStateWithLifecycle()
    val brightness by vm.prefs.brightness.collectAsStateWithLifecycle()
    val lineSpacing by vm.prefs.lineSpacing.collectAsStateWithLifecycle()
    val useCustomFont by vm.prefs.useCustomFont.collectAsStateWithLifecycle()
    val autoTurn by vm.prefs.autoTurn.collectAsStateWithLifecycle()
    val autoTurnSeconds by vm.prefs.autoTurnSeconds.collectAsStateWithLifecycle()
    val readerInfo by vm.prefs.readerInfo.collectAsStateWithLifecycle()
    val readerInfoOffset by vm.prefs.readerInfoOffset.collectAsStateWithLifecycle()
    val readingFont = rememberReadingFont(useCustomFont)

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
    var settingsExpanded by remember { mutableStateOf(false) }
    var autoPaused by remember(item.id) { mutableStateOf(false) }
    var endPanelVisible by remember(item.id) { mutableStateOf(false) }
    var searchVisible by remember(item.id) { mutableStateOf(false) }
    var highlight by remember(item.id) { mutableStateOf<String?>(null) }
    var textStarts by remember(item.id) { mutableStateOf<List<Int>?>(null) }
    var jumpVisible by remember(item.id) { mutableStateOf(false) }

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

    // 뭔가 떠 있는 동안에는 저절로 넘어가면 곤란하다
    val autoRunning = autoTurn && !autoPaused && !settingsVisible &&
        !searchVisible && !endPanelVisible && !jumpVisible && !chromeVisible

    nav.interceptTap = {
        if (autoRunning) {
            autoPaused = true
            true
        } else {
            false
        }
    }

    LaunchedEffect(autoRunning, autoTurnSeconds) {
        if (!autoRunning) return@LaunchedEffect
        while (true) {
            delay((autoTurnSeconds * 1000f).toLong())
            nav.turn(1)
        }
    }

    val onMenu = { chromeVisible = !chromeVisible }
    // 소설은 종이색을 고를 수 있다. 만화는 어느 테마에서든 검은 바탕이 낫다.
    val paper = if (reader.isText) Color(textBackground.paper) else ReaderBackground
    val ink = if (reader.isText) Color(textBackground.ink) else PageText

    // 뒤로가기는 열려 있는 것부터 하나씩 닫는다. 바로 책장으로 튕기면 답답하다
    BackHandler(
        enabled = searchVisible || endPanelVisible || chromeVisible ||
            jumpVisible || settingsVisible
    ) {
        when {
            jumpVisible -> jumpVisible = false
            settingsVisible -> settingsVisible = false
            searchVisible -> searchVisible = false
            endPanelVisible -> endPanelVisible = false
            else -> chromeVisible = false
        }
    }

    Box(Modifier.fillMaxSize().background(paper)) {

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
                        paper = paper,
                        ink = ink,
                        letterSpacing = letterSpacing,
                        lineSpacing = lineSpacing,
                        fontFamily = readingFont,
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
                        paper = paper,
                    )
                }
            }
        }

        if (readerInfo && !chromeVisible) {
            ReaderStatusOverlay(
                title = item.title,
                page = pageNumber.intValue,
                total = totalPages,
                offsetDp = readerInfoOffset,
                paper = paper,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        if (autoTurn && autoPaused && !settingsVisible) {
            AutoTurnPaused(
                seconds = autoTurnSeconds,
                onResume = { autoPaused = false },
                onStop = {
                    autoPaused = false
                    vm.prefs.setAutoTurn(false)
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        ReaderChrome(
            visible = chromeVisible && !settingsVisible,
            title = item.title,
            page = pageNumber.intValue,
            total = totalPages,
            canSearch = reader.isText,
            onBack = onClose,
            onSeek = { nav.seekPage(it) },
            onStep = { nav.seekPage((pageNumber.intValue + it).coerceIn(0, (totalPages - 1).coerceAtLeast(0))) },
            onOpenJump = { jumpVisible = true },
            onSearch = { searchVisible = true },
            onSettings = { settingsVisible = true },
        )

        if (jumpVisible) {
            PageJump(
                total = totalPages,
                current = pageNumber.intValue,
                onGo = {
                    jumpVisible = false
                    nav.seekPage(it)
                },
                onDismiss = { jumpVisible = false },
            )
        }

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

        if (settingsVisible) {
            // 막을 씌우지 않는다. 글자 크기를 조절하면서 본문이 어떻게 변하는지 봐야 한다
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { settingsVisible = false } }
            )
            ReaderSettings(
                modifier = Modifier.align(Alignment.BottomCenter),
                vm = vm,
                expanded = settingsExpanded,
                onToggleExpand = { settingsExpanded = !settingsExpanded },
                isText = reader.isText,
                group = group,
                direction = direction,
                spread = spreadMode,
                onDirection = { vm.setBookState(item.id) { s -> s.copy(direction = it) } },
                onSpread = { vm.setBookState(item.id) { s -> s.copy(spread = it) } },
                onAutoTurnChanged = { autoPaused = false },
                onMakeDefault = {
                    vm.prefs.setDirection(group, direction)
                    vm.prefs.setSpread(group, spreadMode)
                    vm.say("${group.label} 기본값으로 저장했습니다")
                },
                onDismiss = { settingsVisible = false },
            )
        }
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
        if (nav.interceptTap()) return@detectTapGestures
        if (size.height > 0) nav.grabY = (offset.y / size.height).coerceIn(0f, 1f)
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
    paper: Color,
    page: @Composable RowScope.(Int, Int) -> Unit,
) {
    if (spreads.isEmpty()) return
    val scope = rememberCoroutineScope()
    val last = spreads.size - 1

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val zoomed = scale > 1.01f

    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }
    // canPan 을 걸어야 확대 전 한 손가락 드래그가 아래로 내려간다
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

    // ---- 책 넘김 / 효과 없음
    var index by remember(spreads.size) { mutableIntStateOf(spreadIndexOf(spreads, startPage)) }

    /**
     * 넘어가는 정도. +1 은 다음 장으로 완전히 넘어간 상태, -1 은 이전 장이 완전히 펴진 상태.
     * 손가락을 따라 실시간으로 움직이고 손을 떼면 가까운 쪽으로 붙는다.
     */
    var turn by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(spreads) { index = spreadIndexOf(spreads, pageNumber.intValue) }
    LaunchedEffect(index) {
        spreads.getOrNull(index)?.firstOrNull()?.let { pageNumber.intValue = it }
        scale = 1f
        offset = Offset.Zero
    }

    /**
     * 넘김을 끝낸다.
     *
     * index 와 turn 을 한 스냅샷에서 같이 바꾼다. 따로 바꾸면 한 프레임 동안
     * 엉뚱한 장이 보이면서 깜빡인다 — 이전 판의 깜빡임이 이것 때문이었다.
     */
    suspend fun commit(forward: Boolean) {
        if (effect == PageEffect.BOOK) {
            animate(
                initialValue = turn,
                targetValue = if (forward) 1f else -1f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            ) { value, _ -> turn = value }
        }
        Snapshot.withMutableSnapshot {
            index = (index + if (forward) 1 else -1).coerceIn(0, last)
            turn = 0f
        }
    }

    suspend fun cancelTurn() {
        if (turn != 0f) {
            animate(
                initialValue = turn,
                targetValue = 0f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            ) { value, _ -> turn = value }
        }
        turn = 0f
    }

    nav.turn = { delta ->
        when {
            delta > 0 && index >= last -> nav.onPastEnd()
            delta > 0 -> scope.launch { commit(true) }
            delta < 0 && index > 0 -> scope.launch { commit(false) }
        }
    }
    nav.seekPage = { p ->
        val target = spreadIndexOf(spreads, p).coerceIn(0, last)
        Snapshot.withMutableSnapshot {
            index = target
            turn = 0f
        }
    }

    // 책 넘김은 손가락을 따라오고, 효과 없음은 손을 떼는 순간 바뀐다
    val dragModifier = if (effect == PageEffect.BOOK) {
        Modifier.pointerInput(rtl, spreads.size, zoomed) {
            if (zoomed) return@pointerInput
            var pushedPastEnd = false
            detectHorizontalDragGestures(
                onDragStart = { start ->
                    pushedPastEnd = false
                    if (size.height > 0) {
                        nav.grabY = (start.y / size.height).coerceIn(0f, 1f)
                    }
                },
                onDragEnd = {
                    scope.launch {
                        when {
                            turn > 0.28f -> commit(true)
                            turn < -0.28f -> commit(false)
                            else -> {
                                cancelTurn()
                                if (pushedPastEnd) nav.onPastEnd()
                            }
                        }
                    }
                },
                onDragCancel = { scope.launch { cancelTurn() } },
            ) { change, dragAmount ->
                val width = size.width.toFloat().coerceAtLeast(1f)
                // 우철은 오른쪽으로 끌어야 앞으로 간다
                val forwardSign = if (rtl) 1f else -1f
                var next = turn + (dragAmount / width) * forwardSign
                if (index >= last) {
                    if (next > 0f) pushedPastEnd = true
                    next = next.coerceAtMost(0f)
                }
                if (index <= 0) next = next.coerceAtLeast(0f)
                turn = next.coerceIn(-1f, 1f)
                change.consume()
            }
        }
    } else {
        Modifier.swipeToTurn(!zoomed, rtl, nav)
    }

    Box(
        Modifier
            .fillMaxSize()
            .readerTaps(tapToTurn, rtl, nav, onMenu)
            .then(dragModifier)
            .then(zoomModifier)
    ) {
        val progress = turn
        val underIndex = if (progress > 0f) (index + 1).coerceAtMost(last) else index
        Spread(spreads[underIndex.coerceIn(0, last)], rtl, scale, offset, page)

        if (progress != 0f) {
            // 앞으로 갈 때는 지나간 장이 들리고, 뒤로 갈 때는 새 장이 펴진다
            val topIndex = if (progress > 0f) index else (index - 1).coerceAtLeast(0)
            val lifted = if (progress > 0f) progress else 1f + progress
            CurlingPage(lifted = lifted, rtl = rtl, grabY = nav.grabY, paper = paper) {
                Spread(spreads[topIndex.coerceIn(0, last)], rtl, scale, offset, page)
            }
        }
    }
}

/** 두 손가락으로 키우고 줄인다. 확대 중에만 한 손가락 이동을 가져간다 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomBox(
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }
    Box(modifier.transformable(state = transform, canPan = { scale > 1f })) {
        content(
            Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
        )
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
    paper: Color,
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

        ZoomBox(
            modifier = Modifier
                .fillMaxSize()
                // 세로 스크롤에서 좌우 탭은 의미가 없다. 아무 데나 누르면 메뉴
                .readerTaps(tapToTurn = false, rtl = false, nav = nav, onMenu = onMenu)
        ) { inner ->
            LazyColumn(state = listState, modifier = inner.fillMaxSize()) {
                items(count) { index -> VerticalPage(reader, index, widthPx, heightPx) }
            }
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
        paper = paper,
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
    paper: Color,
    ink: Color,
    letterSpacing: Float,
    lineSpacing: Float,
    fontFamily: FontFamily,
) {
    val density = LocalDensity.current
    val style = TextStyle(
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * lineSpacing).sp,
        letterSpacing = letterSpacing.em,
        fontFamily = fontFamily,
        color = ink,
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
                Text("쪽을 나누는 중…", color = ink, style = MaterialTheme.typography.bodySmall)
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
        paper = paper,
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
    onStep: (Int) -> Unit,
    onOpenJump: () -> Unit,
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
                    .padding(8.dp, 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 쪽 번호를 누르면 원하는 쪽으로 바로 뛴다
                Surface(
                    onClick = onOpenJump,
                    enabled = total > 1,
                    color = Color.Transparent,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        if (total > 0) "${page + 1} / $total" else "—",
                        color = PageText,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(14.dp, 4.dp),
                    )
                }

                if (total > 1) {
                    // 화살표는 쪽 번호 기준이다. 옆의 슬라이더와 방향을 맞추기 위함.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onStep(-1) }, enabled = page > 0) {
                            Icon(Icons.Default.KeyboardArrowLeft, "이전 쪽", tint = PageText)
                        }
                        Slider(
                            value = page.coerceIn(0, total - 1).toFloat(),
                            onValueChange = { onSeek(it.toInt()) },
                            valueRange = 0f..(total - 1).toFloat(),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onStep(1) }, enabled = page < total - 1) {
                            Icon(Icons.Default.KeyboardArrowRight, "다음 쪽", tint = PageText)
                        }
                    }
                }
            }
        }
    }
}

/** 쪽 번호를 직접 넣어 이동 */
@Composable
private fun PageJump(
    total: Int,
    current: Int,
    onGo: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf((current + 1).toString()) }
    val parsed = input.toIntOrNull()
    val valid = parsed != null && parsed in 1..total

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("쪽 이동") },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { new -> input = new.filter { it.isDigit() }.take(6) },
                    singleLine = true,
                    label = { Text("1 ~ $total") },
                    isError = input.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let { onGo(it - 1) } }, enabled = valid) { Text("이동") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** 자동 넘기기를 손으로 멈췄을 때 아래에 뜨는 안내 */
@Composable
private fun AutoTurnPaused(
    seconds: Float,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.navigationBarsPadding().padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xF01F1B17),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "자동 넘기기 멈춤 · ${seconds.toInt()}초",
                color = PageText,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onResume) {
                Icon(Icons.Default.PlayArrow, null, tint = PageText)
                Spacer(Modifier.width(4.dp))
                Text("계속", color = PageText)
            }
            TextButton(onClick = onStop) { Text("끄기", color = PageText) }
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

/**
 * 읽는 화면의 보기 설정.
 *
 * 화면을 다 덮는 시트로 두면 글자 크기나 자간을 조절할 때 정작 본문이 안 보인다.
 * 그래서 기본은 아래쪽 절반만 차지하고 안에서 스크롤한다. 항목을 한눈에 보고
 * 싶을 때를 위해 최대화 버튼을 뒀다.
 *
 * 전역 설정은 여기서 직접 읽는다. 인자로 스무 개씩 넘기면 부르는 쪽이 더 어지럽다.
 */
@Composable
private fun ReaderSettings(
    modifier: Modifier,
    vm: MainViewModel,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    isText: Boolean,
    group: FormatGroup,
    direction: ReadDirection,
    spread: SpreadMode,
    onDirection: (ReadDirection) -> Unit,
    onSpread: (SpreadMode) -> Unit,
    onAutoTurnChanged: () -> Unit,
    onMakeDefault: () -> Unit,
    onDismiss: () -> Unit,
) {
    val prefs = vm.prefs
    val scrolling = direction == ReadDirection.VERTICAL

    val effect by prefs.pageEffect.collectAsStateWithLifecycle()
    val autoTurn by prefs.autoTurn.collectAsStateWithLifecycle()
    val autoTurnSeconds by prefs.autoTurnSeconds.collectAsStateWithLifecycle()
    val textSize by prefs.textSize.collectAsStateWithLifecycle()
    val letterSpacing by prefs.letterSpacing.collectAsStateWithLifecycle()
    val lineSpacing by prefs.lineSpacing.collectAsStateWithLifecycle()
    val textBackground by prefs.textBackground.collectAsStateWithLifecycle()
    val orientation by prefs.orientation.collectAsStateWithLifecycle()
    val immersive by prefs.immersive.collectAsStateWithLifecycle()
    val avoidCutout by prefs.avoidCutout.collectAsStateWithLifecycle()
    val readerInfo by prefs.readerInfo.collectAsStateWithLifecycle()
    val readerInfoOffset by prefs.readerInfoOffset.collectAsStateWithLifecycle()
    val cutoutExtra by prefs.cutoutExtra.collectAsStateWithLifecycle()
    val systemBrightness by prefs.systemBrightness.collectAsStateWithLifecycle()
    val brightness by prefs.brightness.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (expanded) Modifier.fillMaxHeight() else Modifier.fillMaxHeight(0.46f))
            // 패널 안을 눌렀을 때 바깥 닫기 감지기로 새어 나가지 않게 한다
            .pointerInput(Unit) { detectTapGestures { } },
        color = MaterialTheme.colorScheme.surface,
        shape = if (expanded) RectangleShape
        else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        // tonalElevation 을 주면 M3 가 primary(황토색) 를 표면에 섞어 누렇게 뜬다
        tonalElevation = 0.dp,
        shadowElevation = 16.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (expanded) Modifier.statusBarsPadding() else Modifier)
                    .padding(start = 20.dp, end = 6.dp, top = 8.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "보기 설정",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        if (expanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                        contentDescription = if (expanded) "줄이기" else "최대화",
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "닫기")
                }
            }
            HorizontalDivider()

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(20.dp, 12.dp, 20.dp, 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // ------------------------------------------------ 자동 넘기기
                // 읽다가 제일 자주 켜고 끄는 것이라 맨 위에 둔다
                PanelToggle(
                    "자동 넘기기",
                    "넘어가는 중에 화면을 누르면 멈춥니다",
                    autoTurn,
                ) {
                    onAutoTurnChanged()
                    prefs.setAutoTurn(it)
                }
                if (autoTurn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "넘기는 간격",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        NumberStepper(
                            value = autoTurnSeconds.toInt(),
                            range = 2..600,
                            onChange = { prefs.setAutoTurnSeconds(it.toFloat()) },
                            suffix = "초",
                            label = "몇 초에 한 번",
                        )
                    }
                }

                PanelDivider()

                // ------------------------------------------------ 넘기기
                PanelTitle("넘기기")
                if (isText) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = scrolling,
                            onClick = { if (!scrolling) onDirection(ReadDirection.VERTICAL) },
                            label = { Text("스크롤") },
                        )
                        FilterChip(
                            selected = !scrolling,
                            onClick = { if (scrolling) onDirection(ReadDirection.LTR) },
                            label = { Text("책") },
                        )
                    }
                    if (!scrolling) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReadDirection.entries.forEach { d ->
                            FilterChip(
                                selected = d == direction,
                                onClick = { onDirection(d) },
                                label = { Text(d.label) },
                            )
                        }
                    }
                    PanelHint(direction.hint)
                }

                Spacer(Modifier.height(4.dp))
                Text("한 화면에 몇 쪽", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpreadMode.entries.forEach { m ->
                        FilterChip(
                            selected = m == spread,
                            onClick = { onSpread(m) },
                            label = { Text(m.label) },
                            enabled = !scrolling,
                        )
                    }
                }
                PanelHint(
                    if (scrolling) "스크롤에서는 항상 1쪽입니다"
                    else "자동: 화면 가로÷세로가 기준값 이상이면 2쪽 — 폴드를 펴면 2쪽이 됩니다"
                )

                Spacer(Modifier.height(4.dp))
                Text("넘김 효과", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageEffect.entries.forEach { e ->
                        FilterChip(
                            selected = e == effect,
                            onClick = { prefs.setPageEffect(e) },
                            label = { Text(e.label) },
                            enabled = !scrolling,
                        )
                    }
                }

                // ------------------------------------------------ 글자
                if (isText) {
                    PanelDivider()
                    PanelTitle("글자")

                    Text(
                        "크기  ${textSize.toInt()}sp",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = textSize,
                        onValueChange = { prefs.setTextSize(it) },
                        valueRange = 12f..34f,
                        steps = 21,
                    )

                    Text(
                        "자간  ${if (letterSpacing >= 0) "+" else ""}${"%.2f".format(letterSpacing)}em",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = letterSpacing,
                        onValueChange = { prefs.setLetterSpacing(it) },
                        valueRange = -0.05f..0.30f,
                        steps = 34,
                    )

                    Text(
                        "줄 간격  ${"%.2f".format(lineSpacing)}배",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = lineSpacing,
                        onValueChange = { prefs.setLineSpacing(it) },
                        valueRange = 1.0f..2.8f,
                        steps = 35,
                    )

                    Text("종이색", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextBackground.entries.forEach { bg ->
                            FilterChip(
                                selected = bg == textBackground,
                                onClick = { prefs.setTextBackground(bg) },
                                label = { Text(bg.label) },
                                leadingIcon = {
                                    Box(
                                        Modifier
                                            .size(16.dp)
                                            .background(Color(bg.paper), RoundedCornerShape(3.dp))
                                    )
                                },
                            )
                        }
                    }
                }

                // ------------------------------------------------ 화면
                PanelDivider()
                PanelTitle("화면")

                Text("회전", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrientationMode.entries.forEach { o ->
                        FilterChip(
                            selected = o == orientation,
                            onClick = { prefs.setOrientation(o) },
                            label = { Text(o.label) },
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                PanelToggle(
                    "상단바 숨기기",
                    if (readerInfo) "정보 줄이 그 자리를 쓰므로 켜 둡니다" else null,
                    immersive || readerInfo,
                    enabled = !readerInfo,
                ) { prefs.setImmersive(it) }

                PanelToggle(
                    "카메라 구멍 피하기",
                    "구멍만큼 화면을 내리고 그 자리는 검게 둡니다",
                    avoidCutout,
                ) { prefs.setAvoidCutout(it) }
                if (avoidCutout) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("더 내리기", style = MaterialTheme.typography.bodyMedium)
                            PanelHint("자동으로 잡힌 여백에 이만큼 더합니다")
                        }
                        NumberStepper(
                            value = cutoutExtra.toInt(),
                            range = 0..96,
                            onChange = { prefs.setCutoutExtra(it.toFloat()) },
                            step = 2,
                            suffix = "dp",
                            label = "더 내릴 만큼 (dp)",
                        )
                    }
                }

                PanelToggle(
                    "정보 줄 보기",
                    "시계 · 배터리 · 책 이름 · 쪽수를 상단바 자리에",
                    readerInfo,
                ) { prefs.setReaderInfo(it) }
                if (readerInfo) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "정보 줄 위치",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        NumberStepper(
                            value = readerInfoOffset.toInt(),
                            range = 0..96,
                            onChange = { prefs.setReaderInfoOffset(it.toFloat()) },
                            step = 2,
                            suffix = "dp",
                            label = "정보 줄 위치 (dp)",
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                PanelToggle(
                    "시스템 밝기 사용",
                    "끄면 이 앱에서만 밝기를 따로 잡습니다",
                    systemBrightness,
                ) { prefs.setSystemBrightness(it) }
                if (!systemBrightness) {
                    Text(
                        "밝기  ${(brightness * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Slider(
                        value = brightness,
                        onValueChange = { prefs.setBrightness(it) },
                        valueRange = 0.05f..1f,
                    )
                }

                // ------------------------------------------------ 저장
                PanelDivider()
                PanelHint("넘기는 방향과 쪽 수는 이 책에만 적용됩니다. 나머지는 전체 공통입니다.")
                Button(onClick = onMakeDefault, modifier = Modifier.fillMaxWidth()) {
                    Text(group.label + " 의 기본값으로 저장")
                }
            }
        }
    }
}

@Composable
private fun PanelTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun PanelHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PanelDivider() {
    Spacer(Modifier.height(6.dp))
    HorizontalDivider()
    Spacer(Modifier.height(2.dp))
}

@Composable
private fun PanelToggle(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) PanelHint(subtitle)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
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
