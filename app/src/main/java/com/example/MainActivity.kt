package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.NumberScopeApp
import com.example.ui.NumberScopeViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: NumberScopeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NumberScopeApp(viewModel = viewModel)
        }
    }
}
