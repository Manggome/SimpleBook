package kr.neptune.simplebook.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 등록한 폴더/파일 목록과 읽기 상태를 파일에 저장한다.
 *
 * 스캔 결과도 함께 캐시한다. SAF 는 폴더 하나 훑는 데 수십 ms 씩 걸려서,
 * 캐시 없이는 책장을 열 때마다 눈에 띄게 멈춘다. 캐시를 먼저 그리고 뒤에서 다시 훑는다.
 */
class LibraryStore(context: Context) {

    private val app = context.applicationContext
    private val libraryFile = File(app.filesDir, "library.json")
    private val cacheFile = File(app.filesDir, "scan-cache.json")

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val _roots = MutableStateFlow<List<ShelfItem>>(emptyList())
    val roots: StateFlow<List<ShelfItem>> = _roots.asStateFlow()

    private val _states = MutableStateFlow<Map<String, BookState>>(emptyMap())
    val states: StateFlow<Map<String, BookState>> = _states.asStateFlow()

    /** parentId(null=루트) 별 스캔 결과 */
    private val scanCache = HashMap<String, List<ShelfItem>>()

    init {
        runCatching { loadLibrary() }.onFailure { Log.w(TAG, "library.json 읽기 실패", it) }
        runCatching { loadCache() }.onFailure { Log.w(TAG, "scan-cache.json 읽기 실패", it) }
    }

    // ---------------------------------------------------------------- 등록 항목

    fun addRoot(item: ShelfItem, parentId: String? = null) {
        val next = _roots.value.filterNot { it.id == item.id } +
            item.copy(isRoot = true, parentId = parentId)
        _roots.value = next
        saveLibrary()
    }

    /**
     * 항목을 뺀다. 앱 안에서 만든 폴더를 빼면 그 안에 있던 것들은 사라지지 않고
     * 최상위로 올라온다 — 등록을 다시 하게 만들 이유가 없다.
     */
    fun removeRoot(id: String) {
        _roots.value = _roots.value
            .filterNot { it.id == id }
            .map { if (it.parentId == id) it.copy(parentId = null) else it }
        scanCache.remove(id)
        saveLibrary()
        saveCache()
    }

    /** 앱 안에서만 존재하는 폴더를 만든다. 폰의 파일은 건드리지 않는다 */
    fun createVirtualFolder(name: String, parentId: String? = null): ShelfItem {
        val id = "virtual:" + java.util.UUID.randomUUID().toString()
        val folder = ShelfItem(
            id = id,
            uri = id,
            treeUri = null,
            docId = null,
            parentId = parentId,
            name = name,
            isFolder = true,
            kind = null,
            isRoot = true,
            isVirtual = true,
        )
        _roots.value = _roots.value + folder
        saveLibrary()
        return folder
    }

    fun renameRoot(id: String, name: String) {
        _roots.value = _roots.value.map { if (it.id == id) it.copy(name = name) else it }
        saveLibrary()
    }

    /** 등록 항목을 앱 폴더 사이로 옮긴다. [parentId] 가 null 이면 최상위로 */
    fun moveRoot(id: String, parentId: String?) {
        if (id == parentId) return
        _roots.value = _roots.value.map { if (it.id == id) it.copy(parentId = parentId) else it }
        saveLibrary()
    }

    /** 스캔으로 갱신된 정보(개수 등)를 등록 항목에 반영한다 */
    fun refreshRoot(item: ShelfItem) {
        val current = _roots.value
        val idx = current.indexOfFirst { it.id == item.id }
        if (idx < 0) return
        val merged = item.copy(isRoot = true, parentId = current[idx].parentId)
        if (current[idx] == merged) return
        _roots.value = current.toMutableList().also { it[idx] = merged }
        saveLibrary()
    }

    // ---------------------------------------------------------------- 읽기 상태

    fun state(id: String): BookState = _states.value[id] ?: BookState()

    fun putState(id: String, state: BookState) {
        _states.value = _states.value + (id to state)
        saveLibrary()
    }

    fun updateState(id: String, block: (BookState) -> BookState) {
        putState(id, block(state(id)))
    }

    fun clearState(id: String) {
        _states.value = _states.value - id
        saveLibrary()
    }

    // ---------------------------------------------------------------- 스캔 캐시

    fun cached(parentId: String?): List<ShelfItem>? = scanCache[parentId ?: ROOT_KEY]

    fun putCache(parentId: String?, items: List<ShelfItem>) {
        scanCache[parentId ?: ROOT_KEY] = items
        saveCache()
    }

    fun dropCache() {
        scanCache.clear()
        saveCache()
    }

    // ---------------------------------------------------------------- 직렬화

    private fun loadLibrary() {
        if (!libraryFile.exists()) return
        val json = JSONObject(libraryFile.readText())

        val rootArray = json.optJSONArray("roots") ?: JSONArray()
        _roots.value = (0 until rootArray.length()).mapNotNull {
            runCatching { itemFromJson(rootArray.getJSONObject(it)) }.getOrNull()
        }

        val stateObj = json.optJSONObject("states") ?: JSONObject()
        val map = HashMap<String, BookState>()
        stateObj.keys().forEach { key ->
            val o = stateObj.optJSONObject(key) ?: return@forEach
            map[key] = BookState(
                page = o.optInt("page", 0),
                pageCount = o.optInt("pageCount", 0),
                lastReadAt = o.optLong("lastReadAt", 0L),
                direction = o.optString("direction").takeIf { it.isNotEmpty() }
                    ?.let { runCatching { ReadDirection.valueOf(it) }.getOrNull() },
                spread = o.optString("spread").takeIf { it.isNotEmpty() }
                    ?.let { runCatching { SpreadMode.valueOf(it) }.getOrNull() },
            )
        }
        _states.value = map
    }

    private fun saveLibrary() {
        val roots = _roots.value
        val states = _states.value
        io.launch {
            writeLock.withLock {
                runCatching {
                    val json = JSONObject()
                    json.put("roots", JSONArray().apply { roots.forEach { put(itemToJson(it)) } })
                    json.put("states", JSONObject().apply {
                        states.forEach { (id, s) ->
                            put(id, JSONObject().apply {
                                put("page", s.page)
                                put("pageCount", s.pageCount)
                                put("lastReadAt", s.lastReadAt)
                                s.direction?.let { put("direction", it.name) }
                                s.spread?.let { put("spread", it.name) }
                            })
                        }
                    })
                    writeAtomic(libraryFile, json.toString())
                }.onFailure { Log.w(TAG, "library.json 저장 실패", it) }
            }
        }
    }

    private fun loadCache() {
        if (!cacheFile.exists()) return
        val json = JSONObject(cacheFile.readText())
        json.keys().forEach { key ->
            val arr = json.optJSONArray(key) ?: return@forEach
            scanCache[key] = (0 until arr.length()).mapNotNull {
                runCatching { itemFromJson(arr.getJSONObject(it)) }.getOrNull()
            }
        }
    }

    private fun saveCache() {
        val snapshot = HashMap(scanCache)
        io.launch {
            writeLock.withLock {
                runCatching {
                    val json = JSONObject()
                    snapshot.forEach { (key, items) ->
                        json.put(key, JSONArray().apply { items.forEach { put(itemToJson(it)) } })
                    }
                    writeAtomic(cacheFile, json.toString())
                }.onFailure { Log.w(TAG, "scan-cache.json 저장 실패", it) }
            }
        }
    }

    /** 쓰다 죽어도 이전 파일이 남도록 임시 파일에 쓰고 바꿔치기한다 */
    private fun writeAtomic(target: File, content: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(content)
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            target.writeText(content)
            tmp.delete()
        }
    }

    companion object {
        private const val TAG = "LibraryStore"
        private const val ROOT_KEY = "__root__"

        fun itemToJson(i: ShelfItem): JSONObject = JSONObject().apply {
            put("id", i.id)
            put("uri", i.uri)
            i.treeUri?.let { put("treeUri", it) }
            i.docId?.let { put("docId", it) }
            i.parentId?.let { put("parentId", it) }
            put("name", i.name)
            put("isFolder", i.isFolder)
            i.kind?.let { put("kind", it.name) }
            put("size", i.size)
            put("modified", i.modified)
            put("childCount", i.childCount)
            put("folderHasImages", i.folderHasImages)
            put("isRoot", i.isRoot)
            put("isVirtual", i.isVirtual)
        }

        fun itemFromJson(o: JSONObject): ShelfItem = ShelfItem(
            id = o.getString("id"),
            uri = o.getString("uri"),
            treeUri = o.optString("treeUri").takeIf { it.isNotEmpty() },
            docId = o.optString("docId").takeIf { it.isNotEmpty() },
            parentId = o.optString("parentId").takeIf { it.isNotEmpty() },
            name = o.getString("name"),
            isFolder = o.getBoolean("isFolder"),
            kind = o.optString("kind").takeIf { it.isNotEmpty() }
                ?.let { runCatching { BookKind.valueOf(it) }.getOrNull() },
            size = o.optLong("size", 0L),
            modified = o.optLong("modified", 0L),
            childCount = o.optInt("childCount", 0),
            folderHasImages = o.optBoolean("folderHasImages", false),
            isRoot = o.optBoolean("isRoot", false),
            isVirtual = o.optBoolean("isVirtual", false),
        )
    }
}
