package com.cardoctor.apr.ui

import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.sqrt

/*
 * 차량 그림의 모양과 재료.
 *
 * 중형 패스트백 세단(쏘나타)의 비율로 그린다: 긴 보닛, 뒤로 길게 누운 뒤 유리, 짧은 트렁크,
 * 앞뒤를 가로지르는 램프. 사진이 아니라 코드로 그린 그림이다.
 *
 * 좌표는 차가 들어가는 사각형에 대한 비율이다. 가로는 가운데에서의 거리(dx, 오른쪽이 +),
 * 세로는 앞(0)에서 뒤(1), 높이는 차 길이에 대한 비율로 적는다.
 *
 * 그림자, 윗면, 지붕처럼 그리는 데 시간이 걸리는 부분은 [CarArt]가 한 번만 그려서 이미지로 들고 있고,
 * 화면을 그릴 때는 그 이미지를 높이에 맞춰 놓기만 한다. 차를 돌리는 동안 매번 다시 그리지 않기 위해서다.
 */

/** 차 사각형의 가로/세로 비. 사이드미러까지 포함한 폭이다. */
internal const val CAR_ASPECT = 0.44f

// 높이(차 길이에 대한 비율)
internal const val Z_FLOOR = 0.045f  // 차체 바닥
internal const val Z_BELT = 0.185f   // 허리선: 옆면의 꼭대기이자 유리가 시작되는 높이
internal const val Z_ROOF = 0.290f   // 지붕
internal const val WHEEL_R = 0.068f  // 바퀴 반지름
private const val ARCH_R = 0.083f    // 휠 하우스 반지름

/** 앞뒤 바퀴 축의 위치. */
internal val AXLES = listOf(0.19f, 0.77f)

/** 옆면을 쌓는 층 수와 유리를 쌓는 층 수. 많을수록 매끄럽지만 그릴 것이 늘어난다. */
private const val WALL_LAYERS = 48
internal const val GLASS_STEPS = 24

/** 3차 곡선 하나. x 값은 차 가운데에서의 거리. */
internal class Seg(val c1x: Float, val c1y: Float, val c2x: Float, val c2y: Float, val x: Float, val y: Float)

internal fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** 차 사각형 안의 좌표 계산. */
internal class Geo(val w: Float, val h: Float) {
    /** 가운데에서 dx만큼 떨어진 가로 위치. */
    fun x(dx: Float) = (0.5f + dx) * w
    fun at(dx: Float, y: Float) = Offset(x(dx), y * h)
}

/** 오른쪽 기준으로 만든 도형을 좌우 대칭으로 두 번 그린다. s는 +1(오른쪽), -1(왼쪽). */
internal inline fun bothSides(draw: (s: Float) -> Unit) {
    draw(1f)
    draw(-1f)
}

/**
 * 오른쪽 절반의 곡선들로 좌우 대칭인 닫힌 도형을 만든다.
 * 앞쪽 가운데(0, [startY])에서 시작해 오른쪽을 따라 내려간 뒤, 같은 곡선을 뒤집어 왼쪽으로 올라온다.
 */
private fun symmetricPath(g: Geo, startY: Float, right: List<Seg>): Path {
    val path = Path()
    path.moveTo(g.x(0f), startY * g.h)
    right.forEach {
        path.cubicTo(g.x(it.c1x), it.c1y * g.h, g.x(it.c2x), it.c2y * g.h, g.x(it.x), it.y * g.h)
    }
    for (i in right.indices.reversed()) {
        val seg = right[i]
        val endX = if (i == 0) 0f else right[i - 1].x
        val endY = if (i == 0) startY else right[i - 1].y
        path.cubicTo(g.x(-seg.c2x), seg.c2y * g.h, g.x(-seg.c1x), seg.c1y * g.h, g.x(-endX), endY * g.h)
    }
    path.close()
    return path
}

/** 차체 윤곽의 오른쪽 절반: 둥근 앞코 → 앞 펜더 → 옆면 → 뒤 펜더 → 둥근 뒤 모서리 → 꼬리. */
private val BODY = listOf(
    Seg(0.12f, 0.004f, 0.22f, 0.008f, 0.30f, 0.016f),
    Seg(0.37f, 0.022f, 0.395f, 0.045f, 0.405f, 0.075f),
    Seg(0.415f, 0.11f, 0.428f, 0.15f, 0.430f, 0.20f),
    Seg(0.432f, 0.30f, 0.426f, 0.40f, 0.428f, 0.50f),
    Seg(0.430f, 0.60f, 0.434f, 0.70f, 0.432f, 0.78f),
    Seg(0.430f, 0.85f, 0.420f, 0.91f, 0.400f, 0.945f),
    Seg(0.385f, 0.972f, 0.34f, 0.986f, 0.28f, 0.990f),
    Seg(0.20f, 0.994f, 0.10f, 0.996f, 0.0f, 0.996f)
)
private const val BODY_START_Y = 0.004f
/** 유리가 시작되는 윤곽(허리선 높이): 앞 유리 아래 → 옆 유리 바깥 → 뒤 유리 아래. */
private val GLASS_BASE = listOf(
    Seg(0.11f, 0.2575f, 0.22f, 0.264f, 0.33f, 0.277f),
    Seg(0.355f, 0.33f, 0.377f, 0.42f, 0.377f, 0.50f),
    Seg(0.377f, 0.62f, 0.366f, 0.73f, 0.318f, 0.812f),
    Seg(0.28f, 0.84f, 0.14f, 0.848f, 0.0f, 0.848f)
)
private const val GLASS_BASE_START_Y = 0.2575f

/** 지붕 윤곽(지붕 높이). GLASS_BASE와 곡선 수가 같아서 둘 사이를 섞어 중간 높이의 윤곽을 만든다. */
private val ROOF_EDGE = listOf(
    Seg(0.10f, 0.376f, 0.22f, 0.378f, 0.292f, 0.388f),
    Seg(0.300f, 0.42f, 0.306f, 0.46f, 0.305f, 0.50f),
    Seg(0.304f, 0.56f, 0.295f, 0.63f, 0.270f, 0.668f),
    Seg(0.20f, 0.676f, 0.10f, 0.676f, 0.0f, 0.676f)
)
private const val ROOF_EDGE_START_Y = 0.376f

internal val GLASS_TOP_COLOR = Color(0xFF39444F)
internal val GLASS_BOTTOM_COLOR = Color(0xFF12171D)
private val SEAM_COLOR = Color(0x4D30363E)

/** 흐리게 번진 도형. 그림자와 부드러운 빛에 쓴다. */
private fun DrawScope.drawBlurred(path: Path, color: Color, radius: Float) {
    if (radius <= 0f || color.alpha <= 0f) return
    drawIntoCanvas { canvas ->
        val paint = Paint()
        paint.color = color
        paint.asFrameworkPaint().maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(path, paint)
    }
}

/** 옆면의 한 층. */
internal class WallLayer(
    val z: Float,
    /** 차체 색으로 물들이는 필터. 차체 바닥보다 낮은 층(바퀴만 있는 높이)이면 null. */
    val tint: ColorFilter?,
    val tailLamp: Boolean,
    val frontLamp: Boolean,
    val grille: Boolean,
    /** 이 높이에서 휠 하우스, 바퀴, 휠의 앞뒤 길이의 절반(차 길이에 대한 비율). 없는 높이면 0. */
    val archHalf: Float,
    val tireHalf: Float,
    val rimHalf: Float
)

/**
 * 크기가 정해진 차량 그림 한 벌. 차의 크기가 바뀔 때만 새로 만든다.
 *
 * 화면을 그릴 때 도형을 다시 계산하지 않도록, 필요한 모양을 모두 이미지로 만들어 둔다.
 * 색만 다른 층(옆면)은 한 가지 색의 실루엣 이미지 하나를 층마다 다른 색으로 물들여 쓴다.
 */
internal class CarArt(val w: Int, val h: Int, density: Density) {
    val geo = Geo(w.toFloat(), h.toFloat())

    /** 이미지 가장자리의 여백. 그림자가 번질 자리다. */
    val pad: Int = (0.07f * h).toInt()

    private val body: Path = symmetricPath(geo, BODY_START_Y, BODY)

    /** 차체 윤곽을 채운 한 가지 색 이미지. 옆면의 각 층과 휠 하우스에 쓴다. */
    val silhouette: ImageBitmap = sprite(density, ImageBitmapConfig.Alpha8) { drawPath(body, Color.Black) }

    /** 차체 윤곽 안쪽을 따라 도는 띠. 옆면에 보이는 램프와 그릴에 쓴다. */
    val rim: ImageBitmap = sprite(density, ImageBitmapConfig.Alpha8) {
        clipPath(body) { drawPath(body, Color.Black, style = Stroke(width = 0.044f * geo.w)) }
    }

    val walls: List<WallLayer> = List(WALL_LAYERS + 1) { i ->
        val z = Z_BELT * i / WALL_LAYERS
        val inBody = z >= Z_FLOOR
        val t = (z - Z_FLOOR) / (Z_BELT - Z_FLOOR)
        val fromAxle = (z - WHEEL_R) * (z - WHEEL_R)
        val rimRadius = WHEEL_R * 0.66f
        WallLayer(
            z = z,
            // 아래쪽은 어두운 하단 몰딩, 위로 갈수록 밝은 차체 색
            tint = when {
                !inBody -> null
                t < 0.14f -> ColorFilter.tint(Color(0xFF2B3036))
                else -> ColorFilter.tint(lerp(Color(0xFF8C929B), Color(0xFFE6E8EB), t))
            },
            tailLamp = inBody && z in 0.118f..0.150f,
            frontLamp = inBody && z in 0.118f..0.142f,
            grille = inBody && z in 0.060f..0.105f,
            archHalf = if (inBody) sqrt((ARCH_R * ARCH_R - fromAxle).coerceAtLeast(0f)) else 0f,
            tireHalf = sqrt((WHEEL_R * WHEEL_R - fromAxle).coerceAtLeast(0f)),
            rimHalf = sqrt((rimRadius * rimRadius - fromAxle).coerceAtLeast(0f))
        )
    }

    /** 유리 층 이미지가 차 사각형 안에서 놓이는 자리. 유리가 있는 부분만 잘라서 만든다. */
    val glassLeft: Int = geo.x(-0.41f).toInt()
    val glassTop: Int = (0.235f * h).toInt()

    /**
     * 유리 윤곽을 지붕 윤곽으로 조금씩 줄여 간 층들. 층마다 유리 색, 앞뒤 기둥의 한 토막,
     * (맨 아래 층에는) 옆 유리의 크롬 몰딩이 그려져 있다. 쌓으면 기울어진 유리와 기둥이 된다.
     */
    val glassRings: List<ImageBitmap> = List(GLASS_STEPS + 1) { j ->
        val t = j / GLASS_STEPS.toFloat()
        val next = (j + 1).coerceAtMost(GLASS_STEPS) / GLASS_STEPS.toFloat()
        val bitmap = ImageBitmap((0.82f * w).toInt(), (0.65f * h).toInt())
        val size = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), size) {
            translate(-glassLeft.toFloat(), -glassTop.toFloat()) {
                val g = geo
                val ring = symmetricPath(
                    g, mix(GLASS_BASE_START_Y, ROOF_EDGE_START_Y, t),
                    GLASS_BASE.zip(ROOF_EDGE) { a, b ->
                        Seg(
                            mix(a.c1x, b.c1x, t), mix(a.c1y, b.c1y, t),
                            mix(a.c2x, b.c2x, t), mix(a.c2y, b.c2y, t),
                            mix(a.x, b.x, t), mix(a.y, b.y, t)
                        )
                    }
                )
                drawPath(ring, lerp(GLASS_BOTTOM_COLOR, GLASS_TOP_COLOR, t))

                val pillarColor = Color(0xFFF1F2F4)
                val pillarWidth = 0.026f * g.w
                bothSides { s ->
                    drawLine(
                        pillarColor,
                        g.at(s * mix(0.337f, 0.291f, t), mix(0.289f, 0.389f, t)),
                        g.at(s * mix(0.337f, 0.291f, next), mix(0.289f, 0.389f, next)),
                        pillarWidth, StrokeCap.Round
                    )
                    drawLine(
                        pillarColor,
                        g.at(s * mix(0.303f, 0.269f, t), mix(0.821f, 0.669f, t)),
                        g.at(s * mix(0.303f, 0.269f, next), mix(0.821f, 0.669f, next)),
                        pillarWidth, StrokeCap.Round
                    )
                    if (j == 0) {
                        // 옆 유리 바깥 가장자리의 크롬 몰딩
                        drawPath(
                            Path().apply {
                                moveTo(g.x(s * 0.348f), 0.305f * g.h)
                                cubicTo(g.x(s * 0.368f), 0.36f * g.h, g.x(s * 0.377f), 0.43f * g.h, g.x(s * 0.377f), 0.50f * g.h)
                                cubicTo(g.x(s * 0.377f), 0.62f * g.h, g.x(s * 0.366f), 0.73f * g.h, g.x(s * 0.318f), 0.812f * g.h)
                            },
                            Color(0xFFD9DEE4), style = Stroke(width = 0.006f * g.w, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
        bitmap
    }

    val shadow: ImageBitmap = sprite(density) { drawShadow() }
    val top: ImageBitmap = sprite(density) { drawTop() }
    val roof: ImageBitmap = sprite(density) { drawRoof() }

    /** 유리의 반사와 뒤 유리 열선. 위에서 볼 때만 겹쳐 그린다. */
    val glassDetail: ImageBitmap = sprite(density) { drawGlassDetail() }

    /** 전조등 렌즈의 모양. 전조등이 고장일 때 윗면 그림 위에 등급 색으로 덧칠한다. */
    fun lampLens(s: Float): Path = Path().apply {
        moveTo(geo.x(s * 0.300f), 0.040f * geo.h)
        lineTo(geo.x(s * 0.392f), 0.070f * geo.h)
        lineTo(geo.x(s * 0.404f), 0.098f * geo.h)
        lineTo(geo.x(s * 0.345f), 0.076f * geo.h)
        lineTo(geo.x(s * 0.295f), 0.054f * geo.h)
        close()
    }

    /** 차 사각형보다 사방으로 [pad]만큼 큰 이미지에 그린다. 그리는 쪽에서는 차 사각형 좌표를 그대로 쓴다. */
    private fun sprite(
        density: Density,
        config: ImageBitmapConfig = ImageBitmapConfig.Argb8888,
        block: DrawScope.() -> Unit
    ): ImageBitmap {
        val bitmap = ImageBitmap(w + 2 * pad, h + 2 * pad, config)
        val size = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), size) {
            translate(pad.toFloat(), pad.toFloat()) { block() }
        }
        return bitmap
    }

    private fun DrawScope.drawShadow() {
        translate(top = 0.014f * geo.h) {
            drawBlurred(body, Color(0x47000000), radius = 0.030f * geo.h)
        }
        drawBlurred(body, Color(0x33000000), radius = 0.008f * geo.h)
    }

    /** 허리선 높이의 그림: 차체 윗면, 보닛과 트렁크, 램프, 사이드미러, 문틈. */
    private fun DrawScope.drawTop() {
        val g = geo
        val w = g.w
        val h = g.h
        val seamWidth = 0.0035f * w

        // 사이드미러
        bothSides { s ->
            val mirror = Path().apply {
                moveTo(g.x(s * 0.424f), 0.300f * h)
                quadraticTo(g.x(s * 0.470f), 0.292f * h, g.x(s * 0.493f), 0.305f * h)
                quadraticTo(g.x(s * 0.500f), 0.322f * h, g.x(s * 0.478f), 0.330f * h)
                quadraticTo(g.x(s * 0.450f), 0.334f * h, g.x(s * 0.424f), 0.322f * h)
                close()
            }
            drawPath(
                mirror,
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFFDFDFE), Color(0xFFC3C8CF)),
                    startY = 0.292f * h,
                    endY = 0.334f * h
                )
            )
            drawPath(mirror, Color(0xFF8D939C), style = Stroke(width = 0.004f * w))
        }

        // 차체 윗면: 가운데는 희고 옆으로 갈수록 어두워져, 옆면이 아래로 꺾여 내려가는 것처럼 보인다.
        drawPath(
            body,
            Brush.horizontalGradient(
                0.00f to Color(0xFF8F959E),
                0.05f to Color(0xFFBFC4CB),
                0.12f to Color(0xFFE9EBEE),
                0.22f to Color(0xFFFAFAFB),
                0.50f to Color.White,
                0.78f to Color(0xFFFAFAFB),
                0.88f to Color(0xFFE9EBEE),
                0.95f to Color(0xFFBFC4CB),
                1.00f to Color(0xFF8F959E),
                startX = g.x(-0.432f),
                endX = g.x(0.432f)
            )
        )
        clipPath(body) {
            // 앞뒤 끝도 아래로 꺾이므로 살짝 어둡게
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x38000000), Color.Transparent),
                    startY = 0f,
                    endY = 0.055f * h
                ),
                size = Size(w, 0.055f * h)
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0x38000000)),
                    startY = 0.935f * h,
                    endY = h
                ),
                topLeft = Offset(0f, 0.935f * h),
                size = Size(w, 0.065f * h)
            )
        }
        // 어깨선: 옆면과 윗면이 만나는 자리
        fun shoulderY(y: Float) = 0.5f + (y - 0.5f) * 0.975f
        val shoulder = symmetricPath(
            g, shoulderY(BODY_START_Y),
            BODY.map {
                Seg(
                    it.c1x * 0.9f, shoulderY(it.c1y),
                    it.c2x * 0.9f, shoulderY(it.c2y),
                    it.x * 0.9f, shoulderY(it.y)
                )
            }
        )
        drawPath(shoulder, Color(0x1F000000), style = Stroke(width = 0.004f * w))
        drawPath(body, Color(0xFF7E848D), style = Stroke(width = 0.005f * w))

        // 보닛과 펜더 사이의 틈, 보닛 위의 부드러운 주름
        bothSides { s ->
            drawPath(
                Path().apply {
                    moveTo(g.x(s * 0.365f), 0.085f * h)
                    cubicTo(g.x(s * 0.385f), 0.15f * h, g.x(s * 0.375f), 0.22f * h, g.x(s * 0.345f), 0.272f * h)
                },
                SEAM_COLOR, style = Stroke(width = seamWidth)
            )
            drawPath(
                Path().apply {
                    moveTo(g.x(s * 0.13f), 0.07f * h)
                    quadraticTo(g.x(s * 0.17f), 0.16f * h, g.x(s * 0.19f), 0.245f * h)
                },
                Color(0x1A000000), style = Stroke(width = 0.006f * w)
            )
        }
        // 보닛 앞 가장자리
        drawPath(
            Path().apply {
                moveTo(g.x(-0.365f), 0.085f * h)
                cubicTo(g.x(-0.28f), 0.040f * h, g.x(0.28f), 0.040f * h, g.x(0.365f), 0.085f * h)
            },
            SEAM_COLOR, style = Stroke(width = seamWidth)
        )

        // 앞 램프: 앞코를 가로지르는 주간주행등 띠와 양쪽 모서리의 전조등
        val lightBar = Path().apply {
            moveTo(g.x(-0.385f), 0.066f * h)
            cubicTo(g.x(-0.30f), 0.018f * h, g.x(0.30f), 0.018f * h, g.x(0.385f), 0.066f * h)
        }
        drawPath(lightBar, Color(0xFF262B32), style = Stroke(width = 0.011f * h, cap = StrokeCap.Round))
        drawPath(lightBar, Color(0xFFF4F7FA), style = Stroke(width = 0.004f * h, cap = StrokeCap.Round))
        bothSides { s ->
            val lamp = lampLens(s)
            drawPath(lamp, Color(0xFFE9EDF1))
            drawPath(lamp, Color(0xFF3A4048), style = Stroke(width = 0.004f * w))
        }

        // 앞 유리 아래의 검은 덮개
        drawPath(
            Path().apply {
                moveTo(g.x(-0.345f), 0.272f * h)
                quadraticTo(g.x(0f), 0.222f * h, g.x(0.345f), 0.272f * h)
                lineTo(g.x(0.33f), 0.280f * h)
                quadraticTo(g.x(0f), 0.242f * h, g.x(-0.33f), 0.280f * h)
                close()
            },
            Color(0xFF1E2228)
        )

        // 문과 트렁크의 틈, 문 손잡이
        bothSides { s ->
            drawLine(SEAM_COLOR, g.at(s * 0.352f, 0.318f), g.at(s * 0.431f, 0.338f), strokeWidth = seamWidth)
            drawLine(SEAM_COLOR, g.at(s * 0.377f, 0.532f), g.at(s * 0.428f, 0.532f), strokeWidth = seamWidth)
            drawPath(
                Path().apply {
                    moveTo(g.x(s * 0.360f), 0.735f * h)
                    quadraticTo(g.x(s * 0.40f), 0.722f * h, g.x(s * 0.431f), 0.705f * h)
                },
                SEAM_COLOR, style = Stroke(width = seamWidth)
            )
            listOf(0.455f, 0.645f).forEach { y ->
                drawRoundRect(
                    color = Color(0xFFD5D9DE),
                    topLeft = Offset(minOf(g.x(s * 0.402f), g.x(s * 0.416f)), y * h),
                    size = Size(0.014f * w, 0.030f * h),
                    cornerRadius = CornerRadius(0.007f * w, 0.007f * w)
                )
            }
            drawLine(SEAM_COLOR, g.at(s * 0.335f, 0.846f), g.at(s * 0.348f, 0.936f), strokeWidth = seamWidth)
        }
        drawPath(
            Path().apply {
                moveTo(g.x(-0.335f), 0.846f * h)
                quadraticTo(g.x(0f), 0.888f * h, g.x(0.335f), 0.846f * h)
            },
            SEAM_COLOR, style = Stroke(width = seamWidth)
        )

        // 뒤 램프: 꼬리를 가로지르는 붉은 띠
        val tailBar = Path().apply {
            moveTo(g.x(-0.375f), 0.940f * h)
            cubicTo(g.x(-0.30f), 0.985f * h, g.x(0.30f), 0.985f * h, g.x(0.375f), 0.940f * h)
        }
        drawPath(tailBar, Color(0xFF7D1414), style = Stroke(width = 0.012f * h, cap = StrokeCap.Round))
        drawPath(tailBar, Color(0xFFE23B32), style = Stroke(width = 0.005f * h, cap = StrokeCap.Round))
    }

    /** 지붕 높이의 그림: 지붕, 파노라마 선루프, 안테나. */
    private fun DrawScope.drawRoof() {
        val g = geo
        val w = g.w
        val h = g.h
        val roof = Path().apply {
            moveTo(g.x(-0.292f), 0.388f * h)
            quadraticTo(g.x(0f), 0.364f * h, g.x(0.292f), 0.388f * h)
            cubicTo(g.x(0.308f), 0.47f * h, g.x(0.302f), 0.58f * h, g.x(0.270f), 0.668f * h)
            quadraticTo(g.x(0f), 0.684f * h, g.x(-0.270f), 0.668f * h)
            cubicTo(g.x(-0.302f), 0.58f * h, g.x(-0.308f), 0.47f * h, g.x(-0.292f), 0.388f * h)
            close()
        }
        drawPath(
            roof,
            Brush.horizontalGradient(
                0.0f to Color(0xFFD3D7DD),
                0.2f to Color(0xFFF4F5F7),
                0.5f to Color.White,
                0.8f to Color(0xFFF4F5F7),
                1.0f to Color(0xFFD3D7DD),
                startX = g.x(-0.30f),
                endX = g.x(0.30f)
            )
        )

        val sunroof = Path().apply {
            addRoundRect(
                RoundRect(
                    left = g.x(-0.205f), top = 0.408f * h, right = g.x(0.205f), bottom = 0.602f * h,
                    cornerRadius = CornerRadius(0.035f * w, 0.035f * w)
                )
            )
        }
        drawPath(
            sunroof,
            Brush.verticalGradient(
                colors = listOf(GLASS_TOP_COLOR, GLASS_BOTTOM_COLOR),
                startY = 0.408f * h,
                endY = 0.602f * h
            )
        )
        clipPath(sunroof) {
            drawBlurred(
                Path().apply {
                    moveTo(g.x(-0.16f), 0.40f * h)
                    lineTo(g.x(-0.06f), 0.40f * h)
                    lineTo(g.x(0.10f), 0.61f * h)
                    lineTo(g.x(0.0f), 0.61f * h)
                    close()
                },
                Color(0x1FFFFFFF), radius = 0.006f * h
            )
        }
        drawLine(Color(0xFF07090C), g.at(-0.205f, 0.502f), g.at(0.205f, 0.502f), strokeWidth = 0.004f * h)
        drawPath(sunroof, Color(0xFF07090C), style = Stroke(width = 0.004f * w))

        // 안테나
        drawPath(
            Path().apply {
                moveTo(g.x(0f), 0.630f * h)
                quadraticTo(g.x(0.016f), 0.646f * h, g.x(0f), 0.660f * h)
                quadraticTo(g.x(-0.016f), 0.646f * h, g.x(0f), 0.630f * h)
                close()
            },
            Color(0xFF1A1E24)
        )
    }

    /** 앞 유리의 반사, 뒤 유리의 열선과 반사, 옆 유리의 가운데 기둥. */
    private fun DrawScope.drawGlassDetail() {
        val g = geo
        val h = g.h
        val windshield = Path().apply {
            moveTo(g.x(-0.33f), 0.277f * h)
            quadraticTo(g.x(0f), 0.238f * h, g.x(0.33f), 0.277f * h)
            lineTo(g.x(0.268f), 0.392f * h)
            quadraticTo(g.x(0f), 0.368f * h, g.x(-0.268f), 0.392f * h)
            close()
        }
        clipPath(windshield) {
            drawBlurred(
                Path().apply {
                    moveTo(g.x(-0.22f), 0.25f * h)
                    lineTo(g.x(-0.10f), 0.25f * h)
                    lineTo(g.x(0.06f), 0.395f * h)
                    lineTo(g.x(-0.06f), 0.395f * h)
                    close()
                },
                Color(0x24FFFFFF), radius = 0.006f * h
            )
        }
        val rearGlass = Path().apply {
            moveTo(g.x(-0.235f), 0.668f * h)
            quadraticTo(g.x(0f), 0.684f * h, g.x(0.235f), 0.668f * h)
            cubicTo(g.x(0.250f), 0.72f * h, g.x(0.268f), 0.78f * h, g.x(0.285f), 0.828f * h)
            quadraticTo(g.x(0f), 0.868f * h, g.x(-0.285f), 0.828f * h)
            cubicTo(g.x(-0.268f), 0.78f * h, g.x(-0.250f), 0.72f * h, g.x(-0.235f), 0.668f * h)
            close()
        }
        clipPath(rearGlass) {
            for (i in 1..8) {
                val y = (0.68f + 0.019f * i) * h
                drawLine(Color(0x1FFFFFFF), Offset(g.x(-0.30f), y), Offset(g.x(0.30f), y), strokeWidth = 1f)
            }
            drawBlurred(
                Path().apply {
                    moveTo(g.x(0.02f), 0.66f * h)
                    lineTo(g.x(0.12f), 0.66f * h)
                    lineTo(g.x(0.24f), 0.86f * h)
                    lineTo(g.x(0.14f), 0.86f * h)
                    close()
                },
                Color(0x1AFFFFFF), radius = 0.006f * h
            )
        }
        bothSides { s ->
            drawLine(Color(0xFF05070A), g.at(s * 0.300f, 0.532f), g.at(s * 0.377f, 0.532f), strokeWidth = 0.007f * h)
        }
    }
}
