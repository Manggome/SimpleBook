package kr.neptune.simplebook.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
// record / drawLayer 는 같은 패키지의 확장 함수라 통째로 들여온다
import androidx.compose.ui.graphics.layer.*
import androidx.compose.ui.graphics.rememberGraphicsLayer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 넘어가는 종이 한 장. 원통으로 휘어 들린다.
 *
 * 페이지를 통째로 3D 회전시키면 판때기가 도는 것처럼 보인다. 실제 종이는 책등 쪽은
 * 거의 평평하고 바깥으로 갈수록 많이 휜다. 그래서 페이지를 세로로 잘라, 조각마다
 * 원통 위의 제 위치로 옮겨 그린다.
 *
 * 조각마다 화면을 다시 그리면 감당이 안 되므로, 페이지를 [GraphicsLayer] 에 한 번
 * 기록해 두고 그 기록을 조각 수만큼 다시 재생한다. 재생은 이미 만들어 둔 그리기
 * 명령을 다시 트는 것이라 값이 싸다.
 *
 * @param lifted 0 이면 평평하고 1 이면 다 넘어간 상태
 * @param paper  종이 바탕색. 이게 없으면 아래 장의 글씨가 비쳐 보인다
 */
@Composable
fun CurlingPage(
    lifted: Float,
    rtl: Boolean,
    paper: Color,
    content: @Composable () -> Unit,
) {
    val layer = rememberGraphicsLayer()

    Box(Modifier.fillMaxSize()) {
        // 기록만 하고 화면에는 그리지 않는다.
        // drawWithContent 가 배경보다 앞에 와야 배경까지 함께 기록된다.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent { layer.record { this@drawWithContent.drawContent() } }
                .background(paper)
        ) {
            content()
        }

        Canvas(Modifier.fillMaxSize()) { drawCurl(layer, lifted, rtl) }
    }
}

/** 세로로 자르는 조각 수. 늘릴수록 매끈하지만 재생 비용이 붙는다 */
private const val STRIPS = 16

private fun DrawScope.drawCurl(layer: GraphicsLayer, lifted: Float, rtl: Boolean) {
    val t = lifted.coerceIn(0f, 1f)
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return

    if (t <= 0.001f) {
        layer.alpha = 1f
        drawLayer(layer)
        return
    }

    // 종이 전체가 휘는 각. 반 바퀴(π)까지 말리면 책등 쪽에 둥글게 감긴 모양이 된다
    val bend = (PI.toFloat() * t).coerceAtLeast(0.0005f)
    val radius = width / bend
    val camera = width * 2.6f

    // 다 말린 뒤에는 조용히 사라진다. 안 그러면 책등 쪽에 뭉친 채로 툭 끊긴다
    layer.alpha = if (t > 0.80f) ((1f - t) / 0.20f).coerceIn(0f, 1f) else 1f

    val spine = if (rtl) width else 0f

    /** 원본 x 가 화면에서 어디에 놓이는지, 그리고 그 자리의 원근 축소율 */
    fun project(sourceX: Float): Pair<Float, Float> {
        // 책등에서부터 잰 종이 길이
        val along = if (rtl) width - sourceX else sourceX
        val theta = along / radius
        val flat = radius * sin(theta)
        val depth = radius * (1f - cos(theta))
        val shrink = camera / (camera + depth)
        val x = spine + (if (rtl) -flat else flat) * shrink
        return x to shrink
    }

    for (i in 0 until STRIPS) {
        val x0 = width * i / STRIPS
        val x1 = width * (i + 1) / STRIPS
        val (screen0, shrink0) = project(x0)
        val (screen1, shrink1) = project(x1)

        val sourceSpan = x1 - x0
        if (sourceSpan <= 0f) continue
        val scaleX = (screen1 - screen0) / sourceSpan
        val scaleY = (shrink0 + shrink1) * 0.5f
        val shiftX = screen0 - scaleX * x0

        // 조각 가운데의 기울기로 밝기를 정한다. 책등 쪽이 우리를 보고 있어 가장 밝다
        val along = if (rtl) width - (x0 + x1) * 0.5f else (x0 + x1) * 0.5f
        val facing = cos(along / radius)
        val shade = (((1f - facing) * 0.5f) * 0.62f * (t * 3f).coerceAtMost(1f))
            .coerceIn(0f, 0.62f)

        withTransform({
            translate(left = shiftX, top = 0f)
            scale(scaleX = scaleX, scaleY = scaleY, pivot = Offset(0f, height * 0.5f))
        }) {
            clipRect(left = x0, top = 0f, right = x1, bottom = height) {
                drawLayer(layer)
                if (shade > 0.001f) {
                    drawRect(
                        color = Color.Black.copy(alpha = shade),
                        topLeft = Offset(x0, 0f),
                        size = Size(sourceSpan, height),
                    )
                }
            }
        }
    }
}
