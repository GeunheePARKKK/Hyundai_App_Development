package com.cardoctor.apr.diagnosis

/** 진단 대상 부품. baseSeverity는 기획안 판정표의 기본 심각도(1~3). */
enum class Part(
    val label: String,
    val baseSeverity: Int,
    val explanation: String
) {
    HEADLAMP(
        label = "전조등",
        baseSeverity = 1,
        explanation = "전조등이 켜지지 않습니다. 낮에는 주행에 지장이 적지만, 어두운 곳에서는 앞이 보이지 않고 다른 차가 내 차를 알아보기 어렵습니다."
    ),
    WIPER(
        label = "와이퍼",
        baseSeverity = 1,
        explanation = "와이퍼가 정상 동작하지 않습니다. 맑은 날에는 문제가 없지만, 비나 눈이 오면 앞 유리를 닦지 못해 시야가 막힙니다."
    ),
    TIRE(
        label = "타이어 공기압",
        baseSeverity = 2,
        explanation = "타이어 공기압이 기준보다 낮습니다. 제동 거리가 길어지고, 빠른 속도나 젖은 노면에서는 미끄러지거나 타이어가 손상될 수 있습니다."
    ),
    COOLANT(
        label = "냉각수 과열",
        baseSeverity = 3,
        explanation = "냉각수 온도가 정상 범위를 넘었습니다. 계속 주행하면 엔진이 손상될 수 있습니다."
    ),
    BRAKE(
        label = "브레이크",
        baseSeverity = 3,
        explanation = "제동 장치에 이상이 감지됐습니다. 차를 제때 세우지 못할 수 있어 안전에 직접 영향을 줍니다."
    ),
    POWER(
        label = "구동 출력 제한",
        baseSeverity = 3,
        explanation = "차량이 구동 출력을 제한하고 있습니다. 가속이 평소보다 느려지고, 원인에 따라 주행 중 멈출 수 있습니다."
    )
}

/** 판정에 반영하는 주행 환경. */
data class Environment(
    val night: Boolean = false,
    val rain: Boolean = false,
    val highway: Boolean = false,
    val longTrip: Boolean = false
)

/** 점수에 더해진 항목 하나. 화면에서 "왜 이 등급인가"를 보여 주는 데 쓴다. */
data class Reason(val label: String, val points: Int)

/** 부품 하나에 대한 판정 결과. level은 1(여유 있게 정비)~4(즉시 정차). */
data class Advice(
    val part: Part,
    val score: Int,
    val level: Int,
    val reasons: List<Reason>
)

object LevelInfo {
    fun title(level: Int): String = when (level) {
        4 -> "즉시 정차"
        3 -> "즉시 정비"
        2 -> "목적지 후 정비"
        else -> "여유 있게 정비"
    }

    /** 기획안 등급표의 권고 문구. */
    fun action(level: Int): String = when (level) {
        4 -> "안전한 곳에 정차하고 견인·긴급출동을 부르세요."
        3 -> "가장 가까운 정비소로 바로 가세요."
        2 -> "목적지까지는 운행할 수 있습니다. 오늘 중 점검을 권장합니다."
        else -> "주말이나 다음 정기점검 때 정비소를 방문하세요."
    }
}
