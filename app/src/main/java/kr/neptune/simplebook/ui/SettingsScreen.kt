package kr.neptune.simplebook.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kr.neptune.simplebook.core.AppUpdater
import kr.neptune.simplebook.core.ReadDirection
import kr.neptune.simplebook.core.SpreadMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val direction by vm.prefs.direction.collectAsStateWithLifecycle()
    val spread by vm.prefs.spread.collectAsStateWithLifecycle()
    val threshold by vm.prefs.spreadThreshold.collectAsStateWithLifecycle()
    val coverAlone by vm.prefs.coverAlone.collectAsStateWithLifecycle()
    val tapToTurn by vm.prefs.tapToTurn.collectAsStateWithLifecycle()
    val keepScreenOn by vm.prefs.keepScreenOn.collectAsStateWithLifecycle()
    val immersive by vm.prefs.immersive.collectAsStateWithLifecycle()
    val autoUpdate by vm.prefs.autoUpdate.collectAsStateWithLifecycle()
    val updateState by AppUpdater.state.collectAsStateWithLifecycle()

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

            SectionTitle("기본 보기 방식")
            Caption("책마다 따로 바꾼 설정이 있으면 그쪽이 우선합니다. 읽는 화면의 ⚙︎ 에서 바꿉니다.")

            Spacer(Modifier.height(4.dp))
            Text("넘기는 방향", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadDirection.entries.forEach { d ->
                    FilterChip(
                        selected = d == direction,
                        onClick = { vm.prefs.setDirection(d) },
                        label = { Text(d.label) },
                    )
                }
            }
            Caption(direction.hint)

            Spacer(Modifier.height(8.dp))
            Text("한 화면에 몇 쪽", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpreadMode.entries.forEach { s ->
                    FilterChip(
                        selected = s == spread,
                        onClick = { vm.prefs.setSpread(s) },
                        label = { Text(s.label) },
                    )
                }
            }

            if (spread == SpreadMode.AUTO) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "2쪽으로 바뀌는 기준  가로÷세로 ${"%.2f".format(threshold)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = threshold,
                    onValueChange = { vm.prefs.setSpreadThreshold((it * 20).roundToInt() / 20f) },
                    valueRange = 0.7f..1.6f,
                )
                Caption(
                    "낮출수록 쉽게 2쪽이 됩니다. 1.00 이면 가로가 세로보다 길 때 — " +
                        "폴드를 펴고 가로로 들었을 때가 여기 해당합니다. " +
                        "펼친 채 세로로 들었을 때도 2쪽으로 보고 싶으면 0.85 근처로 내려 보세요."
                )
            }

            Spacer(Modifier.height(8.dp))
            ToggleRow("표지는 혼자 두기", "2쪽 보기에서 1쪽만 단독으로 띄워 종이책 펼침면과 짝을 맞춥니다", coverAlone) {
                vm.prefs.setCoverAlone(it)
            }
            ToggleRow("화면 좌우 탭으로 넘기기", "끄면 탭이 메뉴 열기로만 동작합니다", tapToTurn) {
                vm.prefs.setTapToTurn(it)
            }
            ToggleRow("읽는 동안 화면 켜두기", null, keepScreenOn) { vm.prefs.setKeepScreenOn(it) }
            ToggleRow("읽을 때 상태바 숨기기", null, immersive) { vm.prefs.setImmersive(it) }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            SectionTitle("앱 업데이트")
            Text(
                "현재 버전 ${AppUpdater.currentVersionName} (${AppUpdater.currentVersionCode})",
                style = MaterialTheme.typography.bodyMedium,
            )
            ToggleRow("시작할 때 새 버전 확인", null, autoUpdate) { vm.prefs.setAutoUpdate(it) }

            when (val s = updateState) {
                is AppUpdater.State.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.height(18.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("  확인 중…", style = MaterialTheme.typography.bodySmall)
                }

                is AppUpdater.State.UpToDate ->
                    Caption("최신 버전입니다")

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

                is AppUpdater.State.Failed ->
                    Caption("확인하지 못했습니다: ${s.message}")

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

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            SectionTitle("정리")
            OutlinedButton(onClick = { vm.clearCovers() }) { Text("표지·목록 캐시 비우기") }
            Caption("표지가 예전 것으로 남아 있거나 목록이 실제와 다를 때 씁니다. 책은 지워지지 않습니다.")

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            SectionTitle("읽을 수 있는 형식")
            Caption(
                "ZIP · CBZ, RAR · CBR(RAR4), PDF, TXT, 그리고 이미지가 들어 있는 폴더.\n" +
                    "RAR5 로 압축된 파일은 아직 읽지 못합니다."
            )
        }
    }
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
