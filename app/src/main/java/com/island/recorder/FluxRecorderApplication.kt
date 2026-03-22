package com.island.recorder

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class IslandRecorderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
