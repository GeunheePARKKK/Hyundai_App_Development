package com.cardoctor.apr.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.cardoctor.apr.env.LiveEnvironment
import com.cardoctor.apr.vehicle.Reading
import com.cardoctor.apr.vehicle.VehicleState
import kotlinx.coroutines.delay

private enum class Screen(val label: String) {
    HOME("차량 상태"),
    SELF_CHECK("자가진단")
}

/*
 * 화면 구성: 왼쪽에 차량 그림, 가운데에 좁은 메뉴, 오른쪽에 상세.
 * 시연용 패널은 메뉴의 스위치로 켰을 때만 아래에 나타난다.
 */
@Composable
fun CarDoctorApp(
    vehicle: VehicleState,
    live: LiveEnvironment,
    voiceStatus: String,
    onAnnounce: (Part, Int) -> Unit,
    onStopSpeaking: () -> Unit,
    modifier: Modifier = Modifier
) {
    var faults by remember { mutableStateOf(setOf<Part>()) }
    var env by remember { mutableStateOf(Environment()) }
    var selected by remember { mutableStateOf<Part?>(null) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    // 실시간 시각·날씨를 판정에 반영할지. 시나리오 버튼은 정해진 상황을 보여 줘야 하므로 이 값을 끈다.
    var useLive by remember { mutableStateOf(true) }
    var voiceOn by remember { mutableStateOf(true) }
    var demoOpen by remember { mutableStateOf(false) }

    // 차량 값, 실시간 시각·날씨, 시연 패널 값을 합쳐서 판정한다.
    val vehicleEnv = vehicle.environment
    val allFaults = faults + vehicle.faults
    val allEnv = env.copy(
        night = env.night || vehicleEnv.night || (useLive && live.night == true),
        rain = env.rain || vehicleEnv.rain || (useLive && live.rain == true)
    )
    val advices = remember(allFaults, allEnv) { AdviceEngine.evaluateAll(allFaults, allEnv) }

    // 고른 부품이 없으면 가장 긴급한 고장을 보여 준다.
    val shownPart = selected ?: advices.firstOrNull()?.part
    val shownAdvice = advices.firstOrNull { it.part == shownPart }

    // 가장 긴급한 고장이 3등급 이상으로 새로 잡히거나 바뀌면 음성으로 알린다.
    val top = advices.firstOrNull()
    LaunchedEffect(top?.part, top?.level, voiceOn) {
        if (!voiceOn) {
            onStopSpeaking()
        } else if (top != null && top.level >= 3) {
            onAnnounce(top.part, top.level)
        }
    }

    CompositionLocalProvider(LocalContentColor provides Lux.Text) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Lux.Surface)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                CarPanel(
                    advices = advices,
                    env = allEnv,
                    onSelect = {
                        selected = it
                        screen = Screen.HOME
                    },
                    modifier = Modifier
                        .weight(1.05f)
                        .fillMaxHeight()
                )
                VerticalLine()
                Menu(
                    screen = screen,
                    onScreen = { screen = it },
                    voiceOn = voiceOn,
                    voiceStatus = voiceStatus,
                    onToggleVoice = { voiceOn = !voiceOn },
                    demoOpen = demoOpen,
                    onToggleDemo = { demoOpen = !demoOpen }
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Lux.SurfaceDim)
                ) {
                    when (screen) {
                        Screen.HOME -> DetailPanel(
                            advices = advices,
                            shownPart = shownPart,
                            shownAdvice = shownAdvice,
                            env = allEnv,
                            envSource = if (useLive) live.summary else "시연 패널에서 정한 상황",
                            onSelect = { selected = it }
                        )

                        Screen.SELF_CHECK -> SelfCheckPanel(
                            advices = advices,
                            onOpenPart = { part ->
                                selected = part
                                screen = Screen.HOME
                            }
                        )
                    }
                }
            }

            if (demoOpen) {
                Hairline()
                DemoPanel(
                    vehicle = vehicle,
                    faults = faults,
                    env = env,
                    useLive = useLive,
                    onToggleLive = { useLive = !useLive },
                    onToggleFault = { part ->
                        faults = if (part in faults) faults - part else faults + part
                        selected = null
                    },
                    onEnvChange = { env = it },
                    onScenario = { newFaults, newEnv, live ->
                        faults = newFaults
                        env = newEnv
                        useLive = live
                        selected = null
                    }
                )
            }
        }
    }
}

@Composable
private fun VerticalLine() {
    Box(
        Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(Lux.Line)
    )
}

private fun environmentText(env: Environment): String = buildList {
    add(if (env.night) "야간" else "주간")
    add(if (env.rain) "비·눈" else "강수 없음")
    add(if (env.highway) "고속도로" else "일반 도로")
    if (env.longTrip) add("남은 거리 김")
}.joinToString("  ·  ")

/** 왼쪽: 현재 환경 한 줄, 전체 상태, 차량 그림. */
@Composable
private fun CarPanel(
    advices: List<Advice>,
    env: Environment,
    onSelect: (Part) -> Unit,
    modifier: Modifier = Modifier
) {
    val topLevel = advices.firstOrNull()?.level

    // 4등급(즉시 정차)일 때만 상태 글자와 차량 그림의 표시를 깜빡인다.
    val pulse = if (topLevel != null && topLevel >= 4) blinkPulse() else 1f

    Column(modifier = modifier.padding(start = 40.dp, top = 32.dp, end = 24.dp, bottom = 20.dp)) {
        Caption(environmentText(env))
        Spacer(Modifier.height(10.dp))
        if (topLevel == null) {
            Text("이상 없음", fontSize = 38.sp, fontWeight = FontWeight.SemiBold, color = Lux.Text)
        } else {
            Text(
                "${topLevel}등급 · ${LevelInfo.title(topLevel)}",
                fontSize = 38.sp,
                fontWeight = FontWeight.SemiBold,
                color = levelColor(topLevel).copy(alpha = if (topLevel >= 4) pulse.coerceAtLeast(0.35f) else 1f)
            )
        }
        CarDiagram(
            advices = advices,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            pulse = pulse,
            onPartTap = onSelect
        )
    }
}

/** 1과 0.25 사이를 오가는 깜빡임 값. 호출하는 동안에만 애니메이션이 돈다. */
@Composable
private fun blinkPulse(): Float {
    val blink = rememberInfiniteTransition(label = "blink")
    val pulse by blink.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "pulse"
    )
    return pulse
}

/** 가운데: 화면 전환과 스위치 두 개. */
@Composable
private fun Menu(
    screen: Screen,
    onScreen: (Screen) -> Unit,
    voiceOn: Boolean,
    voiceStatus: String,
    onToggleVoice: () -> Unit,
    demoOpen: Boolean,
    onToggleDemo: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(230.dp)
            .fillMaxHeight()
            .padding(horizontal = 16.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Screen.entries.forEach { item ->
            val isCurrent = item == screen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Lux.ButtonShape)
                    .background(if (isCurrent) Lux.Fill else Color.Transparent)
                    .clickable { onScreen(item) }
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Text(
                    item.label,
                    fontSize = 23.sp,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isCurrent) Lux.Text else Lux.TextDim
                )
            }
        }

        Spacer(Modifier.weight(1f))

        SwitchRow("음성 안내", voiceOn, onToggleVoice)
        if (voiceStatus != "대기") {
            Caption(voiceStatus, modifier = Modifier.padding(start = 18.dp))
        }
        SwitchRow("시연 패널", demoOpen, onToggleDemo)
    }
}

@Composable
private fun SwitchRow(label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 21.sp, fontWeight = FontWeight.Medium, color = Lux.TextDim, modifier = Modifier.weight(1f))
        LuxSwitch(on = on, onClick = onClick)
    }
}

/** 오른쪽: 차량 상태 상세. 고장이 없으면 요약만, 있으면 고장 목록과 고른 고장의 권고를 보여 준다. */
@Composable
private fun DetailPanel(
    advices: List<Advice>,
    shownPart: Part?,
    shownAdvice: Advice?,
    env: Environment,
    envSource: String,
    onSelect: (Part) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 44.dp, vertical = 36.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("차량 상태", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = Lux.Text)
        Spacer(Modifier.height(6.dp))
        Text(
            if (advices.isEmpty()) "감지된 고장이 없습니다" else "감지된 고장 ${advices.size}건",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = Lux.Accent
        )
        Spacer(Modifier.height(30.dp))

        if (advices.isEmpty()) {
            if (shownPart != null) {
                Field("선택한 부위") {
                    Text("${shownPart.label} · 정상", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Lux.Ok)
                }
            }
            Field("주행 환경") {
                Text(environmentText(env), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Lux.Text)
            }
            Field("기준") {
                Text(envSource, fontSize = 21.sp, color = Lux.TextDim)
            }
            return@Column
        }

        advices.forEach { advice ->
            FaultRow(advice, isSelected = advice.part == shownPart) { onSelect(advice.part) }
        }
        Spacer(Modifier.height(22.dp))
        Hairline()
        Spacer(Modifier.height(26.dp))

        if (shownAdvice == null) {
            if (shownPart != null) {
                Field("선택한 부위") {
                    Text("${shownPart.label} · 정상", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Lux.Ok)
                }
            }
            return@Column
        }

        Field("권고") {
            Text(
                LevelInfo.action(shownAdvice.level),
                fontSize = 28.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.SemiBold,
                color = Lux.Text
            )
        }
        AiExplanation(shownAdvice, env)
        Field("판정 근거") {
            val reasons = shownAdvice.reasons.joinToString("  ·  ") { "${it.label} +${it.points}" }
            Text(
                "$reasons  ·  합계 ${shownAdvice.score}점",
                fontSize = 21.sp,
                lineHeight = 33.sp,
                color = Lux.TextDim
            )
            if (shownAdvice.part.baseSeverity == 3 && shownAdvice.score < 4) {
                Text(
                    "안전에 직결되는 부품이라 점수와 관계없이 최소 3등급입니다.",
                    fontSize = 19.sp,
                    lineHeight = 30.sp,
                    color = Lux.TextFaint
                )
            }
        }
    }
}

/** 회색 설명과 그 아래의 값. 상세 화면의 기본 단위. */
@Composable
private fun Field(caption: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.padding(bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Caption(caption)
        content()
    }
}

/** 고장 목록의 한 줄. 선택된 줄만 회색 바탕을 깐다. */
@Composable
private fun FaultRow(advice: Advice, isSelected: Boolean, onClick: () -> Unit) {
    val color = levelColor(advice.level)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Lux.ButtonShape)
            .background(if (isSelected) Lux.Fill else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(11.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            advice.part.label,
            modifier = Modifier.weight(1f),
            fontSize = 24.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = Lux.Text
        )
        Text(
            "${advice.level}등급 · ${LevelInfo.title(advice.level)}",
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
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
        else -> "생성 중…"
    }
    Field("AI 설명 · $source") {
        Text(
            aiText ?: Explainer.explain(advice, env),
            fontSize = 22.sp,
            lineHeight = 36.sp,
            color = Lux.Text
        )
        Text(
            "설명은 참고용입니다. 최종 판단은 정비 전문가에게 받으세요.",
            fontSize = 18.sp,
            color = Lux.TextFaint
        )
    }
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
private fun SelfCheckPanel(advices: List<Advice>, onOpenPart: (Part) -> Unit) {
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
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 44.dp, vertical = 36.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("자가진단", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = Lux.Text)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        done < 0 -> "기능별로 차량 상태를 확인합니다"
                        running -> "점검 중  $done / $total"
                        else -> {
                            val faultCount = CHECK_ITEMS.count { item -> advices.any { it.part == item.part } }
                            if (faultCount == 0) "${total}개 항목 모두 정상"
                            else "${total}개 항목 중 ${faultCount}개에서 이상 발견"
                        }
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Lux.Accent
                )
            }
            ActionButton(if (done < 0) "전체 점검" else "다시 점검", enabled = !running) { runId++ }
        }

        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(
            progress = { if (done < 0) 0f else done.toFloat() / total },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = Lux.Accent,
            trackColor = Lux.Line
        )

        CHECK_ITEMS.forEachIndexed { index, item ->
            val advice = advices.firstOrNull { it.part == item.part }
            val checked = done > index
            val checking = running && done == index
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = checked && advice != null) { onOpenPart(item.part) }
                    .padding(vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Lux.Text)
                    Caption(item.detail)
                }
                when {
                    checking -> Text("점검 중…", fontSize = 21.sp, fontWeight = FontWeight.Medium, color = Lux.Accent)
                    !checked -> Text("대기", fontSize = 21.sp, color = Lux.TextFaint)
                    advice == null -> Text("정상", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = Lux.Ok)
                    else -> Text(
                        "${advice.level}등급 · ${LevelInfo.title(advice.level)}",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = levelColor(advice.level)
                    )
                }
            }
            Hairline()
        }
    }
}

/** 아래: 에뮬레이터에서 고장과 환경을 직접 넣어 보는 시연용 패널. 메뉴의 스위치로 켰을 때만 보인다. */
@Composable
private fun DemoPanel(
    vehicle: VehicleState,
    faults: Set<Part>,
    env: Environment,
    useLive: Boolean,
    onToggleLive: () -> Unit,
    onToggleFault: (Part) -> Unit,
    onEnvChange: (Environment) -> Unit,
    /** 고장, 환경, 실시간 시각·날씨 반영 여부를 한 번에 정한다. */
    onScenario: (Set<Part>, Environment, Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Lux.Surface)
            .padding(horizontal = 40.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DemoRow("시나리오") {
            Chip("A 낮 · 전조등", on = false) { onScenario(setOf(Part.HEADLAMP), Environment(), false) }
            Chip("B 밤 · 전조등", on = false) { onScenario(setOf(Part.HEADLAMP), Environment(night = true), false) }
            Chip("C 빗길 고속도로 · 타이어", on = false) {
                onScenario(setOf(Part.TIRE), Environment(rain = true, highway = true), false)
            }
            Chip("초기화", on = false) { onScenario(emptySet(), Environment(), true) }
        }
        DemoRow("고장") {
            Part.entries.forEach { part ->
                Chip(part.label, on = part in faults) { onToggleFault(part) }
            }
        }
        DemoRow("환경") {
            Chip("실시간 시각·날씨", on = useLive, onClick = onToggleLive)
            Chip("야간", on = env.night) { onEnvChange(env.copy(night = !env.night)) }
            Chip("비", on = env.rain) { onEnvChange(env.copy(rain = !env.rain)) }
            Chip("고속도로", on = env.highway) { onEnvChange(env.copy(highway = !env.highway)) }
            Chip("남은 거리 김", on = env.longTrip) { onEnvChange(env.copy(longTrip = !env.longTrip)) }
        }
        DemoRow("차량 값") {
            if (!vehicle.connected) {
                Caption("차량 서비스에 연결되지 않았습니다" + (vehicle.connectionError?.let { " ($it)" } ?: ""))
            } else {
                VehicleValue("출력 제한 경고", vehicle.powerDown)
                VehicleValue("브레이크액", vehicle.brakeFluid)
                VehicleValue("전조등", vehicle.lowBeam)
                VehicleValue("와이퍼", vehicle.wiper)
                VehicleValue("경고등", vehicle.telltales)
            }
        }
    }
}

@Composable
private fun DemoRow(title: String, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Caption(title, modifier = Modifier.width(96.dp))
        content()
    }
}

@Composable
private fun VehicleValue(label: String, reading: Reading) {
    Row(
        modifier = Modifier.padding(end = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Caption("$label  ")
        Text(reading.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Lux.Text)
    }
}
