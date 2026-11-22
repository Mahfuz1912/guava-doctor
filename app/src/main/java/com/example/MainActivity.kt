package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guavacare.ai.GuavaCareApplication
import com.guavacare.ai.di.AppContainer
import com.guavacare.ai.presentation.GuavaCareApp
import com.guavacare.ai.presentation.MainViewModel

class MainActivity : ComponentActivity() {

    private val appContainer: AppContainer
        get() = (application as GuavaCareApplication).appContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val mainViewModel: MainViewModel = viewModel(
                factory = MainViewModel.Factory(appContainer)
            )
            GuavaCareApp(viewModel = mainViewModel)
        }
    }
}
