package kr.neptune.simplebook

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kr.neptune.simplebook.core.AppUpdater
import kr.neptune.simplebook.core.OrientationMode
import kr.neptune.simplebook.core.ThemeMode
import kr.neptune.simplebook.ui.ReaderScreen
import kr.neptune.simplebook.ui.SettingsScreen
import kr.neptune.simplebook.ui.ShelfScreen
import kr.neptune.simplebook.ui.MainViewModel
import kr.neptune.simplebook.ui.SimpleBookTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 창을 카메라 구멍 아래까지 펼친다. 구멍을 피할지 말지는 앱이 직접 정한다.
        // SHORT_EDGES 는 가로로 돌리면 구멍이 긴 변에 오면서 시스템이 창을 물려 버려
        // 앱이 손댈 여지가 없어진다. ALWAYS 는 어느 방향이든 창을 끝까지 펼친다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        setContent {
            val vm: MainViewModel = viewModel()
            val theme by vm.prefs.theme.collectAsStateWithLifecycle()
            val dark = when (theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            SimpleBookTheme(dark = dark) {
                val reading by vm.reading.collectAsStateWithLifecycle()
                val immersive by vm.prefs.immersive.collectAsStateWithLifecycle()
                val orientation by vm.prefs.orientation.collectAsStateWithLifecycle()
                val systemBrightness by vm.prefs.systemBrightness.collectAsStateWithLifecycle()
                val brightness by vm.prefs.brightness.collectAsStateWithLifecycle()
                var settingsOpen by remember { mutableStateOf(false) }

                // USER_ 계열을 쓰면 폰의 회전 잠금을 존중하면서 고정할 수 있다
                LaunchedEffect(orientation) {
                    requestedOrientation = when (orientation) {
                        OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                        OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                    }
                }

                // -1 은 "시스템 밝기를 따른다" 는 뜻이다
                LaunchedEffect(systemBrightness, brightness) {
                    window.attributes = window.attributes.apply {
                        screenBrightness =
                            if (systemBrightness) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                            else brightness.coerceIn(0.05f, 1f)
                    }
                }

                BackHandler {
                    when {
                        settingsOpen -> settingsOpen = false
                        !vm.back() -> finish()
                    }
                }

                // 읽는 동안에는 상태바/내비바를 걷어낸다. 만화는 화면을 다 쓰는 편이 낫다
                val view = LocalView.current
                // 정보 줄은 상태바가 있던 자리에 놓인다. 둘 다 켜면 겹치므로 이때는 늘 숨긴다
                val readerInfo by vm.prefs.readerInfo.collectAsStateWithLifecycle()
                LaunchedEffect(reading, immersive, readerInfo) {
                    val controller = WindowInsetsControllerCompat(window, view)
                    if (reading != null && (immersive || readerInfo)) {
                        controller.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }

                // 카메라 구멍을 피할 때는 그 자리를 검게 두고 앱을 그만큼 내린다
                val avoidCutout by vm.prefs.avoidCutout.collectAsStateWithLifecycle()
                val cutoutExtra by vm.prefs.cutoutExtra.collectAsStateWithLifecycle()

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            if (avoidCutout) Color.Black else MaterialTheme.colorScheme.background
                        )
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (avoidCutout) {
                                    Modifier
                                        .displayCutoutPadding()
                                        .padding(top = cutoutExtra.dp)
                                } else {
                                    Modifier
                                }
                            ),
                    ) {
                        val book = reading
                        when {
                            settingsOpen -> SettingsScreen(vm) { settingsOpen = false }
                            book != null -> ReaderScreen(vm, book) { vm.closeBook() }
                            else -> ShelfScreen(vm) { settingsOpen = true }
                        }
                    }
                }

                UpdatePrompt(onOpenSettings = { settingsOpen = true })
            }
        }
    }
}

/** 시작할 때 새 버전을 찾으면 한 번 물어본다 */
@Composable
private fun UpdatePrompt(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val state by AppUpdater.state.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var dismissed by remember { mutableStateOf(false) }

    val available = state as? AppUpdater.State.Available ?: return
    if (dismissed) return

    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text("새 버전 ${available.release.versionName}") },
        text = {
            Text(
                if (available.release.notes.isBlank()) "지금 받아서 설치할까요?"
                else available.release.notes + "\n\n지금 받아서 설치할까요?"
            )
        },
        confirmButton = {
            TextButton(onClick = {
                dismissed = true
                onOpenSettings()
                scope.launch { AppUpdater.download(context) }
            }) { Text("받기") }
        },
        dismissButton = {
            TextButton(onClick = {
                dismissed = true
                AppUpdater.dismiss()
            }) { Text("나중에") }
        },
    )
}
