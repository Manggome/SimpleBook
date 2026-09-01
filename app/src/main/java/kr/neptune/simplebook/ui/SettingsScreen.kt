package kr.neptune.simplebook.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kr.neptune.simplebook.R
import kr.neptune.simplebook.core.AppUpdater
import kr.neptune.simplebook.core.Fonts
import kr.neptune.simplebook.core.FormatGroup
import kr.neptune.simplebook.core.OrientationMode
import kr.neptune.simplebook.core.PageEffect
import kr.neptune.simplebook.core.ReadDirection
import kr.neptune.simplebook.core.SpreadMode
import kr.neptune.simplebook.core.TextBackground
import kr.neptune.simplebook.core.ThemeMode
import kotlin.math.roundToInt

/**
 * 전역 설정.
 *
 * 항목이 늘면서 비슷한 것들이 흩어져 있던 것을 다섯 묶음으로 정리했다 —
 * 책장 / 읽기 / 텍스트 책 / 화면 / 앱.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = vm.prefs

    // 책장
    val groupByKind by prefs.groupByKind.collectAsStateWithLifecycle()

    // 읽기
    val directions by prefs.directions.collectAsStateWithLifecycle()
    val spreads by prefs.spreads.collectAsStateWithLifecycle()
    var group by remember { mutableStateOf(FormatGroup.COMIC) }
    val direction = directions[group] ?: group.defaultDirection
    val spread = spreads[group] ?: group.defaultSpread
    val threshold by prefs.spreadThreshold.collectAsStateWithLifecycle()
    val coverAlone by prefs.coverAlone.collectAsStateWithLifecycle()
    val pageEffect by prefs.pageEffect.collectAsStateWithLifecycle()
    val tapToTurn by prefs.tapToTurn.collectAsStateWithLifecycle()
    val autoTurn by prefs.autoTurn.collectAsStateWithLifecycle()
    val autoTurnSeconds by prefs.autoTurnSeconds.collectAsStateWithLifecycle()

    // 텍스트 책
    val textSize by prefs.textSize.collectAsStateWithLifecycle()
    val letterSpacing by prefs.letterSpacing.collectAsStateWithLifecycle()
    val lineSpacing by prefs.lineSpacing.collectAsStateWithLifecycle()
    val textBackground by prefs.textBackground.collectAsStateWithLifecycle()
    val useCustomFont by prefs.useCustomFont.collectAsStateWithLifecycle()
    val customFontName by prefs.customFontName.collectAsStateWithLifecycle()
    val fontRevision by Fonts.revision.collectAsStateWithLifecycle()
    val hasFont = remember(fontRevision) { vm.hasCustomFont() }

    // 화면
    val theme by prefs.theme.collectAsStateWithLifecycle()
    val orientation by prefs.orientation.collectAsStateWithLifecycle()
    val immersive by prefs.immersive.collectAsStateWithLifecycle()
    val avoidCutout by prefs.avoidCutout.collectAsStateWithLifecycle()
    val readerInfo by prefs.readerInfo.collectAsStateWithLifecycle()
    val readerInfoOffset by prefs.readerInfoOffset.collectAsStateWithLifecycle()
    val keepScreenOn by prefs.keepScreenOn.collectAsStateWithLifecycle()
    val systemBrightness by prefs.systemBrightness.collectAsStateWithLifecycle()
    val brightness by prefs.brightness.collectAsStateWithLifecycle()

    // 앱
    val autoUpdate by prefs.autoUpdate.collectAsStateWithLifecycle()
    val updateState by AppUpdater.state.collectAsStateWithLifecycle()
    var notesOpen by remember { mutableStateOf(false) }
    val changelog = remember {
        runCatching {
            context.resources.openRawResource(R.raw.changelog).bufferedReader().use { it.readText() }
        }.getOrDefault("패치노트를 읽지 못했습니다")
    }

    val fontPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.installFont(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                },
                title = { Text("설정") },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp, 8.dp, 20.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {

            // ---------------------------------------------------------- 책장
            SectionTitle("책장")
            ToggleRow(
                "종류별로 나눠 보기",
                "폴더 / 만화·이미지 / PDF / 텍스트 를 구분선으로 갈라 놓습니다",
                groupByKind,
            ) { prefs.setGroupByKind(it) }
            Caption("표지 바꾸기와 폴더 만들기는 책장에서 항목을 길게 눌러 씁니다.")

            Gap()

            // ---------------------------------------------------------- 읽기
            SectionTitle("읽기")
            Caption(
                "기본 보기 방식은 형식마다 따로 잡습니다. 만화는 우철에 2쪽, 소설은 좌철에 " +
                    "1쪽처럼 성격이 달라서입니다. 책 하나만 다르게 두려면 읽는 화면의 ⚙︎ 에서 바꾸세요."
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormatGroup.entries.forEach { g ->
                    FilterChip(
                        selected = g == group,
                        onClick = { group = g },
                        label = { Text(g.label) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("넘기는 방향", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadDirection.entries.forEach { d ->
                    FilterChip(
                        selected = d == direction,
                        onClick = { prefs.setDirection(group, d) },
                        label = { Text(d.label) },
                    )
                }
            }
            Caption(direction.hint)

            Spacer(Modifier.height(8.dp))
            Text("한 화면에 몇 쪽", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpreadMode.entries.forEach { m ->
                    FilterChip(
                        selected = m == spread,
                        onClick = { prefs.setSpread(group, m) },
                        label = { Text(m.label) },
                    )
                }
            }
            if (spread == SpreadMode.AUTO) {
                Text(
                    "2쪽으로 바뀌는 기준  가로÷세로 ${"%.2f".format(threshold)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = threshold,
                    onValueChange = { prefs.setSpreadThreshold((it * 20).roundToInt() / 20f) },
                    valueRange = 0.7f..1.6f,
                )
                Caption(
                    "낮출수록 쉽게 2쪽이 됩니다. 기본값 0.85 는 폴드를 펴면 세로로 들든 " +
                        "가로로 들든 2쪽이 되는 지점입니다."
                )
            }

            Spacer(Modifier.height(8.dp))
            Text("넘김 효과", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PageEffect.entries.forEach { e ->
                    FilterChip(
                        selected = e == pageEffect,
                        onClick = { prefs.setPageEffect(e) },
                        label = { Text(e.label) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            ToggleRow(
                "표지는 혼자 두기",
                "2쪽 보기에서 1쪽만 단독으로 띄워 종이책 펼침면과 짝을 맞춥니다",
                coverAlone,
            ) { prefs.setCoverAlone(it) }
            ToggleRow(
                "화면 좌우 탭으로 넘기기",
                "좌 25% / 우 25% 로 넘기고 가운데 50% 는 메뉴. 끄면 어디를 눌러도 메뉴만 열립니다",
                tapToTurn,
            ) { prefs.setTapToTurn(it) }
            ToggleRow(
                "자동으로 넘기기",
                "넘어가는 중에 화면을 누르면 멈춥니다. 계속할지 끌지 고를 수 있습니다",
                autoTurn,
            ) { prefs.setAutoTurn(it) }
            if (autoTurn) {
                Text(
                    "${autoTurnSeconds.toInt()}초에 한 번",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = autoTurnSeconds,
                    onValueChange = { prefs.setAutoTurnSeconds(it) },
                    valueRange = 2f..60f,
                    steps = 57,
                )
            }

            Gap()

            // ---------------------------------------------------------- 텍스트 책
            SectionTitle("텍스트 책 (TXT)")

            Text("글자 크기  ${textSize.toInt()}sp", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = textSize,
                onValueChange = { prefs.setTextSize(it) },
                valueRange = 12f..34f,
                steps = 21,
            )

            Text(
                "자간  ${if (letterSpacing >= 0) "+" else ""}${"%.2f".format(letterSpacing)}em",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = letterSpacing,
                onValueChange = { prefs.setLetterSpacing(it) },
                valueRange = -0.05f..0.30f,
                steps = 34,
            )

            Text(
                "줄 간격  ${"%.2f".format(lineSpacing)}배",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = lineSpacing,
                onValueChange = { prefs.setLineSpacing(it) },
                valueRange = 1.0f..2.8f,
                steps = 35,
            )

            Spacer(Modifier.height(4.dp))
            Text("종이색", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextBackground.entries.forEach { bg ->
                    FilterChip(
                        selected = bg == textBackground,
                        onClick = { prefs.setTextBackground(bg) },
                        label = { Text(bg.label) },
                        leadingIcon = {
                            Box(
                                Modifier
                                    .size(16.dp)
                                    .background(Color(bg.paper), RoundedCornerShape(3.dp))
                            )
                        },
                    )
                }
            }
            Caption("만화·PDF 는 어느 테마에서든 검은 바탕으로 봅니다.")

            Spacer(Modifier.height(8.dp))
            Text("글꼴", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !useCustomFont,
                    onClick = { prefs.setUseCustomFont(false) },
                    label = { Text("시스템 글꼴") },
                )
                FilterChip(
                    selected = useCustomFont,
                    onClick = { if (hasFont) prefs.setUseCustomFont(true) },
                    enabled = hasFont,
                    label = {
                        Text(if (hasFont) customFontName.ifBlank { "내 글꼴" } else "내 글꼴 (없음)")
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { fontPicker.launch(arrayOf("*/*")) }) {
                    Text(if (hasFont) "다른 글꼴 고르기" else "글꼴 파일 고르기")
                }
                if (hasFont) {
                    OutlinedButton(onClick = { vm.removeFont() }) { Text("지우기") }
                }
            }
            Caption("ttf / otf 파일을 고르면 앱 안에 복사해 둡니다. 원본을 지워도 글꼴은 남습니다.")

            Gap()

            // ---------------------------------------------------------- 화면
            SectionTitle("화면")

            Text("테마", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { t ->
                    FilterChip(
                        selected = t == theme,
                        onClick = { prefs.setTheme(t) },
                        label = { Text(t.label) },
                    )
                }
            }
            Caption("읽는 화면은 테마와 별개입니다. 만화는 검은 바탕, 소설은 고른 종이색.")

            Spacer(Modifier.height(8.dp))
            Text("회전", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrientationMode.entries.forEach { o ->
                    FilterChip(
                        selected = o == orientation,
                        onClick = { prefs.setOrientation(o) },
                        label = { Text(o.label) },
                    )
                }
            }
            Caption("자동은 폰의 회전 잠금 설정을 그대로 따릅니다.")

            Spacer(Modifier.height(8.dp))
            ToggleRow("읽을 때 상단바 숨기기", null, immersive) { prefs.setImmersive(it) }
            ToggleRow(
                "카메라 구멍 피하기",
                "구멍이 파고든 만큼 화면을 내리고 그 자리는 검게 둡니다",
                avoidCutout,
            ) { prefs.setAvoidCutout(it) }
            ToggleRow(
                "읽을 때 정보 줄 보기",
                "시계 · 배터리 % · 책 이름 · 현재 쪽/전체 쪽을 화면 위에 얇게 띄웁니다",
                readerInfo,
            ) { prefs.setReaderInfo(it) }
            if (readerInfo) {
                Text(
                    "정보 줄 위치  +${readerInfoOffset.toInt()}dp",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = readerInfoOffset,
                    onValueChange = { prefs.setReaderInfoOffset(it) },
                    valueRange = 0f..64f,
                    steps = 31,
                )
                Caption("기본은 안전 영역 바로 아래입니다. 더 내리고 싶으면 값을 올리세요.")
            }
            ToggleRow("읽는 동안 화면 켜두기", null, keepScreenOn) { prefs.setKeepScreenOn(it) }
            ToggleRow(
                "시스템 밝기 사용",
                "끄면 이 앱에서만 밝기를 따로 잡습니다",
                systemBrightness,
            ) { prefs.setSystemBrightness(it) }
            if (!systemBrightness) {
                Text(
                    "앱 밝기  ${(brightness * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = brightness,
                    onValueChange = { prefs.setBrightness(it) },
                    valueRange = 0.05f..1f,
                )
            }

            Gap()

            // ---------------------------------------------------------- 앱
            SectionTitle("앱")
            Text(
                "현재 버전 ${AppUpdater.currentVersionName} (${AppUpdater.currentVersionCode})",
                style = MaterialTheme.typography.bodyMedium,
            )
            ToggleRow("시작할 때 새 버전 확인", null, autoUpdate) { prefs.setAutoUpdate(it) }

            when (val s = updateState) {
                is AppUpdater.State.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.height(18.dp))
                    Text("  확인 중…", style = MaterialTheme.typography.bodySmall)
                }

                is AppUpdater.State.UpToDate -> Caption("최신 버전입니다")

                is AppUpdater.State.Available -> Column {
                    Text(
                        "새 버전 ${s.release.versionName} 이 있습니다",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (s.release.notes.isNotBlank()) Caption(s.release.notes)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { scope.launch { AppUpdater.download(context) } }) {
                        Text("받아서 설치")
                    }
                }

                is AppUpdater.State.Downloading -> Column {
                    Text("받는 중 ${s.percent}%", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = { s.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is AppUpdater.State.ReadyToInstall -> Button(
                    onClick = { AppUpdater.install(context, s.file) }
                ) { Text("설치 화면 열기") }

                is AppUpdater.State.Failed -> Caption("확인하지 못했습니다: ${s.message}")

                AppUpdater.State.Idle -> Unit
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scope.launch { AppUpdater.check(silent = false) } }) {
                    Text("지금 확인")
                }
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(AppUpdater.RELEASE_PAGE))
                    )
                }) { Text("릴리스 페이지") }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { notesOpen = !notesOpen }) {
                Text(if (notesOpen) "패치노트 접기" else "패치노트 보기")
            }
            if (notesOpen) {
                Text(
                    changelog.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.clearCovers() }) { Text("표지·목록 캐시 비우기") }
            Caption(
                "표지가 예전 것으로 남아 있거나 목록이 실제와 다를 때 씁니다. " +
                    "직접 지정한 표지와 책은 지워지지 않습니다."
            )

            Spacer(Modifier.height(8.dp))
            Caption(
                "읽을 수 있는 형식 — ZIP · CBZ, RAR · CBR(RAR4), PDF, TXT, " +
                    "그리고 이미지가 들어 있는 폴더. RAR5 는 아직 읽지 못합니다."
            )
        }
    }
}

@Composable
private fun Gap() {
    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) Caption(subtitle)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
