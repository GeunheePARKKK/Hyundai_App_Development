package com.cardoctor.apr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 화면 전체의 색과 공통 부품.
 *
 * 방향: 어두운 바탕 위에 은색 글자와 샴페인색 강조를 쓰는 고급차 계기판 느낌.
 * 색을 많이 쓰지 않고, 경고 색(노랑·빨강)만 눈에 띄게 한다.
 */
internal object Lux {
    val BackgroundTop = Color(0xFF12161B)
    val BackgroundBottom = Color(0xFF07090B)

    val Panel = Color(0xFF14181D)
    val PanelHigh = Color(0xFF1C2128)
    val Stroke = Color(0x1FFFFFFF)
    val StrokeStrong = Color(0x40FFFFFF)

    val Text = Color(0xFFF1F3F5)
    val TextSoft = Color(0xFFCDD3DA)
    val TextDim = Color(0xFF939CA8)
    val TextFaint = Color(0xFF626B77)

    /** 강조색(샴페인). 선택 상태, 작은 제목, 진행 표시에 쓴다. */
    val Accent = Color(0xFFCDB58B)
    val AccentSoft = Color(0xFFEADFC8)

    val Ok = Color(0xFF7AD3A8)
    val Caution = Color(0xFFEDB54A)
    val Danger = Color(0xFFFF5B4F)

    val PanelShape = RoundedCornerShape(28.dp)
    val RowShape = RoundedCornerShape(18.dp)
    val Pill = RoundedCornerShape(50)
}

/** 기획안 등급표의 색: 1·2등급 노랑, 3·4등급 빨강. 4등급은 화면에서 깜빡인다. */
internal fun levelColor(level: Int): Color = if (level >= 3) Lux.Danger else Lux.Caution

/** 위가 살짝 밝은 어두운 판. 화면의 큰 구역에 쓴다. */
internal fun Modifier.luxPanel(): Modifier = this
    .clip(Lux.PanelShape)
    .background(Brush.verticalGradient(listOf(Lux.PanelHigh, Lux.Panel)))
    .border(1.dp, Lux.Stroke, Lux.PanelShape)

/** 구역의 작은 제목. 글자 사이를 띄워 조용하게 구분한다. */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Lux.Accent) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 3.sp,
        color = color
    )
}

/** 켜고 끄는 알약 모양 버튼. 켜지면 강조색 테두리와 옅은 채움. */
@Composable
internal fun Chip(
    label: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(Lux.Pill)
            .background(if (on) Lux.Accent.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (on) Lux.Accent else Lux.Stroke, Lux.Pill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 19.sp,
            fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
            color = if (on) Lux.AccentSoft else Lux.TextDim
        )
    }
}

/** 강조색으로 채운 주 버튼. 한 화면에 하나만 쓴다. */
@Composable
internal fun PrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(Lux.Pill)
            .background(if (enabled) Lux.Accent else Lux.Stroke)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 36.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Color(0xFF1A1610) else Lux.TextFaint
        )
    }
}

@Composable
internal fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Lux.Stroke)
    )
}
