package com.cardoctor.apr.ai

import android.util.Log
import com.cardoctor.apr.BuildConfig
import com.cardoctor.apr.diagnosis.Advice
import com.cardoctor.apr.diagnosis.Environment
import com.cardoctor.apr.diagnosis.LevelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gemini API로 고장 설명 문장을 만든다.
 *
 * 등급은 AdviceEngine이 정한 값을 그대로 전달하고, 모델에는 설명 문장만 맡긴다.
 * API 키는 local.properties의 GEMINI_API_KEY에서 읽는다(깃에 올라가지 않는 파일).
 */
object GeminiExplainer {

    private const val TAG = "CarDoctor"
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

    private const val SYSTEM_PROMPT =
        "당신은 차량 정비 어드바이저입니다. 운전 중인 일반 운전자에게 차량 고장을 설명합니다. " +
            "주어진 긴급도 등급과 권고 행동은 이미 확정된 값이므로 바꾸거나 다른 등급을 말하지 마세요. " +
            "한국어 존댓말로, 전문 용어 없이 3문장 이내로 답하세요. " +
            "순서는 (1) 무슨 고장인지와 흔한 원인, (2) 지금 주행 상황에서 왜 위험한지 또는 덜 위험한지, (3) 지금 할 일입니다. " +
            "목록 기호, 제목, 강조 표시 없이 문장만 쓰세요."

    private val cache = mutableMapOf<String, String>()

    val isConfigured: Boolean
        get() = BuildConfig.GEMINI_API_KEY.isNotBlank()

    /** 설명 문장을 돌려준다. 키가 없거나 호출에 실패하면 Result.failure. */
    suspend fun explain(advice: Advice, env: Environment): Result<String> {
        if (!isConfigured) return Result.failure(IllegalStateException("API 키 없음"))

        val key = "${advice.part}|${advice.level}|$env"
        cache[key]?.let { return Result.success(it) }

        return withContext(Dispatchers.IO) {
            runCatching {
                val prompt = buildPrompt(advice, env)
                val text = try {
                    callInteractions(prompt)
                } catch (e: ApiException) {
                    // 계정이나 모델에 따라 새 엔드포인트가 안 될 수 있어, 기존 방식으로 한 번 더 시도한다.
                    if (e.code == 404 || e.code == 400) callGenerateContent(prompt) else throw e
                }
                text.trim().also {
                    require(it.isNotEmpty()) { "빈 응답" }
                    cache[key] = it
                }
            }.onFailure { Log.w(TAG, "Gemini failed: $it") }
        }
    }

    private fun buildPrompt(advice: Advice, env: Environment): String {
        val conditions = buildList {
            add(if (env.night) "야간" else "주간")
            add(if (env.rain) "비가 옴" else "비가 오지 않음")
            add(if (env.highway) "고속도로 주행 중" else "일반 도로")
            if (env.longTrip) add("목적지까지 남은 거리가 김")
        }
        return """
            고장 부품: ${advice.part.label}
            고장 내용: ${advice.part.explanation}
            현재 주행 상황: ${conditions.joinToString(", ")}
            확정된 긴급도: ${advice.level}등급 (${LevelInfo.title(advice.level)})
            확정된 권고 행동: ${LevelInfo.action(advice.level)}
            판정 근거: ${advice.reasons.joinToString(", ") { "${it.label} +${it.points}" }}

            위 내용을 운전자에게 설명해 주세요.
        """.trimIndent()
    }

    /** 현재 문서 기준 엔드포인트(interactions). */
    private fun callInteractions(prompt: String): String {
        val body = JSONObject()
            .put("model", BuildConfig.GEMINI_MODEL)
            .put("input", prompt)
            .put("system_instruction", SYSTEM_PROMPT)
            .put("generation_config", JSONObject().put("thinking_level", "low"))
        val response = post("$BASE/interactions", body, mapOf("Api-Revision" to "2026-05-20"))

        val steps = response.optJSONArray("steps") ?: JSONArray()
        val text = StringBuilder()
        for (i in 0 until steps.length()) {
            val step = steps.optJSONObject(i) ?: continue
            if (step.optString("type") != "model_output") continue
            val content = step.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val item = content.optJSONObject(j) ?: continue
                if (item.optString("type") == "text") text.append(item.optString("text"))
            }
        }
        return text.toString()
    }

    /** 이전 방식 엔드포인트(generateContent). */
    private fun callGenerateContent(prompt: String): String {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT))))
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        val response = post("$BASE/models/${BuildConfig.GEMINI_MODEL}:generateContent", body, emptyMap())

        val parts = response.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts") ?: JSONArray()
        val text = StringBuilder()
        for (i in 0 until parts.length()) text.append(parts.optJSONObject(i)?.optString("text").orEmpty())
        return text.toString()
    }

    private fun post(url: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw ApiException(code, text.take(300))
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private class ApiException(val code: Int, detail: String) : Exception("HTTP $code: $detail")
}
