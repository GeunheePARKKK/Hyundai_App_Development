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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
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
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(Lux.Background)
        ) {
            SideNav(
                screen = screen,
                onScreen = { screen = it },
                voiceStatus = voiceStatus,
                voiceOn = voiceOn,
                onToggleVoice = { voiceOn = !voiceOn }
            )
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Lux.Stroke)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (screen) {
                        Screen.HOME -> {
                            StatusPanel(
                                advices = advices,
                                env = allEnv,
                                envSource = if (useLive) live.summary else "시연 패널에서 정한 상황",
                                shownPart = shownPart,
                                onSelect = { selected = it },
                                modifier = Modifier
                                    .weight(1.18f)
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

/** 왼쪽 세로 메뉴: 화면 전환과 음성 안내 스위치. 선택된 항목은 파란 막대와 파란 글자로 표시한다. */
@Composable
private fun SideNav(
    screen: Screen,
    onScreen: (Screen) -> Unit,
    voiceStatus: String,
    voiceOn: Boolean,
    onToggleVoice: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(196.dp)
            .fillMaxHeight()
            .padding(vertical = 28.dp)
    ) {
        Text(
            "CAR DOCTOR",
            modifier = Modifier.padding(start = 26.dp),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 3.sp,
            color = Lux.TextFaint
        )
        Spacer(Modifier.height(30.dp))

        Screen.entries.forEach { item ->
            val isCurrent = item == screen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onScreen(item) }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(width = 4.dp, height = 30.dp)
                        .background(if (isCurrent) Lux.Accent else Color.Transparent)
                )
                Spacer(Modifier.width(22.dp))
                Text(
                    item.label,
                    fontSize = 26.sp,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isCurrent) Lux.Accent else Lux.TextSoft
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier.padding(start = 26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("음성 안내", fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Lux.TextSoft)
            LuxSwitch(on = voiceOn, onClick = onToggleVoice)
            Text(voiceStatus, fontSize = 18.sp, color = Lux.TextFaint)
        }
    }
}

/** 왼쪽 구역: 전체 상태, 현재 환경, 차량 그림, 감지된 고장 목록. */
@Composable
private fun StatusPanel(
    advices: List<Advice>,
    env: Environment,
    envSource: String,
    shownPart: Part?,
    onSelect: (Part) -> Unit,
    modifier: Modifier = Modifier
) {
    val topLevel = advices.firstOrNull()?.level
    var show3d by remember { mutableStateOf(true) }

    // 4등급(즉시 정차)일 때만 상태 표시와 차량 그림의 표시를 깜빡인다.
    val pulse = if (topLevel != null && topLevel >= 4) blinkPulse() else 1f

    Column(
        modifier = modifier
            .luxPanel()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatusHero(topLevel, pulse)

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EnvChip(if (env.night) "야간" else "주간", risky = env.night)
            EnvChip(if (env.rain) "비·눈" else "강수 없음", risky = env.rain)
            EnvChip(if (env.highway) "고속도로" else "일반 도로", risky = env.highway)
            if (env.longTrip) EnvChip("남은 거리 김", risky = true)
        }
        Text(envSource, fontSize = 18.sp, color = Lux.TextFaint)

        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // 차량 무대: 가운데만 은은하게 밝은 검은 바닥. 아래에 파란 조명 선을 깐다.
            Box(
                modifier = Modifier
                    .size(width = 410.dp, height = if (show3d) 440.dp else 590.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF202832), Color(0xFF0C1014), Color(0xFF050608)),
                            radius = 520f
                        )
                    )
            ) {
                if (show3d) {
                    Car3DView(
                        advices = advices,
                        modifier = Modifier.fillMaxSize(),
                        pulse = pulse,
                        onPartTap = onSelect
                    )
                } else {
                    CarDiagram(
                        advices = advices,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(width = 240.dp, height = 520.dp),
                        pulse = pulse,
                        onPartTap = onSelect
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Chip("3D", on = show3d) { show3d = true }
                    Chip("평면", on = !show3d) { show3d = false }
                }
                Text(
                    if (show3d) "끌어서 회전 · 부위를 누르면 상세 정보" else "부위를 누르면 상세 정보",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp),
                    fontSize = 18.sp,
                    color = Lux.TextFaint
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(listOf(Color.Transparent, Lux.Accent, Color.Transparent))
                        )
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                SectionLabel("감지된 고장  ${advices.size}")
                Spacer(Modifier.height(10.dp))
                Hairline()
                if (advices.isEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text("모든 점검 항목이 정상입니다.", fontSize = 22.sp, color = Lux.TextDim)
                }
                advices.forEach { advice ->
                    FaultRow(
                        advice = advice,
                        isSelected = advice.part == shownPart,
                        onClick = { onSelect(advice.part) }
                    )
                    Hairline()
                }
            }
        }
    }
}

/** 전체 상태를 알리는 경고창. 모서리를 깎은 테두리에 등급 색을 쓴다. */
@Composable
private fun StatusHero(topLevel: Int?, pulse: Float) {
    val color = topLevel?.let(::levelColor) ?: Lux.Ok
    val strength = if (topLevel != null && topLevel >= 4) pulse else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Lux.WarningShape)
            .background(
                Brush.horizontalGradient(
                    listOf(color.copy(alpha = 0.22f * strength), Color.Transparent)
                )
            )
            .border(2.dp, color.copy(alpha = strength.coerceAtLeast(0.3f)), Lux.WarningShape)
            .padding(horizontal = 32.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            SectionLabel("현재 상태", color = color)
            Spacer(Modifier.height(4.dp))
            if (topLevel == null) {
                Text("이상 없음", fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = Lux.Text)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${topLevel}등급", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = color)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        LevelInfo.title(topLevel),
                        modifier = Modifier.padding(bottom = 5.dp),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Lux.Text
                    )
                }
            }
        }
    }
}

/** 현재 환경 칩. 위험도를 올리는 상황이면 파란 테두리로 표시한다. */
@Composable
private fun EnvChip(label: String, risky: Boolean) {
    Row(
        modifier = Modifier
            .clip(Lux.Pill)
            .background(if (risky) Lux.AccentDeep else Color.Transparent)
            .border(1.5.dp, if (risky) Lux.Accent else Lux.StrokeStrong, Lux.Pill)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (risky) Lux.Accent else Lux.TextFaint)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            label,
            fontSize = 19.sp,
            fontWeight = if (risky) FontWeight.SemiBold else FontWeight.Medium,
            color = if (risky) Color.White else Lux.TextDim
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

/** 고장 목록의 한 줄. 선택된 줄은 왼쪽 파란 막대와 파란 기운의 바탕으로 표시한다. */
@Composable
private fun FaultRow(advice: Advice, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) Lux.Selected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 44.dp)
                .background(if (isSelected) Lux.Accent else Color.Transparent)
        )
        Spacer(Modifier.width(12.dp))
        LevelBadge(advice.level)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(advice.part.label, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = Lux.Text)
            Text(LevelInfo.title(advice.level), fontSize = 19.sp, color = Lux.TextDim)
        }
    }
}

@Composable
private fun LevelBadge(level: Int) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(levelColor(level)),
        contentAlignment = Alignment.Center
    ) {
        Text("$level", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = onLevelColor(level))
    }
}

/** 오른쪽 구역: 고른 부품의 진단 상세. 정비 권고, AI 설명, 판정 근거. */
@Composable
private fun AdvicePanel(part: Part?, advice: Advice?, env: Environment, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .luxPanel()
            .padding(28.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionLabel("진단 상세")

        if (part == null) {
            Text("주행에 지장을 주는\n고장이 없습니다.", fontSize = 36.sp, lineHeight = 50.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "차량 그림의 부위를 누르면 그 부품의 상태를 볼 수 있습니다.",
                fontSize = 21.sp,
                lineHeight = 32.sp,
                color = Lux.TextDim
            )
            return@Column
        }

        Text(part.label, fontSize = 42.sp, fontWeight = FontWeight.SemiBold, color = Lux.Text)

        if (advice == null) {
            StatePill("정상", Lux.Ok, Color(0xFF04210E))
            Text("이 부위에서 감지된 고장이 없습니다.", fontSize = 24.sp, lineHeight = 36.sp, color = Lux.TextSoft)
            return@Column
        }

        val color = levelColor(advice.level)
        StatePill("${advice.level}등급 · ${LevelInfo.title(advice.level)}", color, onLevelColor(advice.level))
        Text(
            LevelInfo.action(advice.level),
            fontSize = 28.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.SemiBold,
            color = Lux.Text
        )

        Hairline()
        AiExplanation(advice, env)

        Hairline()
        SectionLabel("판정 근거")
        advice.reasons.forEach { reason ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(reason.label, fontSize = 22.sp, color = Lux.TextSoft, modifier = Modifier.weight(1f))
                Text("+${reason.points}", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Lux.Text)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("긴급도 점수", fontSize = 22.sp, color = Lux.TextDim, modifier = Modifier.weight(1f))
            Text("${advice.score}", fontSize = 46.sp, fontWeight = FontWeight.SemiBold, color = color)
            Text("점", modifier = Modifier.padding(start = 4.dp, bottom = 8.dp), fontSize = 21.sp, color = Lux.TextDim)
        }
        if (advice.part.baseSeverity == 3 && advice.score < 4) {
            Text(
                "안전에 직결되는 부품이라 점수와 관계없이 최소 3등급으로 판정합니다.",
                fontSize = 18.sp,
                lineHeight = 28.sp,
                color = Lux.TextFaint
            )
        }
    }
}

/** 등급이나 정상 여부를 알리는 색 알약. */
@Composable
private fun StatePill(label: String, color: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .clip(Lux.Pill)
            .background(color)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = textColor)
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
        SectionLabel("AI 설명", color = Lux.Accent)
        Spacer(Modifier.width(14.dp))
        Text(source, fontSize = 18.sp, color = Lux.TextFaint)
    }
    Text(
        aiText ?: Explainer.explain(advice, env),
        fontSize = 23.sp,
        lineHeight = 37.sp,
        color = Lux.TextSoft
    )
    Text(
        "설명은 참고용입니다. 최종 판단은 정비 전문가에게 받으세요.",
        fontSize = 18.sp,
        color = Lux.TextFaint
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
            .luxPanel()
            .padding(horizontal = 32.dp, vertical = 28.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("자가진단")
                Text(
                    when {
                        done < 0 -> "전체 점검을 시작하세요"
                        running -> "점검 중  $done / $total"
                        else -> {
                            val faultCount = CHECK_ITEMS.count { item -> advices.any { it.part == item.part } }
                            if (faultCount == 0) "${total}개 항목 모두 정상"
                            else "${total}개 항목 중 ${faultCount}개에서 이상 발견"
                        }
                    },
                    fontSize = 36.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Lux.Text
                )
            }
            PrimaryButton(if (done < 0) "전체 점검" else "다시 점검", enabled = !running) { runId++ }
        }

        Spacer(Modifier.height(18.dp))
        LinearProgressIndicator(
            progress = { if (done < 0) 0f else done.toFloat() / total },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(Lux.Pill),
            color = Lux.Accent,
            trackColor = Lux.Stroke
        )
        Spacer(Modifier.height(8.dp))

        CHECK_ITEMS.forEachIndexed { index, item ->
            val advice = advices.firstOrNull { it.part == item.part }
            val checked = done > index
            val checking = running && done == index
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (checking) Lux.Selected else Color.Transparent)
                    .clickable(enabled = checked && advice != null) { onOpenPart(item.part) }
                    .padding(vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(width = 4.dp, height = 44.dp)
                        .background(if (checking) Lux.Accent else Color.Transparent)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = Lux.Text)
                    Text(item.detail, fontSize = 19.sp, color = Lux.TextDim)
                }
                when {
                    checking -> Text("점검 중…", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Lux.Accent)
                    !checked -> Text("대기", fontSize = 22.sp, color = Lux.TextFaint)
                    advice == null -> Text("정상", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Lux.Ok)
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            LevelInfo.title(advice.level),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = levelColor(advice.level)
                        )
                        Spacer(Modifier.width(14.dp))
                        LevelBadge(advice.level)
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            Hairline()
        }
        if (done >= total && advices.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("이상이 있는 항목을 누르면 진단 상세로 이동합니다.", fontSize = 18.sp, color = Lux.TextFaint)
        }
    }
}

/** 아래: 에뮬레이터에서 고장과 환경을 직접 넣어 보는 시연용 패널. */
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
            .luxPanel()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DemoRow("차량 값") {
            if (!vehicle.connected) {
                Text(
                    "차량 서비스에 연결되지 않았습니다" + (vehicle.connectionError?.let { " ($it)" } ?: ""),
                    fontSize = 19.sp,
                    color = Lux.TextDim
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
    }
}

@Composable
private fun DemoRow(title: String, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Lux.TextFaint,
            modifier = Modifier.width(96.dp)
        )
        content()
    }
}

@Composable
private fun VehicleValue(label: String, reading: Reading) {
    Row(
        modifier = Modifier.padding(end = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label  ", fontSize = 18.sp, color = Lux.TextFaint)
        Text(reading.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Lux.TextSoft)
    }
}
