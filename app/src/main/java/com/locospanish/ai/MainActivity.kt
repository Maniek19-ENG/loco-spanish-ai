package com.locospanish.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import com.locospanish.ai.ui.*

class MainActivity : ComponentActivity() {
    private val vm: LocoViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(16, 18, 15)))
        setContent { LocoApp(vm) }
    }
    override fun onStop() {
        super.onStop()
        // Rotation retains the ViewModel and peer. Backgrounding explicitly stops the microphone.
        if (!isChangingConfigurations) vm.finish()
    }
}
