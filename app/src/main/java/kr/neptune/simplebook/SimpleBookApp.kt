package kr.neptune.simplebook

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kr.neptune.simplebook.core.CoverFetcher
import kr.neptune.simplebook.core.CoverKeyer
import kr.neptune.simplebook.core.LibraryStore
import kr.neptune.simplebook.core.Prefs

class SimpleBookApp : Application(), ImageLoaderFactory {

    lateinit var prefs: Prefs
        private set

    lateinit var store: LibraryStore
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        store = LibraryStore(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(CoverFetcher.Factory(this@SimpleBookApp))
            add(CoverKeyer())
        }
        // 표지는 이미 우리가 파일로 캐시하므로 Coil 의 디스크 캐시는 작게 잡는다
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("coil")).maxSizeBytes(32L * 1024 * 1024).build() }
        .crossfade(true)
        .build()
}
