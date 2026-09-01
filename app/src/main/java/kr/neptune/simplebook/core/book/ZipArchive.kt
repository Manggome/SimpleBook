package kr.neptune.simplebook.core.book

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.Inflater
import kotlin.math.min

/**
 * ZIP/CBZ 를 SAF uri 에서 직접, 임의 순서로 읽는다.
 *
 * java.util.zip.ZipFile 은 실제 파일 경로를 요구하는데 SAF uri 는 경로가 없다.
 * ZipInputStream 은 앞에서부터 순차로만 읽혀서 300 페이지짜리 책의 뒤쪽을 펴는 데
 * 앞을 전부 통과해야 한다. 그래서 중앙 디렉터리를 직접 읽어 임의 접근을 만든다.
 */
class ZipArchive private constructor(
    private val channel: FileChannel,
    private val onClose: () -> Unit,
) : Closeable {

    data class Entry(
        val name: String,
        val method: Int,
        val compressedSize: Long,
        val size: Long,
        val headerOffset: Long,
    )

    val entries: List<Entry> = readCentralDirectory()

    // ------------------------------------------------------------ 읽기

    fun read(e: Entry): ByteArray {
        val loc = buffer(30)
        readFully(loc, e.headerOffset)
        if (loc.getInt(0) != LOC_SIG) throw IOException("로컬 헤더가 깨졌습니다: ${e.name}")
        val nameLen = loc.getShort(26).toInt() and 0xFFFF
        val extraLen = loc.getShort(28).toInt() and 0xFFFF
        val dataOffset = e.headerOffset + 30 + nameLen + extraLen

        return when (e.method) {
            METHOD_STORED -> {
                val len = (if (e.size > 0) e.size else e.compressedSize).toInt()
                val out = ByteArray(len)
                readFully(ByteBuffer.wrap(out), dataOffset)
                out
            }
            METHOD_DEFLATED -> inflate(dataOffset, e.size)
            else -> throw IOException("지원하지 않는 압축 방식입니다 (method=${e.method})")
        }
    }

    /**
     * 중앙 디렉터리의 압축 크기를 믿지 않고 스트림이 끝날 때까지 밀어 넣는다.
     * 스트리밍으로 만들어진 zip 은 중앙 디렉터리에 크기가 0 으로 적혀 있는 경우가 있다.
     */
    private fun inflate(dataOffset: Long, expected: Long): ByteArray {
        val inflater = Inflater(true)
        val out = ByteArrayOutputStream(if (expected in 1..(64 shl 20)) expected.toInt() else 1 shl 18)
        val inBytes = ByteArray(64 * 1024)
        val inBuf = ByteBuffer.wrap(inBytes)
        val outBytes = ByteArray(64 * 1024)
        var position = dataOffset
        try {
            while (!inflater.finished()) {
                if (inflater.needsInput()) {
                    inBuf.clear()
                    val n = channel.read(inBuf, position)
                    if (n <= 0) break
                    position += n
                    inflater.setInput(inBytes, 0, n)
                }
                val produced = inflater.inflate(outBytes)
                if (produced > 0) {
                    out.write(outBytes, 0, produced)
                } else if (inflater.needsDictionary()) {
                    break
                } else if (!inflater.needsInput() && !inflater.finished()) {
                    break
                }
            }
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }

    override fun close() {
        runCatching { channel.close() }
        onClose()
    }

    // ------------------------------------------------------------ 헤더 해석

    private fun readCentralDirectory(): List<Entry> {
        val fileSize = channel.size()
        if (fileSize < 22) throw IOException("ZIP 파일이 너무 작습니다")

        val tailLen = min(fileSize, MAX_EOCD_SEARCH).toInt()
        val tail = buffer(tailLen)
        readFully(tail, fileSize - tailLen)

        var eocd = -1
        for (i in tailLen - 22 downTo 0) {
            if (tail.getInt(i) == EOCD_SIG) {
                eocd = i
                break
            }
        }
        if (eocd < 0) throw IOException("ZIP 형식이 아닙니다")

        var count = tail.getShort(eocd + 10).toInt() and 0xFFFF
        var cdSize = tail.getInt(eocd + 12).toLong() and 0xFFFFFFFFL
        var cdOffset = tail.getInt(eocd + 16).toLong() and 0xFFFFFFFFL

        // 4GB 를 넘거나 항목이 65535 개를 넘으면 ZIP64 쪽에 진짜 값이 있다
        if (cdOffset == 0xFFFFFFFFL || cdSize == 0xFFFFFFFFL || count == 0xFFFF) {
            val locator = eocd - 20
            if (locator >= 0 && tail.getInt(locator) == EOCD64_LOCATOR_SIG) {
                val z64Offset = tail.getLong(locator + 8)
                val z64 = buffer(56)
                readFully(z64, z64Offset)
                if (z64.getInt(0) == EOCD64_SIG) {
                    count = z64.getLong(32).toInt()
                    cdSize = z64.getLong(40)
                    cdOffset = z64.getLong(48)
                }
            }
        }

        if (cdSize <= 0 || cdSize > MAX_CD_SIZE) throw IOException("중앙 디렉터리 크기가 이상합니다: $cdSize")

        val cd = buffer(cdSize.toInt())
        readFully(cd, cdOffset)

        val list = ArrayList<Entry>(count.coerceIn(0, 4096))
        var p = 0
        while (p + 46 <= cd.capacity()) {
            if (cd.getInt(p) != CEN_SIG) break
            val flags = cd.getShort(p + 8).toInt() and 0xFFFF
            val method = cd.getShort(p + 10).toInt() and 0xFFFF
            var csize = cd.getInt(p + 20).toLong() and 0xFFFFFFFFL
            var usize = cd.getInt(p + 24).toLong() and 0xFFFFFFFFL
            val nameLen = cd.getShort(p + 28).toInt() and 0xFFFF
            val extraLen = cd.getShort(p + 30).toInt() and 0xFFFF
            val commentLen = cd.getShort(p + 32).toInt() and 0xFFFF
            var offset = cd.getInt(p + 42).toLong() and 0xFFFFFFFFL

            val nameBytes = ByteArray(nameLen)
            cd.position(p + 46)
            cd.get(nameBytes)

            if (usize == 0xFFFFFFFFL || csize == 0xFFFFFFFFL || offset == 0xFFFFFFFFL) {
                var e = p + 46 + nameLen
                val end = e + extraLen
                while (e + 4 <= end) {
                    val id = cd.getShort(e).toInt() and 0xFFFF
                    val len = cd.getShort(e + 2).toInt() and 0xFFFF
                    if (id == 0x0001) {
                        var f = e + 4
                        if (usize == 0xFFFFFFFFL && f + 8 <= end) { usize = cd.getLong(f); f += 8 }
                        if (csize == 0xFFFFFFFFL && f + 8 <= end) { csize = cd.getLong(f); f += 8 }
                        if (offset == 0xFFFFFFFFL && f + 8 <= end) { offset = cd.getLong(f) }
                        break
                    }
                    e += 4 + len
                }
            }

            val name = decodeName(nameBytes, flags and 0x800 != 0)
            // 디렉터리 항목과 찌꺼기는 버린다
            if (!name.endsWith("/") && !name.substringAfterLast('/').startsWith(".") &&
                !name.startsWith("__MACOSX/")
            ) {
                list += Entry(name, method, csize, usize, offset)
            }
            p += 46 + nameLen + extraLen + commentLen
        }
        return list
    }

    private fun buffer(size: Int): ByteBuffer =
        ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

    private fun readFully(dst: ByteBuffer, position: Long) {
        var pos = position
        while (dst.hasRemaining()) {
            val n = channel.read(dst, pos)
            if (n < 0) throw EOFException("파일 끝에 도달했습니다 (position=$pos)")
            pos += n
        }
        dst.rewind()
    }

    companion object {
        private const val EOCD_SIG = 0x06054b50
        private const val EOCD64_LOCATOR_SIG = 0x07064b50
        private const val EOCD64_SIG = 0x06064b50
        private const val CEN_SIG = 0x02014b50
        private const val LOC_SIG = 0x04034b50
        private const val METHOD_STORED = 0
        private const val METHOD_DEFLATED = 8
        private const val MAX_EOCD_SEARCH = 66_000L
        private const val MAX_CD_SIZE = 64L * 1024 * 1024

        fun open(context: Context, uri: Uri): ZipArchive {
            val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IOException("파일을 열 수 없습니다")
            val stream = FileInputStream(pfd.fileDescriptor)
            return try {
                ZipArchive(stream.channel) {
                    runCatching { stream.close() }
                    runCatching { pfd.close() }
                }
            } catch (t: Throwable) {
                runCatching { stream.close() }
                runCatching { pfd.close() }
                throw t
            }
        }

        /**
         * 한국에서 만들어진 압축 파일은 파일명이 CP949 인 경우가 많다.
         * UTF-8 플래그가 없어도 실제로는 UTF-8 인 경우가 흔해서 엄격 디코딩을 먼저 시도한다.
         */
        private val fallbacks: List<Charset> = listOf("MS949", "EUC-KR", "Shift_JIS")
            .mapNotNull { runCatching { Charset.forName(it) }.getOrNull() }

        private fun decodeName(bytes: ByteArray, utf8Flag: Boolean): String {
            if (utf8Flag) return String(bytes, Charsets.UTF_8)
            strictUtf8(bytes)?.let { return it }
            fallbacks.forEach { cs -> runCatching { return String(bytes, cs) } }
            return String(bytes, Charsets.ISO_8859_1)
        }

        private fun strictUtf8(bytes: ByteArray): String? = runCatching {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        }.getOrNull()
    }
}
