package kr.neptune.simplebook.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Date

/** 정보 줄에 띄울 것. 액티비티가 그리므로 리더가 여기에 담아 올려 준다 */
data class ReaderStatus(
    val title: String,
    val page: Int,
    val total: Int,
    val paper: Color,
)

/**
 * 읽는 동안 화면 맨 위에 얇게 얹는 정보 줄. 시계 · 배터리 · 책 이름 · 쪽수.
 *
 * 상태바를 숨기고 읽으면 시계와 배터리를 볼 수 없어서 필요하다.
 * 화면 맨 위에 딱 붙는다. 상태바가 있던 그 자리다 — 그래서 이 줄을 켜면 상태바는
 * 늘 숨긴 채로 둔다.
 *
 * 바탕은 이 줄이 놓인 자리의 색([backdrop])을 그대로 쓴다. 카메라 구멍을 피해
 * 화면을 내렸으면 그 자리는 검정이므로 검정으로 칠해 한 덩어리로 보이게 하고,
 * 아니면 종이색으로 칠해 페이지에 녹아들게 한다. 종이색으로 고정하면 검은 띠 위에
 * 회색 띠가 하나 더 얹혀 세 겹으로 보인다.
 *
 * [minHeightDp] 는 카메라 구멍 자리의 높이다. 그만큼 키워 가운데에 글자를 놓으면
 * 검은 띠와 정보 줄이 정확히 겹친다.
 */
@Composable
fun ReaderStatusOverlay(
    title: String,
    page: Int,
    total: Int,
    backdrop: Color,
    minHeightDp: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var clock by remember { mutableStateOf("") }
    var battery by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) {
        while (true) {
            clock = DateFormat.getTimeFormat(context).format(Date())
            battery = batteryPercent(context)
            delay(20_000)
        }
    }

    val onDark = backdrop.luminance() < 0.5f
    val ink = if (onDark) Color(0xFFC9A46A) else Color(0xFF6B5330)

    val left = buildString {
        if (clock.isNotEmpty()) append(clock)
        if (battery >= 0) {
            if (isNotEmpty()) append("   ")
            append(battery).append('%')
        }
    }

    Row(
        modifier
            .fillMaxWidth()
            .background(backdrop)
            .heightIn(min = minHeightDp.dp)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (left.isNotEmpty()) {
            Text(
                left,
                color = ink,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
        Text(
            title,
            color = ink,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (total > 0) {
            Text(
                "${page + 1} / $total",
                color = ink,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

/** 배터리 잔량. 권한 없이 마지막 방송을 읽어 온다 */
private fun batteryPercent(context: Context): Int {
    val status = runCatching {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }.getOrNull() ?: return -1
    val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    return if (level >= 0 && scale > 0) level * 100 / scale else -1
}
