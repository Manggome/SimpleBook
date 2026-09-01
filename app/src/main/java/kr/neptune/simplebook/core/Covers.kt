package kr.neptune.simplebook.core

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kr.neptune.simplebook.core.book.Bitmaps
import kr.neptune.simplebook.core.book.Books
import java.io.File
import java.security.MessageDigest

/**
 * 책 표지(첫 페이지)를 뽑아 캐시에 JPEG 로 둔다.
 *
 * 표지를 얻으려면 압축을 열어야 해서 한 장에 수십~수백 ms 가 든다.
 * 책장이 스크롤될 때마다 다시 뽑으면 못 쓰므로 파일로 남긴다.
 *
 * 사용자가 직접 고른 표지는 캐시가 아니라 filesDir 에 둔다. "캐시 비우기" 로
 * 날아가면 안 되고, 자동 추출보다 항상 우선한다.
 */
object Covers {

    private const val TAG = "Covers"
    private const val WIDTH = 480
    private const val HEIGHT = 720
    private const val BUDGET = 200L * 1024 * 1024

    /** 동시에 압축을 여러 개 여는 것을 막는다. 폴드에서도 메모리가 금방 찬다 */
    private val gate = Semaphore(2)
    private val locks = HashMap<String, Mutex>()

    /**
     * 표지가 바뀔 때마다 올라간다. Coil 의 캐시 키에 섞어 넣어 갱신을 강제한다.
     * 하나만 바뀌어도 전부 무효가 되지만, 표지는 디스크에 있어 다시 읽는 값이 싸다.
     */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private fun lockFor(key: String): Mutex = synchronized(locks) {
        locks.getOrPut(key) { Mutex() }
    }

    fun cacheDir(context: Context): File =
        File(context.cacheDir, "covers").apply { mkdirs() }

    /** 사용자가 직접 지정한 표지. 캐시가 아니라 영구 저장소에 둔다 */
    fun customDir(context: Context): File =
        File(context.filesDir, "covers-custom").apply { mkdirs() }

    fun cachedFile(context: Context, item: ShelfItem): File =
        File(cacheDir(context), sha1(item.id) + ".jpg")

    fun customFile(context: Context, item: ShelfItem): File =
        File(customDir(context), sha1(item.id) + ".jpg")

    fun hasCustom(context: Context, item: ShelfItem): Boolean =
        customFile(context, item).let { it.exists() && it.length() > 0 }

    /** 캐시에 있으면 그대로, 없으면 만들어서 돌려준다. 표지를 뽑을 수 없으면 null */
    suspend fun file(context: Context, item: ShelfItem): File? {
        customFile(context, item).takeIf { it.exists() && it.length() > 0 }?.let { return it }

        // 폴더와 TXT 는 뽑아낼 그림이 없다. 직접 지정한 표지만 쓴다
        if (item.isFolder || item.kind == BookKind.TXT) return null

        val target = cachedFile(context, item)
        if (target.exists() && target.length() > 0) return target

        return lockFor(item.id).withLock {
            if (target.exists() && target.length() > 0) return@withLock target
            gate.withPermit {
                withContext(Dispatchers.IO) {
                    runCatching { generate(context, item, target) }
                        .onFailure { Log.w(TAG, "표지 생성 실패: ${item.name}", it) }
                        .getOrNull()
                }
            }
        }
    }

    /** 사용자가 고른 이미지를 이 항목의 표지로 삼는다 */
    suspend fun setCustom(context: Context, item: ShelfItem, source: Uri): Boolean =
        withContext(Dispatchers.IO) {
            val ok = runCatching {
                val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
                    ?: return@runCatching false
                val bitmap = Bitmaps.decode(bytes, WIDTH, HEIGHT) ?: return@runCatching false
                val target = customFile(context, item)
                val tmp = File(target.parentFile, target.name + ".tmp")
                tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                bitmap.recycle()
                if (target.exists()) target.delete()
                tmp.renameTo(target).also { if (!it) tmp.delete() }
            }.onFailure { Log.w(TAG, "표지 지정 실패: ${item.name}", it) }.getOrDefault(false)
            if (ok) bump()
            ok
        }

    /**
     * 다른 항목의 표지를 그대로 가져다 쓴다.
     * 폴더 썸네일을 안에 든 PDF·만화의 첫 페이지로 잡을 때 쓴다.
     */
    suspend fun useCoverOf(context: Context, target: ShelfItem, source: ShelfItem): Boolean {
        val from = file(context, source) ?: return false
        return withContext(Dispatchers.IO) {
            val ok = runCatching {
                val dst = customFile(context, target)
                from.copyTo(dst, overwrite = true)
                dst.length() > 0
            }.onFailure { Log.w(TAG, "표지 복사 실패", it) }.getOrDefault(false)
            if (ok) bump()
            ok
        }
    }

    /** 직접 지정한 표지를 지우고 자동 추출로 되돌린다 */
    fun clearCustom(context: Context, item: ShelfItem) {
        runCatching { customFile(context, item).delete() }
        runCatching { cachedFile(context, item).delete() }
        bump()
    }

    private suspend fun generate(context: Context, item: ShelfItem, target: File): File? {
        val bitmap: Bitmap = Books.open(context, item).use { source ->
            source.render(0, WIDTH, HEIGHT)
        } ?: return null

        trim(cacheDir(context))
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        bitmap.recycle()
        if (target.exists()) target.delete()
        if (tmp.renameTo(target)) return target
        tmp.delete()
        return null
    }

    /** 오래 안 쓴 표지부터 지워 캐시 크기를 예산 안에 둔다 */
    private fun trim(dir: File) {
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= BUDGET) break
            total -= f.length()
            f.delete()
        }
    }

    fun forget(context: Context, item: ShelfItem) {
        runCatching { cachedFile(context, item).delete() }
        runCatching { customFile(context, item).delete() }
        bump()
    }

    /** 자동 추출분만 비운다. 직접 지정한 표지는 남긴다 */
    fun clear(context: Context) {
        runCatching { cacheDir(context).deleteRecursively() }
        bump()
    }

    private fun bump() {
        _revision.value = _revision.value + 1
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
