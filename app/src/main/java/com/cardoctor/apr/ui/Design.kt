package com.cardoctor.apr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
 * 방향: 밝은 바탕에 요소를 적게 두는 단순한 화면.
 * 상자와 테두리를 거의 쓰지 않고 여백과 글자 크기로 구분한다.
 * 색은 글자의 먹색과 회색이 기본이고, 등급 색(주황·빨강)과 파란색 스위치만 예외다.
 */
internal object Lux {
    /** 차량 그림과 메뉴가 놓이는 흰 바탕. */
    val Surface = Color.White

    /** 오른쪽 상세 구역의 옅은 회색 바탕. */
    val SurfaceDim = Color(0xFFF4F4F5)

    /** 버튼과 선택된 줄의 회색. */
    val Fill = Color(0xFFE8E9EB)
    val Line = Color(0xFFE2E3E6)

    val Text = Color(0xFF1C1C1E)
    val TextDim = Color(0xFF5C5E62)
    val TextFaint = Color(0xFF8A8D93)

    val Accent = Color(0xFF3E6AE1)

    val Ok = Color(0xFF12824C)
    val Caution = Color(0xFFC77700)
    val Danger = Color(0xFFD62B2B)

    val ButtonShape = RoundedCornerShape(10.dp)
    val Pill = RoundedCornerShape(50)
}

/** 기획안 등급표의 색: 1·2등급 노랑(주황빛), 3·4등급 빨강. 4등급은 화면에서 깜빡인다. */
internal fun levelColor(level: Int): Color = if (level >= 3) Lux.Danger else Lux.Caution

/** 값 위에 붙는 작은 회색 설명. */
@Composable
internal fun Caption(text: String, modifier: Modifier = Modifier, color: Color = Lux.TextFaint) {
    Text(text, modifier = modifier, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = color)
}

/** 옅은 회색 버튼. 켜진 상태는 진한 바탕에 흰 글자. */
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
            .clip(Lux.ButtonShape)
            .background(if (on) Lux.Text else Lux.Fill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
            color = if (on) Color.White else Lux.Text
        )
    }
}

/** 한 화면에 하나만 쓰는 큰 버튼. */
@Composable
internal fun ActionButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(Lux.ButtonShape)
            .background(Lux.Fill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 30.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Lux.Text else Lux.TextFaint
        )
    }
}

/** 켜짐·꺼짐 스위치. */
@Composable
internal fun LuxSwitch(on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 54.dp, height = 30.dp)
            .clip(Lux.Pill)
            .background(if (on) Lux.Accent else Color(0xFFC9CBD0))
            .clickable(onClick = onClick)
            .padding(3.dp),
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
            .background(Lux.Line)
    )
}
