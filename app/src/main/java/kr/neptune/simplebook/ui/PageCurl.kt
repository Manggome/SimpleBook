package kr.neptune.simplebook.ui

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
// record / drawLayer / toImageBitmap 은 같은 패키지의 확장 함수라 통째로 들여온다
import androidx.compose.ui.graphics.layer.*
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** 가로로 나눌 칸 수. 촘촘할수록 곡면이 매끈하다 */
private const val MESH_COLUMNS = 48

/** 세로로 나눌 칸 수. 접히는 선이 기울어지려면 세로도 나뉘어 있어야 한다 */
private const val MESH_ROWS = 24

/** 명암 텍스처 크기. 같은 메시에 씌우므로 작아도 부드럽게 늘어난다 */
private const val SHADE_W = 64
private const val SHADE_H = 32

/** 다 넘어갔을 때 책등 축으로 돌아간 각 */
private const val SWING_MAX = 1.75f

/** 넘김 중간에서 종이가 휘는 정도 */
private const val BEND_MAX = 0.80f

/** 잡은 자리에서 먼 쪽이 얼마나 늦게 따라오는지. 이것 때문에 접힌 선이 기운다 */
private const val LAG = 0.30f

/**
 * 넘어가는 종이 한 장.
 *
 * 페이지를 가로 [MESH_COLUMNS] × 세로 [MESH_ROWS] 격자로 잘라 꼭짓점을 원통 위
 * 제자리로 옮긴다(drawBitmapMesh). 세로로도 나뉘어 있어서 **손가락을 댄 높이가
 * 먼저 들리고 먼 쪽이 늦게 따라온다** — 접히는 선이 기울어 종이를 쓸어 넘기는
 * 모양이 된다. 세로 조각으로 자르던 이전 방식은 접힌 선이 늘 수직이라 판때기가
 * 도는 것처럼 보였고, 조각 경계도 자글거렸다.
 *
 * 격자를 씌우려면 비트맵이 필요한데, 넘어가는 동안 내용은 바뀌지 않으므로
 * 시작할 때 [GraphicsLayer] 를 한 장 떠서 그것만 계속 구부린다.
 *
 * @param lifted 0 이면 평평하고 1 이면 다 넘어간 상태
 * @param grabY  손가락을 댄 세로 위치 (0 위 ~ 1 아래)
 * @param paper  종이 바탕색. 이게 없으면 아래 장의 글씨가 비쳐 보인다
 */
@Composable
fun CurlingPage(
    lifted: Float,
    rtl: Boolean,
    grabY: Float,
    paper: Color,
    content: @Composable () -> Unit,
) {
    val layer = rememberGraphicsLayer()
    var sheet by remember { mutableStateOf<Bitmap?>(null) }

    val mesh = remember { FloatArray((MESH_COLUMNS + 1) * (MESH_ROWS + 1) * 2) }
    val shadePixels = remember { IntArray(SHADE_W * SHADE_H) }
    val shadeBitmap = remember {
        Bitmap.createBitmap(SHADE_W, SHADE_H, Bitmap.Config.ARGB_8888)
    }
    val paint = remember { Paint().apply { isAntiAlias = true; isFilterBitmap = true } }

    // 레이어가 한 번 그려진 뒤라야 떠낼 수 있다. 두 프레임 기다린다
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        sheet = runCatching { layer.toImageBitmap().asAndroidBitmap() }.getOrNull()
    }

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

        Canvas(Modifier.fillMaxSize()) {
            val bitmap = sheet
            if (bitmap == null || bitmap.isRecycled || lifted <= 0.001f) {
                // 아직 떠내기 전이거나 평평한 상태 — 그냥 그대로 그린다
                drawLayer(layer)
                return@Canvas
            }
            drawCurledSheet(bitmap, shadeBitmap, shadePixels, mesh, paint, lifted, rtl, grabY)
        }
    }
}

private fun DrawScope.drawCurledSheet(
    sheet: Bitmap,
    shadeBitmap: Bitmap,
    shadePixels: IntArray,
    mesh: FloatArray,
    paint: Paint,
    lifted: Float,
    rtl: Boolean,
    grabY: Float,
) {
    val t = lifted.coerceIn(0f, 1f)
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return

    val camera = width * 2.6f
    val spine = if (rtl) width else 0f
    val centerY = height * 0.5f
    val grab = grabY.coerceIn(0f, 1f)
    val reach = max(grab, 1f - grab).coerceAtLeast(0.001f)

    /** 이 행이 얼마나 넘어갔는지. 잡은 자리에서 멀수록 늦게 시작한다 */
    fun rowProgress(v: Float): Float {
        val lag = LAG * abs(v - grab) / reach
        return ((t - lag) / (1f - lag).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
    }

    var index = 0
    for (row in 0..MESH_ROWS) {
        val v = row / MESH_ROWS.toFloat()
        val sourceY = v * height

        val local = rowProgress(v)
        val swing = SWING_MAX * local
        val bend = BEND_MAX * sin(PI.toFloat() * local)
        val flat = bend < 0.002f
        val curvature = bend / width

        for (column in 0..MESH_COLUMNS) {
            val u = column / MESH_COLUMNS.toFloat()
            val sourceX = u * width
            val along = if (rtl) width - sourceX else sourceX
            val theta = swing + if (flat) 0f else curvature * along

            // 책등에서부터의 가로 거리와, 화면 앞쪽으로 들린 높이
            val across: Float
            val raised: Float
            if (flat) {
                across = along * cos(swing)
                raised = along * sin(swing)
            } else {
                across = (sin(theta) - sin(swing)) / curvature
                raised = (cos(swing) - cos(theta)) / curvature
            }

            // 들린 쪽이 커 보여야 종이가 이쪽으로 넘어오는 것처럼 보인다
            val near = camera / (camera - raised).coerceAtLeast(camera * 0.35f)

            mesh[index++] = spine + (if (rtl) -across else across) * near
            mesh[index++] = centerY + (sourceY - centerY) * near
        }
    }

    val fade = if (t > 0.86f) ((1f - t) / 0.14f).coerceIn(0f, 1f) else 1f

    drawIntoCanvas { canvas ->
        paint.alpha = (fade * 255f).toInt().coerceIn(0, 255)
        canvas.nativeCanvas.drawBitmapMesh(
            sheet, MESH_COLUMNS, MESH_ROWS, mesh, 0, null, 0, paint,
        )

        // 명암도 같은 격자에 씌운다. 종이가 휘는 대로 그늘이 따라 붙는다
        fillShade(shadePixels, rtl, t, grab, reach)
        shadeBitmap.setPixels(shadePixels, 0, SHADE_W, 0, 0, SHADE_W, SHADE_H)
        paint.alpha = (fade * 255f).toInt().coerceIn(0, 255)
        canvas.nativeCanvas.drawBitmapMesh(
            shadeBitmap, MESH_COLUMNS, MESH_ROWS, mesh, 0, null, 0, paint,
        )
    }
}

/** 칸마다의 기울기를 검은 반투명 텍스처로 구워 둔다 */
private fun fillShade(
    pixels: IntArray,
    rtl: Boolean,
    t: Float,
    grab: Float,
    reach: Float,
) {
    var at = 0
    for (row in 0 until SHADE_H) {
        val v = row / (SHADE_H - 1f)
        val lag = LAG * abs(v - grab) / reach
        val local = ((t - lag) / (1f - lag).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
        val swing = SWING_MAX * local
        val bend = BEND_MAX * sin(PI.toFloat() * local)

        for (column in 0 until SHADE_W) {
            val u = column / (SHADE_W - 1f)
            val along = if (rtl) 1f - u else u
            val facing = cos(swing + bend * along)
            val shade = ((1f - facing) * 0.5f * 0.55f).coerceIn(0f, 0.55f)
            pixels[at++] = ((shade * 255f).toInt().coerceIn(0, 255) shl 24)
        }
    }
}
