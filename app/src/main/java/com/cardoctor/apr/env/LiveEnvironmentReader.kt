package com.cardoctor.apr.env

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** 실시간으로 알아낸 주행 환경. 값을 알 수 없으면 null. */
data class LiveEnvironment(
    val night: Boolean? = null,
    val rain: Boolean? = null,
    val summary: String = "실시간 환경 확인 중"
)

/**
 * 현재 시각과 날씨로 야간·악천후 여부를 판단한다.
 *
 * 날씨와 일출·일몰 시각은 Open-Meteo(무료, API 키 없음)에서 받는다.
 * 인터넷이 안 되면 날씨는 알 수 없음으로 두고, 시각만으로 낮·밤을 판단한다.
 * 에뮬레이터의 시간대 설정과 관계없이 한국 시각(Asia/Seoul)을 쓴다.
 */
object LiveEnvironmentReader {

    private const val TAG = "CarDoctor"

    // 기준 위치: Pleos 에뮬레이터 지도의 기본 차량 위치(성남시 수정구).
    private const val LATITUDE = 37.41
    private const val LONGITUDE = 127.10
    private const val PLACE = "성남"

    private val KOREA: ZoneId = ZoneId.of("Asia/Seoul")
    private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    // 마지막으로 받은 일출·일몰 시각. 받기 전에는 대략적인 값을 쓴다.
    private var sunrise: LocalTime = LocalTime.of(6, 30)
    private var sunset: LocalTime = LocalTime.of(18, 30)

    suspend fun read(): LiveEnvironment = withContext(Dispatchers.IO) {
        try {
            fromWeatherService()
        } catch (e: Exception) {
            Log.w(TAG, "weather failed: $e")
            fromClock()
        }
    }

    private fun fromWeatherService(): LiveEnvironment {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$LATITUDE&longitude=$LONGITUDE" +
            "&current=is_day,precipitation,weather_code,temperature_2m" +
            "&daily=sunrise,sunset&timezone=Asia%2FSeoul&forecast_days=1"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        val json = try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }

        val daily = json.getJSONObject("daily")
        sunrise = LocalDateTime.parse(daily.getJSONArray("sunrise").getString(0)).toLocalTime()
        sunset = LocalDateTime.parse(daily.getJSONArray("sunset").getString(0)).toLocalTime()

        val current = json.getJSONObject("current")
        val code = current.getInt("weather_code")
        val night = current.getInt("is_day") == 0
        val rain = current.getDouble("precipitation") > 0.0 || code in WET_CODES
        val temperature = current.getDouble("temperature_2m")

        return LiveEnvironment(
            night = night,
            rain = rain,
            summary = "$PLACE 기준 · ${weatherText(code)} ${temperature}°C · " +
                "일몰 ${sunset.format(CLOCK)} · ${now().format(CLOCK)} 갱신"
        )
    }

    private fun fromClock(): LiveEnvironment {
        val time = now().toLocalTime()
        return LiveEnvironment(
            night = time.isBefore(sunrise) || !time.isBefore(sunset),
            rain = null,
            summary = "날씨를 받지 못해 시각만 반영 · ${time.format(CLOCK)}"
        )
    }

    private fun now(): ZonedDateTime = ZonedDateTime.now(KOREA)

    /** 비·눈·소나기·뇌우에 해당하는 WMO 날씨 코드. */
    private val WET_CODES = (51..67) + (71..77) + (80..86) + (95..99)

    private fun weatherText(code: Int): String = when (code) {
        0 -> "맑음"
        1, 2 -> "대체로 맑음"
        3 -> "흐림"
        45, 48 -> "안개"
        in 51..57 -> "이슬비"
        in 61..67 -> "비"
        in 71..77 -> "눈"
        in 80..82 -> "소나기"
        85, 86 -> "눈 소나기"
        in 95..99 -> "뇌우"
        else -> "날씨 코드 $code"
    }
}
