package com.cardoctor.apr.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cardoctor.apr.diagnosis.Part

/**
 * 음성 안내. 미리 만들어 둔 음성 파일(res/raw/voice_<부품>_<등급>.wav)을 재생한다.
 *
 * Pleos Connect 에뮬레이터에는 일반 앱이 쓸 수 있는 음성 합성 엔진이 없어서
 * (안드로이드 TextToSpeech 초기화 실패, Pleos TTS SDK는 준비 중) 녹음 파일 방식을 쓴다.
 * 파일은 tools/make_voice_clips.ps1 로 만들며, 3·4등급 안내만 있다.
 */
class VoiceGuide(context: Context) {

    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null

    /** 화면에 보여 줄 현재 상태. */
    var status by mutableStateOf("대기")
        private set

    /** 해당 부품·등급의 안내를 재생한다. 재생 중이던 안내는 끊는다. */
    fun announce(part: Part, level: Int) {
        stop()
        val name = "voice_${part.name.lowercase()}_$level"
        val resId = appContext.resources.getIdentifier(name, "raw", appContext.packageName)
        if (resId == 0) {
            Log.w(TAG, "no voice clip: $name")
            return
        }
        try {
            val file = appContext.resources.openRawResourceFd(resId)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.fileDescriptor, file.startOffset, file.length)
                file.close()
                setOnCompletionListener {
                    Log.d(TAG, "voice done: $name")
                    status = "대기"
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "voice error: $what/$extra")
                    status = "재생 실패"
                    true
                }
                prepare()
                start()
            }
            status = "안내 중"
            Log.d(TAG, "voice start: $name")
        } catch (e: Exception) {
            Log.w(TAG, "voice failed: $e")
            status = "재생 실패"
        }
    }

    fun stop() {
        player?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
                // 이미 끝났거나 준비되지 않은 상태. 해제만 하면 된다.
            }
            it.release()
        }
        player = null
        if (status == "안내 중") status = "대기"
    }

    private companion object {
        const val TAG = "CarDoctor"
    }
}
