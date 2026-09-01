package kr.neptune.simplebook.core

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * Coil 에 넘기는 표지 요청.
 *
 * [revision] 은 표지가 바뀔 때마다 올라간다. 이것이 캐시 키에 섞여 있어야
 * 표지를 새로 지정했을 때 Coil 이 옛 그림을 그대로 다시 쓰지 않는다.
 */
data class CoverRequest(val item: ShelfItem, val revision: Int)

class CoverFetcher(
    private val context: Context,
    private val request: CoverRequest,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val file = Covers.file(context, request.item) ?: return null
        return SourceResult(
            source = ImageSource(file = file.toOkioPath(), fileSystem = FileSystem.SYSTEM),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<CoverRequest> {
        override fun create(
            data: CoverRequest,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = CoverFetcher(context, data)
    }
}

class CoverKeyer : Keyer<CoverRequest> {
    override fun key(data: CoverRequest, options: Options): String =
        data.item.id + "#" + data.revision
}
