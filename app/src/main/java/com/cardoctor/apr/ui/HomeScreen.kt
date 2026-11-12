package com.cardoctor.apr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardoctor.apr.diagnosis.Advice
import com.cardoctor.apr.diagnosis.AdviceEngine
import com.cardoctor.apr.diagnosis.Environment
import com.cardoctor.apr.diagnosis.LevelInfo
import com.cardoctor.apr.diagnosis.Part
import com.cardoctor.apr.vehicle.Reading
import com.cardoctor.apr.vehicle.VehicleState

private val Background = Color(0xFFF3F5F8)
private val Panel = Color.White
private val TextMain = Color(0xFF1B1F24)
private val TextSub = Color(0xFF5B6470)
private val Outline = Color(0xFFD5DAE1)
private val Normal = Color(0xFF2E9E5B)
private val CarBody = Color(0xFFC9CED6)
private val CarGlass = Color(0xFF8A94A3)
private val CarPart = Color(0xFF6F7885)

private fun levelColor(level: Int): Color = when (level) {
    4 -> Color(0xFFD32F2F)
    3 -> Color(0xFFEF6C00)
    2 -> Color(0xFFC79100)
    else -> Color(0xFF1976D2)
}

@Composable
fun CarDoctorApp(vehicle: VehicleState, modifier: Modifier = Modifier) {
    var faults by remember { mutableStateOf(setOf<Part>()) }
    var env by remember { mutableStateOf(Environment()) }
    var selected by remember { mutableStateOf<Part?>(null) }

    // 차량에서 읽은 값과 시연 패널에서 넣은 값을 합쳐서 판정한다.
    val vehicleEnv = vehicle.environment
    val allFaults = faults + vehicle.faults
    val allEnv = env.copy(
        night = env.night || vehicleEnv.night,
        rain = env.rain || vehicleEnv.rain
    )
    val advices = remember(allFaults, allEnv) { AdviceEngine.evaluateAll(allFaults, allEnv) }
    val current = advices.firstOrNull { it.part == selected } ?: advices.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            StatusPanel(
                advices = advices,
                current = current,
                onSelect = { selected = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            AdvicePanel(
                advice = current,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
        DemoPanel(
            vehicle = vehicle,
            faults = faults,
            env = env,
            onToggleFault = { part ->
                faults = if (part in faults) faults - part else faults + part
                selected = null
            },
            onEnvChange = { env = it },
            onScenario = { newFaults, newEnv ->
                faults = newFaults
                env = newEnv
                selected = null
            }
        )
    }
}

/** 왼쪽: 전체 상태, 차량 그림, 감지된 고장 목록. */
@Composable
private fun StatusPanel(
    advices: List<Advice>,
    current: Advice?,
    onSelect: (Part) -> Unit,
    modifier: Modifier = Modifier
) {
    val topLevel = advices.firstOrNull()?.level
    val bannerColor = topLevel?.let(::levelColor) ?: Normal

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Panel)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("차량 상태", fontSize = 22.sp, color = TextSub)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(bannerColor)
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            Text(
                text = if (topLevel == null) "이상 없음" else "${topLevel}등급 · ${LevelInfo.title(topLevel)}",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            CarDiagram(
                advices = advices,
                modifier = Modifier.size(width = 230.dp, height = 400.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("감지된 고장 ${advices.size}건", fontSize = 22.sp, color = TextSub)
                if (advices.isEmpty()) {
                    Text(
                        "감지된 고장이 없습니다.\n아래 시연 패널에서 고장을 넣어 볼 수 있습니다.",
                        fontSize = 22.sp,
                        lineHeight = 32.sp,
                        color = TextMain
                    )
                }
                advices.forEach { advice ->
                    FaultRow(
                        advice = advice,
                        isSelected = advice.part == current?.part,
                        onClick = { onSelect(advice.part) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FaultRow(advice: Advice, isSelected: Boolean, onClick: () -> Unit) {
    val color = levelColor(advice.level)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) color else Outline,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text("${advice.level}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(advice.part.label, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
            Text(LevelInfo.title(advice.level), fontSize = 20.sp, color = TextSub)
        }
    }
}

/** 오른쪽: 선택한 고장의 정비 권고와 판정 근거. */
@Composable
private fun AdvicePanel(advice: Advice?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Panel)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("정비 권고", fontSize = 22.sp, color = TextSub)
        if (advice == null) {
            Text(
                "현재 주행에 지장을 주는 고장이 없습니다.",
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMain
            )
            return@Column
        }

        val color = levelColor(advice.level)
        Text(advice.part.label, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = TextMain)
        Text(
            "${advice.level}등급 · ${LevelInfo.title(advice.level)}",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(LevelInfo.action(advice.level), fontSize = 24.sp, lineHeight = 36.sp, color = TextMain)

        Divider()
        Text("어떤 고장인가요", fontSize = 22.sp, color = TextSub)
        Text(advice.part.explanation, fontSize = 22.sp, lineHeight = 34.sp, color = TextMain)

        Divider()
        Text("이 등급으로 판정한 근거", fontSize = 22.sp, color = TextSub)
        advice.reasons.forEach { reason ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(reason.label, fontSize = 22.sp, color = TextMain, modifier = Modifier.weight(1f))
                Text("+${reason.points}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextMain)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "긴급도 점수",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextMain,
                modifier = Modifier.weight(1f)
            )
            Text("${advice.score}점", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
        }
        if (advice.part.baseSeverity == 3 && advice.score < 4) {
            Text(
                "안전에 직결되는 부품이라 점수와 관계없이 최소 3등급으로 판정합니다.",
                fontSize = 20.sp,
                lineHeight = 30.sp,
                color = TextSub
            )
        }
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Outline)
    )
}

/** 아래: 에뮬레이터에서 고장과 환경을 직접 넣어 보는 시연용 패널. */
@Composable
private fun DemoPanel(
    vehicle: VehicleState,
    faults: Set<Part>,
    env: Environment,
    onToggleFault: (Part) -> Unit,
    onEnvChange: (Environment) -> Unit,
    onScenario: (Set<Part>, Environment) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Panel)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DemoRow("차량 값") {
            if (!vehicle.connected) {
                Text(
                    "차량 서비스에 연결되지 않았습니다" + (vehicle.connectionError?.let { " ($it)" } ?: ""),
                    fontSize = 20.sp,
                    color = TextMain
                )
            } else {
                VehicleValue("출력 제한 경고", vehicle.powerDown)
                VehicleValue("브레이크액", vehicle.brakeFluid)
                VehicleValue("전조등", vehicle.lowBeam)
                VehicleValue("와이퍼", vehicle.wiper)
                VehicleValue("경고등", vehicle.telltales)
            }
        }
        DemoRow("시나리오") {
            OutlinedButton(onClick = { onScenario(setOf(Part.HEADLAMP), Environment()) }) {
                Text("A 낮 · 전조등", fontSize = 20.sp)
            }
            OutlinedButton(onClick = { onScenario(setOf(Part.HEADLAMP), Environment(night = true)) }) {
                Text("B 밤 · 전조등", fontSize = 20.sp)
            }
            OutlinedButton(onClick = { onScenario(setOf(Part.TIRE), Environment(rain = true, highway = true)) }) {
                Text("C 빗길 고속도로 · 타이어", fontSize = 20.sp)
            }
            OutlinedButton(onClick = { onScenario(emptySet(), Environment()) }) {
                Text("초기화", fontSize = 20.sp)
            }
        }
        DemoRow("고장") {
            Part.entries.forEach { part ->
                Toggle(part.label, part in faults) { onToggleFault(part) }
            }
        }
        DemoRow("환경") {
            Toggle("야간", env.night) { onEnvChange(env.copy(night = !env.night)) }
            Toggle("비", env.rain) { onEnvChange(env.copy(rain = !env.rain)) }
            Toggle("고속도로", env.highway) { onEnvChange(env.copy(highway = !env.highway)) }
            Toggle("남은 거리 김", env.longTrip) { onEnvChange(env.copy(longTrip = !env.longTrip)) }
        }
    }
}

@Composable
private fun DemoRow(title: String, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, fontSize = 20.sp, color = TextSub, modifier = Modifier.width(96.dp))
        content()
    }
}

@Composable
private fun VehicleValue(label: String, reading: Reading) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Background)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text("$label ", fontSize = 20.sp, color = TextSub)
        Text(reading.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onClick: () -> Unit) {
    if (on) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = TextMain, contentColor = Color.White)
        ) { Text(label, fontSize = 20.sp) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label, fontSize = 20.sp, color = TextMain) }
    }
}

/** 위에서 내려다본 차량 그림. 고장 난 부품을 등급 색으로 칠한다. 차 앞쪽이 위. */
@Composable
private fun CarDiagram(advices: List<Advice>, modifier: Modifier = Modifier) {
    val colors = advices.associate { it.part to levelColor(it.level) }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        fun color(part: Part, normal: Color) = colors[part] ?: normal

        // 타이어 4개
        val tireW = w * 0.13f
        val tireH = h * 0.16f
        val tireColor = color(Part.TIRE, CarPart)
        listOf(0.17f, 0.67f).forEach { y ->
            rounded(tireColor, 0f, h * y, tireW, tireH, 10f)
            rounded(tireColor, w - tireW, h * y, tireW, tireH, 10f)
        }

        // 차체
        rounded(CarBody, w * 0.10f, 0f, w * 0.80f, h, 60f)

        // 전조등
        val lampColor = color(Part.HEADLAMP, Color.White)
        rounded(lampColor, w * 0.16f, h * 0.025f, w * 0.20f, h * 0.045f, 12f)
        rounded(lampColor, w * 0.64f, h * 0.025f, w * 0.20f, h * 0.045f, 12f)

        // 냉각수·구동 장치(보닛 안)
        drawCircle(colors[Part.POWER] ?: color(Part.COOLANT, CarPart), radius = w * 0.09f, center = Offset(w * 0.5f, h * 0.16f))

        // 앞 유리, 지붕, 뒤 유리
        rounded(CarGlass, w * 0.18f, h * 0.27f, w * 0.64f, h * 0.14f, 20f)
        rounded(Color(0xFFB4BAC4), w * 0.18f, h * 0.43f, w * 0.64f, h * 0.27f, 16f)
        rounded(CarGlass, w * 0.20f, h * 0.72f, w * 0.60f, h * 0.10f, 20f)

        // 와이퍼
        val wiperColor = color(Part.WIPER, Color.White)
        val wiperY = h * 0.395f
        drawLine(wiperColor, Offset(w * 0.24f, wiperY), Offset(w * 0.47f, wiperY - h * 0.05f), 8f, StrokeCap.Round)
        drawLine(wiperColor, Offset(w * 0.53f, wiperY), Offset(w * 0.76f, wiperY - h * 0.05f), 8f, StrokeCap.Round)

        // 브레이크(바퀴 안쪽)
        val brakeColor = color(Part.BRAKE, CarPart)
        listOf(0.25f, 0.75f).forEach { y ->
            drawCircle(brakeColor, radius = w * 0.045f, center = Offset(w * 0.19f, h * y))
            drawCircle(brakeColor, radius = w * 0.045f, center = Offset(w * 0.81f, h * y))
        }
    }
}

private fun DrawScope.rounded(color: Color, x: Float, y: Float, width: Float, height: Float, radius: Float) {
    drawRoundRect(color, topLeft = Offset(x, y), size = Size(width, height), cornerRadius = CornerRadius(radius, radius))
}
