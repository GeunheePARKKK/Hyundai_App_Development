package com.cardoctor.apr.diagnosis

/**
 * 규칙 기반 설명 문구. 현재 주행 환경을 문장에 넣어 "왜 지금 이 등급인지"를 말해 준다.
 * AI 설명을 쓸 수 없을 때(키 없음, 네트워크 오류)에도 이 문구가 그대로 표시된다.
 */
object Explainer {

    /** 지금 상황에서 이 고장이 왜 위험한지(또는 덜 위험한지) 한 문장. */
    fun situation(part: Part, env: Environment): String = when (part) {
        Part.HEADLAMP ->
            if (env.night) "지금은 야간이라 전조등 없이는 앞이 잘 보이지 않고, 단속 대상이기도 합니다."
            else "지금은 낮이라 운행에 지장이 없습니다."

        Part.WIPER ->
            if (env.rain) "비가 오는데 와이퍼가 제대로 움직이지 않아 앞 유리 시야를 확보하기 어렵습니다."
            else "지금은 비가 오지 않아 운행에 지장이 없습니다."

        Part.TIRE -> when {
            env.rain && env.highway -> "빗길 고속 주행 중이라 미끄러지거나 타이어가 손상될 위험이 큽니다."
            env.rain -> "노면이 젖어 있어 공기압이 낮은 타이어는 제동 거리가 더 길어집니다."
            env.highway -> "고속 주행 중에는 공기압이 낮은 타이어가 과열되어 손상되기 쉽습니다."
            else -> "일반 도로에서 속도를 줄여 주행하면 당장 큰 위험은 없습니다."
        }

        Part.COOLANT ->
            if (env.longTrip) "목적지까지 남은 거리가 길어, 이대로 계속 주행하면 엔진이 손상될 수 있습니다."
            else "엔진이 과열된 상태로 계속 주행하면 엔진이 손상될 수 있습니다."

        Part.BRAKE -> when {
            env.rain && env.highway -> "빗길 고속 주행 중이라 제동 거리가 길어져, 제때 서지 못할 위험이 매우 큽니다."
            env.rain -> "노면이 젖어 있어 제동 거리가 더 길어진 상태입니다."
            env.highway -> "고속 주행 중에는 제동 이상이 곧바로 사고로 이어질 수 있습니다."
            else -> "제동 장치 이상은 도로 상황과 관계없이 안전에 직접 영향을 줍니다."
        }

        Part.POWER -> "출력이 제한되어 가속이 느려지고, 원인에 따라 주행 중 멈출 수 있습니다."
    }

    /** 고장 설명과 지금 상황을 합친 기본 설명. 권고 행동은 화면에서 따로 보여 준다. */
    fun explain(advice: Advice, env: Environment): String =
        advice.part.explanation + " " + situation(advice.part, env)
}
