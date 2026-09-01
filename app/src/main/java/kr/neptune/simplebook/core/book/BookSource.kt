package kr.neptune.simplebook.core.book

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kr.neptune.simplebook.core.BookKind
import kr.neptune.simplebook.core.Library
import kr.neptune.simplebook.core.ShelfItem
import java.io.Closeable

/** 사용자에게 그대로 보여줄 수 있는 실패 사유 */
class BookException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 한 권을 페이지 단위로 내주는 창구. 포맷별 차이는 전부 이 뒤로 숨긴다.
 * EPUB 을 나중에 붙이더라도 뷰어는 손대지 않아도 되도록 이 인터페이스만 구현하면 된다.
 */
interface BookSource : Closeable {

    /** 이미지 기반이면 페이지 수, 텍스트면 1 (텍스트 분할은 화면 크기에 따라 뷰어가 계산한다) */
    val pageCount: Int

    val isText: Boolean get() = false

    /** 텍스트 책의 본문 */
    val text: String? get() = null

    /** [maxWidth] x [maxHeight] 안에 들어가도록 줄여서 그린다 */
    suspend fun render(index: Int, maxWidth: Int, maxHeight: Int): Bitmap?
}

object Books {

    /** 책 한 권을 연다. 실패하면 [BookException] 으로 이유를 담아 던진다 */
    fun open(context: Context, item: ShelfItem): BookSource {
        val uri = Uri.parse(item.uri)
        val kind = item.kind ?: Library.kindOf(item.name)
        ?: throw BookException("지원하지 않는 형식입니다")
        return try {
            when (kind) {
                BookKind.ZIP -> ZipSource(context, uri)
                BookKind.RAR -> RarSource(context, uri, item.name)
                BookKind.PDF -> PdfSource(context, uri)
                BookKind.TXT -> TxtSource(context, uri)
                BookKind.IMAGE_FOLDER -> FolderSource(context, item)
            }
        } catch (e: BookException) {
            throw e
        } catch (t: Throwable) {
            throw BookException(t.message ?: "책을 열지 못했습니다", t)
        }
    }
}

object Bitmaps {

    /**
     * 화면에 필요한 만큼만 줄여서 디코딩한다.
     * 만화 원본은 한 장이 2000x3000 을 넘는 일이 흔해서 그대로 올리면 폴드에서도 금방 터진다.
     */
    fun decode(bytes: ByteArray, maxWidth: Int, maxHeight: Int): Bitmap? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = sampleSize(bounds.outWidth, bounds.outHeight, maxWidth, maxHeight)
        repeat(4) {
            try {
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            } catch (e: OutOfMemoryError) {
                sample *= 2
            }
        }
        return null
    }

    fun sampleSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
        if (maxWidth <= 0 || maxHeight <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= maxWidth && height / (sample * 2) >= maxHeight) {
            sample *= 2
        }
        return sample
    }
}
