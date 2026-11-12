package com.cardoctor.apr

import com.cardoctor.apr.diagnosis.AdviceEngine
import com.cardoctor.apr.diagnosis.Environment
import com.cardoctor.apr.diagnosis.Part
import org.junit.Assert.assertEquals
import org.junit.Test

/** 기획안의 시나리오 A·B·C와 판정 규칙을 확인한다. */
class AdviceEngineTest {

    @Test
    fun scenarioA_daytimeHeadlamp_isLevel1() {
        assertEquals(1, AdviceEngine.evaluate(Part.HEADLAMP, Environment()).level)
    }

    @Test
    fun scenarioB_nightHeadlamp_isLevel3() {
        assertEquals(3, AdviceEngine.evaluate(Part.HEADLAMP, Environment(night = true)).level)
    }

    @Test
    fun scenarioC_rainyHighwayTire_isLevel3() {
        val advice = AdviceEngine.evaluate(Part.TIRE, Environment(rain = true, highway = true))
        assertEquals(5, advice.score)
        assertEquals(3, advice.level)
    }

    @Test
    fun baseSeverity3_isAtLeastLevel3() {
        assertEquals(3, AdviceEngine.evaluate(Part.COOLANT, Environment()).level)
        assertEquals(3, AdviceEngine.evaluate(Part.BRAKE, Environment()).level)
    }

    @Test
    fun rainyHighwayBrake_isLevel4() {
        assertEquals(4, AdviceEngine.evaluate(Part.BRAKE, Environment(rain = true, highway = true)).level)
    }

    @Test
    fun evaluateAll_sortsMostUrgentFirst() {
        val result = AdviceEngine.evaluateAll(setOf(Part.HEADLAMP, Part.BRAKE, Part.TIRE), Environment())
        assertEquals(listOf(Part.BRAKE, Part.TIRE, Part.HEADLAMP), result.map { it.part })
    }
}
