package com.cardoctor.apr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.cardoctor.apr.diagnosis.Advice
import com.cardoctor.apr.diagnosis.Part

/**
 * 그림에서 누른 위치(비율 좌표)에 해당하는 부품. 해당하는 부품이 없으면 null.
 * 보닛은 냉각수와 구동 장치가 같은 자리라, 둘 중 고장 난 쪽을 우선한다.
 */
private fun partAt(x: Float, y: Float, faulty: Set<Part>): Part? {
    val inWheelRow = y in 0.12f..0.31f || y in 0.67f..0.87f
    val side = minOf(x, 1f - x) // 가장 가까운 옆면까지의 거리
    return when {
        inWheelRow && side < 0.16f -> Part.TIRE
        inWheelRow && side < 0.32f -> Part.BRAKE
        y < 0.11f -> Part.HEADLAMP
        y in 0.23f..0.41f -> Part.WIPER
        y in 0.08f..0.25f -> if (Part.POWER in faulty && Part.COOLANT !in faulty) Part.POWER else Part.COOLANT
        else -> null
    }
}

/**
 * 위에서 내려다본 차량 그림. 차 앞쪽이 위.
 * 고장 난 부품은 등급 색으로 옅게 물들이고, 그 위치에 빛나는 점과 이름표를 표시한다.
 * 좌표는 모두 그림 크기에 대한 비율(0~1)이다.
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
    val faultyParts = advices.map { it.part }.toSet()
    val levels = advices.associate { it.part to it.level }
    val phase = rememberPingPhase(advices.isNotEmpty())
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier = modifier.pointerInput(faultyParts) {
            detectTapGestures { tap ->
                partAt(tap.x / size.width, tap.y / size.height, faultyParts)?.let(onPartTap)
            }
        }
    ) {
        val w = size.width
        val h = size.height
        fun at(x: Float, y: Float) = Offset(x * w, y * h)

        /** 왼쪽 기준으로 만든 도형을 좌우 대칭으로 두 번 그린다. */
        fun mirrored(draw: (mx: (Float) -> Float) -> Unit) {
            draw { it }
            draw { 1f - it }
        }

        fun polygon(points: List<Offset>) = Path().apply {
            moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }

        val body = Path().apply {
            moveTo(0.5f * w, 0.012f * h)
            cubicTo(0.72f * w, 0.012f * h, 0.86f * w, 0.03f * h, 0.885f * w, 0.11f * h)
            cubicTo(0.90f * w, 0.17f * h, 0.905f * w, 0.26f * h, 0.90f * w, 0.36f * h)
            cubicTo(0.895f * w, 0.50f * h, 0.895f * w, 0.70f * h, 0.90f * w, 0.82f * h)
            cubicTo(0.905f * w, 0.92f * h, 0.84f * w, 0.985f * h, 0.5f * w, 0.988f * h)
            cubicTo(0.16f * w, 0.985f * h, 0.095f * w, 0.92f * h, 0.10f * w, 0.82f * h)
            cubicTo(0.105f * w, 0.70f * h, 0.105f * w, 0.50f * h, 0.10f * w, 0.36f * h)
            cubicTo(0.095f * w, 0.26f * h, 0.10f * w, 0.17f * h, 0.115f * w, 0.11f * h)
            cubicTo(0.14f * w, 0.03f * h, 0.28f * w, 0.012f * h, 0.5f * w, 0.012f * h)
            close()
        }

        // 바닥 그림자
        translate(left = 0f, top = 0.012f * h) {
            drawPath(body, Color(0x26000000))
        }

        // 타이어: 차체 밖으로 조금 나온 부분만 보인다
        val tireColor = colors[Part.TIRE]?.let { lerp(Color(0xFF23272E), it, 0.6f) } ?: Color(0xFF23272E)
        listOf(0.145f, 0.70f).forEach { top ->
            mirrored { mx ->
                val left = minOf(mx(0.045f), mx(0.16f))
                drawRoundRect(
                    color = tireColor,
                    topLeft = at(left, top),
                    size = Size(0.115f * w, 0.14f * h),
                    cornerRadius = CornerRadius(0.03f * w, 0.03f * w)
                )
            }
        }

        // 사이드미러
        mirrored { mx ->
            drawPath(
                polygon(listOf(at(mx(0.115f), 0.292f), at(mx(0.035f), 0.300f), at(mx(0.03f), 0.322f), at(mx(0.115f), 0.325f))),
                Color(0xFF8D949E)
            )
        }

        // 차체: 가운데가 밝고 양옆이 어두운 금속 느낌
        drawPath(
            body,
            Brush.horizontalGradient(
                listOf(
                    Color(0xFF9BA2AC),
                    Color(0xFFDDE1E6),
                    Color(0xFFF6F7F9),
                    Color(0xFFDDE1E6),
                    Color(0xFF9BA2AC)
                )
            )
        )
        drawPath(body, Color(0xFF7C838D), style = Stroke(width = 0.008f * w))

        // 보닛 주름선
        mirrored { mx ->
            drawLine(Color(0x33000000), at(mx(0.30f), 0.05f), at(mx(0.22f), 0.265f), strokeWidth = 0.006f * w)
        }

        // 전조등
        val lampColor = colors[Part.HEADLAMP]?.let { lerp(Color.White, it, 0.7f) } ?: Color.White
        mirrored { mx ->
            val lamp = polygon(listOf(at(mx(0.165f), 0.058f), at(mx(0.37f), 0.034f), at(mx(0.37f), 0.058f), at(mx(0.15f), 0.094f)))
            drawPath(lamp, lampColor)
            drawPath(lamp, Color(0xFF7C838D), style = Stroke(width = 0.005f * w))
        }

        // 유리: 앞 유리, 옆 유리, 뒤 유리
        val glass = Brush.verticalGradient(listOf(Color(0xFF55616F), Color(0xFF232B35)))
        val windshield = Path().apply {
            moveTo(0.19f * w, 0.285f * h)
            quadraticTo(0.5f * w, 0.235f * h, 0.81f * w, 0.285f * h)
            lineTo(0.755f * w, 0.39f * h)
            quadraticTo(0.5f * w, 0.375f * h, 0.245f * w, 0.39f * h)
            close()
        }
        drawPath(windshield, glass)
        mirrored { mx ->
            drawPath(
                polygon(listOf(
                    at(mx(0.175f), 0.305f), at(mx(0.228f), 0.40f), at(mx(0.228f), 0.715f),
                    at(mx(0.19f), 0.80f), at(mx(0.158f), 0.77f), at(mx(0.158f), 0.36f))
                ),
                Color(0xFF2B333D)
            )
        }
        val rearWindow = Path().apply {
            moveTo(0.25f * w, 0.725f * h)
            quadraticTo(0.5f * w, 0.74f * h, 0.75f * w, 0.725f * h)
            lineTo(0.79f * w, 0.815f * h)
            quadraticTo(0.5f * w, 0.85f * h, 0.21f * w, 0.815f * h)
            close()
        }
        drawPath(rearWindow, glass)

        // 지붕과 파노라마 선루프
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color(0xFFB9BFC8), Color(0xFFE6E9ED), Color(0xFFB9BFC8))),
            topLeft = at(0.245f, 0.392f),
            size = Size(0.51f * w, 0.33f * h),
            cornerRadius = CornerRadius(0.04f * w, 0.04f * w)
        )
        drawRoundRect(
            color = Color(0xFF2B333D),
            topLeft = at(0.295f, 0.415f),
            size = Size(0.41f * w, 0.24f * h),
            cornerRadius = CornerRadius(0.035f * w, 0.035f * w)
        )

        // 와이퍼
        val wiperColor = colors[Part.WIPER] ?: Color(0xFF0F1318)
        val wiperWidth = (if (Part.WIPER in colors) 0.022f else 0.012f) * w
        drawLine(wiperColor, at(0.27f, 0.378f), at(0.50f, 0.345f), wiperWidth, StrokeCap.Round)
        drawLine(wiperColor, at(0.52f, 0.378f), at(0.74f, 0.340f), wiperWidth, StrokeCap.Round)

        // 후미등
        mirrored { mx ->
            drawPath(
                polygon(listOf(at(mx(0.15f), 0.925f), at(mx(0.40f), 0.958f), at(mx(0.40f), 0.972f), at(mx(0.165f), 0.95f))),
                Color(0xFFB3261E)
            )
        }
        drawLine(Color(0xFFB3261E), at(0.40f, 0.965f), at(0.60f, 0.965f), strokeWidth = 0.006f * h)

        // 고장 위치 표시: 부품마다 빛나는 점을 찍고, 그중 한 곳에 이름표를 단다.
        // 이름표는 차 그림 밖(무대의 여백)까지 나갈 수 있다.
        val labelArea = Rect(-100.dp.toPx(), 0f, w + 100.dp.toPx(), h)
        val placed = mutableListOf<Rect>()
        fun mark(part: Part, spots: List<Offset>, labelDirection: Offset) {
            val color = colors[part] ?: return
            spots.forEach { drawFaultMarker(it, color, phase) }
            drawFaultLabel(
                measurer, spots.first(), labelDirection,
                FaultLabel(part.label, levels.getValue(part)), color, labelArea, placed
            )
        }
        mark(Part.HEADLAMP, listOf(at(0.74f, 0.063f), at(0.26f, 0.063f)), Offset(1f, -0.1f))
        mark(Part.WIPER, listOf(at(0.5f, 0.345f)), Offset(1f, 0.05f))
        mark(
            Part.TIRE,
            listOf(at(0.10f, 0.215f), at(0.90f, 0.215f), at(0.10f, 0.77f), at(0.90f, 0.77f)),
            Offset(-1f, -0.35f)
        )
        mark(
            Part.BRAKE,
            listOf(at(0.785f, 0.215f), at(0.215f, 0.215f), at(0.215f, 0.77f), at(0.785f, 0.77f)),
            Offset(1f, 0.45f)
        )
        val hoodPart = if (Part.POWER in colors && Part.COOLANT !in colors) Part.POWER else Part.COOLANT
        mark(hoodPart, listOf(at(0.5f, 0.165f)), Offset(-1f, 0.1f))
    }
}
