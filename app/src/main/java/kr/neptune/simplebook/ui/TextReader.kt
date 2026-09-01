package kr.neptune.simplebook.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val HighlightColor = Color(0x99C89A63)

/**
 * TXT 를 화면 크기에 맞춰 쪽으로 자른다.
 *
 * 한 번 재는 데 몇 ms 라 장편 소설은 1초 가까이 걸린다. 그래서 배경에서 계산하고
 * 끝날 때까지 화면에는 진행 표시만 둔다. 글자 크기나 화면 크기가 바뀌면 다시 잰다.
 *
 * 스크롤 모드도 같은 결과를 쓴다. 쪽 경계를 알아야 검색 결과로 정확히 뛸 수 있고,
 * 소설 한 권을 Text 하나에 통째로 넣으면 스크롤이 눈에 띄게 버벅인다.
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

// ---------------------------------------------------------------- 검색

/** 본문에서 [query] 가 나오는 위치를 전부 찾는다 */
suspend fun findAll(text: String, query: String, limit: Int = 400): List<Int> =
    withContext(Dispatchers.Default) {
        if (query.isBlank()) return@withContext emptyList()
        val hits = ArrayList<Int>()
        var from = 0
        while (hits.size < limit) {
            val at = text.indexOf(query, from, ignoreCase = true)
            if (at < 0) break
            hits += at
            from = at + query.length
        }
        hits
    }

/** 글자 위치가 몇 쪽에 있는지 */
fun pageOfOffset(starts: List<Int>, offset: Int): Int {
    var low = 0
    var high = starts.size - 1
    var found = 0
    while (low <= high) {
        val mid = (low + high) / 2
        if (starts[mid] <= offset) {
            found = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return found
}

/** 검색 결과 목록에 보여줄 앞뒤 문맥 */
fun snippetAt(text: String, offset: Int, length: Int, around: Int = 30): String {
    val from = (offset - around).coerceAtLeast(0)
    val to = (offset + length + around).coerceAtMost(text.length)
    val head = if (from > 0) "…" else ""
    val tail = if (to < text.length) "…" else ""
    return head + text.substring(from, to).replace('\n', ' ') + tail
}

// ---------------------------------------------------------------- 그리기

/** 잘라 둔 한 쪽을 그린다 */
@Composable
fun TextPage(
    text: String,
    starts: List<Int>,
    index: Int,
    style: TextStyle,
    highlight: String? = null,
    modifier: Modifier = Modifier,
) {
    val from = starts.getOrNull(index) ?: return
    val to = starts.getOrNull(index + 1) ?: text.length
    val body = remember(text, from, to, highlight) {
        slice(text, from, to.coerceAtLeast(from), highlight)
    }
    Box(modifier) {
        Text(text = body, style = style, overflow = TextOverflow.Clip)
    }
}

/** 스크롤 모드에서 쓰는 한 덩이. 높이를 고정하지 않아 위아래가 이어져 보인다 */
@Composable
fun TextChunk(
    text: String,
    starts: List<Int>,
    index: Int,
    style: TextStyle,
    highlight: String? = null,
    modifier: Modifier = Modifier,
) {
    val from = starts.getOrNull(index) ?: return
    val to = starts.getOrNull(index + 1) ?: text.length
    val body = remember(text, from, to, highlight) {
        slice(text, from, to.coerceAtLeast(from), highlight)
    }
    Text(text = body, style = style, modifier = modifier.fillMaxWidth())
}

private fun slice(text: String, from: Int, to: Int, highlight: String?): AnnotatedString {
    val raw = text.substring(from, to)
    if (highlight.isNullOrBlank()) return AnnotatedString(raw)
    return buildAnnotatedString {
        var cursor = 0
        while (true) {
            val hit = raw.indexOf(highlight, cursor, ignoreCase = true)
            if (hit < 0) {
                append(raw.substring(cursor))
                break
            }
            append(raw.substring(cursor, hit))
            withStyle(SpanStyle(background = HighlightColor)) {
                append(raw.substring(hit, hit + highlight.length))
            }
            cursor = hit + highlight.length
        }
    }
}
