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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 넘어가는 종이 한 장.
 *
 * 종이를 책등 축으로 돌리면서(swing) 동시에 부드럽게 휘게 한다(bend). 휘는 정도는
 * 넘김 중간에서 가장 크고 처음과 끝에서는 0 이라, 평평하게 놓였다가 들렸다가 다시
 * 평평해진다.
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

/** 세로로 자르는 조각 수. 적으면 경계가 각져 보인다 */
private const val STRIPS = 32

/** 다 넘어갔을 때 책등 축으로 돌아간 각 */
private const val SWING_MAX = 1.75f

/** 넘김 중간에서 종이가 휘는 정도 */
private const val BEND_MAX = 0.80f

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

    val swing = SWING_MAX * t
    // 처음과 끝에서는 평평하고 중간에서 가장 많이 휜다
    val bend = BEND_MAX * sin(PI.toFloat() * t)
    val flat = bend < 0.002f
    val curvature = bend / width
    val camera = width * 2.6f

    // 거의 다 넘어가면 조용히 사라진다. 안 그러면 책등 쪽에서 툭 끊긴다
    layer.alpha = if (t > 0.85f) ((1f - t) / 0.15f).coerceIn(0f, 1f) else 1f

    val spine = if (rtl) width else 0f

    /** 원본 x 가 화면 어디에 놓이는지와, 앞으로 들린 만큼의 확대율 */
    fun project(sourceX: Float): Pair<Float, Float> {
        val along = if (rtl) width - sourceX else sourceX
        val theta = swing + if (flat) 0f else curvature * along
        // 책등에서부터의 가로 거리와, 화면 앞쪽으로 들린 높이
        val across: Float
        val height3d: Float
        if (flat) {
            across = along * cos(swing)
            height3d = along * sin(swing)
        } else {
            across = (sin(theta) - sin(swing)) / curvature
            height3d = (cos(swing) - cos(theta)) / curvature
        }
        // 앞으로 들린 쪽이 커 보여야 종이가 이쪽으로 넘어오는 것처럼 보인다.
        // 반대로 잡으면 책 뒤로 말려 들어가는 것처럼 보인다.
        val near = camera / (camera - height3d).coerceAtLeast(camera * 0.35f)
        val screen = spine + (if (rtl) -across else across) * near
        return screen to near
    }

    for (i in 0 until STRIPS) {
        val x0 = width * i / STRIPS
        val x1 = width * (i + 1) / STRIPS
        val span = x1 - x0
        if (span <= 0f) continue

        val (screen0, near0) = project(x0)
        val (screen1, near1) = project(x1)

        val scaleX = (screen1 - screen0) / span
        val scaleY = (near0 + near1) * 0.5f
        val shiftX = screen0 - scaleX * x0

        // 조각 사이에 1px 틈이 생기면 자글자글해 보인다. 살짝 겹쳐서 덮는다
        val bleed = min(span * 0.5f, 0.9f / abs(scaleX).coerceAtLeast(0.05f))
        val clipLeft = (x0 - bleed).coerceAtLeast(0f)
        val clipRight = (x1 + bleed).coerceAtMost(width)

        val along = if (rtl) width - (x0 + x1) * 0.5f else (x0 + x1) * 0.5f
        val facing = cos(swing + if (flat) 0f else curvature * along)
        val shade = (((1f - facing) * 0.5f) * 0.55f * (t * 4f).coerceAtMost(1f))
            .coerceIn(0f, 0.55f)

        withTransform({
            translate(left = shiftX, top = 0f)
            scale(scaleX = scaleX, scaleY = scaleY, pivot = Offset(0f, height * 0.5f))
        }) {
            clipRect(left = clipLeft, top = 0f, right = clipRight, bottom = height) {
                drawLayer(layer)
                if (shade > 0.001f) {
                    drawRect(
                        color = Color.Black.copy(alpha = shade),
                        topLeft = Offset(clipLeft, 0f),
                        size = Size(clipRight - clipLeft, height),
                    )
                }
            }
        }
    }
}
