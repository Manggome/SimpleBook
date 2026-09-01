package kr.neptune.simplebook.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 종이와 나무 책장을 떠올리게 하는 따뜻한 색. 읽는 화면은 어느 테마에서든 어둡게 둔다.
private val Accent = Color(0xFFC89A63)

private val Dark = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF231A10),
    secondary = Color(0xFFB9A48A),
    background = Color(0xFF111113),
    onBackground = Color(0xFFE9E6E2),
    // 표면은 중성 회색에 가깝게 둔다. 갈색을 섞으면 패널이 누렇게 보인다
    surface = Color(0xFF191A1C),
    onSurface = Color(0xFFE9E6E2),
    surfaceVariant = Color(0xFF26282B),
    onSurfaceVariant = Color(0xFFBCBAB6),
    outline = Color(0xFF4E5054),
)

private val Light = lightColorScheme(
    primary = Color(0xFF8A5F2B),
    onPrimary = Color.White,
    secondary = Color(0xFF6F6155),
    background = Color(0xFFFBF6EF),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFEDE3D6),
    onSurfaceVariant = Color(0xFF4E453C),
    outline = Color(0xFFB6A895),
)

/** 읽는 화면 배경. 밝은 테마에서도 만화는 검은 바탕이 눈에 편하다 */
val ReaderBackground = Color(0xFF0B0A09)

@Composable
fun SimpleBookTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = Typography(),
        content = content,
    )
}
