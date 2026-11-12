package com.cardoctor.apr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardoctor.apr.diagnosis.Advice
import com.cardoctor.apr.diagnosis.LevelInfo
import com.cardoctor.apr.diagnosis.Part
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * 차량 그림을 화면에 놓고, 그 위에 고장 표시를 그린다.
 * 기본은 위에서 내려다본 모습(차 앞쪽이 위)이고, 끌면 기울이고 돌려 볼 수 있다.
 *
 * 입체는 "같은 평면 그림을 높이별로 겹쳐 쌓는" 방식으로 만든다.
 *   바닥 그림자 → 차체 윤곽을 바닥부터 허리선까지 쌓음(옆면) → 허리선 높이에 윗면 그림
 *   → 유리 윤곽을 지붕 윤곽으로 조금씩 줄여 가며 쌓음(기울어진 유리) → 지붕 높이에 지붕 그림
 * 위에서 똑바로 보면 모든 층이 겹쳐서 평면 그림 그대로 보이고, 기울이면 층이 어긋나 옆면이 드러난다.
 * 그림의 모양과 재료는 CarArt.kt에 있다.
 */

/** 가장 많이 눕혀서 볼 때의 각도(수평에서 올려다본 각). 90도가 위에서 똑바로 본 모습. */
private const val MIN_ELEVATION = 28f

private val TAIL_TINT = ColorFilter.tint(Color(0xFFD9342B))
private val FRONT_LAMP_TINT = ColorFilter.tint(Color(0xFFF4F7FA))
private val GRILLE_TINT = ColorFilter.tint(Color(0xFF1E2228))
private val ARCH_TINT = ColorFilter.tint(Color(0xFF14171B))

/** 고장 표시를 찍는 자리. [sideNormal]이 0이 아니면 옆면에 있는 자리라 반대편에서는 보이지 않는다. */
private class Spot(val dx: Float, val y: Float, val z: Float, val sideNormal: Float = 0f)

/** 부품별 표시 자리와, 이름표를 차의 어느 쪽에 둘지. */
private class PartSpots(val part: Part, val labelOnRight: Boolean, val spots: List<Spot>)

private val PART_SPOTS = listOf(
    PartSpots(Part.HEADLAMP, true, listOf(Spot(0.35f, 0.068f, Z_BELT), Spot(-0.35f, 0.068f, Z_BELT))),
    PartSpots(Part.WIPER, true, listOf(Spot(0.14f, 0.275f, Z_BELT + 0.012f))),
    PartSpots(
        Part.BRAKE, true,
        AXLES.flatMap { y -> listOf(Spot(0.33f, y, Z_BELT), Spot(-0.33f, y, Z_BELT)) }
    ),
    PartSpots(Part.COOLANT, false, listOf(Spot(-0.06f, 0.115f, Z_BELT))),
    PartSpots(Part.POWER, false, listOf(Spot(-0.08f, 0.215f, Z_BELT))),
    PartSpots(
        Part.TIRE, false,
        AXLES.flatMap { y -> listOf(Spot(-0.443f, y, WHEEL_R, -1f), Spot(0.443f, y, WHEEL_R, 1f)) }
    )
)

/** 화면에 놓을 이름표 하나. */
private class Label(
    val part: Part,
    val color: Color,
    val level: Int,
    val anchor: Offset,
    val others: List<Offset>,
    val onRight: Boolean,
    val name: TextLayoutResult,
    val state: TextLayoutResult
)

/**
 * 고장 난 부품은 등급 색으로 옅게 물들이고, 그 위치에 등급 숫자가 든 점을 찍는다.
 * 부품 이름과 해야 할 일은 차 옆 여백에 글자로 적고, 점과 가는 선으로 잇는다.
 * 부품이나 글자를 누르면 [onPartTap]이 불린다. 차를 끌면 기울이고 돌려 볼 수 있다.
 */
@Composable
internal fun CarDiagram(
    advices: List<Advice>,
    modifier: Modifier = Modifier,
    /** 4등급 표시를 깜빡이게 하는 값. 1이면 원래 색, 0에 가까울수록 옅어진다. */
    pulse: Float = 1f,
    onPartTap: (Part) -> Unit = {}
) {
    // 보는 방향. yaw는 차를 시계 방향으로 돌린 각도, elevation은 90이면 위에서 똑바로 본 모습.
    var yaw by remember { mutableFloatStateOf(0f) }
    var elevation by remember { mutableFloatStateOf(90f) }

    val levels = advices.associate { it.part to it.level }
    // 점과 글자의 색. 4등급은 깜빡인다. 차 그림 자체의 색은 깜빡이지 않는 등급 색을 쓴다.
    val markerColors = advices.associate {
        val color = levelColor(it.level)
        it.part to if (it.level >= 4) lerp(Color.White, color, pulse) else color
    }
    // 이름표 글자의 배치 결과를 넉넉히 기억해 둔다. 차를 돌리는 동안 같은 글자를 매번 다시 배치하지 않게 한다.
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val currentOnPartTap by rememberUpdatedState(onPartTap)

    // 한 번 그려 둔 차량 그림. 차의 크기가 바뀌면 새로 만든다.
    val artHolder = remember { arrayOfNulls<CarArt>(1) }

    // 마지막으로 그린 이름표와 부품 자리. 누른 위치의 부품을 찾는 데 쓴다.
    val labelHits = remember { mutableListOf<Pair<Rect, Part>>() }
    val spotHits = remember { mutableListOf<Pair<Offset, Part>>() }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        yaw += drag.x * 0.35f
                        elevation = (elevation + drag.y * 0.22f).coerceIn(MIN_ELEVATION, 90f)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val label = labelHits.firstOrNull { it.first.contains(tap) }
                        if (label != null) {
                            currentOnPartTap(label.second)
                        } else {
                            val reach = 52.dp.toPx()
                            spotHits
                                .map { it to (it.first - tap).getDistance() }
                                .filter { it.second < reach }
                                .minByOrNull { it.second }
                                ?.let { currentOnPartTap(it.first.second) }
                        }
                    }
                }
        ) {
            // 차 크기. 좌우에 이름표를 둘 여백을 남긴다. 그림이 흐려지지 않게 짝수 픽셀로 맞춘다.
            val carHeight = (minOf(size.height * 0.86f, 640.dp.toPx(), size.width * 0.38f / CAR_ASPECT).toInt() / 2) * 2
            val carWidth = ((carHeight * CAR_ASPECT).toInt() / 2) * 2
            if (carHeight < 40) return@Canvas

            val art = artHolder[0]?.takeIf { it.w == carWidth && it.h == carHeight }
                ?: CarArt(carWidth, carHeight, this).also { artHolder[0] = it }
            val g = art.geo
            val w = g.w
            val h = g.h
            val pad = art.pad.toFloat()

            val yawRad = yaw * PI.toFloat() / 180f
            val elevationRad = elevation * PI.toFloat() / 180f
            val cosYaw = cos(yawRad)
            val sinYaw = sin(yawRad)
            val cosE = cos(elevationRad).coerceAtLeast(0f)
            val sinE = sin(elevationRad)
            // 기울이면 층이 위로 쌓이므로, 차가 가운데에 있도록 중심을 그만큼 내린다.
            val center = Offset(
                (size.width / 2f).roundToInt().toFloat(),
                (size.height / 2f + Z_ROOF * h * cosE / 2f).roundToInt().toFloat()
            )

            /** 높이 z의 수평면에 그린다. 그 안에서는 차 사각형 좌표(0..w, 0..h)를 그대로 쓴다. */
            fun plane(z: Float, block: DrawScope.() -> Unit) {
                withTransform({
                    translate(center.x, center.y - z * h * cosE)
                    scale(1f, sinE, Offset.Zero)
                    rotate(yaw, Offset.Zero)
                    translate(-w / 2f, -h / 2f)
                }) { block() }
            }

            /** 차 위의 한 점이 화면의 어디에 오는지. plane()과 같은 계산이다. */
            fun project(dx: Float, y: Float, z: Float): Offset {
                val lx = dx * w
                val ly = (y - 0.5f) * h
                return Offset(
                    center.x + lx * cosYaw - ly * sinYaw,
                    center.y + (lx * sinYaw + ly * cosYaw) * sinE - z * h * cosE
                )
            }

            val lampFault = levels[Part.HEADLAMP]?.let(::levelColor)
            val wiperFault = levels[Part.WIPER]?.let(::levelColor)
            val tireFault = levels[Part.TIRE]?.let(::levelColor)
            val spriteCorner = Offset(-pad, -pad)

            // ================================================================ 바닥 그림자
            plane(0f) { drawImage(art.shadow, topLeft = spriteCorner) }

            // ================================================================ 옆면: 바닥부터 허리선까지 쌓기
            val tireColor = tireFault?.let { lerp(Color(0xFF1B1E23), it, 0.8f) } ?: Color(0xFF1B1E23)
            // 평소에는 타이어가 아주 조금만 보이고, 고장일 때는 색이 보이도록 조금 더 내민다.
            val tireEdge = if (tireFault != null) 0.452f else 0.441f
            val lampTint = lampFault?.let { ColorFilter.tint(it) }
            val padInt = art.pad

            /**
             * 이미지에서 차 사각형 좌표의 (left, top)~(right, bottom) 부분만 그 자리에 그린다.
             * 윤곽 이미지의 일부만 그리면 "윤곽 안쪽이면서 이 사각형 안쪽"인 모양이 된다.
             */
            fun DrawScope.piece(image: ImageBitmap, left: Float, top: Float, right: Float, bottom: Float, tint: ColorFilter) {
                val l = left.toInt().coerceIn(-padInt, art.w + padInt)
                val t = top.toInt().coerceIn(-padInt, art.h + padInt)
                val r = right.toInt().coerceIn(-padInt, art.w + padInt)
                val b = bottom.toInt().coerceIn(-padInt, art.h + padInt)
                if (r <= l || b <= t) return
                drawImage(
                    image = image,
                    srcOffset = IntOffset(l + padInt, t + padInt),
                    srcSize = IntSize(r - l, b - t),
                    dstOffset = IntOffset(l, t),
                    dstSize = IntSize(r - l, b - t),
                    colorFilter = tint
                )
            }

            // 기울여 볼 때 반대편 옆면의 바퀴와 휠 하우스는 차체에 가려 보이지 않으므로 그리지 않는다.
            fun sideShown(s: Float) = cosE < 0.05f || s * sinYaw > -0.05f

            art.walls.forEach { layer ->
                plane(layer.z) {
                    if (layer.tint != null) {
                        drawImage(art.silhouette, topLeft = spriteCorner, colorFilter = layer.tint)
                        if (layer.tailLamp) piece(art.rim, -pad, 0.90f * h, w + pad, h + pad, TAIL_TINT)
                        if (layer.frontLamp) {
                            piece(art.rim, -pad, -pad, w + pad, 0.10f * h, FRONT_LAMP_TINT)
                            if (lampTint != null) {
                                piece(art.rim, g.x(0.27f), -pad, w + pad, 0.10f * h, lampTint)
                                piece(art.rim, -pad, -pad, g.x(-0.27f), 0.10f * h, lampTint)
                            }
                        }
                        if (layer.grille) piece(art.rim, g.x(-0.30f), -pad, g.x(0.30f), 0.06f * h, GRILLE_TINT)
                        // 휠 하우스: 바퀴 둘레의 어두운 반원
                        if (layer.archHalf > 0f) {
                            AXLES.forEach { axle ->
                                val top = (axle - layer.archHalf) * h
                                val bottom = (axle + layer.archHalf) * h
                                if (sideShown(1f)) piece(art.silhouette, g.x(0.395f), top, w + pad, bottom, ARCH_TINT)
                                if (sideShown(-1f)) piece(art.silhouette, -pad, top, g.x(-0.395f), bottom, ARCH_TINT)
                            }
                        }
                    }
                    // 바퀴: 높이에 따라 길이가 달라지는 띠를 쌓으면 옆에서 볼 때 원이 된다.
                    if (layer.tireHalf > 0f) {
                        AXLES.forEach { axle ->
                            bothSides { s ->
                                if (!sideShown(s)) return@bothSides
                                drawRect(
                                    color = tireColor,
                                    topLeft = Offset(minOf(g.x(s * 0.392f), g.x(s * tireEdge)), (axle - layer.tireHalf) * h),
                                    size = Size((tireEdge - 0.392f) * w, 2f * layer.tireHalf * h)
                                )
                                if (layer.rimHalf > 0f) {
                                    drawRect(
                                        color = Color(0xFFC3C8D0),
                                        // 띠의 폭이 층 간격보다 넓어야 옆에서 볼 때 줄무늬가 생기지 않는다.
                                        topLeft = Offset(
                                            minOf(g.x(s * (tireEdge - 0.026f)), g.x(s * tireEdge)),
                                            (axle - layer.rimHalf) * h
                                        ),
                                        size = Size(0.026f * w, 2f * layer.rimHalf * h)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ================================================================ 윗면
            plane(Z_BELT) {
                drawImage(art.top, topLeft = spriteCorner)
                // 전조등이 고장이면 렌즈를 등급 색으로 덧칠한다.
                if (lampFault != null) {
                    bothSides { s ->
                        val lens = art.lampLens(s)
                        drawPath(lens, lerp(Color(0xFFE9EDF1), lampFault, 0.85f))
                        drawPath(lens, Color(0xFF3A4048), style = Stroke(width = 0.004f * w))
                    }
                }
            }

            // ================================================================ 유리: 허리선에서 지붕까지 쌓기
            val glassCorner = Offset(art.glassLeft.toFloat(), art.glassTop.toFloat())
            for (j in 0..GLASS_STEPS) {
                plane(mix(Z_BELT, Z_ROOF, j / GLASS_STEPS.toFloat())) {
                    drawImage(art.glassRings[j], topLeft = glassCorner)
                    if (j == 2) {
                        // 와이퍼
                        val wiperColor = wiperFault ?: Color(0xFF05070A)
                        val wiperWidth = (if (wiperFault != null) 0.016f else 0.008f) * w
                        drawLine(wiperColor, g.at(-0.28f, 0.283f), g.at(-0.02f, 0.2685f), wiperWidth, StrokeCap.Round)
                        drawLine(wiperColor, g.at(0.01f, 0.2695f), g.at(0.27f, 0.2835f), wiperWidth, StrokeCap.Round)
                    }
                }
            }

            // 유리의 반사와 뒤 유리 열선. 유리가 기울어 보이기 시작하면 평면에 그린 무늬가 어긋나므로,
            // 위에서 볼 때만 보이고 기울일수록 사라지게 한다.
            val glassDetail = ((elevation - 76f) / 12f).coerceIn(0f, 1f)
            if (glassDetail > 0f) {
                plane(mix(Z_BELT, Z_ROOF, 0.5f)) {
                    drawImage(art.glassDetail, topLeft = spriteCorner, alpha = glassDetail)
                }
            }

            // ================================================================ 지붕
            plane(Z_ROOF) { drawImage(art.roof, topLeft = spriteCorner) }

            // ================================================================ 고장 표시
            // 화면 좌표에 그린다. 차를 돌려도 글자는 눕지 않는다.
            spotHits.clear()
            labelHits.clear()

            /** 옆면에 있는 자리가 지금 보는 쪽에 있는지. 위에서 똑바로 볼 때는 모두 보인다. */
            fun visible(spot: Spot) = spot.sideNormal * sinYaw * cosE >= -0.02f

            // 차가 화면에서 차지하는 범위. 이름표를 그 바깥에 둔다.
            val corners = listOf(-0.5f, 0.5f).flatMap { dx ->
                listOf(0f, 1f).flatMap { y -> listOf(project(dx, y, 0f), project(dx, y, Z_ROOF)) }
            }
            val carBounds = Rect(
                corners.minOf { it.x }, corners.minOf { it.y },
                corners.maxOf { it.x }, corners.maxOf { it.y }
            )

            // 처음 모습(위에서 본 모습)에서는 부품마다 정해 둔 쪽에 이름표를 둔다.
            // 돌려 본 뒤에는 자리가 뒤바뀌므로, 그때그때 화면에서 가까운 쪽에 둔다.
            val turned = ((yaw % 360f) + 540f) % 360f - 180f
            val designedLayout = elevation > 84f && abs(turned) < 20f

            val labels = PART_SPOTS.mapNotNull { entry ->
                val shown = entry.spots.filter(::visible).map { project(it.dx, it.y, it.z) }
                shown.forEach { spotHits += it to entry.part }
                val color = markerColors[entry.part] ?: return@mapNotNull null
                if (shown.isEmpty()) return@mapNotNull null
                val level = levels.getValue(entry.part)
                val onRight = if (designedLayout) {
                    entry.labelOnRight
                } else {
                    shown.maxBy { abs(it.x - center.x) }.x >= center.x
                }
                // 이름표 쪽에 가장 가까운 자리에서 선을 뽑는다. 같으면 화면 아래쪽 자리.
                val anchor = shown.maxWith(
                    compareBy<Offset> { if (onRight) it.x else -it.x }.thenBy { it.y }
                )
                Label(
                    part = entry.part,
                    color = color,
                    level = level,
                    anchor = anchor,
                    others = shown - anchor,
                    onRight = onRight,
                    name = measurer.measure(
                        entry.part.label,
                        TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Medium, color = Lux.TextDim)
                    ),
                    state = measurer.measure(
                        LevelInfo.title(level),
                        TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color)
                    )
                )
            }

            val margin = 4.dp.toPx()
            val gap = 34.dp.toPx()
            val bigDot = 14.dp.toPx()
            val smallDot = 7.dp.toPx()

            // 1) 이름표 자리를 정하고 선을 긋는다.
            val placed = mutableListOf<Pair<Label, Rect>>()
            listOf(true, false).forEach { onRight ->
                var nextTop = margin
                labels.filter { it.onRight == onRight }.sortedBy { it.anchor.y }.forEach { label ->
                    val textWidth = maxOf(label.name.size.width, label.state.size.width).toFloat()
                    val textHeight = (label.name.size.height + label.state.size.height).toFloat()

                    // 차 바로 옆에 두되, 그림 영역을 벗어나면 안쪽으로 밀어 넣는다.
                    val wantedLeft = if (onRight) carBounds.right + gap else carBounds.left - gap - textWidth
                    val left = wantedLeft.coerceIn(margin, (size.width - textWidth - margin).coerceAtLeast(margin))
                    // 위아래로 겹치지 않게 차례로 놓는다.
                    val top = maxOf(label.anchor.y - textHeight / 2f, nextTop)
                        .coerceAtMost((size.height - textHeight - margin).coerceAtLeast(margin))
                    nextTop = top + textHeight + 10.dp.toPx()

                    val lineEnd = Offset(
                        if (onRight) left - 10.dp.toPx() else left + textWidth + 10.dp.toPx(),
                        top + textHeight / 2f
                    )
                    drawLine(Lux.TextFaint, label.anchor, lineEnd, strokeWidth = 1.8.dp.toPx())
                    placed += label to Rect(left, top, left + textWidth, top + textHeight)
                }
            }

            // 2) 점: 선이 나오는 자리는 등급 숫자가 든 큰 점, 나머지 자리는 작은 점
            placed.forEach { (label, _) ->
                label.others.forEach {
                    drawCircle(Color(0x33000000), smallDot + 3.dp.toPx(), it)
                    drawCircle(Color.White, smallDot + 2.dp.toPx(), it)
                    drawCircle(label.color, smallDot, it)
                }
                drawCircle(Color(0x33000000), bigDot + 4.dp.toPx(), label.anchor)
                drawCircle(Color.White, bigDot + 3.dp.toPx(), label.anchor)
                drawCircle(label.color, bigDot, label.anchor)
                val number = measurer.measure(
                    label.level.toString(),
                    TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                )
                drawText(
                    number,
                    topLeft = Offset(
                        label.anchor.x - number.size.width / 2f,
                        label.anchor.y - number.size.height / 2f
                    )
                )
            }

            // 3) 글자. 다른 부품의 점에 가려지지 않게 맨 나중에 그린다.
            placed.forEach { (label, rect) ->
                // 차 위에 겹쳐 놓을 수밖에 없을 때는 글자가 읽히도록 흰 바탕을 깐다.
                if (rect.overlaps(carBounds)) {
                    drawRoundRect(
                        color = Color(0xEBFFFFFF),
                        topLeft = Offset(rect.left - 10.dp.toPx(), rect.top - 6.dp.toPx()),
                        size = Size(rect.width + 20.dp.toPx(), rect.height + 12.dp.toPx()),
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                    )
                }
                drawText(
                    label.name,
                    topLeft = Offset(if (label.onRight) rect.left else rect.right - label.name.size.width, rect.top)
                )
                drawText(
                    label.state,
                    topLeft = Offset(
                        if (label.onRight) rect.left else rect.right - label.state.size.width,
                        rect.top + label.name.size.height
                    )
                )
                labelHits += rect to label.part
            }
        }

        // 돌려 본 뒤에는 처음 모습으로 돌아가는 버튼을 보여 준다.
        if (abs(yaw) > 0.5f || elevation < 89.5f) {
            Chip("위에서 보기", on = false, modifier = Modifier.align(Alignment.BottomStart)) {
                yaw = 0f
                elevation = 90f
            }
        } else {
            Caption("차를 끌어서 돌려 볼 수 있습니다", modifier = Modifier.align(Alignment.BottomStart))
        }
    }
}
