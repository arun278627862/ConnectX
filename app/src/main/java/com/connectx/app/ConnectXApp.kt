package com.connectx.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.webrtc.PeerConnectionFactory

@HiltAndroidApp
class ConnectXApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Must initialize WebRTC native libraries from Application context
        // before any PeerConnectionFactory is created elsewhere
        val options = PeerConnectionFactory.InitializationOptions.builder(this)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }
}
