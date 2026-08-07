package com.connectx.app.webrtc

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.*
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
    val callerId: String,
    val targetId: String,
    val peerName: String,
    val peerAvatar: String?,
    val callType: CallType,
    val isIncoming: Boolean
)

@Singleton
class WebRtcClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "WebRtcClient"
    private val scope = CoroutineScope(Dispatchers.IO)

    // ─── States ───
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

    // ─── Outbound Signaling Flows ──
    // Repository collects these and emits them over Socket.IO
    private val _sdpOfferFlow = MutableSharedFlow<Pair<String, String>>() // targetId, sdpJson
    val sdpOfferFlow: SharedFlow<Pair<String, String>> = _sdpOfferFlow

    private val _sdpAnswerFlow = MutableSharedFlow<Pair<String, String>>() // callerId, sdpJson
    val sdpAnswerFlow: SharedFlow<Pair<String, String>> = _sdpAnswerFlow

    private val _iceCandidateFlow = MutableSharedFlow<Pair<String, String>>() // targetId, candidateJson
    val iceCandidateFlow: SharedFlow<Pair<String, String>> = _iceCandidateFlow

    private val _callEndedFlow = MutableSharedFlow<String>() // targetId
    val callEndedFlow: SharedFlow<String> = _callEndedFlow

    private val _callRejectedFlow = MutableSharedFlow<String>() // callerId
    val callRejectedFlow: SharedFlow<String> = _callRejectedFlow

    // ─── WebRTC Internals ───
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var localVideoCapturer: VideoCapturer? = null
    private var localSurfaceRenderer: SurfaceViewRenderer? = null
    private var remoteSurfaceRenderer: SurfaceViewRenderer? = null
    private var eglBase: EglBase? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    // ICE Servers: Google STUN + Metered.ca free public TURN
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        // Metered.ca free open TURN — no account required
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer()
    )

    init {
        initializePeerConnectionFactory()
    }

    private fun initializePeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        eglBase = EglBase.create()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase!!.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true))
            .createPeerConnectionFactory()

        Log.d(TAG, "PeerConnectionFactory initialized")
    }

    // ─── OUTGOING CALL ───
    fun startCall(
        callerId: String,
        targetId: String,
        targetName: String,
        targetAvatar: String?,
        type: CallType
    ) {
        _currentSession.value = CallSession(
            callId = "call_${System.currentTimeMillis()}",
            callerId = callerId,
            targetId = targetId,
            peerName = targetName,
            peerAvatar = targetAvatar,
            callType = type,
            isIncoming = false
        )
        _callState.value = CallState.OUTGOING
        requestAudioFocus()
        createPeerConnection()
        createAndSendOffer(targetId, type)
    }

    // ─── INCOMING CALL ───
    fun receiveIncomingCall(
        callerId: String,
        callerName: String,
        callerAvatar: String?,
        type: CallType
    ) {
        _currentSession.value = CallSession(
            callId = "call_${System.currentTimeMillis()}",
            callerId = callerId,
            targetId = "",
            peerName = callerName,
            peerAvatar = callerAvatar,
            callType = type,
            isIncoming = true
        )
        _callState.value = CallState.INCOMING
    }

    // ─── ACCEPT CALL — callee side ───
    fun acceptCall() {
        requestAudioFocus()
        createPeerConnection()
        _callState.value = CallState.CONNECTED
    }

    // ─── RECEIVE SDP OFFER (callee receives from caller) ───
    fun onRemoteSdpOffer(callerId: String, sdpJson: String) {
        Log.d(TAG, "Received SDP offer from $callerId")
        try {
            val sdpObj = JSONObject(sdpJson)
            val sdp = SessionDescription(
                SessionDescription.Type.OFFER,
                sdpObj.optString("sdp", sdpJson)
            )
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    Log.d(TAG, "Remote SDP offer set successfully")
                    // Create answer
                    peerConnection?.createAnswer(object : SdpObserver {
                        override fun onCreateSuccess(answer: SessionDescription) {
                            peerConnection?.setLocalDescription(object : SdpObserver {
                                override fun onSetSuccess() {
                                    val answerJson = JSONObject().apply {
                                        put("type", "answer")
                                        put("sdp", answer.description)
                                    }.toString()
                                    scope.launch { _sdpAnswerFlow.emit(Pair(callerId, answerJson)) }
                                    Log.d(TAG, "SDP answer emitted to $callerId")
                                }
                                override fun onSetFailure(s: String?) { Log.e(TAG, "Set local desc failed: $s") }
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onCreateFailure(s: String?) {}
                            }, answer)
                        }
                        override fun onCreateFailure(s: String?) { Log.e(TAG, "Create answer failed: $s") }
                        override fun onSetSuccess() {}
                        override fun onSetFailure(s: String?) {}
                    }, MediaConstraints())
                }
                override fun onSetFailure(s: String?) { Log.e(TAG, "Set remote SDP failed: $s") }
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(s: String?) {}
            }, sdp)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process SDP offer: ${e.message}")
        }
    }

    // ─── RECEIVE SDP ANSWER (caller receives from callee) ───
    fun onRemoteSdpAnswer(sdpJson: String) {
        Log.d(TAG, "Received SDP answer")
        try {
            val sdpObj = JSONObject(sdpJson)
            val sdp = SessionDescription(
                SessionDescription.Type.ANSWER,
                sdpObj.optString("sdp", sdpJson)
            )
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    Log.d(TAG, "Remote SDP answer set — PeerConnection negotiated!")
                    _callState.value = CallState.CONNECTED
                }
                override fun onSetFailure(s: String?) { Log.e(TAG, "Set remote answer failed: $s") }
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(s: String?) {}
            }, sdp)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process SDP answer: ${e.message}")
        }
    }

    // ─── RECEIVE ICE CANDIDATE (both sides) ───
    fun onRemoteIceCandidate(candidateJson: String) {
        try {
            val obj = JSONObject(candidateJson)
            val candidate = IceCandidate(
                obj.optString("sdpMid"),
                obj.optInt("sdpMLineIndex"),
                obj.optString("candidate")
            )
            peerConnection?.addIceCandidate(candidate)
            Log.d(TAG, "Added remote ICE candidate")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add ICE candidate: ${e.message}")
        }
    }

    // ─── END CALL ───
    fun endCall() {
        val targetId = _currentSession.value?.let {
            if (it.isIncoming) it.callerId else it.targetId
        } ?: ""
        scope.launch { _callEndedFlow.emit(targetId) }
        cleanupCall()
    }

    // ─── REJECT CALL (callee rejects incoming) ───
    fun rejectCall() {
        val callerId = _currentSession.value?.callerId ?: ""
        scope.launch { _callRejectedFlow.emit(callerId) }
        cleanupCall()
    }

    // ─── MEDIA CONTROLS ───
    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        localAudioTrack?.setEnabled(!_isMuted.value)
    }

    fun toggleVideo() {
        _isVideoEnabled.value = !_isVideoEnabled.value
        localVideoTrack?.setEnabled(_isVideoEnabled.value)
    }

    fun toggleSpeaker() {
        _isSpeakerOn.value = !_isSpeakerOn.value
        audioManager?.isSpeakerphoneOn = _isSpeakerOn.value
    }

    fun switchCamera() {
        _isFrontCamera.value = !_isFrontCamera.value
        (localVideoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    // ─── RENDERER SETUP ───
    fun setLocalSurfaceRenderer(renderer: SurfaceViewRenderer) {
        localSurfaceRenderer = renderer
        renderer.init(eglBase?.eglBaseContext, null)
        renderer.setMirror(true)
    }

    fun setRemoteSurfaceRenderer(renderer: SurfaceViewRenderer) {
        remoteSurfaceRenderer = renderer
        renderer.init(eglBase?.eglBaseContext, null)
        renderer.setMirror(false)
    }

    // ─── PRIVATE HELPERS ───

    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val candidateJson = JSONObject().apply {
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                    put("candidate", candidate.sdp)
                }.toString()
                val targetId = _currentSession.value?.let {
                    if (it.isIncoming) it.callerId else it.targetId
                } ?: ""
                scope.launch { _iceCandidateFlow.emit(Pair(targetId, candidateJson)) }
                Log.d(TAG, "New ICE candidate → $targetId")
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                Log.d(TAG, "PeerConnectionState: $newState")
                when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> _callState.value = CallState.CONNECTED
                    PeerConnection.PeerConnectionState.DISCONNECTED,
                    PeerConnection.PeerConnectionState.FAILED -> cleanupCall()
                    else -> {}
                }
            }
            override fun onTrack(transceiver: RtpTransceiver) {
                transceiver.receiver.track()?.let { track ->
                    if (track is VideoTrack) {
                        track.addSink(remoteSurfaceRenderer)
                    }
                }
            }
            override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?) {}
            override fun onAddStream(s: MediaStream?) {}
            override fun onRemoveStream(s: MediaStream?) {}
            override fun onDataChannel(dc: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(r: RtpReceiver?, streams: Array<out MediaStream>?) {}
        })

        // Add audio track
        val audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory?.createAudioTrack("audio_track_local", audioSource)
        localAudioTrack?.setEnabled(true)
        peerConnection?.addTrack(localAudioTrack)

        // Add video track for video calls
        val session = _currentSession.value
        if (session?.callType == CallType.VIDEO) {
            val videoSource = peerConnectionFactory?.createVideoSource(false)
            localVideoCapturer = createVideoCapturer()
            localVideoCapturer?.initialize(
                SurfaceTextureHelper.create("CaptureThread", eglBase?.eglBaseContext),
                context,
                videoSource?.capturerObserver
            )
            localVideoCapturer?.startCapture(1280, 720, 30)
            localVideoTrack = peerConnectionFactory?.createVideoTrack("video_track_local", videoSource)
            localVideoTrack?.setEnabled(true)
            localVideoTrack?.addSink(localSurfaceRenderer)
            peerConnection?.addTrack(localVideoTrack)
        }

        Log.d(TAG, "PeerConnection created with ${iceServers.size} ICE servers")
    }

    private fun createAndSendOffer(targetId: String, type: CallType) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            if (type == CallType.VIDEO) {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }
        }
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(offer: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        val offerJson = JSONObject().apply {
                            put("type", "offer")
                            put("sdp", offer.description)
                        }.toString()
                        scope.launch { _sdpOfferFlow.emit(Pair(targetId, offerJson)) }
                        Log.d(TAG, "SDP offer created and emitted to $targetId")
                    }
                    override fun onSetFailure(s: String?) { Log.e(TAG, "Set local offer failed: $s") }
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(s: String?) {}
                }, offer)
            }
            override fun onCreateFailure(s: String?) { Log.e(TAG, "Create offer failed: $s") }
            override fun onSetSuccess() {}
            override fun onSetFailure(s: String?) {}
        }, constraints)
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        // Prefer front camera
        for (name in deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                val capturer = enumerator.createCapturer(name, null)
                if (capturer != null) return capturer
            }
        }
        // Fallback to any camera
        for (name in deviceNames) {
            val capturer = enumerator.createCapturer(name, null)
            if (capturer != null) return capturer
        }
        return null
    }

    private fun requestAudioFocus() {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager?.isSpeakerphoneOn = true
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build()
            )
            .build()
        audioFocusRequest = focusRequest
        audioManager?.requestAudioFocus(focusRequest)
    }

    private fun cleanupCall() {
        try {
            localVideoCapturer?.stopCapture()
            localVideoCapturer?.dispose()
            localVideoTrack?.dispose()
            localAudioTrack?.dispose()
            peerConnection?.close()
            peerConnection?.dispose()
        } catch (e: Exception) { Log.e(TAG, "Cleanup error: ${e.message}") }

        localVideoCapturer = null
        localVideoTrack = null
        localAudioTrack = null
        peerConnection = null

        audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        audioManager?.mode = AudioManager.MODE_NORMAL

        _callState.value = CallState.IDLE
        _currentSession.value = null
        _isMuted.value = false
        _isVideoEnabled.value = true
        _isSpeakerOn.value = true
    }
}
