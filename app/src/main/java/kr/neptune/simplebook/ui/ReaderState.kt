package kr.neptune.simplebook.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.neptune.simplebook.core.ShelfItem
import kr.neptune.simplebook.core.book.BookException
import kr.neptune.simplebook.core.book.BookSource
import kr.neptune.simplebook.core.book.Books

/**
 * 펼쳐 놓은 책 한 권. 압축을 연 상태를 들고 있으면서 요청한 페이지를 그려 준다.
 *
 * 화면 크기가 바뀌면(폴드를 펴거나 돌리면) 캐시를 비우고 새 크기로 다시 그린다.
 * 줄여서 디코딩한 비트맵을 늘려 쓰면 만화 글씨가 뭉개지기 때문이다.
 */
@Stable
class ReaderState(private val context: Context, val item: ShelfItem) {

    var source: BookSource? by mutableStateOf(null)
        private set

    var error: String? by mutableStateOf(null)
        private set

    var loading: Boolean by mutableStateOf(true)
        private set

    val pageCount: Int get() = source?.pageCount ?: 0
    val isText: Boolean get() = source?.isText == true
    val text: String? get() = source?.text

    private var targetWidth = 0
    private var targetHeight = 0

    // 힙의 1/5 만 페이지 캐시에 쓴다. 폴드 2쪽 보기는 한 장이 10MB 를 넘기도 한다
    private val cache = object : LruCache<Int, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 5).coerceIn(24L * 1024 * 1024, 320L * 1024 * 1024).toInt()
    ) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
    }

    suspend fun open() {
        loading = true
        error = null
        val opened = withContext(Dispatchers.IO) {
            try {
                Result.success(Books.open(context, item))
            } catch (e: BookException) {
                Result.failure(e)
            } catch (t: Throwable) {
                Result.failure(BookException(t.message ?: "책을 열지 못했습니다", t))
            }
        }
        opened.fold(
            onSuccess = { source = it },
            onFailure = { error = it.message ?: "책을 열지 못했습니다" },
        )
        loading = false
    }

    /** [index] 페이지를 [width]x[height] 안에 맞춰 그린다 */
    suspend fun page(index: Int, width: Int, height: Int): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val src = source ?: return null
        if (index !in 0 until src.pageCount) return null

        if (width != targetWidth || height != targetHeight) {
            targetWidth = width
            targetHeight = height
            cache.evictAll()
        }
        cache.get(index)?.let { return it }

        val bitmap = runCatching { src.render(index, width, height) }.getOrNull() ?: return null
        cache.put(index, bitmap)
        return bitmap
    }

    fun close() {
        cache.evictAll()
        runCatching { source?.close() }
        source = null
    }
}

/**
 * 펼침면 목록. 한 칸이 화면 하나다.
 *
 * 2쪽 보기에서 표지를 혼자 두는 것은 종이책 펼침면과 짝을 맞추기 위해서다.
 * 표지가 오른쪽 페이지와 붙어 버리면 그 뒤 모든 펼침면이 한 장씩 밀린다.
 */
fun buildSpreads(pageCount: Int, double: Boolean, coverAlone: Boolean): List<IntArray> {
    if (pageCount <= 0) return emptyList()
    if (!double) return (0 until pageCount).map { intArrayOf(it) }

    val out = ArrayList<IntArray>(pageCount / 2 + 2)
    var i = 0
    if (coverAlone) {
        out += intArrayOf(0)
        i = 1
    }
    while (i < pageCount) {
        if (i + 1 < pageCount) out += intArrayOf(i, i + 1) else out += intArrayOf(i)
        i += 2
    }
    return out
}

/** 페이지 번호가 들어 있는 펼침면의 위치 */
fun spreadIndexOf(spreads: List<IntArray>, page: Int): Int {
    val found = spreads.indexOfFirst { it.contains(page) }
    return if (found >= 0) found else 0
}
