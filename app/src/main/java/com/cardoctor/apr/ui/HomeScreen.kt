package com.cardoctor.apr.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardoctor.apr.ai.GeminiExplainer
import com.cardoctor.apr.diagnosis.Advice
import com.cardoctor.apr.diagnosis.AdviceEngine
import com.cardoctor.apr.diagnosis.Environment
import com.cardoctor.apr.diagnosis.Explainer
import com.cardoctor.apr.diagnosis.LevelInfo
import com.cardoctor.apr.diagnosis.Part
import com.cardoctor.apr.vehicle.Reading
import com.cardoctor.apr.vehicle.VehicleState
import kotlinx.coroutines.delay

private val Background = Color(0xFFF3F5F8)
private val Panel = Color.White
private val TextMain = Color(0xFF1B1F24)
private val TextSub = Color(0xFF5B6470)
private val Outline = Color(0xFFD5DAE1)
private val Normal = Color(0xFF2E9E5B)
private val Caution = Color(0xFFC79100)
private val Danger = Color(0xFFD32F2F)

/** 기획안 등급표의 색: 1·2등급 노랑, 3·4등급 빨강. 4등급은 화면에서 깜빡인다. */
internal fun levelColor(level: Int): Color = if (level >= 3) Danger else Caution

private enum class Screen(val label: String) {
    HOME("차량 상태"),
    SELF_CHECK("자가진단")
}

@Composable
fun CarDoctorApp(vehicle: VehicleState, modifier: Modifier = Modifier) {
    var faults by remember { mutableStateOf(setOf<Part>()) }
    var env by remember { mutableStateOf(Environment()) }
    var selected by remember { mutableStateOf<Part?>(null) }
    var screen by remember { mutableStateOf(Screen.HOME) }

    // 차량에서 읽은 값과 시연 패널에서 넣은 값을 합쳐서 판정한다.
    val vehicleEnv = vehicle.environment
    val allFaults = faults + vehicle.faults
    val allEnv = env.copy(
        night = env.night || vehicleEnv.night,
        rain = env.rain || vehicleEnv.rain
    )
    val advices = remember(allFaults, allEnv) { AdviceEngine.evaluateAll(allFaults, allEnv) }

    // 고른 부품이 없으면 가장 긴급한 고장을 보여 준다.
    val shownPart = selected ?: advices.firstOrNull()?.part
    val shownAdvice = advices.firstOrNull { it.part == shownPart }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Screen.entries.forEach { item ->
                Toggle(item.label, on = screen == item) { screen = item }
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            when (screen) {
                Screen.HOME -> {
                    StatusPanel(
                        advices = advices,
                        shownPart = shownPart,
                        onSelect = { selected = it },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    AdvicePanel(
                        part = shownPart,
                        advice = shownAdvice,
                        env = allEnv,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

                Screen.SELF_CHECK -> SelfCheckPanel(
                    advices = advices,
                    onOpenPart = { part ->
                        selected = part
                        screen = Screen.HOME
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
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
    shownPart: Part?,
    onSelect: (Part) -> Unit,
    modifier: Modifier = Modifier
) {
    val topLevel = advices.firstOrNull()?.level

    // 4등급(즉시 정차)은 배너와 차량 그림의 표시를 깜빡인다.
    val blink = rememberInfiniteTransition(label = "blink")
    val pulse by blink.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "pulse"
    )
    val bannerColor = when {
        topLevel == null -> Normal
        topLevel >= 4 -> lerp(Color(0xFF7A1212), Danger, pulse)
        else -> levelColor(topLevel)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Panel)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CarDiagram(
                    advices = advices,
                    modifier = Modifier.size(width = 250.dp, height = 540.dp),
                    pulse = pulse,
                    onPartTap = onSelect
                )
                Text("부위를 누르면 상세 정보가 열립니다", fontSize = 18.sp, color = TextSub)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("감지된 고장 ${advices.size}건", fontSize = 22.sp, color = TextSub)
                if (advices.isEmpty()) {
                    Text("감지된 고장이 없습니다.", fontSize = 22.sp, color = TextMain)
                }
                advices.forEach { advice ->
                    FaultRow(
                        advice = advice,
                        isSelected = advice.part == shownPart,
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
        LevelBadge(advice.level)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(advice.part.label, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
            Text(LevelInfo.title(advice.level), fontSize = 20.sp, color = TextSub)
        }
    }
}

@Composable
private fun LevelBadge(level: Int) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(levelColor(level)),
        contentAlignment = Alignment.Center
    ) {
        Text("$level", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/** 오른쪽: 고른 부품의 진단 상세. 정비 권고, AI 설명, 판정 근거. */
@Composable
private fun AdvicePanel(part: Part?, advice: Advice?, env: Environment, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Panel)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("진단 상세", fontSize = 22.sp, color = TextSub)

        if (part == null) {
            Text(
                "현재 주행에 지장을 주는 고장이 없습니다.",
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMain
            )
            return@Column
        }

        Text(part.label, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = TextMain)

        if (advice == null) {
            Text("정상", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Normal)
            Text("이 부위에서 감지된 고장이 없습니다.", fontSize = 24.sp, lineHeight = 36.sp, color = TextMain)
            return@Column
        }

        val color = levelColor(advice.level)
        Text(
            "${advice.level}등급 · ${LevelInfo.title(advice.level)}",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            LevelInfo.action(advice.level),
            fontSize = 26.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMain
        )

        Divider()
        AiExplanation(advice, env)

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

/**
 * 고장 설명. Gemini가 만든 문장을 보여 주고, 만드는 중이거나 쓸 수 없으면 규칙 기반 문구를 보여 준다.
 * 등급과 권고 행동은 AdviceEngine이 정한 값이며 AI가 바꾸지 않는다.
 */
@Composable
private fun AiExplanation(advice: Advice, env: Environment) {
    var aiText by remember(advice.part, advice.level, env) { mutableStateOf<String?>(null) }
    var failed by remember(advice.part, advice.level, env) { mutableStateOf(false) }

    LaunchedEffect(advice.part, advice.level, env) {
        if (!GeminiExplainer.isConfigured) return@LaunchedEffect
        delay(1200) // 버튼을 연달아 누를 때 호출이 몰리지 않게 기다린다. 무료 등급은 분당 호출 수가 적다.
        GeminiExplainer.explain(advice, env)
            .onSuccess { aiText = it }
            .onFailure { failed = true }
    }

    val source = when {
        aiText != null -> "Gemini가 생성"
        !GeminiExplainer.isConfigured -> "기본 설명 · Gemini API 키 미설정"
        failed -> "기본 설명 · AI 응답 실패"
        else -> "AI 설명 생성 중…"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("AI 설명", fontSize = 22.sp, color = TextSub)
        Spacer(Modifier.width(12.dp))
        Text(source, fontSize = 18.sp, color = TextSub)
    }
    Text(
        aiText ?: Explainer.explain(advice, env),
        fontSize = 22.sp,
        lineHeight = 34.sp,
        color = TextMain
    )
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

private data class CheckItem(val name: String, val detail: String, val part: Part)

private val CHECK_ITEMS = listOf(
    CheckItem("엔진·냉각", "냉각수 온도", Part.COOLANT),
    CheckItem("제동", "브레이크 경고, 브레이크액", Part.BRAKE),
    CheckItem("타이어", "공기압", Part.TIRE),
    CheckItem("등화", "전조등", Part.HEADLAMP),
    CheckItem("시야", "와이퍼", Part.WIPER),
    CheckItem("구동·배터리", "출력 제한 경고", Part.POWER)
)

/** 자가진단 실행 화면: 전체 점검 버튼과 기능별 진행 상태. */
@Composable
private fun SelfCheckPanel(
    advices: List<Advice>,
    onOpenPart: (Part) -> Unit,
    modifier: Modifier = Modifier
) {
    // done: 점검을 마친 항목 수. -1이면 아직 점검을 시작하지 않은 상태.
    var done by remember { mutableIntStateOf(-1) }
    var runId by remember { mutableIntStateOf(0) }
    val total = CHECK_ITEMS.size
    val running = done in 0 until total

    LaunchedEffect(runId) {
        if (runId == 0) return@LaunchedEffect
        done = 0
        while (done < total) {
            delay(700)
            done++
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Panel)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("자가진단", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = TextMain)
                Text(
                    when {
                        done < 0 -> "전체 점검을 누르면 기능별로 차량 상태를 확인합니다."
                        running -> "점검 중입니다… ($done/$total)"
                        else -> {
                            val faultCount = CHECK_ITEMS.count { item -> advices.any { it.part == item.part } }
                            if (faultCount == 0) "점검 완료 · ${total}개 항목 모두 정상입니다."
                            else "점검 완료 · ${total}개 항목 중 ${faultCount}개에서 이상을 발견했습니다."
                        }
                    },
                    fontSize = 22.sp,
                    color = TextSub
                )
            }
            Button(
                onClick = { runId++ },
                enabled = !running,
                colors = ButtonDefaults.buttonColors(containerColor = TextMain, contentColor = Color.White)
            ) {
                Text(
                    if (done < 0) "전체 점검" else "다시 점검",
                    fontSize = 24.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        LinearProgressIndicator(
            progress = { if (done < 0) 0f else done.toFloat() / total },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp)),
            color = TextMain,
            trackColor = Outline
        )

        CHECK_ITEMS.forEachIndexed { index, item ->
            val advice = advices.firstOrNull { it.part == item.part }
            val checked = done > index
            val checking = running && done == index
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Outline, RoundedCornerShape(14.dp))
                    .clickable(enabled = checked && advice != null) { onOpenPart(item.part) }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
                    Text(item.detail, fontSize = 20.sp, color = TextSub)
                }
                when {
                    checking -> Text("점검 중…", fontSize = 24.sp, color = TextSub)
                    !checked -> Text("대기", fontSize = 24.sp, color = Outline)
                    advice == null -> Text("정상", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Normal)
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "이상 · ${LevelInfo.title(advice.level)}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = levelColor(advice.level)
                        )
                        Spacer(Modifier.width(12.dp))
                        LevelBadge(advice.level)
                    }
                }
            }
        }
        if (done >= total && advices.isNotEmpty()) {
            Text("이상이 있는 항목을 누르면 진단 상세로 이동합니다.", fontSize = 20.sp, color = TextSub)
        }
    }
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
