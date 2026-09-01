package kr.neptune.simplebook.core.book

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kr.neptune.simplebook.core.Library
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * RAR / CBR.
 *
 * junrar 는 실제 파일을 요구하고 SAF uri 는 경로가 없어서 캐시에 한 번 복사한 뒤 연다.
 * 캐시는 총량 제한을 두고 오래된 것부터 지운다.
 *
 * junrar 는 RAR5 를 지원하지 않는다. 자바로 쓸 수 있는 무료 RAR5 구현이 없어서,
 * 여기서는 서명을 먼저 보고 RAR5 면 알아들을 수 있는 안내를 띄운다.
 */
class RarSource(context: Context, uri: Uri, displayName: String) : BookSource {

    private val cached: File = copyToCache(context, uri, displayName)

    private val archive: Archive = try {
        Archive(cached)
    } catch (t: Throwable) {
        throw BookException(explain(t), t)
    }

    private val pages: List<FileHeader> = try {
        archive.fileHeaders
            .filter { !it.isDirectory && Library.isImage(it.fileName.orEmpty()) }
            .sortedWith(compareBy(Library.NATURAL) { it.fileName.orEmpty().replace('\\', '/') })
    } catch (t: Throwable) {
        runCatching { archive.close() }
        throw BookException(explain(t), t)
    }

    init {
        if (pages.isEmpty()) {
            runCatching { archive.close() }
            throw BookException("압축 안에 이미지가 없습니다")
        }
    }

    private val lock = Mutex()

    override val pageCount: Int get() = pages.size

    override suspend fun render(index: Int, maxWidth: Int, maxHeight: Int): Bitmap? {
        val header = pages.getOrNull(index) ?: return null
        return withContext(Dispatchers.IO) {
            val bytes = lock.withLock {
                val out = ByteArrayOutputStream(header.fullUnpackSize.toInt().coerceIn(1 shl 12, 1 shl 25))
                archive.extractFile(header, out)
                out.toByteArray()
            }
            Bitmaps.decode(bytes, maxWidth, maxHeight)
        }
    }

    override fun close() {
        runCatching { archive.close() }
    }

    private companion object {
        const val TAG = "RarSource"
        const val CACHE_BUDGET = 600L * 1024 * 1024

        fun explain(t: Throwable): String {
            val name = t::class.java.simpleName
            val msg = t.message.orEmpty()
            return when {
                name.contains("RarV5", true) || msg.contains("RAR5", true) ->
                    "RAR5 로 압축된 파일입니다. 아직 읽지 못하니 ZIP/CBZ 로 다시 묶어 주세요."
                msg.contains("password", true) || name.contains("Crypt", true) ->
                    "암호가 걸린 압축 파일입니다"
                else -> "RAR 을 읽지 못했습니다" + if (msg.isEmpty()) "" else " ($msg)"
            }
        }

        fun copyToCache(context: Context, uri: Uri, displayName: String): File {
            val dir = File(context.cacheDir, "rar").apply { mkdirs() }
            val key = sha1(uri.toString())
            val target = File(dir, "$key.rar")

            val sourceSize = runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
            }.getOrNull() ?: -1L

            if (target.exists() && (sourceSize <= 0L || target.length() == sourceSize)) {
                target.setLastModified(System.currentTimeMillis())
                verifyRar4(target, displayName)
                return target
            }

            trim(dir, sourceSize.coerceAtLeast(0L))

            val tmp = File(dir, "$key.tmp")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
                } ?: throw BookException("파일을 열 수 없습니다")
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) throw BookException("캐시에 복사하지 못했습니다")
            } catch (e: BookException) {
                tmp.delete()
                throw e
            } catch (t: Throwable) {
                tmp.delete()
                throw BookException("파일을 캐시에 복사하지 못했습니다 (저장 공간을 확인해 주세요)", t)
            }

            verifyRar4(target, displayName)
            return target
        }

        /** junrar 에 넘기기 전에 서명을 보고 RAR5 를 걸러낸다 */
        fun verifyRar4(file: File, displayName: String) {
            val head = ByteArray(8)
            val read = file.inputStream().use { it.read(head) }
            if (read < 7) throw BookException("파일이 너무 작습니다")
            val rar4 = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00)
            val rar5 = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00)
            if (read >= 8 && head.copyOfRange(0, 8).contentEquals(rar5)) {
                throw BookException("$displayName 은 RAR5 형식입니다. 아직 읽지 못하니 ZIP/CBZ 로 다시 묶어 주세요.")
            }
            if (!head.copyOfRange(0, 7).contentEquals(rar4)) {
                Log.w(TAG, "RAR 서명이 아닙니다. 그래도 열어 봅니다: $displayName")
            }
        }

        /** 이번에 복사할 크기를 더해도 예산을 넘지 않도록 오래된 캐시부터 지운다 */
        fun trim(dir: File, incoming: Long) {
            val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
            var total = files.sumOf { it.length() } + incoming
            for (f in files) {
                if (total <= CACHE_BUDGET) break
                total -= f.length()
                f.delete()
            }
        }

        fun sha1(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
