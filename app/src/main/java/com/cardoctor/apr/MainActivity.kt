package com.cardoctor.apr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cardoctor.apr.env.LiveEnvironment
import com.cardoctor.apr.env.LiveEnvironmentReader
import com.cardoctor.apr.ui.CarDoctorApp
import com.cardoctor.apr.ui.theme.CarDoctorTheme
import com.cardoctor.apr.vehicle.VehicleReader
import com.cardoctor.apr.voice.VoiceGuide
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var vehicleReader: VehicleReader
    private lateinit var voiceGuide: VoiceGuide

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vehicleReader = VehicleReader(this)
        vehicleReader.start()
        voiceGuide = VoiceGuide(this)

        setContent {
            // 차량 값을 2초마다 다시 읽는다.
            LaunchedEffect(Unit) {
                while (true) {
                    vehicleReader.refresh()
                    delay(2000)
                }
            }
            // 날씨와 낮·밤은 5분마다 다시 확인한다.
            var live by remember { mutableStateOf(LiveEnvironment()) }
            LaunchedEffect(Unit) {
                while (true) {
                    live = LiveEnvironmentReader.read()
                    delay(5 * 60 * 1000L)
                }
            }
            CarDoctorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CarDoctorApp(
                        vehicle = vehicleReader.state,
                        live = live,
                        voiceStatus = voiceGuide.status,
                        onAnnounce = voiceGuide::announce,
                        onStopSpeaking = voiceGuide::stop,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        vehicleReader.stop()
        voiceGuide.stop()
        super.onDestroy()
    }
}
