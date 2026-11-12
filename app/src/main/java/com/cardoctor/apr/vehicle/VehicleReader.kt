package com.cardoctor.apr.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cardoctor.apr.diagnosis.Environment
import com.cardoctor.apr.diagnosis.Part

/** 차량에서 읽은 값 하나. value가 null이고 error가 있으면 읽기에 실패한 것. */
data class Reading(val value: String? = null, val error: String? = null) {
    val text: String
        get() = value ?: when {
            error == null -> "확인 중"
            "Permission" in error -> "권한 없음"
            "not supported" in error -> "미지원 차량"
            else -> "읽기 실패"
        }
}

/** Vehicle SDK로 읽은 현재 차량 상태. */
data class VehicleState(
    val connected: Boolean = false,
    val connectionError: String? = null,
    val brakeFluid: Reading = Reading(),
    val powerDown: Reading = Reading(),
    val lowBeam: Reading = Reading(),
    val wiper: Reading = Reading(),
    val telltales: Reading = Reading(),
    /** 켜져 있는 경고등 중 진단 대상 부품에 해당하는 것. */
    val telltaleFaults: Set<Part> = emptySet()
) {
    /** 차량 값에서 판단한 고장. */
    val faults: Set<Part>
        get() = buildSet {
            if (brakeFluid.value in BRAKE_FLUID_FAULTS) add(Part.BRAKE)
            if (powerDown.value == POWER_DOWN_ON) add(Part.POWER)
            addAll(telltaleFaults)
        }

    /** 차량 값에서 추정한 주행 환경. 전조등이 켜져 있으면 야간, 와이퍼가 동작 중이면 비. */
    val environment: Environment
        get() = Environment(
            night = lowBeam.value == LightState.ON.name,
            rain = wiper.value == WiperState.ON.name
        )

    companion object {
        const val POWER_DOWN_ON = "켜짐"
        const val POWER_DOWN_OFF = "꺼짐"
        private val BRAKE_FLUID_FAULTS = setOf(
            OilLevelState.LOW.name,
            OilLevelState.CRITICALLY_LOW.name,
            OilLevelState.ERROR.name
        )
    }
}

/**
 * Pleos Vehicle SDK에서 진단에 쓰는 값을 읽는다.
 *
 * 일반 앱 권한으로 읽을 수 있는 값: 출력 제한 경고, 브레이크액 수위, 전조등(하향등) 상태, 와이퍼 상태.
 * 브레이크액 수위는 차종에 따라 지원하지 않는다(Pleos Connect 에뮬레이터 v2.1은 미지원).
 * 경고등(Telltales)과 실제 타이어 공기압은 SDK에서 시스템 앱 전용 권한
 * (PRIVATE_CAR_SAFETY, signature|privileged)으로 막혀 있어, 시도는 하되 실패하면 그대로 표시한다.
 */
class VehicleReader(context: Context) {

    private val vehicle = Vehicle(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var initialized = false

    var state by mutableStateOf(VehicleState())
        private set

    fun start() {
        if (initialized) return
        try {
            vehicle.initialize()
            initialized = true
            update { copy(connected = true, connectionError = null) }
        } catch (e: Exception) {
            Log.w(TAG, "Vehicle initialize failed", e)
            update { copy(connected = false, connectionError = e.message ?: e.javaClass.simpleName) }
        }
    }

    /** 모든 값을 한 번씩 다시 읽는다. 결과는 콜백으로 state에 반영된다. */
    fun refresh() {
        if (!initialized) {
            start()
            if (!initialized) return
        }

        read("brakeFluid", { copy(brakeFluid = it) }) { done, failed ->
            vehicle.getSafety().getBrakeFluidLevelState({ done(it?.name) }, failed)
        }
        read("powerDown", { copy(powerDown = it) }) { done, failed ->
            vehicle.getSafety().hasPowerDownWarningIndicator(
                { done(it?.let { on -> if (on) VehicleState.POWER_DOWN_ON else VehicleState.POWER_DOWN_OFF }) },
                failed
            )
        }
        read("lowBeam", { copy(lowBeam = it) }) { done, failed ->
            vehicle.getLight().getCurrentLowBeamLightState({ done(it?.name) }, failed)
        }
        read("wiper", { copy(wiper = it) }) { done, failed ->
            vehicle.getWiper().getCurrentWiperState(
                WindowArea(WindowValue.FRONT_WINDSHIELD),
                { done(it?.name) },
                failed
            )
        }
        readTelltales()
    }

    fun stop() {
        if (!initialized) return
        try {
            vehicle.release()
        } catch (e: Exception) {
            Log.w(TAG, "Vehicle release failed", e)
        }
        initialized = false
    }

    private fun readTelltales() {
        val onFailed: (Exception) -> Unit = { e ->
            Log.w(TAG, "telltales failed: $e")
            update { copy(telltales = Reading(error = e.message ?: e.javaClass.simpleName), telltaleFaults = emptySet()) }
        }
        try {
            vehicle.getSafety().getTelltalesInfo(
                { result ->
                    val active = result.telltales.filter { it.state != TelltaleState.OFF }
                    Log.d(TAG, "telltales on: ${active.map { it.type }}")
                    val faults = active.mapNotNull { TELLTALE_TO_PART[it.type] }.toSet()
                    update { copy(telltales = Reading(value = "켜진 경고등 ${active.size}개"), telltaleFaults = faults) }
                },
                onFailed
            )
        } catch (e: Exception) {
            onFailed(e)
        }
    }

    private fun read(
        name: String,
        apply: VehicleState.(Reading) -> VehicleState,
        call: (done: (String?) -> Unit, failed: (Exception) -> Unit) -> Unit
    ) {
        val failed: (Exception) -> Unit = { e ->
            Log.w(TAG, "$name failed: $e")
            update { apply(Reading(error = e.message ?: e.javaClass.simpleName)) }
        }
        try {
            call(
                { value ->
                    Log.d(TAG, "$name = $value")
                    update { apply(Reading(value = value ?: "값 없음")) }
                },
                failed
            )
        } catch (e: Exception) {
            failed(e)
        }
    }

    /** SDK 콜백은 다른 스레드에서 올 수 있어 메인 스레드에서 상태를 바꾼다. */
    private fun update(change: VehicleState.() -> VehicleState) {
        mainHandler.post { state = state.change() }
    }

    private companion object {
        const val TAG = "CarDoctor"

        val TELLTALE_TO_PART = mapOf(
            TelltaleType.TPMS to Part.TIRE,
            TelltaleType.ENGINE_TEMPERATURE to Part.COOLANT,
            TelltaleType.ABS to Part.BRAKE,
            TelltaleType.LED_HEADLAMP to Part.HEADLAMP
        )
    }
}
