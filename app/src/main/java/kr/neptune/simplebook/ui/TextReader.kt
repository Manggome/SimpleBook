package kr.neptune.simplebook.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * TXT 를 화면 크기에 맞춰 쪽으로 자른다.
 *
 * 한 번 재는 데 몇 ms 라 장편 소설은 1초 가까이 걸린다. 그래서 배경에서 계산하고
 * 끝날 때까지 화면에는 진행 표시만 둔다. 글자 크기나 화면 크기가 바뀌면 다시 잰다.
 */
@Composable
fun rememberTextPages(
    text: String,
    style: TextStyle,
    widthPx: Int,
    heightPx: Int,
): List<Int>? {
    // 캐시를 끄지 않으면 배경 스레드에서 재는 동안 내부 캐시가 꼬일 수 있다
    val measurer = rememberTextMeasurer(cacheSize = 0)
    var pages by remember(text, style, widthPx, heightPx) { mutableStateOf<List<Int>?>(null) }

    LaunchedEffect(text, style, widthPx, heightPx) {
        if (widthPx <= 0 || heightPx <= 0) return@LaunchedEffect
        pages = withContext(Dispatchers.Default) {
            paginate(measurer, text, style, widthPx, heightPx)
        }
    }
    return pages
}

private const val CHUNK = 8000
private const val MAX_PAGES = 50_000

private fun paginate(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    width: Int,
    height: Int,
): List<Int> {
    if (text.isEmpty()) return listOf(0)
    val starts = ArrayList<Int>(256)
    var start = 0

    while (start < text.length && starts.size < MAX_PAGES) {
        starts += start
        val end = minOf(text.length, start + CHUNK)
        val layout = measurer.measure(
            text = AnnotatedString(text.substring(start, end)),
            style = style,
            constraints = Constraints(maxWidth = width, maxHeight = height),
            overflow = TextOverflow.Clip,
            softWrap = true,
        )

        val consumed = if (!layout.didOverflowHeight && end == text.length) {
            text.length - start
        } else {
            var line = layout.lineCount - 1
            while (line > 0 && layout.getLineBottom(line) > height) line--
            layout.getLineEnd(line, visibleEnd = false)
        }
        start += consumed.coerceAtLeast(1)
    }
    return starts
}

/** 잘라 둔 한 쪽을 그린다 */
@Composable
fun TextPage(
    text: String,
    starts: List<Int>,
    index: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val from = starts.getOrNull(index) ?: return
    val to = starts.getOrNull(index + 1) ?: text.length
    Box(modifier) {
        Text(
            text = text.substring(from, to.coerceAtLeast(from)),
            style = style,
            overflow = TextOverflow.Clip,
        )
    }
}

/** 세로 스크롤 모드. 쪽을 나누지 않고 통째로 흘려 보낸다 */
@Composable
fun TextScroll(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    SelectionContainer {
        Box(modifier.fillMaxSize().verticalScroll(scroll)) {
            Text(text = text, style = style)
        }
    }
}
