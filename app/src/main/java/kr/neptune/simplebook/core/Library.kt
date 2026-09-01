package kr.neptune.simplebook.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SAF(Storage Access Framework) 로 폰의 폴더/파일을 훑는다.
 *
 * DocumentFile 대신 ContentResolver 를 직접 쓴다. DocumentFile.listFiles() 는
 * 항목마다 별도 질의를 날려서 수백 개짜리 폴더에서 몇 초씩 걸린다.
 * 여기서는 한 번의 질의로 필요한 컬럼을 전부 받아온다.
 */
object Library {

    private const val TAG = "Library"

    private val IMAGE_EXT = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif"
    )

    private val PROJECTION = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE,
        Document.COLUMN_LAST_MODIFIED,
    )

    fun ext(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun isImage(name: String): Boolean = ext(name) in IMAGE_EXT

    /** 파일 이름으로 렌더러를 고른다. 모르는 확장자는 책장에 올리지 않는다 */
    fun kindOf(name: String): BookKind? = when (ext(name)) {
        "zip", "cbz" -> BookKind.ZIP
        "rar", "cbr" -> BookKind.RAR
        "pdf" -> BookKind.PDF
        "txt" -> BookKind.TXT
        else -> null
    }

    /** 숨김 파일과 압축 프로그램이 남긴 찌꺼기는 건너뛴다 */
    private fun ignored(name: String): Boolean =
        name.startsWith(".") || name.equals("__MACOSX", true) || name.equals("Thumbs.db", true)

    // ------------------------------------------------------------ 폴더/파일 등록

    fun openTreeIntent(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }

    fun openFileIntent(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }

    /** 권한을 영구 저장한다. 이걸 하지 않으면 앱을 껐다 켜면 접근이 끊긴다 */
    fun persist(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }.onFailure { Log.w(TAG, "권한 영구 저장 실패: $uri", it) }
    }

    fun releasePermission(context: Context, item: ShelfItem) {
        val uri = Uri.parse(item.treeUri ?: item.uri)
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    /** 사용자가 고른 폴더를 책장 최상위 항목으로 만든다 */
    suspend fun rootFromTree(context: Context, treeUri: Uri): ShelfItem? =
        withContext(Dispatchers.IO) {
            val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
                ?: return@withContext null
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val name = queryName(context, docUri) ?: docId.substringAfterLast('/').ifEmpty { "폴더" }

            val children = listChildren(context, treeUri.toString(), docId)
            val books = children.count { it.isDir || kindOf(it.name) != null }
            val hasImages = children.any { !it.isDir && isImage(it.name) }

            ShelfItem(
                id = docUri.toString(),
                uri = docUri.toString(),
                treeUri = treeUri.toString(),
                docId = docId,
                parentId = null,
                name = name,
                isFolder = true,
                kind = null,
                childCount = books + if (hasImages) 1 else 0,
                folderHasImages = hasImages,
                isRoot = true,
            )
        }

    /** 사용자가 고른 개별 파일을 책장 최상위 항목으로 만든다 */
    suspend fun rootFromFile(context: Context, uri: Uri): ShelfItem? =
        withContext(Dispatchers.IO) {
            val name = queryName(context, uri) ?: uri.lastPathSegment ?: return@withContext null
            val kind = kindOf(name) ?: return@withContext null
            ShelfItem(
                id = uri.toString(),
                uri = uri.toString(),
                treeUri = null,
                docId = null,
                parentId = null,
                name = name,
                isFolder = false,
                kind = kind,
                isRoot = true,
            )
        }

    // ------------------------------------------------------------ 스캔

    private data class Raw(
        val docId: String,
        val name: String,
        val mime: String,
        val size: Long,
        val modified: Long,
    ) {
        val isDir get() = mime == Document.MIME_TYPE_DIR
    }

    private fun listChildren(context: Context, treeUri: String, parentDocId: String): List<Raw> {
        val tree = Uri.parse(treeUri)
        val childrenUri = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
        }.getOrNull() ?: return emptyList()

        val out = ArrayList<Raw>()
        runCatching {
            context.contentResolver.query(childrenUri, PROJECTION, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    if (ignored(name)) continue
                    out += Raw(
                        docId = c.getString(0) ?: continue,
                        name = name,
                        mime = c.getString(2) ?: "",
                        size = if (c.isNull(3)) 0L else c.getLong(3),
                        modified = if (c.isNull(4)) 0L else c.getLong(4),
                    )
                }
            }
        }.onFailure { Log.w(TAG, "목록 조회 실패: $parentDocId", it) }
        return out
    }

    /**
     * 폴더 하나를 훑어 책장에 놓을 항목들을 만든다.
     *
     * 폴더 판정 규칙 — 안에 이미지가 있고 하위 폴더가 없으면 그 폴더 자체가 한 권의 책이다.
     * 하위 폴더가 섞여 있으면 폴더로 두고, 들어갔을 때 "이 폴더를 책으로 읽기" 를 띄운다.
     */
    suspend fun scanFolder(context: Context, parent: ShelfItem): List<ShelfItem> =
        withContext(Dispatchers.IO) {
            val treeUri = parent.treeUri ?: return@withContext emptyList()
            val parentDocId = parent.docId ?: return@withContext emptyList()
            val tree = Uri.parse(treeUri)

            val children = listChildren(context, treeUri, parentDocId)
            val out = ArrayList<ShelfItem>(children.size)

            for (raw in children) {
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, raw.docId)
                if (raw.isDir) {
                    val inner = listChildren(context, treeUri, raw.docId)
                    val innerImages = inner.count { !it.isDir && isImage(it.name) }
                    val innerDirs = inner.count { it.isDir }
                    val innerBooks = inner.count { !it.isDir && kindOf(it.name) != null }

                    val isBook = innerImages > 0 && innerDirs == 0
                    if (!isBook && innerDirs == 0 && innerBooks == 0 && innerImages == 0) {
                        continue // 볼 것이 하나도 없는 폴더는 감춘다
                    }
                    out += ShelfItem(
                        id = uri.toString(),
                        uri = uri.toString(),
                        treeUri = treeUri,
                        docId = raw.docId,
                        parentId = parent.id,
                        name = raw.name,
                        isFolder = !isBook,
                        kind = if (isBook) BookKind.IMAGE_FOLDER else null,
                        size = 0L,
                        modified = raw.modified,
                        childCount = if (isBook) innerImages else innerDirs + innerBooks + if (innerImages > 0) 1 else 0,
                        folderHasImages = innerImages > 0,
                    )
                } else {
                    val kind = kindOf(raw.name) ?: continue
                    out += ShelfItem(
                        id = uri.toString(),
                        uri = uri.toString(),
                        treeUri = treeUri,
                        docId = raw.docId,
                        parentId = parent.id,
                        name = raw.name,
                        isFolder = false,
                        kind = kind,
                        size = raw.size,
                        modified = raw.modified,
                    )
                }
            }
            out
        }

    /** 이미지 폴더(=책) 안의 페이지 목록. 자연 정렬로 1,2,10 순서를 맞춘다 */
    fun imagePages(context: Context, folder: ShelfItem): List<Uri> {
        val treeUri = folder.treeUri ?: return emptyList()
        val docId = folder.docId ?: return emptyList()
        val tree = Uri.parse(treeUri)
        return listChildren(context, treeUri, docId)
            .filter { !it.isDir && isImage(it.name) }
            .sortedWith(compareBy(NATURAL) { it.name })
            .map { DocumentsContract.buildDocumentUriUsingTree(tree, it.docId) }
    }

    fun queryName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    // ------------------------------------------------------------ 자연 정렬

    /** "1.jpg, 2.jpg, 10.jpg" 를 사람이 기대하는 순서로 놓는다 */
    val NATURAL: Comparator<String> = Comparator { a, b ->
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val si = i
                while (i < a.length && a[i].isDigit()) i++
                val sj = j
                while (j < b.length && b[j].isDigit()) j++
                val na = a.substring(si, i).trimStart('0')
                val nb = b.substring(sj, j).trimStart('0')
                if (na.length != nb.length) return@Comparator na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return@Comparator c
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return@Comparator c
                i++
                j++
            }
        }
        (a.length - i) - (b.length - j)
    }
}
