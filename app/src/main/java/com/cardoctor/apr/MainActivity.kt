package com.cardoctor.apr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.cardoctor.apr.ui.CarDoctorApp
import com.cardoctor.apr.ui.theme.CarDoctorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CarDoctorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CarDoctorApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
