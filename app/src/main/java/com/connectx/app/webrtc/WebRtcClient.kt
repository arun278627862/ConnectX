package com.connectx.app.webrtc

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class CallState {
    IDLE, OUTGOING, INCOMING, CONNECTED, ENDED
}

enum class CallType {
    VOICE, VIDEO
}

data class CallSession(
    val callId: String,
    val peerName: String,
    val peerAvatar: String?,
    val callType: CallType,
    val isIncoming: Boolean
)

@Singleton
class WebRtcClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState

    private val _currentSession = MutableStateFlow<CallSession?>(null)
    val currentSession: StateFlow<CallSession?> = _currentSession

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted

    private val _isVideoEnabled = MutableStateFlow(true)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn

    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera

    fun startCall(peerName: String, peerAvatar: String?, type: CallType) {
        val session = CallSession(
            callId = System.currentTimeMillis().toString(),
            peerName = peerName,
            peerAvatar = peerAvatar,
            callType = type,
            isIncoming = false
        )
        _currentSession.value = session
        _callState.value = CallState.OUTGOING
    }

    fun receiveIncomingCall(peerName: String, peerAvatar: String?, type: CallType) {
        val session = CallSession(
            callId = System.currentTimeMillis().toString(),
            peerName = peerName,
            peerAvatar = peerAvatar,
            callType = type,
            isIncoming = true
        )
        _currentSession.value = session
        _callState.value = CallState.INCOMING
    }

    fun acceptCall() {
        _callState.value = CallState.CONNECTED
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun toggleVideo() {
        _isVideoEnabled.value = !_isVideoEnabled.value
    }

    fun toggleSpeaker() {
        _isSpeakerOn.value = !_isSpeakerOn.value
    }

    fun switchCamera() {
        _isFrontCamera.value = !_isFrontCamera.value
    }

    fun endCall() {
        _callState.value = CallState.ENDED
        _callState.value = CallState.IDLE
        _currentSession.value = null
        _isMuted.value = false
        _isVideoEnabled.value = true
    }
}
