package kr.neptune.simplebook.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * `< 5초 >` 처럼 화살표로 값을 올리고 내린다. 가운데 숫자를 누르면 직접 넣는다.
 *
 * 슬라이더는 몇 초인지 정확히 맞추기가 어렵다. 초 단위처럼 값이 몇 개 안 되고
 * 정확한 숫자가 중요한 설정은 이쪽이 낫다.
 */
@Composable
fun NumberStepper(
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    step: Int = 1,
    suffix: String = "",
    label: String = "값",
) {
    var editing by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onChange((value - step).coerceIn(range)) },
            enabled = value > range.first,
        ) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "줄이기")
        }
        Surface(
            onClick = { editing = true },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                "$value$suffix",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            )
        }
        IconButton(
            onClick = { onChange((value + step).coerceIn(range)) },
            enabled = value < range.last,
        ) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "늘리기")
        }
    }

    if (editing) {
        NumberDialog(
            title = label,
            initial = value,
            range = range,
            onConfirm = {
                onChange(it.coerceIn(range))
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun NumberDialog(
    title: String,
    initial: Int,
    range: IntRange,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf(initial.toString()) }
    val parsed = input.toIntOrNull()
    val valid = parsed != null && parsed in range

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { new -> input = new.filter { it.isDigit() }.take(5) },
                    singleLine = true,
                    isError = input.isNotEmpty() && !valid,
                    label = { Text("${range.first} ~ ${range.last}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = valid) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** 지금 세로로 들고 있는지 */
@Composable
fun isPortrait(): Boolean =
    LocalConfiguration.current.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE

/**
 * 기기가 알려 주는 카메라 구멍 여백. Compose 가 소비하기 전의 원본을 본다.
 * 방향에 따라 값이 제각각이라 사람이 눈으로 확인할 수 있게 그대로 보여 준다.
 */
@Composable
fun detectedCutout(): androidx.core.graphics.Insets? {
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    return remember(configuration, view) {
        ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.displayCutout())
    }
}

/** 감지된 여백 중 가장 큰 값. "감지값으로 맞추기" 가 쓴다 */
@Composable
fun detectedCutoutDp(): Int {
    val insets = detectedCutout() ?: return 0
    val density = LocalDensity.current
    return with(density) {
        maxOf(insets.top, insets.bottom, insets.left, insets.right).toDp().value.toInt()
    }
}

@Composable
fun detectedCutoutText(): String {
    val insets = detectedCutout() ?: return "감지하지 못했습니다"
    val density = LocalDensity.current
    return with(density) {
        "위 ${insets.top.toDp().value.toInt()} · 아래 ${insets.bottom.toDp().value.toInt()} · " +
            "왼 ${insets.left.toDp().value.toInt()} · 오 ${insets.right.toDp().value.toInt()} dp"
    }
}
