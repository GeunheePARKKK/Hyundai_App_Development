package com.cardoctor.apr.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt

/*
 * 차량 그림 위의 고장 위치 표시. 평면 그림과 3D 그림이 같이 쓴다.
 *
 * 부품을 원색으로 덮는 대신, 고장 위치에 작은 점과 은은한 빛 번짐을 두고
 * 바깥으로 퍼지는 고리로 시선을 끈다. 부품 이름과 등급은 가는 선으로 이은 이름표에 적는다.
 */

/** 이름표에 적을 내용. */
internal class FaultLabel(val text: String, val level: Int)

/**
 * 고리가 퍼지는 정도(0~1)를 돌려준다. 고장이 있을 때만 애니메이션이 돈다.
 */
@Composable
internal fun rememberPingPhase(active: Boolean): Float {
    if (!active) return 0f
    val transition = rememberInfiniteTransition(label = "ping")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    return phase
}

/** 고장 위치 하나: 빛 번짐, 퍼지는 고리 두 개, 가운데 점. */
internal fun DrawScope.drawFaultMarker(center: Offset, color: Color, phase: Float) {
    val base = 6.dp.toPx()

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.42f), color.copy(alpha = 0f)),
            center = center,
            radius = base * 4.4f
        ),
        radius = base * 4.4f,
        center = center
    )
    listOf(phase, (phase + 0.5f) % 1f).forEach { p ->
        drawCircle(
            color = color.copy(alpha = (1f - p) * 0.75f),
            radius = base * (1.6f + 3.2f * p),
            center = center,
            style = Stroke(width = base * 0.28f)
        )
    }
    drawCircle(color, base * 1.6f, center, style = Stroke(width = base * 0.24f))
    drawCircle(Color.White, base, center)
    drawCircle(color, base * 0.62f, center)
}

/**
 * 고장 위치에서 가는 선으로 이어진 이름표.
 *
 * @param direction 이름표를 둘 방향(대략적인 값). 이름표는 [bounds] 안으로 밀어 넣는다.
 * @param occupied 이미 놓인 이름표들의 자리. 겹치면 위아래로 비켜서 놓고, 놓은 자리를 여기에 더한다.
 */
internal fun DrawScope.drawFaultLabel(
    measurer: TextMeasurer,
    anchor: Offset,
    direction: Offset,
    label: FaultLabel,
    color: Color,
    bounds: Rect,
    occupied: MutableList<Rect>
) {
    val text = measurer.measure(
        label.text,
        TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    )
    val number = measurer.measure(
        label.level.toString(),
        TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = onLevelColor(label.level))
    )
    val badge = 12.dp.toPx()
    val height = 36.dp.toPx()
    val width = 8.dp.toPx() + badge * 2 + 8.dp.toPx() + text.size.width + 14.dp.toPx()

    val length = sqrt(direction.x * direction.x + direction.y * direction.y).coerceAtLeast(0.001f)
    val reach = 78.dp.toPx()
    val wanted = anchor + Offset(direction.x / length * reach, direction.y / length * reach)
    val margin = 4.dp.toPx()
    val left = (wanted.x - width / 2).coerceIn(bounds.left + margin, (bounds.right - width - margin).coerceAtLeast(bounds.left + margin))
    val topLimit = bounds.top + margin
    val bottomLimit = (bounds.bottom - height - margin).coerceAtLeast(topLimit)
    var top = (wanted.y - height / 2).coerceIn(topLimit, bottomLimit)

    // 다른 이름표와 겹치면 한 칸씩 비킨다. 먼저 원하던 방향(위 또는 아래)으로, 막히면 반대로.
    val step = height + 6.dp.toPx()
    fun overlaps(y: Float) = occupied.any { it.overlaps(Rect(left, y, left + width, y + height)) }
    if (overlaps(top)) {
        val first = if (direction.y <= 0f) -step else step
        val candidates = (1..6).flatMap { listOf(top + first * it, top - first * it) }
        top = candidates.firstOrNull { it in topLimit..bottomLimit && !overlaps(it) } ?: top
    }
    occupied += Rect(left, top, left + width, top + height)

    // 이음선: 고장 위치에서 이름표의 가장 가까운 가장자리까지
    val nearest = Offset(anchor.x.coerceIn(left, left + width), anchor.y.coerceIn(top, top + height))
    val gap = nearest - anchor
    val gapLength = sqrt(gap.x * gap.x + gap.y * gap.y)
    val ring = 10.dp.toPx()
    if (gapLength > ring) {
        val start = anchor + Offset(gap.x / gapLength * ring, gap.y / gapLength * ring)
        drawLine(color, start, nearest, strokeWidth = 1.5.dp.toPx())
    }

    val corner = CornerRadius(height / 2, height / 2)
    drawRoundRect(Color(0xF012161B), Offset(left, top), Size(width, height), corner)
    drawRoundRect(color, Offset(left, top), Size(width, height), corner, style = Stroke(1.5.dp.toPx()))

    val badgeCenter = Offset(left + 8.dp.toPx() + badge, top + height / 2)
    drawCircle(color, badge, badgeCenter)
    drawText(number, topLeft = Offset(badgeCenter.x - number.size.width / 2f, badgeCenter.y - number.size.height / 2f))
    drawText(text, topLeft = Offset(badgeCenter.x + badge + 8.dp.toPx(), top + (height - text.size.height) / 2f))
}
