package kr.neptune.simplebook.core.book

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kr.neptune.simplebook.core.Library
import kr.neptune.simplebook.core.ShelfItem
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlin.math.min

/** ZIP / CBZ */
class ZipSource(context: Context, uri: Uri) : BookSource {

    private val archive = ZipArchive.open(context, uri)

    private val pages = archive.entries
        .filter { Library.isImage(it.name) }
        .sortedWith(compareBy(Library.NATURAL) { it.name })

    init {
        if (pages.isEmpty()) {
            archive.close()
            throw BookException("압축 안에 이미지가 없습니다")
        }
    }

    // 채널 하나를 공유하므로 동시에 두 페이지를 읽으면 위치가 꼬인다
    private val lock = Mutex()

    override val pageCount: Int get() = pages.size

    override suspend fun render(index: Int, maxWidth: Int, maxHeight: Int): Bitmap? {
        val entry = pages.getOrNull(index) ?: return null
        return withContext(Dispatchers.IO) {
            val bytes = lock.withLock { archive.read(entry) }
            Bitmaps.decode(bytes, maxWidth, maxHeight)
        }
    }

    override fun close() = archive.close()
}

/** 이미지들이 그대로 들어 있는 폴더 */
class FolderSource(private val context: Context, item: ShelfItem) : BookSource {

    private val pages: List<Uri> = Library.imagePages(context, item)

    init {
        if (pages.isEmpty()) throw BookException("폴더 안에 이미지가 없습니다")
    }

    override val pageCount: Int get() = pages.size

    override suspend fun render(index: Int, maxWidth: Int, maxHeight: Int): Bitmap? {
        val uri = pages.getOrNull(index) ?: return null
        return withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            Bitmaps.decode(bytes, maxWidth, maxHeight)
        }
    }

    override fun close() = Unit
}

/** PDF. 안드로이드 기본 PdfRenderer 를 쓴다 */
class PdfSource(context: Context, uri: Uri) : BookSource {

    private val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
        ?: throw BookException("PDF 를 열 수 없습니다")

    private val renderer: PdfRenderer = try {
        PdfRenderer(pfd)
    } catch (t: Throwable) {
        runCatching { pfd.close() }
        throw BookException(
            if (t.message?.contains("password", true) == true) "암호가 걸린 PDF 는 열 수 없습니다"
            else "PDF 를 읽지 못했습니다",
            t
        )
    }

    // PdfRenderer 는 한 번에 한 페이지만 열 수 있다
    private val lock = Mutex()

    override val pageCount: Int get() = renderer.pageCount

    override suspend fun render(index: Int, maxWidth: Int, maxHeight: Int): Bitmap? {
        if (index !in 0 until renderer.pageCount) return null
        return withContext(Dispatchers.IO) {
            lock.withLock {
                renderer.openPage(index).use { page ->
                    val scale = min(
                        maxWidth.toFloat() / page.width,
                        maxHeight.toFloat() / page.height
                    ).coerceAtLeast(0.1f)
                    val w = (page.width * scale).toInt().coerceIn(1, 4096)
                    val h = (page.height * scale).toInt().coerceIn(1, 4096)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    // PDF 는 투명 배경이 기본이라 흰 종이를 깔아 준다
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}

/** TXT. 페이지 분할은 화면 크기를 아는 뷰어가 한다 */
class TxtSource(context: Context, uri: Uri) : BookSource {

    override val isText: Boolean = true

    override val text: String = run {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readAtMost(MAX_BYTES)
        } ?: throw BookException("파일을 열 수 없습니다")
        if (bytes.isEmpty()) throw BookException("내용이 비어 있습니다")
        decode(bytes).replace("\r\n", "\n").replace('\r', '\n')
    }

    override val pageCount: Int get() = 1

    override suspend fun render(index: Int, maxWidth: Int, maxHeight: Int): Bitmap? = null

    override fun close() = Unit

    private companion object {
        const val MAX_BYTES = 32 * 1024 * 1024

        fun java.io.InputStream.readAtMost(limit: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream(minOf(limit, 1 shl 16))
            val buf = ByteArray(1 shl 16)
            var total = 0
            while (total < limit) {
                val n = read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
                total += n
            }
            return out.toByteArray()
        }

        /**
         * 한국어 TXT 는 UTF-8 과 CP949 가 반반이라 자동 판별이 필요하다.
         * BOM → 엄격 UTF-8 → CP949 순으로 시도한다.
         */
        fun decode(bytes: ByteArray): String {
            if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
            ) {
                return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            }
            if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            }
            if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            }
            runCatching {
                val decoder = Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                return decoder.decode(ByteBuffer.wrap(bytes)).toString()
            }
            listOf("MS949", "EUC-KR").forEach { name ->
                runCatching { return String(bytes, Charset.forName(name)) }
            }
            return String(bytes, Charsets.ISO_8859_1)
        }
    }
}
