package kr.neptune.simplebook.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 앱 전역 설정. SharedPreferences 에 저장하고 Compose 가 구독할 수 있게 StateFlow 로 내보낸다.
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("simplebook", Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- 책장

    private val _viewMode = MutableStateFlow(enum("view_mode", ViewMode.GRID))
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _sortMode = MutableStateFlow(enum("sort_mode", SortMode.RECENT))
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    fun setViewMode(v: ViewMode) = put("view_mode", v, _viewMode)
    fun setSortMode(v: SortMode) = put("sort_mode", v, _sortMode)

    // ---------------------------------------------------------------- 보기

    /**
      * 형식별 기본 보기 방식. 예전에 쓰던 단일 설정이 있으면 그 값을 출발점으로 삼는다.
      */
    private val _directions = MutableStateFlow(
        FormatGroup.entries.associateWith { loadDirection(it) }
    )
    val directions: StateFlow<Map<FormatGroup, ReadDirection>> = _directions.asStateFlow()

    private val _spreads = MutableStateFlow(
        FormatGroup.entries.associateWith { loadSpread(it) }
    )
    val spreads: StateFlow<Map<FormatGroup, SpreadMode>> = _spreads.asStateFlow()

    fun direction(group: FormatGroup): ReadDirection =
        _directions.value[group] ?: group.defaultDirection

    fun spread(group: FormatGroup): SpreadMode =
        _spreads.value[group] ?: group.defaultSpread

    private fun loadDirection(group: FormatGroup): ReadDirection {
        val saved = sp.getString("direction_" + group.key, null)
            ?: sp.getString("direction", null)
            ?: return group.defaultDirection
        return runCatching { ReadDirection.valueOf(saved) }.getOrDefault(group.defaultDirection)
    }

    private fun loadSpread(group: FormatGroup): SpreadMode {
        val saved = sp.getString("spread_" + group.key, null)
            ?: sp.getString("spread", null)
            ?: return group.defaultSpread
        return runCatching { SpreadMode.valueOf(saved) }.getOrDefault(group.defaultSpread)
    }

    /**
     * 자동 판정 기준. 화면 가로÷세로가 이 값 이상이면 2쪽을 편다.
     *
     * 기본값 0.85 는 폴드를 펴면 세로로 들든 가로로 들든 2쪽이 되는 지점이다
     * (펴고 세로 ~0.86, 펴고 가로 ~1.16). 1.0 으로 올리면 펴고 가로일 때만 2쪽이 된다.
     */
    private val _spreadThreshold = MutableStateFlow(sp.getFloat("spread_threshold", 0.85f))
    val spreadThreshold: StateFlow<Float> = _spreadThreshold.asStateFlow()

    /** 2쪽 보기에서 표지(1쪽)는 혼자 두기. 종이책 펼침면과 짝을 맞추기 위함 */
    private val _coverAlone = MutableStateFlow(sp.getBoolean("cover_alone", true))
    val coverAlone: StateFlow<Boolean> = _coverAlone.asStateFlow()

    private val _pageEffect = MutableStateFlow(enum("page_effect", PageEffect.SLIDE))
    val pageEffect: StateFlow<PageEffect> = _pageEffect.asStateFlow()

    fun setPageEffect(v: PageEffect) = put("page_effect", v, _pageEffect)

    /** 화면 좌우 25% 를 탭해서 넘기기. 가운데 50% 는 메뉴 */
    private val _tapToTurn = MutableStateFlow(sp.getBoolean("tap_to_turn", true))
    val tapToTurn: StateFlow<Boolean> = _tapToTurn.asStateFlow()

    /** 읽는 동안 화면 켜두기 */
    private val _keepScreenOn = MutableStateFlow(sp.getBoolean("keep_screen_on", true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    /** 읽을 때 상태바/내비바 숨기기 */
    private val _immersive = MutableStateFlow(sp.getBoolean("immersive", true))
    val immersive: StateFlow<Boolean> = _immersive.asStateFlow()

    fun setDirection(group: FormatGroup, v: ReadDirection) {
        sp.edit().putString("direction_" + group.key, v.name).apply()
        _directions.value = _directions.value + (group to v)
    }

    /** 형식별 기본값을 처음 상태로 */
    fun resetFormatDefaults() {
        val editor = sp.edit()
        FormatGroup.entries.forEach {
            editor.remove("direction_" + it.key).remove("spread_" + it.key)
        }
        // 예전 단일 설정이 남아 있으면 그것까지 지워야 정말 처음으로 돌아간다
        editor.remove("direction").remove("spread").apply()
        _directions.value = FormatGroup.entries.associateWith { it.defaultDirection }
        _spreads.value = FormatGroup.entries.associateWith { it.defaultSpread }
    }

    fun setSpread(group: FormatGroup, v: SpreadMode) {
        sp.edit().putString("spread_" + group.key, v.name).apply()
        _spreads.value = _spreads.value + (group to v)
    }
    fun setSpreadThreshold(v: Float) {
        sp.edit().putFloat("spread_threshold", v).apply()
        _spreadThreshold.value = v
    }
    fun setCoverAlone(v: Boolean) = put("cover_alone", v, _coverAlone)
    fun setTapToTurn(v: Boolean) = put("tap_to_turn", v, _tapToTurn)
    fun setKeepScreenOn(v: Boolean) = put("keep_screen_on", v, _keepScreenOn)
    fun setImmersive(v: Boolean) = put("immersive", v, _immersive)

    // ---------------------------------------------------------------- 자동 넘기기

    private val _autoTurn = MutableStateFlow(sp.getBoolean("auto_turn", false))
    val autoTurn: StateFlow<Boolean> = _autoTurn.asStateFlow()

    /** 몇 초에 한 번 넘길지 */
    private val _autoTurnSeconds = MutableStateFlow(sp.getFloat("auto_turn_seconds", 8f))
    val autoTurnSeconds: StateFlow<Float> = _autoTurnSeconds.asStateFlow()

    fun setAutoTurn(v: Boolean) = put("auto_turn", v, _autoTurn)

    fun setAutoTurnSeconds(v: Float) {
        val clamped = v.coerceIn(2f, 60f)
        sp.edit().putFloat("auto_turn_seconds", clamped).apply()
        _autoTurnSeconds.value = clamped
    }

    // ---------------------------------------------------------------- 읽을 때 화면 위 표시

    /** 시계 · 배터리 · 책 이름 · 쪽수를 한꺼번에 켜고 끈다 */
    private val _readerInfo = MutableStateFlow(
        sp.getBoolean(
            "reader_info",
            // 예전에는 넷을 따로 켰다. 하나라도 켜 뒀으면 이어받는다
            sp.getBoolean("overlay_clock", false) || sp.getBoolean("overlay_battery", false) ||
                sp.getBoolean("overlay_title", false) || sp.getBoolean("overlay_page", false),
        )
    )
    val readerInfo: StateFlow<Boolean> = _readerInfo.asStateFlow()

    fun setReaderInfo(v: Boolean) = put("reader_info", v, _readerInfo)

    // ---------------------------------------------------------------- 화면

    private val _theme = MutableStateFlow(enum("theme", ThemeMode.SYSTEM))
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    fun setTheme(v: ThemeMode) = put("theme", v, _theme)

    /**
     * 켜면 카메라 구멍이 파고든 만큼 화면 전체를 내린다.
     * 구멍 자리는 검게 남아서 그림이나 글자를 가리지 않는다.
     */
    private val _avoidCutout = MutableStateFlow(sp.getBoolean("avoid_cutout", false))
    val avoidCutout: StateFlow<Boolean> = _avoidCutout.asStateFlow()

    fun setAvoidCutout(v: Boolean) = put("avoid_cutout", v, _avoidCutout)

    /**
     * 화면을 얼마나 내릴지 (dp). 자동으로 잡힌 값에 더하는 것이 아니라 이 값 그대로다 —
     * 0 이면 정말 0. 기기가 알려 주는 여백이 방향마다 제각각이라 세로/가로를 따로 둔다.
     */
    private val _cutoutPortrait = MutableStateFlow(sp.getFloat("cutout_portrait", 0f))
    val cutoutPortrait: StateFlow<Float> = _cutoutPortrait.asStateFlow()

    private val _cutoutLandscape = MutableStateFlow(sp.getFloat("cutout_landscape", 0f))
    val cutoutLandscape: StateFlow<Float> = _cutoutLandscape.asStateFlow()

    fun cutoutInset(portrait: Boolean): Float =
        if (portrait) _cutoutPortrait.value else _cutoutLandscape.value

    fun setCutoutInset(portrait: Boolean, v: Float) {
        val clamped = v.coerceIn(0f, 160f)
        val key = if (portrait) "cutout_portrait" else "cutout_landscape"
        sp.edit().putFloat(key, clamped).apply()
        if (portrait) _cutoutPortrait.value = clamped else _cutoutLandscape.value = clamped
    }

    private val _orientation = MutableStateFlow(enum("orientation", OrientationMode.AUTO))
    val orientation: StateFlow<OrientationMode> = _orientation.asStateFlow()

    /** 끄면 아래 [brightness] 값으로 앱 화면 밝기를 직접 잡는다 */
    private val _systemBrightness = MutableStateFlow(sp.getBoolean("system_brightness", true))
    val systemBrightness: StateFlow<Boolean> = _systemBrightness.asStateFlow()

    private val _brightness = MutableStateFlow(sp.getFloat("brightness", 0.6f))
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    fun setOrientation(v: OrientationMode) = put("orientation", v, _orientation)
    fun setSystemBrightness(v: Boolean) = put("system_brightness", v, _systemBrightness)
    fun setBrightness(v: Float) {
        val clamped = v.coerceIn(0.05f, 1f)
        sp.edit().putFloat("brightness", clamped).apply()
        _brightness.value = clamped
    }

    /** TXT 본문 글자 크기 (sp) */
    private val _textSize = MutableStateFlow(sp.getFloat("text_size", 18f))
    val textSize: StateFlow<Float> = _textSize.asStateFlow()

    fun setTextSize(v: Float) {
        val clamped = v.coerceIn(12f, 34f)
        sp.edit().putFloat("text_size", clamped).apply()
        _textSize.value = clamped
    }

    /** TXT 종이색 */
    private val _textBackground = MutableStateFlow(enum("text_background", TextBackground.GRAY))
    val textBackground: StateFlow<TextBackground> = _textBackground.asStateFlow()

    fun setTextBackground(v: TextBackground) = put("text_background", v, _textBackground)

    /** 자간. 글자 크기에 대한 비율(em) 이라 글자를 키워도 균형이 유지된다 */
    private val _letterSpacing = MutableStateFlow(sp.getFloat("letter_spacing", 0f))
    val letterSpacing: StateFlow<Float> = _letterSpacing.asStateFlow()

    fun setLetterSpacing(v: Float) {
        val clamped = v.coerceIn(-0.05f, 0.30f)
        sp.edit().putFloat("letter_spacing", clamped).apply()
        _letterSpacing.value = clamped
    }

    /** 줄 간격(세로 자간). 글자 크기에 대한 배수 */
    private val _lineSpacing = MutableStateFlow(sp.getFloat("line_spacing", 1.75f))
    val lineSpacing: StateFlow<Float> = _lineSpacing.asStateFlow()

    fun setLineSpacing(v: Float) {
        val clamped = v.coerceIn(1.0f, 2.8f)
        sp.edit().putFloat("line_spacing", clamped).apply()
        _lineSpacing.value = clamped
    }

    /** 내려받아 둔 ttf 를 쓸지, 시스템 폰트를 쓸지 */
    private val _useCustomFont = MutableStateFlow(sp.getBoolean("use_custom_font", false))
    val useCustomFont: StateFlow<Boolean> = _useCustomFont.asStateFlow()

    private val _customFontName = MutableStateFlow(sp.getString("custom_font_name", "") ?: "")
    val customFontName: StateFlow<String> = _customFontName.asStateFlow()

    fun setUseCustomFont(v: Boolean) = put("use_custom_font", v, _useCustomFont)

    fun setCustomFontName(v: String) {
        sp.edit().putString("custom_font_name", v).apply()
        _customFontName.value = v
    }

    /** 책장을 종류(폴더 / 만화·이미지 / PDF / 텍스트)별로 나눠 보기 */
    private val _groupByKind = MutableStateFlow(sp.getBoolean("group_by_kind", false))
    val groupByKind: StateFlow<Boolean> = _groupByKind.asStateFlow()

    fun setGroupByKind(v: Boolean) = put("group_by_kind", v, _groupByKind)

    // ---------------------------------------------------------------- 업데이트

    private val _autoUpdate = MutableStateFlow(sp.getBoolean("auto_update", true))
    val autoUpdate: StateFlow<Boolean> = _autoUpdate.asStateFlow()

    fun setAutoUpdate(v: Boolean) = put("auto_update", v, _autoUpdate)

    // ---------------------------------------------------------------- 내부

    private inline fun <reified T : Enum<T>> enum(key: String, fallback: T): T {
        val raw = sp.getString(key, null) ?: return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }

    private fun <T : Enum<T>> put(key: String, v: T, flow: MutableStateFlow<T>) {
        sp.edit().putString(key, v.name).apply()
        flow.value = v
    }

    private fun put(key: String, v: Boolean, flow: MutableStateFlow<Boolean>) {
        sp.edit().putBoolean(key, v).apply()
        flow.value = v
    }
}
