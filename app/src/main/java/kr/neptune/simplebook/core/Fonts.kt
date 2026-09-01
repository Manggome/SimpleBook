package kr.neptune.simplebook.core

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 사용자가 고른 ttf/otf 를 앱 안에 복사해 둔다.
 *
 * SAF uri 를 그대로 들고 있을 수도 있지만, 원본이 지워지거나 권한이 끊기면
 * 글꼴이 통째로 사라진다. 한 번 복사해 두면 그럴 일이 없다.
 */
object Fonts {

    private const val TAG = "Fonts"
    private const val MAX_BYTES = 24 * 1024 * 1024

    /** 글꼴이 바뀔 때마다 올라간다. Compose 가 이 값을 키로 다시 만든다 */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun file(context: Context): File =
        File(context.filesDir, "fonts").apply { mkdirs() }.let { File(it, "custom.ttf") }

    fun exists(context: Context): Boolean = file(context).let { it.exists() && it.length() > 0 }

    /**
     * 고른 글꼴을 설치한다. 성공하면 표시용 이름, 실패하면 null.
     * 안드로이드가 읽지 못하는 파일이면 되돌린다.
     */
    suspend fun install(context: Context, source: Uri): String? = withContext(Dispatchers.IO) {
        val target = file(context)
        val tmp = File(target.parentFile, "custom.tmp")
        try {
            val size = context.contentResolver.openInputStream(source)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
            } ?: return@withContext null
            if (size <= 0 || size > MAX_BYTES) {
                tmp.delete()
                return@withContext null
            }

            // 글꼴로 못 읽히면 여기서 걸러낸다. 나중에 화면에서 터지는 것보다 낫다
            val typeface = runCatching { Typeface.createFromFile(tmp) }.getOrNull()
            if (typeface == null || typeface == Typeface.DEFAULT) {
                tmp.delete()
                return@withContext null
            }

            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.delete()
                return@withContext null
            }
            _revision.value = _revision.value + 1
            Library.queryName(context, source) ?: source.lastPathSegment ?: "사용자 글꼴"
        } catch (t: Throwable) {
            Log.w(TAG, "글꼴 설치 실패", t)
            tmp.delete()
            null
        }
    }

    fun remove(context: Context) {
        runCatching { file(context).delete() }
        _revision.value = _revision.value + 1
    }
}
