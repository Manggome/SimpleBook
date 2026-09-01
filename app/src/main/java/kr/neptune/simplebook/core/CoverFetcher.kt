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
 * Coil 이 [ShelfItem] 을 그대로 model 로 받아 표지를 그릴 수 있게 해 준다.
 * 이렇게 해야 Coil 의 메모리 캐시와 재활용이 그대로 붙는다.
 */
class CoverFetcher(
    private val context: Context,
    private val item: ShelfItem,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val file = Covers.file(context, item) ?: return null
        return SourceResult(
            source = ImageSource(file = file.toOkioPath(), fileSystem = FileSystem.SYSTEM),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<ShelfItem> {
        override fun create(data: ShelfItem, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.isFolder) null else CoverFetcher(context, data)
    }
}

/** 캐시 키. document uri 는 파일이 그대로면 변하지 않는다 */
class CoverKeyer : Keyer<ShelfItem> {
    override fun key(data: ShelfItem, options: Options): String = data.id
}
