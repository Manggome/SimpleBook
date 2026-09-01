package kr.neptune.simplebook.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kr.neptune.simplebook.SimpleBookApp
import kr.neptune.simplebook.core.AppUpdater
import kr.neptune.simplebook.core.BookState
import kr.neptune.simplebook.core.Covers
import kr.neptune.simplebook.core.Fonts
import kr.neptune.simplebook.core.Library
import kr.neptune.simplebook.core.ShelfItem
import kr.neptune.simplebook.core.SortMode

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SimpleBookApp
    private val ctx: Context get() = getApplication<Application>()
    val prefs = app.prefs
    val store = app.store

    /** 현재 들어와 있는 폴더 경로. 비어 있으면 최상위 책장 */
    private val _path = MutableStateFlow<List<ShelfItem>>(emptyList())
    val path: StateFlow<List<ShelfItem>> = _path.asStateFlow()

    private val _items = MutableStateFlow<List<ShelfItem>>(emptyList())
    val items: StateFlow<List<ShelfItem>> = _items.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /**
     * 읽는 화면 맨 위 정보 줄에 띄울 것.
     *
     * 이 줄만은 카메라 구멍 여백 바깥, 화면 물리적 맨 위에 놓여야 해서
     * 리더 안이 아니라 액티비티가 그린다. 그래서 값을 여기로 올려 둔다.
     * 띄우지 않을 때(메뉴가 열렸을 때 등)는 null.
     */
    private val _readerStatus = MutableStateFlow<ReaderStatus?>(null)
    val readerStatus: StateFlow<ReaderStatus?> = _readerStatus.asStateFlow()

    fun publishReaderStatus(status: ReaderStatus?) {
        _readerStatus.value = status
    }

    /** null 이 아니면 이 책을 펴고 있다 */
    private val _reading = MutableStateFlow<ShelfItem?>(null)
    val reading: StateFlow<ShelfItem?> = _reading.asStateFlow()

    val current: ShelfItem? get() = _path.value.lastOrNull()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            store.roots.collect { _ ->
                // 최상위나 앱 폴더 안에 있을 때는 등록 목록이 곧 화면이다
                val here = current
                if (here == null || here.isVirtual) _items.value = shelfItemsOf(here)
            }
        }
        if (prefs.autoUpdate.value) {
            viewModelScope.launch { AppUpdater.check(silent = true) }
        }
    }

    // ------------------------------------------------------------ 탐색

    fun enter(folder: ShelfItem) {
        _path.value = _path.value + folder
        load(useCache = true)
    }

    /** 뒤로. 최상위에서는 false 를 돌려주어 앱이 닫히게 한다 */
    fun back(): Boolean {
        if (_reading.value != null) {
            closeBook()
            return true
        }
        if (_path.value.isEmpty()) return false
        _path.value = _path.value.dropLast(1)
        load(useCache = true)
        return true
    }

    fun jumpTo(index: Int) {
        if (index < -1 || index >= _path.value.size) return
        _path.value = if (index < 0) emptyList() else _path.value.take(index + 1)
        load(useCache = true)
    }

    fun refresh() = load(useCache = false)

    /** 최상위(null) 또는 앱 폴더 안에 놓인 등록 항목들 */
    private fun shelfItemsOf(folder: ShelfItem?): List<ShelfItem> {
        val roots = store.roots.value
        val parentId = folder?.id
        return roots
            .filter { it.parentId == parentId }
            .map { item ->
                // 앱 폴더는 안에 든 개수를 여기서 센다
                if (item.isVirtual) item.copy(childCount = roots.count { it.parentId == item.id })
                else item
            }
    }

    private fun load(useCache: Boolean) {
        loadJob?.cancel()
        val folder = current
        if (folder == null || folder.isVirtual) {
            _items.value = shelfItemsOf(folder)
            _busy.value = false
            if (!useCache && folder == null) refreshRoots()
            return
        }

        val cached = if (useCache) store.cached(folder.id) else null
        _items.value = cached ?: emptyList()
        _busy.value = cached == null

        loadJob = viewModelScope.launch {
            val scanned = Library.scanFolder(ctx, folder)
            store.putCache(folder.id, scanned)
            _items.value = scanned
            _busy.value = false
            if (scanned.isEmpty() && !hasPermission(folder)) {
                _notice.value = "폴더 접근 권한이 끊겼습니다. 다시 등록해 주세요."
            }
        }
    }

    /** 등록된 폴더들의 항목 수를 다시 센다 */
    private fun refreshRoots() {
        viewModelScope.launch {
            _busy.value = true
            store.roots.value.filter { it.isFolder && it.treeUri != null }.forEach { root ->
                val uri = Uri.parse(root.treeUri)
                Library.rootFromTree(ctx, uri)?.let { store.refreshRoot(it) }
            }
            _busy.value = false
        }
    }

    private fun hasPermission(item: ShelfItem): Boolean {
        val target = item.treeUri ?: item.uri
        return ctx.contentResolver.persistedUriPermissions
            .any { it.uri.toString() == target && it.isReadPermission }
    }

    // ------------------------------------------------------------ 등록

    fun registerFolder(treeUri: Uri) {
        viewModelScope.launch {
            Library.persist(ctx, treeUri)
            val root = Library.rootFromTree(ctx, treeUri)
            if (root == null) {
                _notice.value = "폴더를 등록하지 못했습니다"
                return@launch
            }
            // 앱 폴더 안에서 등록하면 그 안에 들어간다
            store.addRoot(root, currentVirtualId())
            _notice.value = "${root.name} 등록됨"
        }
    }

    fun registerFiles(uris: List<Uri>) {
        viewModelScope.launch {
            var added = 0
            var skipped = 0
            uris.forEach { uri ->
                Library.persist(ctx, uri)
                val item = Library.rootFromFile(ctx, uri)
                if (item == null) skipped++ else {
                    store.addRoot(item, currentVirtualId())
                    added++
                }
            }
            _notice.value = when {
                added > 0 && skipped > 0 -> "${added}개 추가, ${skipped}개는 지원하지 않는 형식"
                added > 0 -> "${added}개 추가됨"
                else -> "지원하지 않는 형식입니다 (ZIP/CBZ, RAR/CBR, PDF, TXT)"
            }
        }
    }

    fun removeRoot(item: ShelfItem) {
        store.removeRoot(item.id)
        Covers.forget(ctx, item)
        if (!item.isVirtual) Library.releasePermission(ctx, item)
        _notice.value =
            if (item.isVirtual) "${item.title} 폴더를 없앴습니다 (안에 있던 것은 최상위로 올라갑니다)"
            else "${item.title} 제거됨 (원본 파일은 그대로입니다)"
    }

    // ------------------------------------------------------------ 앱 안 폴더

    private fun currentVirtualId(): String? = current?.takeIf { it.isVirtual }?.id

    /** 지금 있는 자리에 앱 폴더를 만든다 */
    fun createFolder(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) {
            _notice.value = "폴더 이름을 넣어 주세요"
            return
        }
        store.createVirtualFolder(clean, currentVirtualId())
        _notice.value = "${clean} 폴더를 만들었습니다"
    }

    fun renameFolder(item: ShelfItem, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        store.renameRoot(item.id, clean)
    }

    /** 옮길 수 있는 앱 폴더 목록 (자기 자신 제외) */
    fun folderTargets(item: ShelfItem): List<ShelfItem> = folderTargets(setOf(item.id))

    fun folderTargets(exclude: Set<String>): List<ShelfItem> =
        store.roots.value.filter { it.isVirtual && it.id !in exclude }
            .sortedWith(compareBy(Library.NATURAL) { it.title })

    fun moveTo(item: ShelfItem, folderId: String?) {
        store.moveRoot(item.id, folderId)
        _notice.value = if (folderId == null) "최상위로 옮겼습니다" else "옮겼습니다"
    }

    // ------------------------------------------------------------ 읽기

    fun openBook(item: ShelfItem) {
        _reading.value = item
    }

    /** 폴더 안에 이미지가 바로 들어 있을 때 그 폴더 자체를 한 권으로 편다 */
    fun openFolderAsBook(folder: ShelfItem) {
        _reading.value = folder.copy(
            isFolder = false,
            kind = kr.neptune.simplebook.core.BookKind.IMAGE_FOLDER,
        )
    }

    fun closeBook() {
        _reading.value = null
    }

    fun saveProgress(id: String, page: Int, pageCount: Int) {
        store.updateState(id) {
            it.copy(page = page, pageCount = pageCount, lastReadAt = System.currentTimeMillis())
        }
    }

    fun setBookState(id: String, block: (BookState) -> BookState) = store.updateState(id, block)

    /** 고른 것들을 한꺼번에 다 읽음으로 */
    fun markReadAll(items: List<ShelfItem>) {
        val books = items.filterNot { it.isFolder }
        if (books.isEmpty()) {
            _notice.value = "책을 고른 다음에 눌러 주세요"
            return
        }
        // lastReadAt 은 건드리지 않는다. 손으로 표시한 것이 최근에 읽은 책을 밀어내면 곤란하다
        books.forEach { book -> store.updateState(book.id) { it.copy(done = true) } }
        _notice.value = "${books.size}권을 다 읽음으로 표시했습니다"
    }

    /** 고른 것들을 한꺼번에 앱 폴더로 */
    fun moveAllTo(items: List<ShelfItem>, folderId: String?) {
        val roots = items.filter { it.isRoot }
        if (roots.isEmpty()) {
            _notice.value = "책장에 직접 등록한 항목만 옮길 수 있습니다"
            return
        }
        roots.forEach { store.moveRoot(it.id, folderId) }
        _notice.value =
            if (folderId == null) "${roots.size}개를 최상위로 옮겼습니다"
            else "${roots.size}개를 옮겼습니다"
    }

    /** 고른 것들을 한꺼번에 책장에서 빼기. 폰의 파일은 건드리지 않는다 */
    fun removeRoots(items: List<ShelfItem>) {
        val roots = items.filter { it.isRoot }
        if (roots.isEmpty()) {
            _notice.value = "책장에 직접 등록한 항목만 뺄 수 있습니다"
            return
        }
        roots.forEach { root ->
            store.removeRoot(root.id)
            Covers.forget(ctx, root)
            if (!root.isVirtual) Library.releasePermission(ctx, root)
        }
        _notice.value = "${roots.size}개를 책장에서 뺐습니다 (원본 파일은 그대로입니다)"
    }

    /** 고른 것들을 한꺼번에 안 읽음으로. 폴더는 읽기 상태가 없으니 건너뛴다 */
    fun markUnreadAll(items: List<ShelfItem>) {
        val books = items.filterNot { it.isFolder }
        if (books.isEmpty()) {
            _notice.value = "책을 고른 다음에 눌러 주세요"
            return
        }
        books.forEach { store.clearState(it.id) }
        _notice.value = "${books.size}권을 안 읽음으로 표시했습니다"
    }

    fun markUnread(item: ShelfItem) {
        store.clearState(item.id)
        _notice.value = "${item.title} 을(를) 안 읽음으로 표시했습니다"
    }

    /**
     * 같은 폴더의 다음 책. 이름 자연 정렬 기준이라 "1화, 2화, 10화" 가 순서대로 이어진다.
     * 마지막 쪽에서 "다음 화" 를 띄울 때 쓴다.
     */
    fun nextBook(item: ShelfItem): ShelfItem? {
        val siblings = (if (item.isRoot) store.roots.value.filter { it.parentId == item.parentId }
        else store.cached(item.parentId) ?: _items.value)
            .filterNot { it.isFolder }
            .sortedWith(compareBy(Library.NATURAL) { it.title })
        val here = siblings.indexOfFirst { it.id == item.id }
        return siblings.getOrNull(here + 1).takeIf { here >= 0 }
    }

    // ------------------------------------------------------------ 표지

    fun setCover(item: ShelfItem, source: Uri) {
        viewModelScope.launch {
            val ok = Covers.setCustom(ctx, item, source)
            _notice.value = if (ok) "표지를 바꿨습니다" else "이 이미지를 표지로 쓰지 못했습니다"
        }
    }

    fun resetCover(item: ShelfItem) {
        Covers.clearCustom(ctx, item)
        _notice.value = "표지를 기본으로 되돌렸습니다"
    }

    fun hasCustomCover(item: ShelfItem): Boolean = Covers.hasCustom(ctx, item)

    /** "안에 있는 책에서 표지 가져오기" 용 후보. null 이면 아직 훑는 중 */
    private val _coverCandidates = MutableStateFlow<List<ShelfItem>?>(null)
    val coverCandidates: StateFlow<List<ShelfItem>?> = _coverCandidates.asStateFlow()

    fun loadCoverCandidates(folder: ShelfItem) {
        viewModelScope.launch {
            _coverCandidates.value = null
            val list = store.cached(folder.id)
                ?: Library.scanFolder(ctx, folder).also { store.putCache(folder.id, it) }
            _coverCandidates.value = list.filterNot { it.isFolder }
                .sortedWith(compareBy(Library.NATURAL) { it.title })
        }
    }

    fun clearCoverCandidates() {
        _coverCandidates.value = null
    }

    fun useCoverOf(target: ShelfItem, source: ShelfItem) {
        viewModelScope.launch {
            val ok = Covers.useCoverOf(ctx, target, source)
            _notice.value =
                if (ok) "${source.title} 의 표지를 가져왔습니다" else "그 책의 표지를 뽑지 못했습니다"
        }
    }

    // ------------------------------------------------------------ 글꼴

    fun installFont(source: Uri) {
        viewModelScope.launch {
            val name = Fonts.install(ctx, source)
            if (name == null) {
                _notice.value = "글꼴로 읽지 못했습니다 (ttf / otf 만 됩니다)"
                return@launch
            }
            prefs.setCustomFontName(name)
            prefs.setUseCustomFont(true)
            _notice.value = "$name 적용"
        }
    }

    fun removeFont() {
        Fonts.remove(ctx)
        prefs.setUseCustomFont(false)
        prefs.setCustomFontName("")
        _notice.value = "시스템 글꼴로 돌아갑니다"
    }

    fun hasCustomFont(): Boolean = Fonts.exists(ctx)

    // ------------------------------------------------------------ 정렬

    fun sorted(list: List<ShelfItem>, mode: SortMode): List<ShelfItem> {
        val states = store.states.value
        val folders = list.filter { it.isFolder }
        val books = list.filterNot { it.isFolder }
        val byTitle = compareBy(Library.NATURAL) { i: ShelfItem -> i.title }
        return when (mode) {
            SortMode.TITLE -> folders.sortedWith(byTitle) + books.sortedWith(byTitle)
            // 최근에 읽은 것이 위로. 한 번도 안 읽은 책은 제목순으로 뒤에 붙인다
            SortMode.RECENT -> {
                val read = books.filter { states[it.id]?.started == true }
                    .sortedByDescending { states[it.id]?.lastReadAt ?: 0L }
                val unread = books.filterNot { states[it.id]?.started == true }
                    .sortedWith(byTitle)
                read + folders.sortedWith(byTitle) + unread
            }
        }
    }

    // ------------------------------------------------------------ 기타

    fun consumeNotice() {
        _notice.value = null
    }

    fun say(text: String) {
        _notice.value = text
    }

    fun clearCovers() {
        Covers.clear(ctx)
        store.dropCache()
        _notice.value = "표지 캐시를 비웠습니다"
        refresh()
    }

    fun openTreeIntent(): Intent = Library.openTreeIntent()
    fun openFileIntent(): Intent = Library.openFileIntent()
}
