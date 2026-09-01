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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

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
