package kr.neptune.simplebook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kr.neptune.simplebook.core.AppUpdater
import kr.neptune.simplebook.ui.ReaderScreen
import kr.neptune.simplebook.ui.SettingsScreen
import kr.neptune.simplebook.ui.ShelfScreen
import kr.neptune.simplebook.ui.MainViewModel
import kr.neptune.simplebook.ui.SimpleBookTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            SimpleBookTheme {
                val vm: MainViewModel = viewModel()
                val reading by vm.reading.collectAsStateWithLifecycle()
                val immersive by vm.prefs.immersive.collectAsStateWithLifecycle()
                var settingsOpen by remember { mutableStateOf(false) }

                BackHandler {
                    when {
                        settingsOpen -> settingsOpen = false
                        !vm.back() -> finish()
                    }
                }

                // 읽는 동안에는 상태바/내비바를 걷어낸다. 만화는 화면을 다 쓰는 편이 낫다
                val view = LocalView.current
                LaunchedEffect(reading, immersive) {
                    val controller = WindowInsetsControllerCompat(window, view)
                    if (reading != null && immersive) {
                        controller.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }

                Surface(color = MaterialTheme.colorScheme.background) {
                    val book = reading
                    when {
                        settingsOpen -> SettingsScreen(vm) { settingsOpen = false }
                        book != null -> ReaderScreen(vm, book) { vm.closeBook() }
                        else -> ShelfScreen(vm) { settingsOpen = true }
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
