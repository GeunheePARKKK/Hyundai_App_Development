package com.cardoctor.apr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.cardoctor.apr.ui.CarDoctorApp
import com.cardoctor.apr.ui.theme.CarDoctorTheme
import com.cardoctor.apr.vehicle.VehicleReader
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var vehicleReader: VehicleReader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vehicleReader = VehicleReader(this)
        vehicleReader.start()

        setContent {
            // 차량 값을 2초마다 다시 읽는다.
            LaunchedEffect(Unit) {
                while (true) {
                    vehicleReader.refresh()
                    delay(2000)
                }
            }
            CarDoctorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CarDoctorApp(
                        vehicle = vehicleReader.state,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        vehicleReader.stop()
        super.onDestroy()
    }
}
