package com.connectx.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ConnectXApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
