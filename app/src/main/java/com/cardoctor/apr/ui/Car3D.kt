package com.cardoctor.apr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardoctor.apr.diagnosis.Advice
import com.cardoctor.apr.diagnosis.Part
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * 3D 차량 뷰.
 *
 * 외부 3D 라이브러리나 모델 파일 없이, 코드로 만든 저폴리곤 차량을 Canvas에 직접 그린다.
 * 좌표계: x = 차 앞쪽, y = 위, z = 차 오른쪽. 단위는 대략 미터.
 * 그리는 순서는 "묶음(차체, 실내, 바퀴 …) 순서 → 묶음 안에서는 먼 면부터"이고, 뒤를 향한 면은 그리지 않는다.
 */

private data class Vec(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec) = Vec(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Float) = Vec(x * k, y * k, z * k)
    infix fun dot(o: Vec) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec) = Vec(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun normalized(): Vec {
        val length = sqrt(this dot this)
        return if (length == 0f) this else this * (1f / length)
    }
}

/** 눌렀을 때 부품으로 이어지는 영역. */
private enum class Zone { HEADLAMP, WIPER, TIRE, BRAKE, HOOD }

/** 그리는 순서를 정하는 묶음. */
private enum class Group { BODY, BODY_DETAIL, CABIN, CABIN_DETAIL, WHEEL }

private class Face(
    val points: List<Vec>,
    val color: Color,
    val group: Group,
    val zone: Zone? = null,
    /** 고장 색을 면 전체에 칠할지(true), 살짝 물들이기만 할지(false). */
    val fullTint: Boolean = true,
    /** 바퀴가 차의 어느 쪽에 있는지. 오른쪽 +1, 왼쪽 -1, 바퀴가 아니면 0. */
    val side: Int = 0
) {
    val centroid: Vec = points.reduce { a, b -> a + b } * (1f / points.size)
    val normal: Vec = ((points[1] - points[0]) cross (points[2] - points[0])).normalized()
}

private val BodyColor = Color(0xFFD9DDE3)
private val GlassColor = Color(0xFF2B333D)
private val TireColor = Color(0xFF23272E)
private val RimColor = Color(0xFFB8BEC7)

/** 면의 바깥쪽이 물체 중심의 반대편을 향하도록 점 순서를 맞춘다. */
private fun face(
    points: List<Vec>,
    objectCenter: Vec,
    color: Color,
    group: Group,
    zone: Zone? = null,
    fullTint: Boolean = true,
    side: Int = 0
): Face {
    val normal = (points[1] - points[0]) cross (points[2] - points[0])
    val centroid = points.reduce { a, b -> a + b } * (1f / points.size)
    val ordered = if ((normal dot (centroid - objectCenter)) < 0f) points.reversed() else points
    return Face(ordered, color, group, zone, fullTint, side)
}

private fun mix(a: Vec, b: Vec, t: Float) = a + (b - a) * t

private val CAR_MESH: List<Face> by lazy { buildCar() }

private fun buildCar(): List<Face> {
    val faces = mutableListOf<Face>()

    // --- 차체 아래쪽: 옆에서 본 윤곽을 폭 방향으로 밀어서 만든다.
    val half = 0.86f
    val profile = listOf(
        -2.20f to 0.35f, 2.15f to 0.35f, 2.25f to 0.55f, 2.20f to 0.80f,
        1.00f to 0.98f, -2.00f to 1.00f, -2.20f to 0.90f
    )
    val bodyCenter = Vec(0f, 0.65f, 0f)
    profile.indices.forEach { i ->
        val (x1, y1) = profile[i]
        val (x2, y2) = profile[(i + 1) % profile.size]
        val isHood = i == 3 // (2.20, 0.80) → (1.00, 0.98) 구간이 보닛
        faces += face(
            listOf(Vec(x1, y1, -half), Vec(x2, y2, -half), Vec(x2, y2, half), Vec(x1, y1, half)),
            bodyCenter, BodyColor, Group.BODY, zone = if (isHood) Zone.HOOD else null
        )
    }
    listOf(-half, half).forEach { z ->
        faces += face(profile.map { (x, y) -> Vec(x, y, z) }, bodyCenter, BodyColor, Group.BODY)
    }

    // --- 차체 장식: 전조등, 그릴, 후미등
    fun frontX(y: Float) = 2.25f - (y - 0.55f) / 0.25f * 0.05f + 0.012f
    listOf(-1f, 1f).forEach { s ->
        faces += face(
            listOf(
                Vec(frontX(0.60f), 0.60f, 0.40f * s), Vec(frontX(0.60f), 0.60f, 0.83f * s),
                Vec(frontX(0.78f), 0.78f, 0.83f * s), Vec(frontX(0.78f), 0.78f, 0.40f * s)
            ),
            bodyCenter, Color(0xFFF7F9FB), Group.BODY_DETAIL, zone = Zone.HEADLAMP
        )
        faces += face(
            listOf(
                Vec(-2.212f, 0.62f, 0.38f * s), Vec(-2.212f, 0.62f, 0.84f * s),
                Vec(-2.212f, 0.86f, 0.84f * s), Vec(-2.212f, 0.86f, 0.38f * s)
            ),
            bodyCenter, Color(0xFFC62828), Group.BODY_DETAIL
        )
    }
    fun bumperX(y: Float) = 2.15f + (y - 0.35f) / 0.20f * 0.10f + 0.012f
    faces += face(
        listOf(
            Vec(bumperX(0.40f), 0.40f, -0.50f), Vec(bumperX(0.40f), 0.40f, 0.50f),
            Vec(bumperX(0.52f), 0.52f, 0.50f), Vec(bumperX(0.52f), 0.52f, -0.50f)
        ),
        bodyCenter, Color(0xFF3A4048), Group.BODY_DETAIL
    )
    faces += face(
        listOf(
            Vec(-2.212f, 0.78f, -0.38f), Vec(-2.212f, 0.78f, 0.38f),
            Vec(-2.212f, 0.86f, 0.38f), Vec(-2.212f, 0.86f, -0.38f)
        ),
        bodyCenter, Color(0xFF8E1C1C), Group.BODY_DETAIL
    )

    // --- 실내 부분(유리와 지붕): 위로 갈수록 좁아지는 상자
    val cabinCenter = Vec(-0.5f, 1.2f, 0f)
    fun bottomFront(s: Float) = Vec(0.95f, 0.98f, 0.80f * s)
    fun topFront(s: Float) = Vec(0.35f, 1.50f, 0.66f * s)
    fun topRear(s: Float) = Vec(-1.30f, 1.50f, 0.66f * s)
    fun bottomRear(s: Float) = Vec(-1.90f, 1.00f, 0.80f * s)

    faces += face(
        listOf(bottomFront(-1f), bottomFront(1f), topFront(1f), topFront(-1f)),
        cabinCenter, GlassColor, Group.CABIN, zone = Zone.WIPER, fullTint = false
    )
    faces += face(
        listOf(topFront(-1f), topFront(1f), topRear(1f), topRear(-1f)),
        cabinCenter, BodyColor, Group.CABIN
    )
    faces += face(
        listOf(topRear(-1f), topRear(1f), bottomRear(1f), bottomRear(-1f)),
        cabinCenter, GlassColor, Group.CABIN
    )
    listOf(-1f, 1f).forEach { s ->
        val side = listOf(bottomFront(s), topFront(s), topRear(s), bottomRear(s))
        faces += face(side, cabinCenter, BodyColor, Group.CABIN)
        // 옆 유리: 옆면을 조금 줄여서 바깥으로 살짝 띄운다.
        val middle = side.reduce { a, b -> a + b } * 0.25f
        val glass = side.map { mix(it, middle, 0.16f) + Vec(0f, 0f, 0.012f * s) }
        faces += face(glass, cabinCenter, GlassColor, Group.CABIN_DETAIL)
    }
    // 파노라마 선루프
    val roof = listOf(topFront(-1f), topFront(1f), topRear(1f), topRear(-1f))
    val roofMiddle = roof.reduce { a, b -> a + b } * 0.25f
    faces += face(
        roof.map { mix(it, roofMiddle, 0.2f) + Vec(0f, 0.012f, 0f) },
        cabinCenter, GlassColor, Group.CABIN_DETAIL
    )

    // 와이퍼: 앞 유리 면 위의 얇은 띠 두 개
    val glassNormal = ((bottomFront(1f) - bottomFront(-1f)) cross (topFront(-1f) - bottomFront(-1f))).normalized()
    val outward = if ((glassNormal dot (bottomFront(1f) - cabinCenter)) < 0f) glassNormal * -1f else glassNormal
    fun onWindshield(u: Float, v: Float) =
        mix(Vec(0.95f, 0.98f, 0.80f * u), Vec(0.35f, 1.50f, 0.66f * u), v) + outward * 0.015f
    listOf(-0.62f to -0.08f, 0.06f to 0.60f).forEach { (u1, u2) ->
        faces += face(
            listOf(
                onWindshield(u1, 0.05f), onWindshield(u2, 0.24f),
                onWindshield(u2, 0.29f), onWindshield(u1, 0.10f)
            ),
            cabinCenter, Color(0xFF0C0F13), Group.CABIN_DETAIL, zone = Zone.WIPER
        )
    }

    // --- 바퀴 4개
    val segments = 18
    val radius = 0.37f
    listOf(1.35f, -1.35f).forEach { cx ->
        listOf(-1, 1).forEach { side ->
            val zIn = 0.84f * side
            val zOut = 0.99f * side
            val center = Vec(cx, radius, (zIn + zOut) / 2f)
            fun ring(r: Float, z: Float) = List(segments) { i ->
                val angle = 2.0 * PI * i / segments
                Vec(cx + r * cos(angle).toFloat(), radius + r * sin(angle).toFloat(), z)
            }
            val inner = ring(radius, zIn)
            val outer = ring(radius, zOut)
            for (i in 0 until segments) {
                val j = (i + 1) % segments
                faces += face(
                    listOf(inner[i], inner[j], outer[j], outer[i]),
                    center, TireColor, Group.WHEEL, zone = Zone.TIRE, side = side
                )
            }
            faces += face(outer, center, Color(0xFF2E333A), Group.WHEEL, zone = Zone.TIRE, side = side)
            faces += face(ring(0.24f, zOut + 0.006f * side), center, RimColor, Group.WHEEL, zone = Zone.BRAKE, side = side)
            faces += face(ring(0.08f, zOut + 0.012f * side), center, Color(0xFF5B6470), Group.WHEEL, zone = Zone.BRAKE, side = side)
        }
    }
    return faces
}

private class DrawnFace(val polygon: List<Offset>, val zone: Zone?)

private fun contains(polygon: List<Offset>, point: Offset): Boolean {
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[j]
        if ((a.y > point.y) != (b.y > point.y) &&
            point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x
        ) inside = !inside
        j = i
    }
    return inside
}

private const val DEFAULT_YAW = -38f
private const val DEFAULT_PITCH = 22f

/**
 * 끌어서 돌리고 두 손가락(또는 버튼)으로 확대하는 3D 차량 그림.
 * 고장 난 부품은 등급 색으로 칠하고, 부위를 누르면 onPartTap이 불린다.
 */
@Composable
internal fun Car3DView(
    advices: List<Advice>,
    modifier: Modifier = Modifier,
    /** 4등급 표시를 깜빡이게 하는 값. 1이면 원래 색, 0에 가까울수록 옅어진다. */
    pulse: Float = 1f,
    onPartTap: (Part) -> Unit = {}
) {
    var yaw by remember { mutableFloatStateOf(DEFAULT_YAW) }
    var pitch by remember { mutableFloatStateOf(DEFAULT_PITCH) }
    var zoom by remember { mutableFloatStateOf(1f) }

    val colors = advices.associate {
        val color = levelColor(it.level)
        it.part to if (it.level >= 4) lerp(Color.White, color, pulse) else color
    }
    fun zoneColor(zone: Zone?): Color? = when (zone) {
        Zone.HEADLAMP -> colors[Part.HEADLAMP]
        Zone.WIPER -> colors[Part.WIPER]
        Zone.TIRE -> colors[Part.TIRE]
        Zone.BRAKE -> colors[Part.BRAKE]
        Zone.HOOD -> colors[Part.POWER] ?: colors[Part.COOLANT]
        null -> null
    }
    fun zonePart(zone: Zone): Part = when (zone) {
        Zone.HEADLAMP -> Part.HEADLAMP
        Zone.WIPER -> Part.WIPER
        Zone.TIRE -> Part.TIRE
        Zone.BRAKE -> Part.BRAKE
        Zone.HOOD -> if (Part.POWER in colors && Part.COOLANT !in colors) Part.POWER else Part.COOLANT
    }

    // 마지막으로 그린 면들(앞에 있는 면이 뒤쪽). 눌린 위치의 부품을 찾는 데 쓴다.
    val drawn = remember { mutableListOf<DrawnFace>() }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        yaw += pan.x * 0.35f
                        pitch = (pitch + pan.y * 0.25f).coerceIn(4f, 78f)
                        zoom = (zoom * gestureZoom).coerceIn(0.6f, 2.4f)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val hit = drawn.lastOrNull { contains(it.polygon, tap) }
                        hit?.zone?.let { onPartTap(zonePart(it)) }
                    }
                }
        ) {
            val yawRad = yaw * PI.toFloat() / 180f
            val pitchRad = pitch * PI.toFloat() / 180f
            val cosYaw = cos(yawRad)
            val sinYaw = sin(yawRad)
            val cosPitch = cos(pitchRad)
            val sinPitch = sin(pitchRad)
            val distance = 9f
            val unit = minOf(size.width, size.height) / 4.8f * zoom
            val origin = Offset(size.width / 2f, size.height * 0.54f)
            val modelCenter = Vec(0f, 0.72f, 0f)

            /** 카메라 좌표로 변환. z가 클수록 보는 사람에게 가깝다. */
            fun toCamera(p: Vec): Vec {
                val v = p - modelCenter
                val x1 = v.x * cosYaw + v.z * sinYaw
                val z1 = -v.x * sinYaw + v.z * cosYaw
                return Vec(x1, v.y * cosPitch - z1 * sinPitch, v.y * sinPitch + z1 * cosPitch)
            }

            fun toScreen(camera: Vec): Offset {
                val scale = distance / (distance - camera.z) * unit
                return Offset(origin.x + camera.x * scale, origin.y - camera.y * scale)
            }

            val cameraPosition = modelCenter + Vec(-distance * cosPitch * sinYaw, distance * sinPitch, distance * cosPitch * cosYaw)
            val light = Vec(0.35f, 0.85f, 0.4f).normalized()

            // 바닥 그림자
            val shadow = listOf(Vec(-2.3f, 0f, -1f), Vec(2.3f, 0f, -1f), Vec(2.3f, 0f, 1f), Vec(-2.3f, 0f, 1f))
                .map { toScreen(toCamera(it)) }
            drawPath(polygonPath(shadow), Color(0x1F000000))

            fun order(face: Face): Int = when (face.group) {
                // 카메라 반대편 바퀴는 차체에 가려지므로 맨 먼저, 카메라 쪽 바퀴는 맨 나중에 그린다.
                Group.WHEEL -> if (face.side * (cameraPosition.z - modelCenter.z) >= 0f) 5 else 0
                Group.BODY -> 1
                Group.BODY_DETAIL -> 2
                Group.CABIN -> 3
                Group.CABIN_DETAIL -> 4
            }

            drawn.clear()
            CAR_MESH
                .filter { (it.normal dot (cameraPosition - it.centroid)) > 0f }
                .sortedWith(compareBy<Face> { order(it) }.thenBy { toCamera(it.centroid).z })
                .forEach { face ->
                    val polygon = face.points.map { toScreen(toCamera(it)) }
                    val brightness = 0.7f + 0.3f * max(0f, face.normal dot light)
                    val fault = zoneColor(face.zone)
                    val color = when {
                        fault == null -> shade(face.color, brightness)
                        face.fullTint -> shade(fault, 0.82f + 0.18f * brightness)
                        else -> shade(lerp(face.color, fault, 0.35f), brightness)
                    }
                    val path = polygonPath(polygon)
                    drawPath(path, color)
                    // 면 사이의 실틈이 보이지 않게 같은 색으로 가장자리를 한 번 더 그린다.
                    drawPath(path, color, style = Stroke(width = 1.5f))
                    drawn += DrawnFace(polygon, face.zone)
                }
        }

        Column(
            modifier = Modifier.align(Alignment.TopEnd),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ViewButton("+") { zoom = (zoom * 1.2f).coerceAtMost(2.4f) }
            ViewButton("−") { zoom = (zoom / 1.2f).coerceAtLeast(0.6f) }
            ViewButton("↺") {
                yaw = DEFAULT_YAW
                pitch = DEFAULT_PITCH
                zoom = 1f
            }
        }
    }
}

@Composable
private fun ViewButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(label, fontSize = 22.sp, color = Color(0xFF1B1F24))
    }
}

private fun polygonPath(points: List<Offset>) = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    close()
}

private fun shade(color: Color, brightness: Float): Color {
    val b = brightness.coerceIn(0f, 1f)
    return Color(color.red * b, color.green * b, color.blue * b, color.alpha)
}
