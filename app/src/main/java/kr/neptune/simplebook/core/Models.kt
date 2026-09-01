package kr.neptune.simplebook.core

/** 책장 표시 방식 */
enum class ViewMode { GRID, LIST }

/** 책장 정렬 방식 */
enum class SortMode(val label: String) {
    /** 최근에 읽은 순. 한 번도 안 읽은 책은 뒤로 간다 */
    RECENT("읽은순"),
    TITLE("제목순"),
}

/** 페이지 진행 방향 */
enum class ReadDirection(val label: String, val hint: String) {
    /** 한국식(좌철). 1쪽이 왼쪽, 오른쪽을 탭하면 다음 */
    LTR("한국식", "왼쪽 → 오른쪽"),
    /** 일본식(우철). 1쪽이 오른쪽, 왼쪽을 탭하면 다음 */
    RTL("일본식", "오른쪽 → 왼쪽"),
    /** 세로 스크롤. 웹툰/텍스트에 적합 */
    VERTICAL("세로 스크롤", "아래로 이어보기"),
}

/** 한 화면에 몇 쪽을 펼칠지 */
enum class SpreadMode(val label: String) {
    /** 화면 가로÷세로 비율로 판단 (폴드를 펴거나 가로로 돌리면 2쪽) */
    AUTO("자동"),
    SINGLE("1쪽 고정"),
    DOUBLE("2쪽 고정"),
}

/**
 * TXT 를 읽을 때의 종이색. 이미지 책은 항상 검은 바탕이라 여기 해당하지 않는다.
 *
 * @param paper 배경, [ink] 글자색 (ARGB)
 */
enum class TextBackground(val label: String, val paper: Long, val ink: Long) {
    WHITE("하양", 0xFFFAF7F2, 0xFF1B1815),
    GRAY("어두운 회색", 0xFF2B2B2E, 0xFFDCD8D2),
    BLACK("검정", 0xFF000000, 0xFFCFCAC3),
}

/** 페이지가 넘어갈 때의 움직임 */
enum class PageEffect(val label: String) {
    /** 페이지가 손가락을 따라 옆으로 밀려 나간다 */
    SLIDE("슬라이드"),
    /** 애니메이션 없이 즉시 바뀐다. 느린 기기에서 체감이 빠르다 */
    NONE("없음"),
}

/** 화면 회전 */
enum class OrientationMode(val label: String) {
    AUTO("자동"),
    PORTRAIT("세로 고정"),
    LANDSCAPE("가로 고정"),
}

/** 파일 종류. 렌더러가 여기서 갈린다 */
enum class BookKind(val label: String) {
    ZIP("ZIP/CBZ"),
    RAR("RAR/CBR"),
    PDF("PDF"),
    TXT("TXT"),
    IMAGE_FOLDER("이미지 폴더"),
}

/**
 * 책장에 놓이는 한 칸. 폴더이거나 책이다.
 *
 * @param id        안정 식별자. document uri 문자열을 그대로 쓴다
 * @param treeUri   SAF 트리 uri. 단일 파일로 등록한 항목은 null
 * @param docId     트리 안에서의 document id. 하위 목록을 조회할 때 필요
 * @param parentId  상위 폴더의 id. null 이면 사용자가 직접 등록한 최상위 항목
 */
data class ShelfItem(
    val id: String,
    val uri: String,
    val treeUri: String?,
    val docId: String?,
    val parentId: String?,
    val name: String,
    val isFolder: Boolean,
    val kind: BookKind?,
    val size: Long = 0L,
    val modified: Long = 0L,
    /** 폴더일 때 안에 든 항목 수. 목록에 "12권" 처럼 보여준다 */
    val childCount: Int = 0,
    /** 폴더인데 안에 이미지가 바로 들어 있으면 "이 폴더를 책으로 읽기" 를 띄운다 */
    val folderHasImages: Boolean = false,
    /** 사용자가 직접 등록한 최상위 항목인지 (제거 버튼 노출용) */
    val isRoot: Boolean = false,
) {
    /** 확장자를 뗀 표시용 제목 */
    val title: String
        get() = if (isFolder) name else name.substringBeforeLast('.', name)
}

/** 책 한 권의 읽기 상태. 정렬(읽은순)과 이어보기에 쓴다 */
data class BookState(
    val page: Int = 0,
    val pageCount: Int = 0,
    val lastReadAt: Long = 0L,
    /** 이 책에만 적용할 방향. null 이면 전역 기본값을 따른다 */
    val direction: ReadDirection? = null,
    /** 이 책에만 적용할 펼침 설정. null 이면 전역 기본값을 따른다 */
    val spread: SpreadMode? = null,
) {
    val percent: Int
        get() = if (pageCount <= 0) 0 else ((page + 1) * 100 / pageCount).coerceIn(0, 100)

    val started: Boolean get() = lastReadAt > 0L
    val finished: Boolean get() = pageCount > 0 && page >= pageCount - 1
}
