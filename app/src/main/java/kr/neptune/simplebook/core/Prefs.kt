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

    private val _direction = MutableStateFlow(enum("direction", ReadDirection.RTL))
    val direction: StateFlow<ReadDirection> = _direction.asStateFlow()

    private val _spread = MutableStateFlow(enum("spread", SpreadMode.AUTO))
    val spread: StateFlow<SpreadMode> = _spread.asStateFlow()

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

    fun setDirection(v: ReadDirection) = put("direction", v, _direction)
    fun setSpread(v: SpreadMode) = put("spread", v, _spread)
    fun setSpreadThreshold(v: Float) {
        sp.edit().putFloat("spread_threshold", v).apply()
        _spreadThreshold.value = v
    }
    fun setCoverAlone(v: Boolean) = put("cover_alone", v, _coverAlone)
    fun setTapToTurn(v: Boolean) = put("tap_to_turn", v, _tapToTurn)
    fun setKeepScreenOn(v: Boolean) = put("keep_screen_on", v, _keepScreenOn)
    fun setImmersive(v: Boolean) = put("immersive", v, _immersive)

    // ---------------------------------------------------------------- 화면

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
