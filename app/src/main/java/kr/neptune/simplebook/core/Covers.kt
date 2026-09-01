package kr.neptune.simplebook.core

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kr.neptune.simplebook.core.book.Books
import java.io.File
import java.security.MessageDigest

/**
 * 책 표지(첫 페이지)를 뽑아 캐시에 JPEG 로 둔다.
 *
 * 표지를 얻으려면 압축을 열어야 해서 한 장에 수십~수백 ms 가 든다.
 * 책장이 스크롤될 때마다 다시 뽑으면 못 쓰므로 파일로 남긴다.
 */
object Covers {

    private const val TAG = "Covers"
    private const val WIDTH = 480
    private const val HEIGHT = 720
    private const val BUDGET = 200L * 1024 * 1024

    /** 동시에 압축을 여러 개 여는 것을 막는다. 폴드에서도 메모리가 금방 찬다 */
    private val gate = Semaphore(2)
    private val locks = HashMap<String, Mutex>()

    private fun lockFor(key: String): Mutex = synchronized(locks) {
        locks.getOrPut(key) { Mutex() }
    }

    fun cacheDir(context: Context): File =
        File(context.cacheDir, "covers").apply { mkdirs() }

    fun cachedFile(context: Context, item: ShelfItem): File =
        File(cacheDir(context), sha1(item.id) + ".jpg")

    /** 캐시에 있으면 그대로, 없으면 만들어서 돌려준다. 표지를 뽑을 수 없으면 null */
    suspend fun file(context: Context, item: ShelfItem): File? {
        if (item.isFolder) return null
        if (item.kind == BookKind.TXT) return null

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
    }

    fun clear(context: Context) {
        runCatching { cacheDir(context).deleteRecursively() }
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
