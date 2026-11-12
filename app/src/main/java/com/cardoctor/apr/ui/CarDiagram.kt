package com.cardoctor.apr.ui

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardoctor.apr.diagnosis.Advice
import com.cardoctor.apr.diagnosis.LevelInfo
import com.cardoctor.apr.diagnosis.Part

/*
 * 위에서 내려다본 차량 그림. 차 앞쪽이 위.
 *
 * 중형 패스트백 세단(쏘나타)의 비율로 그린다: 긴 보닛, 뒤로 길게 누운 뒤 유리, 짧은 트렁크,
 * 앞뒤를 가로지르는 램프. 사진이 아니라 코드로 그린 그림이며, 부드러운 그림자와 유리 반사,
 * 차체 가장자리의 음영으로 입체감을 낸다.
 *
 * 좌표는 차가 들어가는 사각형에 대한 비율이다. 가로는 가운데에서의 거리(dx, 오른쪽이 +)로 적는다.
 */

/** 차 사각형의 가로/세로 비. 사이드미러까지 포함한 폭이다. */
private const val CAR_ASPECT = 0.44f

/**
 * 그림에서 누른 위치(차 사각형에 대한 비율 좌표)에 해당하는 부품. 해당하는 부품이 없으면 null.
 * 보닛은 냉각수와 구동 장치가 같은 자리라, 둘 중 고장 난 쪽을 우선한다.
 */
private fun partAt(x: Float, y: Float, faulty: Set<Part>): Part? {
    if (x !in 0f..1f || y !in 0f..1f) return null
    val inWheelRow = y in 0.12f..0.26f || y in 0.70f..0.84f
    val side = minOf(x, 1f - x) // 가장 가까운 옆면까지의 거리
    return when {
        inWheelRow && side < 0.13f -> Part.TIRE
        inWheelRow && side < 0.28f -> Part.BRAKE
        y < 0.085f -> Part.HEADLAMP
        y in 0.24f..0.40f -> Part.WIPER
        y in 0.085f..0.24f -> if (Part.POWER in faulty && Part.COOLANT !in faulty) Part.POWER else Part.COOLANT
        else -> null
    }
}

/** 그림 영역 안에서 차가 놓이는 자리. 좌우에 이름표를 둘 여백을 남기고 가운데에 둔다. */
private fun carRect(area: Size, maxHeight: Float): Rect {
    val height = minOf(area.height * 0.90f, maxHeight, area.width * 0.365f / CAR_ASPECT)
    val width = height * CAR_ASPECT
    val left = (area.width - width) / 2f
    val top = (area.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

/** 3차 곡선 하나. x 값은 차 가운데에서의 거리. */
private class Seg(val c1x: Float, val c1y: Float, val c2x: Float, val c2y: Float, val x: Float, val y: Float)

/**
 * 오른쪽 절반의 곡선들로 좌우 대칭인 닫힌 도형을 만든다.
 * 앞쪽 가운데(0, [startY])에서 시작해 오른쪽을 따라 내려간 뒤, 같은 곡선을 뒤집어 왼쪽으로 올라온다.
 */
private fun symmetricPath(w: Float, h: Float, startY: Float, right: List<Seg>): Path {
    val path = Path()
    path.moveTo(0.5f * w, startY * h)
    right.forEach {
        path.cubicTo(
            (0.5f + it.c1x) * w, it.c1y * h,
            (0.5f + it.c2x) * w, it.c2y * h,
            (0.5f + it.x) * w, it.y * h
        )
    }
    for (i in right.indices.reversed()) {
        val seg = right[i]
        val endX = if (i == 0) 0f else right[i - 1].x
        val endY = if (i == 0) startY else right[i - 1].y
        path.cubicTo(
            (0.5f - seg.c2x) * w, seg.c2y * h,
            (0.5f - seg.c1x) * w, seg.c1y * h,
            (0.5f - endX) * w, endY * h
        )
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

/** 흐리게 번진 도형. 그림자와 부드러운 빛에 쓴다. */
private fun DrawScope.drawBlurred(path: Path, color: Color, radius: Float, strokeWidth: Float = 0f) {
    if (radius <= 0f) return
    drawIntoCanvas { canvas ->
        val paint = Paint()
        paint.color = color
        if (strokeWidth > 0f) {
            paint.style = PaintingStyle.Stroke
            paint.strokeWidth = strokeWidth
        }
        paint.asFrameworkPaint().maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(path, paint)
    }
}

/**
 * 고장 난 부품은 등급 색으로 옅게 물들이고, 그 위치에 작은 점을 찍는다.
 * 부품 이름과 등급은 차 옆 여백에 글자로만 적고, 점과 가는 선으로 잇는다.
 * 부품이나 글자를 누르면 [onPartTap]이 불린다.
 */
@Composable
internal fun CarDiagram(
    advices: List<Advice>,
    modifier: Modifier = Modifier,
    /** 4등급 표시를 깜빡이게 하는 값. 1이면 원래 색, 0에 가까울수록 옅어진다. */
    pulse: Float = 1f,
    onPartTap: (Part) -> Unit = {}
) {
    val colors = advices.associate {
        val color = levelColor(it.level)
        it.part to if (it.level >= 4) lerp(Color.White, color, pulse) else color
    }
    val levels = advices.associate { it.part to it.level }
    val faultyParts = advices.map { it.part }.toSet()
    val measurer = rememberTextMeasurer()

    // 마지막으로 그린 이름표의 자리. 글자를 눌러도 부품이 선택되게 한다.
    val labelHits = remember { mutableListOf<Pair<Rect, Part>>() }

    Canvas(
        modifier = modifier.pointerInput(faultyParts) {
            detectTapGestures { tap ->
                val label = labelHits.firstOrNull { it.first.contains(tap) }
                if (label != null) {
                    onPartTap(label.second)
                } else {
                    val car = carRect(Size(size.width.toFloat(), size.height.toFloat()), 660.dp.toPx())
                    partAt((tap.x - car.left) / car.width, (tap.y - car.top) / car.height, faultyParts)
                        ?.let(onPartTap)
                }
            }
        }
    ) {
        val car = carRect(size, 660.dp.toPx())
        val w = car.width
        val h = car.height
        labelHits.clear()

        translate(car.left, car.top) {
            /** 가운데에서 dx만큼 떨어진 가로 위치. */
            fun x(dx: Float) = (0.5f + dx) * w
            fun at(dx: Float, y: Float) = Offset(x(dx), y * h)

            /** 오른쪽 기준으로 만든 도형을 좌우 대칭으로 두 번 그린다. s는 +1(오른쪽), -1(왼쪽). */
            fun bothSides(draw: (s: Float) -> Unit) {
                draw(1f)
                draw(-1f)
            }

            val body = symmetricPath(w, h, BODY_START_Y, BODY)

            // ---------------------------------------------------------------- 바닥 그림자
            translate(top = 0.014f * h) {
                drawBlurred(body, Color(0x47000000), radius = 0.030f * h)
            }
            drawBlurred(body, Color(0x33000000), radius = 0.008f * h)

            // ---------------------------------------------------------------- 타이어
            // 차체 밖으로 살짝 나온 부분만 보인다.
            val tireColor = colors[Part.TIRE]?.let { lerp(Color(0xFF1B1E23), it, 0.8f) } ?: Color(0xFF1B1E23)
            // 평소에는 아주 조금만 보이고, 고장일 때는 색이 보이도록 조금 더 내민다.
            val tireEdge = if (Part.TIRE in colors) 0.452f else 0.441f
            listOf(0.135f, 0.715f).forEach { top ->
                bothSides { s ->
                    drawRoundRect(
                        color = tireColor,
                        topLeft = Offset(minOf(x(s * 0.405f), x(s * tireEdge)), top * h),
                        size = Size((tireEdge - 0.405f) * w, 0.11f * h),
                        cornerRadius = CornerRadius(0.012f * w, 0.012f * w)
                    )
                }
            }

            // ---------------------------------------------------------------- 사이드미러
            bothSides { s ->
                val mirror = Path().apply {
                    moveTo(x(s * 0.424f), 0.300f * h)
                    quadraticTo(x(s * 0.470f), 0.292f * h, x(s * 0.493f), 0.305f * h)
                    quadraticTo(x(s * 0.500f), 0.322f * h, x(s * 0.478f), 0.330f * h)
                    quadraticTo(x(s * 0.450f), 0.334f * h, x(s * 0.424f), 0.322f * h)
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

            // ---------------------------------------------------------------- 차체
            // 가운데는 희고 옆으로 갈수록 어두워져, 옆면이 아래로 꺾여 내려가는 것처럼 보인다.
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
                    startX = x(-0.432f),
                    endX = x(0.432f)
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
            val shoulder = symmetricPath(
                w, h, 0.5f + (BODY_START_Y - 0.5f) * 0.975f,
                BODY.map {
                    fun sy(y: Float) = 0.5f + (y - 0.5f) * 0.975f
                    Seg(it.c1x * 0.9f, sy(it.c1y), it.c2x * 0.9f, sy(it.c2y), it.x * 0.9f, sy(it.y))
                }
            )
            drawPath(shoulder, Color(0x1F000000), style = Stroke(width = 0.004f * w))
            drawPath(body, Color(0xFF7E848D), style = Stroke(width = 0.005f * w))

            // ---------------------------------------------------------------- 보닛
            val seam = Color(0x4D30363E)
            val seamWidth = 0.0035f * w
            // 보닛과 펜더 사이의 틈
            bothSides { s ->
                drawPath(
                    Path().apply {
                        moveTo(x(s * 0.365f), 0.085f * h)
                        cubicTo(x(s * 0.385f), 0.15f * h, x(s * 0.375f), 0.22f * h, x(s * 0.345f), 0.272f * h)
                    },
                    seam, style = Stroke(width = seamWidth)
                )
                // 보닛 위의 부드러운 주름
                drawPath(
                    Path().apply {
                        moveTo(x(s * 0.13f), 0.07f * h)
                        quadraticTo(x(s * 0.17f), 0.16f * h, x(s * 0.19f), 0.245f * h)
                    },
                    Color(0x1A000000), style = Stroke(width = 0.006f * w)
                )
            }
            // 보닛 앞 가장자리
            drawPath(
                Path().apply {
                    moveTo(x(-0.365f), 0.085f * h)
                    cubicTo(x(-0.28f), 0.040f * h, x(0.28f), 0.040f * h, x(0.365f), 0.085f * h)
                },
                seam, style = Stroke(width = seamWidth)
            )

            // ---------------------------------------------------------------- 앞 램프
            // 앞코를 가로지르는 주간주행등 띠와, 양쪽 모서리의 전조등
            val lightBar = Path().apply {
                moveTo(x(-0.385f), 0.066f * h)
                cubicTo(x(-0.30f), 0.018f * h, x(0.30f), 0.018f * h, x(0.385f), 0.066f * h)
            }
            drawPath(lightBar, Color(0xFF262B32), style = Stroke(width = 0.011f * h, cap = StrokeCap.Round))
            drawPath(lightBar, Color(0xFFF4F7FA), style = Stroke(width = 0.004f * h, cap = StrokeCap.Round))

            val lampFault = colors[Part.HEADLAMP]
            bothSides { s ->
                val lamp = Path().apply {
                    moveTo(x(s * 0.300f), 0.040f * h)
                    lineTo(x(s * 0.392f), 0.070f * h)
                    lineTo(x(s * 0.404f), 0.098f * h)
                    lineTo(x(s * 0.345f), 0.076f * h)
                    lineTo(x(s * 0.295f), 0.054f * h)
                    close()
                }
                drawPath(lamp, lampFault?.let { lerp(Color(0xFFE9EDF1), it, 0.85f) } ?: Color(0xFFE9EDF1))
                drawPath(lamp, Color(0xFF3A4048), style = Stroke(width = 0.004f * w))
            }

            // ---------------------------------------------------------------- 유리
            val glassTop = Color(0xFF39444F)
            val glassBottom = Color(0xFF12171D)

            // 앞 유리 아래의 검은 덮개
            drawPath(
                Path().apply {
                    moveTo(x(-0.345f), 0.272f * h)
                    quadraticTo(x(0f), 0.222f * h, x(0.345f), 0.272f * h)
                    lineTo(x(0.33f), 0.280f * h)
                    quadraticTo(x(0f), 0.242f * h, x(-0.33f), 0.280f * h)
                    close()
                },
                Color(0xFF1E2228)
            )

            val windshield = Path().apply {
                moveTo(x(-0.33f), 0.277f * h)
                quadraticTo(x(0f), 0.238f * h, x(0.33f), 0.277f * h)
                lineTo(x(0.268f), 0.392f * h)
                quadraticTo(x(0f), 0.368f * h, x(-0.268f), 0.392f * h)
                close()
            }
            val wiperFault = colors[Part.WIPER]
            drawPath(
                windshield,
                Brush.verticalGradient(
                    colors = listOf(glassBottom, glassTop),
                    startY = 0.25f * h,
                    endY = 0.39f * h
                )
            )
            clipPath(windshield) {
                drawBlurred(
                    Path().apply {
                        moveTo(x(-0.22f), 0.25f * h)
                        lineTo(x(-0.10f), 0.25f * h)
                        lineTo(x(0.06f), 0.395f * h)
                        lineTo(x(-0.06f), 0.395f * h)
                        close()
                    },
                    Color(0x24FFFFFF), radius = 0.006f * h
                )
            }

            // 지붕
            val roof = Path().apply {
                moveTo(x(-0.292f), 0.388f * h)
                quadraticTo(x(0f), 0.364f * h, x(0.292f), 0.388f * h)
                cubicTo(x(0.308f), 0.47f * h, x(0.302f), 0.58f * h, x(0.270f), 0.668f * h)
                quadraticTo(x(0f), 0.684f * h, x(-0.270f), 0.668f * h)
                cubicTo(x(-0.302f), 0.58f * h, x(-0.308f), 0.47f * h, x(-0.292f), 0.388f * h)
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
                    startX = x(-0.30f),
                    endX = x(0.30f)
                )
            )

            // 파노라마 선루프
            val sunroof = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = x(-0.205f), top = 0.408f * h, right = x(0.205f), bottom = 0.602f * h,
                        cornerRadius = CornerRadius(0.035f * w, 0.035f * w)
                    )
                )
            }
            drawPath(
                sunroof,
                Brush.verticalGradient(colors = listOf(glassTop, glassBottom), startY = 0.408f * h, endY = 0.602f * h)
            )
            clipPath(sunroof) {
                drawBlurred(
                    Path().apply {
                        moveTo(x(-0.16f), 0.40f * h)
                        lineTo(x(-0.06f), 0.40f * h)
                        lineTo(x(0.10f), 0.61f * h)
                        lineTo(x(0.0f), 0.61f * h)
                        close()
                    },
                    Color(0x1FFFFFFF), radius = 0.006f * h
                )
            }
            drawLine(Color(0xFF07090C), at(-0.205f, 0.502f), at(0.205f, 0.502f), strokeWidth = 0.004f * h)
            drawPath(sunroof, Color(0xFF07090C), style = Stroke(width = 0.004f * w))

            // 옆 유리: 지붕과 차체 어깨 사이로 보이는 좁은 띠. 바깥 가장자리에 크롬 몰딩이 지나간다.
            bothSides { s ->
                val sideGlass = Path().apply {
                    moveTo(x(s * 0.348f), 0.305f * h)
                    cubicTo(x(s * 0.368f), 0.36f * h, x(s * 0.377f), 0.43f * h, x(s * 0.377f), 0.50f * h)
                    cubicTo(x(s * 0.377f), 0.62f * h, x(s * 0.366f), 0.73f * h, x(s * 0.318f), 0.812f * h)
                    cubicTo(x(s * 0.305f), 0.76f * h, x(s * 0.285f), 0.71f * h, x(s * 0.268f), 0.672f * h)
                    cubicTo(x(s * 0.300f), 0.58f * h, x(s * 0.305f), 0.47f * h, x(s * 0.288f), 0.394f * h)
                    close()
                }
                drawPath(
                    sideGlass,
                    Brush.verticalGradient(colors = listOf(glassTop, glassBottom), startY = 0.30f * h, endY = 0.81f * h)
                )
                drawPath(
                    Path().apply {
                        moveTo(x(s * 0.348f), 0.305f * h)
                        cubicTo(x(s * 0.368f), 0.36f * h, x(s * 0.377f), 0.43f * h, x(s * 0.377f), 0.50f * h)
                        cubicTo(x(s * 0.377f), 0.62f * h, x(s * 0.366f), 0.73f * h, x(s * 0.318f), 0.812f * h)
                    },
                    Color(0xFFD9DEE4), style = Stroke(width = 0.006f * w, cap = StrokeCap.Round)
                )
                // 가운데 기둥
                drawLine(Color(0xFF05070A), at(s * 0.300f, 0.532f), at(s * 0.377f, 0.532f), strokeWidth = 0.007f * h)
            }

            // 뒤 유리: 패스트백이라 길게 누워 있다.
            val rearGlass = Path().apply {
                moveTo(x(-0.235f), 0.668f * h)
                quadraticTo(x(0f), 0.684f * h, x(0.235f), 0.668f * h)
                cubicTo(x(0.250f), 0.72f * h, x(0.268f), 0.78f * h, x(0.285f), 0.828f * h)
                quadraticTo(x(0f), 0.868f * h, x(-0.285f), 0.828f * h)
                cubicTo(x(-0.268f), 0.78f * h, x(-0.250f), 0.72f * h, x(-0.235f), 0.668f * h)
                close()
            }
            drawPath(
                rearGlass,
                Brush.verticalGradient(colors = listOf(glassTop, glassBottom), startY = 0.668f * h, endY = 0.85f * h)
            )
            clipPath(rearGlass) {
                // 열선
                for (i in 1..8) {
                    val y = (0.68f + 0.019f * i) * h
                    drawLine(Color(0x1FFFFFFF), Offset(x(-0.30f), y), Offset(x(0.30f), y), strokeWidth = 1f)
                }
                drawBlurred(
                    Path().apply {
                        moveTo(x(0.02f), 0.66f * h)
                        lineTo(x(0.12f), 0.66f * h)
                        lineTo(x(0.24f), 0.86f * h)
                        lineTo(x(0.14f), 0.86f * h)
                        close()
                    },
                    Color(0x1AFFFFFF), radius = 0.006f * h
                )
            }

            // 지붕 뒤쪽의 안테나
            drawPath(
                Path().apply {
                    moveTo(x(0f), 0.630f * h)
                    quadraticTo(x(0.016f), 0.646f * h, x(0f), 0.660f * h)
                    quadraticTo(x(-0.016f), 0.646f * h, x(0f), 0.630f * h)
                    close()
                },
                Color(0xFF1A1E24)
            )

            // ---------------------------------------------------------------- 와이퍼
            val wiperColor = wiperFault ?: Color(0xFF05070A)
            val wiperWidth = (if (wiperFault != null) 0.014f else 0.008f) * w
            drawLine(wiperColor, at(-0.28f, 0.281f), at(-0.02f, 0.2665f), wiperWidth, StrokeCap.Round)
            drawLine(wiperColor, at(0.01f, 0.2675f), at(0.27f, 0.2815f), wiperWidth, StrokeCap.Round)

            // ---------------------------------------------------------------- 문과 트렁크의 틈
            bothSides { s ->
                drawLine(seam, at(s * 0.352f, 0.318f), at(s * 0.431f, 0.338f), strokeWidth = seamWidth)
                drawLine(seam, at(s * 0.377f, 0.532f), at(s * 0.428f, 0.532f), strokeWidth = seamWidth)
                drawPath(
                    Path().apply {
                        moveTo(x(s * 0.360f), 0.735f * h)
                        quadraticTo(x(s * 0.40f), 0.722f * h, x(s * 0.431f), 0.705f * h)
                    },
                    seam, style = Stroke(width = seamWidth)
                )
                // 문 손잡이
                listOf(0.455f, 0.645f).forEach { y ->
                    drawRoundRect(
                        color = Color(0xFFD5D9DE),
                        topLeft = Offset(minOf(x(s * 0.402f), x(s * 0.416f)), y * h),
                        size = Size(0.014f * w, 0.030f * h),
                        cornerRadius = CornerRadius(0.007f * w, 0.007f * w)
                    )
                }
                drawLine(seam, at(s * 0.335f, 0.846f), at(s * 0.348f, 0.936f), strokeWidth = seamWidth)
            }
            drawPath(
                Path().apply {
                    moveTo(x(-0.335f), 0.846f * h)
                    quadraticTo(x(0f), 0.888f * h, x(0.335f), 0.846f * h)
                },
                seam, style = Stroke(width = seamWidth)
            )

            // ---------------------------------------------------------------- 뒤 램프
            // 꼬리를 가로지르는 붉은 띠
            val tailBar = Path().apply {
                moveTo(x(-0.375f), 0.940f * h)
                cubicTo(x(-0.30f), 0.985f * h, x(0.30f), 0.985f * h, x(0.375f), 0.940f * h)
            }
            drawPath(tailBar, Color(0xFF7D1414), style = Stroke(width = 0.012f * h, cap = StrokeCap.Round))
            drawPath(tailBar, Color(0xFFE23B32), style = Stroke(width = 0.005f * h, cap = StrokeCap.Round))

            // ---------------------------------------------------------------- 고장 표시
            /**
             * 부품 위치마다 작은 점을 찍고, 첫 번째 위치에서 차 옆 여백으로 가는 선을 그어
             * 부품 이름(회색)과 등급(등급 색)을 글자로 적는다.
             */
            fun label(part: Part, spots: List<Offset>, rightSide: Boolean) {
                val color = colors[part] ?: return
                val level = levels.getValue(part)
                val anchor = spots.first()

                val name = measurer.measure(
                    part.label,
                    TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Lux.TextDim)
                )
                val state = measurer.measure(
                    "${level}등급 · ${LevelInfo.title(level)}",
                    TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = color)
                )
                val textWidth = maxOf(name.size.width, state.size.width).toFloat()
                val textHeight = (name.size.height + state.size.height).toFloat()
                val gap = 30.dp.toPx()
                val left = if (rightSide) w + gap else -gap - textWidth
                val top = anchor.y - textHeight / 2f

                val lineEnd = Offset(
                    if (rightSide) left - 10.dp.toPx() else left + textWidth + 10.dp.toPx(),
                    anchor.y
                )
                drawLine(Lux.TextFaint, anchor, lineEnd, strokeWidth = 1.2.dp.toPx())
                spots.forEach {
                    drawCircle(Color(0x33000000), 7.5.dp.toPx(), it)
                    drawCircle(Color.White, 6.5.dp.toPx(), it)
                    drawCircle(color, 4.5.dp.toPx(), it)
                }
                drawText(name, topLeft = Offset(if (rightSide) left else left + textWidth - name.size.width, top))
                drawText(
                    state,
                    topLeft = Offset(
                        if (rightSide) left else left + textWidth - state.size.width,
                        top + name.size.height
                    )
                )
                labelHits += Rect(left, top, left + textWidth, top + textHeight).translate(car.left, car.top) to part
            }

            // 이름표가 서로 겹치지 않게 부품마다 자리를 정해 둔다: 오른쪽에 전조등·와이퍼·브레이크, 왼쪽에 보닛·타이어.
            label(Part.HEADLAMP, listOf(at(0.35f, 0.068f), at(-0.35f, 0.068f)), rightSide = true)
            label(Part.WIPER, listOf(at(0.14f, 0.275f)), rightSide = true)
            label(
                Part.BRAKE,
                listOf(at(0.33f, 0.77f), at(-0.33f, 0.77f), at(-0.33f, 0.19f), at(0.33f, 0.19f)),
                rightSide = true
            )
            label(Part.COOLANT, listOf(at(-0.06f, 0.115f)), rightSide = false)
            label(Part.POWER, listOf(at(-0.08f, 0.215f)), rightSide = false)
            label(
                Part.TIRE,
                listOf(at(-0.435f, 0.77f), at(0.435f, 0.77f), at(-0.435f, 0.19f), at(0.435f, 0.19f)),
                rightSide = false
            )
        }
    }
}
