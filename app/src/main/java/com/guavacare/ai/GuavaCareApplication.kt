package com.guavacare.ai

import android.app.Application
import com.guavacare.ai.di.AppContainer

class GuavaCareApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(this) }

    override fun onTerminate() {
        appContainer.release()
        super.onTerminate()
    }
}
