package com.cardoctor.apr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * 3D 차량 뷰.
 *
 * 외부 3D 라이브러리나 모델 파일 없이, 코드로 만든 차량을 Canvas에 직접 그린다.
 * 좌표계: x = 차 앞쪽, y = 위, z = 차 오른쪽. 단위는 대략 미터.
 *
 * 차체는 "앞뒤 방향으로 잘게 나눈 단면"을 이어 붙인 곡면이다. 단면마다 폭과 높이가 조금씩 달라서
 * 둥근 앞뒤, 기울어진 유리, 위로 갈수록 좁아지는 지붕이 만들어진다. 각 조각이 유리인지, 전조등인지,
 * 보닛인지는 위치로 정한다.
 *
 * 그리는 순서: 카메라 반대편 바퀴·미러 → 차체(먼 면부터) → 차체 위 장식 → 카메라 쪽 바퀴·미러.
 * 뒤를 향한 면은 그리지 않는다.
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

/** 그리는 순서를 정하는 묶음. SIDE는 차 옆에 붙은 것(바퀴, 미러). */
private enum class Group { HULL, HULL_DETAIL, SIDE }

private class Face(
    val points: List<Vec>,
    val color: Color,
    val group: Group,
    val zone: Zone? = null,
    /** 고장 색을 면 전체에 칠할지(true), 살짝 물들이기만 할지(false). */
    val fullTint: Boolean = true,
    /** SIDE 묶음이 차의 어느 쪽에 있는지. 오른쪽 +1, 왼쪽 -1. */
    val side: Int = 0,
    /** SIDE 묶음에서 같은 물체(바퀴 하나)를 묶는 중심점. 먼 물체부터 그린다. */
    val objectCenter: Vec = Vec(0f, 0f, 0f),
    /** 같은 물체 안에서 겹쳐 그리는 순서. 작은 값이 먼저(아래). */
    val rank: Int = 0,
    /** 유리처럼 빛을 덜 타는 면. */
    val glossy: Boolean = false,
    /**
     * 이 값보다 비스듬하게 보이면 그리지 않는다(0이면 정면이 조금이라도 보이면 그림).
     * 휠 하우스처럼 납작한 면이 옆에서 검은 막대로 보이는 것을 막는다.
     */
    val minFacing: Float = 0f
) {
    val centroid: Vec = points.reduce { a, b -> a + b } * (1f / points.size)
    val normal: Vec = ((points[1] - points[0]) cross (points[2] - points[0])).normalized()
}

private val BodyColor = Color(0xFFE4E7EB)
private val TrimColor = Color(0xFFB4BAC3)
private val GlassColor = Color(0xFF0E1319)
private val TireColor = Color(0xFF1F2328)
private val RimColor = Color(0xFFC3C8D0)
private val LampColor = Color(0xFFFBFCFD)
private val TailColor = Color(0xFFC62828)

/** 면의 바깥쪽이 [inside]의 반대편을 향하도록 점 순서를 맞춘다. */
private fun face(
    points: List<Vec>,
    inside: Vec,
    color: Color,
    group: Group,
    zone: Zone? = null,
    fullTint: Boolean = true,
    side: Int = 0,
    objectCenter: Vec = inside,
    rank: Int = 0,
    glossy: Boolean = false,
    minFacing: Float = 0f
): Face {
    val normal = (points[1] - points[0]) cross (points[2] - points[0])
    val centroid = points.reduce { a, b -> a + b } * (1f / points.size)
    val ordered = if ((normal dot (centroid - inside)) < 0f) points.reversed() else points
    return Face(ordered, color, group, zone, fullTint, side, objectCenter, rank, glossy, minFacing)
}

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * 몇 개의 기준점을 부드럽게 잇는 곡선(단조 3차 보간).
 * 기준점 사이에서 값이 튀지 않아 차체 윤곽에 쓰기 좋다.
 */
private class Curve(private val xs: FloatArray, private val ys: FloatArray) {
    private val tangents = FloatArray(xs.size)

    init {
        val n = xs.size
        val slopes = FloatArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]) }
        tangents[0] = slopes[0]
        tangents[n - 1] = slopes[n - 2]
        for (i in 1 until n - 1) {
            val a = slopes[i - 1]
            val b = slopes[i]
            tangents[i] = if (a * b <= 0f) 0f else 2f * a * b / (a + b)
        }
    }

    fun at(x: Float): Float {
        val clamped = x.coerceIn(xs.first(), xs.last())
        var i = 0
        while (i < xs.size - 2 && clamped > xs[i + 1]) i++
        val h = xs[i + 1] - xs[i]
        val t = (clamped - xs[i]) / h
        val t2 = t * t
        val t3 = t2 * t
        return (2 * t3 - 3 * t2 + 1) * ys[i] + (t3 - 2 * t2 + t) * h * tangents[i] +
            (-2 * t3 + 3 * t2) * ys[i + 1] + (t3 - t2) * h * tangents[i + 1]
    }

    fun slope(x: Float): Float = (at(x + 0.02f) - at(x - 0.02f)) / 0.04f
}

// 차체 윤곽의 기준점. x는 뒤(-2.25)에서 앞(2.25)까지.
private val KEY_X = floatArrayOf(-2.25f, -2.10f, -1.75f, -1.00f, -0.30f, 0.25f, 1.00f, 1.90f, 2.15f, 2.25f)

/** 윗선 높이: 트렁크 → 뒤 유리 → 지붕 → 앞 유리 → 보닛 → 앞 끝. */
private val TOP = Curve(KEY_X, floatArrayOf(0.86f, 0.98f, 1.03f, 1.44f, 1.47f, 1.42f, 0.95f, 0.82f, 0.74f, 0.64f))

/** 허리선 높이(옆 유리가 시작되는 높이). 실내가 없는 구간에서는 윗선 바로 아래. */
private val BELT = Curve(KEY_X, floatArrayOf(0.84f, 0.96f, 1.01f, 1.00f, 0.98f, 0.97f, 0.93f, 0.80f, 0.72f, 0.62f))

/** 바닥 높이. 앞뒤 끝은 살짝 들려 있다. */
private val FLOOR = Curve(KEY_X, floatArrayOf(0.46f, 0.34f, 0.30f, 0.30f, 0.30f, 0.30f, 0.30f, 0.30f, 0.33f, 0.44f))

/** 차체 폭의 절반. 앞뒤 끝으로 갈수록 좁아져 위에서 보면 둥글다. */
private val WIDTH = Curve(KEY_X, floatArrayOf(0.66f, 0.82f, 0.89f, 0.91f, 0.91f, 0.91f, 0.90f, 0.86f, 0.78f, 0.62f))

/** 윗면 폭의 절반. 실내 구간에서는 지붕이 차체보다 좁다. */
private val TOP_WIDTH = Curve(KEY_X, floatArrayOf(0.59f, 0.74f, 0.78f, 0.64f, 0.64f, 0.61f, 0.80f, 0.77f, 0.70f, 0.56f))

private const val CROWN = 0.04f // 윗면 가운데가 가장자리보다 볼록한 정도
private const val HULL_STEP = 0.125f

/** 단면 하나의 오른쪽 절반. 아래에서 위로 9개 점. */
private fun halfSection(x: Float): List<Vec> {
    val floor = FLOOR.at(x)
    val belt = BELT.at(x)
    val top = TOP.at(x)
    val w = WIDTH.at(x)
    val wt = TOP_WIDTH.at(x)
    val h = belt - floor
    return listOf(
        Vec(x, floor, 0.80f * w),                                   // 0 바닥 모서리
        Vec(x, floor + 0.10f * h, 0.96f * w),                       // 1
        Vec(x, floor + 0.45f * h, w),                               // 2 가장 넓은 곳
        Vec(x, floor + 0.85f * h, 0.985f * w),                      // 3 어깨
        Vec(x, belt, 0.94f * w),                                    // 4 허리선
        Vec(x, belt + 0.88f * (top - belt), mix(0.94f * w, wt, 0.9f)), // 5 옆 유리 윗변
        Vec(x, top, 0.86f * wt),                                    // 6 윗면 가장자리
        Vec(x, top + 0.7f * CROWN, 0.45f * wt),                     // 7
        Vec(x, top + CROWN, 0f)                                     // 8 윗면 가운데
    )
}

/** 윗면 위의 한 점. f는 좌우 위치(-1 왼쪽 가장자리 ~ 1 오른쪽 가장자리). */
private fun topPoint(x: Float, f: Float, lift: Float): Vec {
    val top = TOP.at(x)
    val wt = TOP_WIDTH.at(x)
    val a = abs(f)
    val y = if (a < 0.45f / 0.86f) mix(top + CROWN, top + 0.7f * CROWN, a / (0.45f / 0.86f))
    else mix(top + 0.7f * CROWN, top, (a - 0.45f / 0.86f) / (1f - 0.45f / 0.86f))
    return Vec(x, y + lift, f * 0.86f * wt)
}

private val CAR_MESH: List<Face> by lazy { buildCar() }

private fun buildCar(): List<Face> {
    val faces = mutableListOf<Face>()

    // --- 차체 곡면
    val stations = generateSequence(-2.25f) { it + HULL_STEP }.takeWhile { it <= 2.2501f }.toList()
    val loops = stations.map { x ->
        val right = halfSection(x)
        val left = right.dropLast(1).reversed().map { Vec(it.x, it.y, -it.z) }
        right + left // 오른쪽 아래 → 위 가운데 → 왼쪽 아래. 마지막 점과 첫 점 사이가 바닥.
    }
    val loopSize = loops[0].size // 17

    for (i in 0 until stations.size - 1) {
        val xMid = (stations[i] + stations[i + 1]) / 2f
        // 단면 안쪽의 한 점. 앞뒤 끝은 단면이 낮으므로 그 구간의 높이 가운데로 잡아야 면 방향이 맞는다.
        val inside = Vec(xMid, (FLOOR.at(xMid) + TOP.at(xMid)) / 2f, 0f)
        val cabin = TOP.at(xMid) - BELT.at(xMid) // 실내(유리) 높이. 보닛·트렁크 구간에서는 거의 0.
        val slope = abs(TOP.slope(xMid))

        for (j in 0 until loopSize) {
            val quad = listOf(loops[i][j], loops[i][(j + 1) % loopSize], loops[i + 1][(j + 1) % loopSize], loops[i + 1][j])
            // 띠 번호: 0~7은 오른쪽 절반(아래→위), 왼쪽은 대칭. 바닥은 -1.
            val band = when {
                j == loopSize - 1 -> -1
                j < 8 -> j
                else -> 15 - j
            }

            var color = BodyColor
            var zone: Zone? = null
            var fullTint = true
            var glossy = false

            val isTopBand = band == 6 || band == 7
            when {
                band == -1 || band == 0 -> color = TrimColor
                // 앞 유리, 뒤 유리: 윗선이 가파르게 오르내리는 구간의 윗면
                isTopBand && cabin > 0.04f && slope > 0.2f -> {
                    color = GlassColor
                    glossy = true
                    if (xMid > 0f) {
                        zone = Zone.WIPER
                        fullTint = false
                    }
                }
                // 파노라마 선루프: 지붕 가운데
                band == 7 && cabin > 0.3f && xMid > -0.95f && xMid < 0.15f -> {
                    color = GlassColor
                    glossy = true
                }
                // 옆 유리. 가운데 기둥(B필러) 한 칸은 차체 색으로 남긴다.
                band == 4 && cabin > 0.2f && !(xMid > -0.5f && xMid < -0.375f) -> {
                    color = GlassColor
                    glossy = true
                }
                // 전조등, 후미등: 앞뒤 모서리를 감싸는 조각
                (band == 2 || band == 3) && xMid > 1.98f -> {
                    color = LampColor
                    zone = Zone.HEADLAMP
                }
                (band == 2 || band == 3) && xMid < -2.02f -> color = TailColor
                // 보닛
                band >= 4 && xMid > 1.0f && xMid < 2.0f -> zone = Zone.HOOD
            }
            faces += face(quad, inside, color, Group.HULL, zone, fullTint, glossy = glossy)
        }
    }
    // 앞뒤 끝을 막는 면
    faces += face(loops.first(), Vec(0f, 0.65f, 0f), BodyColor, Group.HULL)
    faces += face(loops.last(), Vec(0f, 0.55f, 0f), BodyColor, Group.HULL)

    // --- 차체 위 장식: 그릴, 뒤 라이트 바, 와이퍼
    val hullInside = Vec(0f, 0.75f, 0f)
    faces += face(
        listOf(Vec(2.262f, 0.47f, -0.42f), Vec(2.262f, 0.47f, 0.42f), Vec(2.262f, 0.58f, 0.42f), Vec(2.262f, 0.58f, -0.42f)),
        hullInside, Color(0xFF343A42), Group.HULL_DETAIL
    )
    faces += face(
        listOf(Vec(-2.262f, 0.70f, -0.50f), Vec(-2.262f, 0.70f, 0.50f), Vec(-2.262f, 0.79f, 0.50f), Vec(-2.262f, 0.79f, -0.50f)),
        hullInside, Color(0xFF9B1C1C), Group.HULL_DETAIL
    )
    listOf(-0.80f to -0.12f, 0.02f to 0.70f).forEach { (f1, f2) ->
        faces += face(
            listOf(
                topPoint(0.955f, f1, 0.02f), topPoint(0.80f, f2, 0.02f),
                topPoint(0.765f, f2, 0.02f), topPoint(0.92f, f1, 0.02f)
            ),
            hullInside, Color(0xFF0B0E12), Group.HULL_DETAIL, zone = Zone.WIPER
        )
    }

    // --- 사이드미러
    listOf(-1, 1).forEach { side ->
        val s = side.toFloat()
        val center = Vec(0.74f, 1.035f, 0.95f * s)
        val xs = listOf(0.68f, 0.80f)
        val ys = listOf(1.00f, 1.07f)
        val zs = listOf(0.86f * s, 1.03f * s)
        fun corner(a: Int, b: Int, c: Int) = Vec(xs[a], ys[b], zs[c])
        val quads = listOf(
            listOf(corner(0, 0, 0), corner(1, 0, 0), corner(1, 1, 0), corner(0, 1, 0)),
            listOf(corner(0, 0, 1), corner(1, 0, 1), corner(1, 1, 1), corner(0, 1, 1)),
            listOf(corner(0, 0, 0), corner(0, 0, 1), corner(0, 1, 1), corner(0, 1, 0)),
            listOf(corner(1, 0, 0), corner(1, 0, 1), corner(1, 1, 1), corner(1, 1, 0)),
            listOf(corner(0, 1, 0), corner(1, 1, 0), corner(1, 1, 1), corner(0, 1, 1)),
            listOf(corner(0, 0, 0), corner(1, 0, 0), corner(1, 0, 1), corner(0, 0, 1))
        )
        quads.forEach { faces += face(it, center, BodyColor, Group.SIDE, side = side, objectCenter = center) }
    }

    // --- 바퀴 4개. 차체 옆면에 거의 붙여서, 바깥으로 나온 부분만 만든다.
    val segments = 20
    val radius = 0.36f
    listOf(1.38f, -1.38f).forEach { cx ->
        listOf(-1, 1).forEach { side ->
            val zIn = 0.86f * side
            val zOut = 0.935f * side
            val center = Vec(cx, radius, (zIn + zOut) / 2f)
            fun ring(r: Float, z: Float) = List(segments) { i ->
                val angle = 2.0 * PI * i / segments
                Vec(cx + r * cos(angle).toFloat(), radius + r * sin(angle).toFloat(), z)
            }
            fun wheelFace(points: List<Vec>, color: Color, zone: Zone?, rank: Int) =
                face(points, center, color, Group.SIDE, zone, side = side, objectCenter = center, rank = rank)

            // 휠 하우스: 바퀴 위쪽을 감싸는 어두운 반원
            val arch = List(13) { i ->
                val angle = PI * (-0.08 + 1.16 * i / 12)
                Vec(cx + 0.45f * cos(angle).toFloat(), radius + 0.45f * sin(angle).toFloat(), 0.915f * side)
            }
            faces += face(
                arch, center, Color(0xFF14171B), Group.SIDE,
                side = side, objectCenter = center, rank = 0, minFacing = 0.45f
            )

            val inner = ring(radius, zIn)
            val outer = ring(radius, zOut)
            for (i in 0 until segments) {
                val j = (i + 1) % segments
                faces += wheelFace(listOf(inner[i], inner[j], outer[j], outer[i]), TireColor, Zone.TIRE, 1)
            }
            faces += wheelFace(outer, Color(0xFF2A2F36), Zone.TIRE, 2)
            faces += wheelFace(ring(0.25f, zOut + 0.004f * side), RimColor, Zone.BRAKE, 3)
            faces += wheelFace(ring(0.17f, zOut + 0.008f * side), Color(0xFF8E96A1), Zone.BRAKE, 4)
            faces += wheelFace(ring(0.06f, zOut + 0.012f * side), Color(0xFF4A515B), Zone.BRAKE, 5)
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
private const val DEFAULT_PITCH = 20f

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
            val unit = minOf(size.width, size.height) / 4.9f * zoom
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

            val cameraPosition = modelCenter +
                Vec(-distance * cosPitch * sinYaw, distance * sinPitch, distance * cosPitch * cosYaw)
            val light = Vec(0.30f, 0.86f, 0.42f).normalized()
            val view = (cameraPosition - modelCenter).normalized()
            val halfway = (light + view).normalized() // 반사광이 가장 밝은 방향

            // 바닥 그림자: 차 아래의 둥근 얼룩
            val shadow = List(24) { i ->
                val angle = 2.0 * PI * i / 24
                toScreen(toCamera(Vec(2.45f * cos(angle).toFloat(), 0f, 1.08f * sin(angle).toFloat())))
            }
            drawPath(polygonPath(shadow), Color(0x8C000000))

            class Item(val face: Face, val layer: Int, val objectDepth: Float, val depth: Float)

            val items = CAR_MESH
                .filter { (it.normal dot (cameraPosition - it.centroid).normalized()) > it.minFacing }
                .map { face ->
                    val layer = when (face.group) {
                        // 카메라 반대편 바퀴·미러는 차체에 가려지므로 맨 먼저, 카메라 쪽은 맨 나중에 그린다.
                        Group.SIDE -> if (face.side * (cameraPosition.z - modelCenter.z) >= 0f) 3 else 0
                        Group.HULL -> 1
                        Group.HULL_DETAIL -> 2
                    }
                    val objectDepth = if (face.group == Group.SIDE) toCamera(face.objectCenter).z else 0f
                    Item(face, layer, objectDepth, toCamera(face.centroid).z)
                }
                .sortedWith(
                    compareBy<Item> { it.layer }
                        .thenBy { it.objectDepth }
                        .thenBy { it.face.rank }
                        .thenBy { it.depth }
                )

            drawn.clear()
            items.forEach { item ->
                val face = item.face
                val polygon = face.points.map { toScreen(toCamera(it)) }
                val diffuse = max(0f, face.normal dot light)
                val shine = max(0f, face.normal dot halfway).pow(24)
                val fault = zoneColor(face.zone)
                val color = when {
                    fault != null && face.fullTint -> shade(fault, 0.84f + 0.16f * diffuse, 0f)
                    face.glossy -> {
                        val base = if (fault != null) lerp(face.color, fault, 0.4f) else face.color
                        shade(base, 0.85f + 0.15f * diffuse, 0.35f * shine)
                    }
                    else -> shade(face.color, 0.66f + 0.34f * diffuse, 0.18f * shine)
                }
                val path = polygonPath(polygon)
                drawPath(path, color)
                // 면 사이의 실틈이 보이지 않게 같은 색으로 가장자리를 한 번 더 그린다.
                drawPath(path, color, style = Stroke(width = 1.5f))
                drawn += DrawnFace(polygon, face.zone)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color(0x66101317))
            .border(1.dp, Lux.StrokeStrong, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 22.sp, color = Lux.TextSoft)
    }
}

private fun polygonPath(points: List<Offset>) = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    close()
}

/** 밝기를 곱하고, 반사광만큼 흰색 쪽으로 당긴다. */
private fun shade(color: Color, brightness: Float, highlight: Float): Color {
    val b = brightness.coerceIn(0f, 1f)
    val h = highlight.coerceIn(0f, 1f)
    return Color(
        red = (color.red * b + (1f - color.red * b) * h).coerceIn(0f, 1f),
        green = (color.green * b + (1f - color.green * b) * h).coerceIn(0f, 1f),
        blue = (color.blue * b + (1f - color.blue * b) * h).coerceIn(0f, 1f),
        alpha = color.alpha
    )
}
