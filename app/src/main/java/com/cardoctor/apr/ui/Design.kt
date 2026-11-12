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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 화면 전체의 색과 공통 부품.
 *
 * 방향: 밝은 바탕에 흰 판을 올리고, 글자는 진한 먹색으로 또렷하게 쓴다.
 * 장식 색은 금색 하나만 조금 쓰고, 경고 색(주황·빨강)만 눈에 띄게 한다.
 */
internal object Lux {
    val BackgroundTop = Color(0xFFF7F8FA)
    val BackgroundBottom = Color(0xFFECEFF3)

    val Panel = Color.White
    val RowFill = Color(0xFFF6F7F9)
    val Stroke = Color(0x1F0B1220)
    val StrokeStrong = Color(0x520B1220)

    val Text = Color(0xFF14171B)
    val TextSoft = Color(0xFF2A3038)
    val TextDim = Color(0xFF4B5460)
    val TextFaint = Color(0xFF6C7581)

    /** 선택 상태와 주 버튼에 쓰는 진한 먹색. */
    val Accent = Color(0xFF171B21)

    /** 장식용 금색. 앱 이름, 탭 밑줄, 진행 표시처럼 작은 곳에만 쓴다. */
    val Gold = Color(0xFF9A7B4F)
    val GoldLight = Color(0xFFE2CFA6)

    val Ok = Color(0xFF1B8A57)
    val Caution = Color(0xFFC77700)
    val Danger = Color(0xFFD92D20)

    val PanelShape = RoundedCornerShape(28.dp)
    val RowShape = RoundedCornerShape(18.dp)
    val Pill = RoundedCornerShape(50)
}

/** 기획안 등급표의 색: 1·2등급 노랑(주황빛), 3·4등급 빨강. 4등급은 화면에서 깜빡인다. */
internal fun levelColor(level: Int): Color = if (level >= 3) Lux.Danger else Lux.Caution

/** 옅은 그림자가 있는 흰 판. 화면의 큰 구역에 쓴다. */
internal fun Modifier.luxPanel(): Modifier = this
    .shadow(elevation = 10.dp, shape = Lux.PanelShape, ambientColor = Color(0x14000000), spotColor = Color(0x1F000000))
    .clip(Lux.PanelShape)
    .background(Lux.Panel)
    .border(1.dp, Lux.Stroke, Lux.PanelShape)

/** 구역의 작은 제목. */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Lux.TextDim) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        color = color
    )
}

/** 켜고 끄는 알약 모양 버튼. 켜지면 진한 바탕에 흰 글자. */
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
            .background(if (on) Lux.Accent else Color.White)
            .border(1.dp, if (on) Lux.Accent else Lux.StrokeStrong, Lux.Pill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 20.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
            color = if (on) Color.White else Lux.Text
        )
    }
}

/** 진한 색으로 채운 주 버튼. 한 화면에 하나만 쓴다. */
@Composable
internal fun PrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(Lux.Pill)
            .background(if (enabled) Lux.Accent else Color(0xFFC9CED6))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 36.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
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
