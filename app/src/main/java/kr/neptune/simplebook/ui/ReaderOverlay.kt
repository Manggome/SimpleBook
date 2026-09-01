package kr.neptune.simplebook.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

/**
 * 읽는 동안 화면 맨 위에 얇게 얹는 정보 줄.
 *
 * 상태바를 숨기고 읽으면 시계와 배터리를 볼 수 없어서, 필요한 것만 골라 여기에 띄운다.
 * 종이색 밝기를 보고 알약 배경과 글자색을 뒤집으므로 만화든 소설이든 읽힌다.
 */
@Composable
fun ReaderStatusOverlay(
    title: String,
    page: Int,
    total: Int,
    showClock: Boolean,
    showBattery: Boolean,
    showTitle: Boolean,
    showPage: Boolean,
    paper: Color,
    modifier: Modifier = Modifier,
) {
    if (!showClock && !showBattery && !showTitle && !showPage) return

    val context = LocalContext.current
    var clock by remember { mutableStateOf("") }
    var battery by remember { mutableIntStateOf(-1) }

    LaunchedEffect(showClock, showBattery) {
        if (!showClock && !showBattery) return@LaunchedEffect
        while (true) {
            if (showClock) clock = DateFormat.getTimeFormat(context).format(Date())
            if (showBattery) battery = batteryPercent(context)
            delay(20_000)
        }
    }

    val onDark = paper.luminance() < 0.5f
    val pill = if (onDark) Color.Black.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.62f)
    val ink = if (onDark) Color(0xFFEDE6DC) else Color(0xFF221E1A)

    val left = buildString {
        if (showClock && clock.isNotEmpty()) append(clock)
        if (showBattery && battery >= 0) {
            if (isNotEmpty()) append("   ")
            append(battery).append('%')
        }
    }

    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .displayCutoutPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (left.isNotEmpty()) Pill(left, pill, ink)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (showTitle) Pill(title, pill, ink)
        }
        if (showPage && total > 0) Pill("${page + 1} / $total", pill, ink)
    }
}

@Composable
private fun Pill(text: String, background: Color, ink: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(6.dp), color = background) {
        Text(
            text,
            color = ink,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
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
