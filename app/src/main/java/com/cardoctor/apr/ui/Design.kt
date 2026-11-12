package com.cardoctor.apr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 화면 전체의 색과 공통 부품.
 *
 * 방향: 차량 순정 인포테인먼트 화면처럼 검은 바탕에 흰 글자, 선택 표시는 파란색 하나.
 * 상자를 쌓지 않고 가는 선으로 줄을 나누며, 경고는 주황·빨강 테두리로 알린다.
 * 글자는 흰색과 밝은 회색만 쓰고 가는 글꼴은 쓰지 않는다(어두운 바탕에서 읽기 어렵다).
 */
internal object Lux {
    val Background = Color(0xFF050608)
    val Panel = Color(0xFF0D1014)

    /** 선택된 줄의 바탕(파란 기운). */
    val Selected = Color(0xFF10202F)
    val Stroke = Color(0xFF242B34)
    val StrokeStrong = Color(0xFF3D4753)

    val Text = Color(0xFFFFFFFF)
    val TextSoft = Color(0xFFE6EAEE)
    val TextDim = Color(0xFFB7C0CA)
    val TextFaint = Color(0xFF8D97A3)

    /** 선택·활성 표시에 쓰는 파란색. */
    val Accent = Color(0xFF2E9BFF)
    val AccentDeep = Color(0xFF0F3558)

    val Ok = Color(0xFF34D058)
    val Caution = Color(0xFFFF9F1A)
    val Danger = Color(0xFFFF4D42)

    val PanelShape = RoundedCornerShape(16.dp)
    val Pill = RoundedCornerShape(50)

    /** 경고 창에 쓰는 모서리를 깎은 모양. */
    val WarningShape = CutCornerShape(24.dp)
}

/** 기획안 등급표의 색: 1·2등급 주황(노랑), 3·4등급 빨강. 4등급은 화면에서 깜빡인다. */
internal fun levelColor(level: Int): Color = if (level >= 3) Lux.Danger else Lux.Caution

/** 등급 색 위에 올리는 글자색. 밝은 주황 위에는 검은 글자가 더 잘 읽힌다. */
internal fun onLevelColor(level: Int): Color = if (level >= 3) Color.White else Color(0xFF1C1200)

/** 화면의 큰 구역. 테두리 없이 바탕보다 조금 밝은 판. */
internal fun Modifier.luxPanel(): Modifier = this
    .clip(Lux.PanelShape)
    .background(Lux.Panel)

/** 구역의 작은 제목. */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Lux.TextFaint) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = color
    )
}

/** 켜고 끄는 알약 모양 버튼. 켜지면 파란 테두리와 파란 바탕. */
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
            .background(if (on) Lux.AccentDeep else Color.Transparent)
            .border(1.5.dp, if (on) Lux.Accent else Lux.StrokeStrong, Lux.Pill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 20.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
            color = if (on) Color.White else Lux.TextSoft
        )
    }
}

/** 파란색으로 채운 주 버튼. 한 화면에 하나만 쓴다. */
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
            fontSize = 23.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Color.White else Lux.TextFaint
        )
    }
}

/** 켜짐·꺼짐 스위치. */
@Composable
internal fun LuxSwitch(on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 58.dp, height = 32.dp)
            .clip(Lux.Pill)
            .background(if (on) Lux.Accent else Lux.StrokeStrong)
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White)
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
