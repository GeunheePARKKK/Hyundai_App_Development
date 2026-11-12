package com.cardoctor.apr.diagnosis

/**
 * 규칙 기반 긴급도 판정.
 *
 * 긴급도 점수 = 기본 심각도(1~3) + 환경 가중치의 합
 * 등급: 6 이상 = 4, 4~5 = 3, 2~3 = 2, 1 = 1
 * 기본 심각도가 3인 부품은 환경과 관계없이 최소 3등급.
 */
object AdviceEngine {

    fun evaluate(part: Part, env: Environment): Advice {
        val reasons = mutableListOf(Reason("기본 심각도 (${part.label})", part.baseSeverity))

        if (env.night && part == Part.HEADLAMP) {
            reasons += Reason("야간 주행 중 등화류 고장", 3)
        }
        if (env.rain) {
            when (part) {
                Part.WIPER -> reasons += Reason("비 오는 날 와이퍼 고장", 3)
                Part.TIRE, Part.BRAKE -> reasons += Reason("젖은 노면에서 제동·접지 저하", 2)
                else -> Unit
            }
        }
        if (env.highway && (part == Part.TIRE || part == Part.BRAKE)) {
            reasons += Reason("고속 주행 중", 1)
        }
        if (env.longTrip && part == Part.COOLANT) {
            reasons += Reason("목적지까지 남은 거리가 김", 1)
        }

        val score = reasons.sumOf { it.points }
        var level = when {
            score >= 6 -> 4
            score >= 4 -> 3
            score >= 2 -> 2
            else -> 1
        }
        if (part.baseSeverity == 3) level = maxOf(level, 3)

        return Advice(part, score, level, reasons)
    }

    /** 여러 고장을 판정해 긴급한 순서로 돌려준다. */
    fun evaluateAll(faults: Set<Part>, env: Environment): List<Advice> =
        faults.map { evaluate(it, env) }
            .sortedWith(compareByDescending<Advice> { it.level }.thenByDescending { it.score })
}
