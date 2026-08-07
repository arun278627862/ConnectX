package com.connectx.app.data.remote.socket

import android.util.Log
import com.connectx.app.data.local.preferences.AppPreferencesManager
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class SocketMessage(
    val id: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long
)

@Singleton
class RealtimeSocketManager @Inject constructor(
    private val prefsManager: AppPreferencesManager
) {
    private val TAG = "RealtimeSocketManager"
    private var socket: Socket? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // ── Inbound flows ──────────────────────────────────────────────────

    private val _incomingMessages = MutableSharedFlow<SocketMessage>()
    val incomingMessages: SharedFlow<SocketMessage> = _incomingMessages

    private val _incomingCallOffer = MutableSharedFlow<JSONObject>()
    val incomingCallOffer: SharedFlow<JSONObject> = _incomingCallOffer

    private val _newUserDiscovered = MutableSharedFlow<JSONObject>()
    val newUserDiscovered: SharedFlow<JSONObject> = _newUserDiscovered

    private val _userStatusUpdate = MutableSharedFlow<JSONObject>()
    val userStatusUpdate: SharedFlow<JSONObject> = _userStatusUpdate

    // WebRTC signaling inbound flows
    private val _sdpOfferFlow = MutableSharedFlow<JSONObject>()
    val sdpOfferFlow: SharedFlow<JSONObject> = _sdpOfferFlow

    private val _sdpAnswerFlow = MutableSharedFlow<JSONObject>()
    val sdpAnswerFlow: SharedFlow<JSONObject> = _sdpAnswerFlow

    private val _iceCandidateFlow = MutableSharedFlow<JSONObject>()
    val iceCandidateFlow: SharedFlow<JSONObject> = _iceCandidateFlow

    private val _callRejectFlow = MutableSharedFlow<JSONObject>()
    val callRejectFlow: SharedFlow<JSONObject> = _callRejectFlow

    private val _callEndFlow = MutableSharedFlow<JSONObject>()
    val callEndFlow: SharedFlow<JSONObject> = _callEndFlow

    private val _callBusyFlow = MutableSharedFlow<JSONObject>()
    val callBusyFlow: SharedFlow<JSONObject> = _callBusyFlow

    init {
        scope.launch {
            val config = prefsManager.appConfigFlow.first()
            val tokens = prefsManager.authTokensFlow.first()
            val userId = tokens.userId ?: "user_${System.currentTimeMillis()}"
            connect(config.webSocketUrl, userId)
        }
    }

    fun connect(wsUrl: String, userId: String) {
        try {
            val options = IO.Options().apply {
                forceNew = true
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 1000
                timeout = 20000
            }
            val cleanUrl = wsUrl
                .replace("wss://", "https://")
                .replace("ws://", "http://")

            socket = IO.socket(cleanUrl, options)

            // ── Connect ──
            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Connected: $cleanUrl")
                socket?.emit("register_user", userId)
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.w(TAG, "Disconnected from server")
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "Connection error: ${args.firstOrNull()}")
            }

            // ── User discovery ──
            socket?.on("user_registered") { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    scope.launch { _newUserDiscovered.emit(args[0] as JSONObject) }
                }
            }

            socket?.on("user_status") { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    scope.launch { _userStatusUpdate.emit(args[0] as JSONObject) }
                }
            }

            // ── Chat messages ──
            socket?.on("receive_message") { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    val obj = args[0] as JSONObject
                    val msg = SocketMessage(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        chatId = obj.optString("senderId", ""),
                        senderId = obj.optString("senderId", ""),
                        senderName = obj.optString("senderName", "Friend"),
                        content = obj.optString("content", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                    scope.launch { _incomingMessages.emit(msg) }
                }
            }

            // ── WebRTC Call Invite ──
            socket?.on("call_offer") { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    scope.launch { _incomingCallOffer.emit(args[0] as JSONObject) }
                }
            }

            // ── WebRTC SDP Offer (from caller → callee) ──
            socket?.on("sdp_offer") { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    Log.d(TAG, "Received sdp_offer")
                    scope.launch { _sdpOfferFlow.emit(args[0] as JSONObject) }
                }
            }

            // ── WebRTC SDP Answer (from callee → caller) ──
            socket?.on("sdp_answer") { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    Log.d(TAG, "Received sdp_answer")
                    scope.launch { _sdpAnswerFlow.emit(args[0] as JSONObject) }
                }
            }

            // ── ICE Candidates (both directions) ──
            socket?.on("ice_candidate") { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    scope.launch { _iceCandidateFlow.emit(args[0] as JSONObject) }
                }
            }

            // ── Call Control ──
            socket?.on("call_reject") { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    scope.launch { _callRejectFlow.emit(args[0] as JSONObject) }
                }
            }

            socket?.on("call_end") { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    scope.launch { _callEndFlow.emit(args[0] as JSONObject) }
                }
            }

            socket?.on("call_busy") { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    scope.launch { _callBusyFlow.emit(args[0] as JSONObject) }
                }
            }

            socket?.connect()
            Log.d(TAG, "Connecting to $cleanUrl as $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Socket connection error: ${e.message}")
        }
    }

    // ── Outbound: Chat ──

    fun sendMessage(chatId: String, senderId: String, senderName: String, content: String) {
        val obj = JSONObject().apply {
            put("id", "msg_${System.currentTimeMillis()}")
            put("chatId", chatId)
            put("senderId", senderId)
            put("senderName", senderName)
            put("content", content)
            put("timestamp", System.currentTimeMillis())
        }
        socket?.emit("send_message", obj)
    }

    // ── Outbound: Call Invite ──
    fun sendCallOffer(callerId: String, callerName: String, targetId: String, callType: String) {
        val obj = JSONObject().apply {
            put("callerId", callerId)
            put("callerName", callerName)
            put("targetId", targetId)
            put("callType", callType)
        }
        socket?.emit("call_offer", obj)
    }

    // ── Outbound: SDP Offer ──
    fun sendSdpOffer(callerId: String, targetId: String, sdpJson: String) {
        val obj = JSONObject().apply {
            put("callerId", callerId)
            put("targetId", targetId)
            put("sdp", sdpJson)
        }
        socket?.emit("sdp_offer", obj)
        Log.d(TAG, "Sent sdp_offer to $targetId")
    }

    // ── Outbound: SDP Answer ──
    fun sendSdpAnswer(callerId: String, targetId: String, sdpJson: String) {
        val obj = JSONObject().apply {
            put("callerId", callerId)
            put("targetId", targetId)
            put("sdp", sdpJson)
        }
        socket?.emit("sdp_answer", obj)
        Log.d(TAG, "Sent sdp_answer to $callerId")
    }

    // ── Outbound: ICE Candidate ──
    fun sendIceCandidate(senderId: String, targetId: String, candidateJson: String) {
        val obj = JSONObject().apply {
            put("senderId", senderId)
            put("targetId", targetId)
            put("candidate", candidateJson)
        }
        socket?.emit("ice_candidate", obj)
    }

    // ── Outbound: Call Control ──
    fun sendCallReject(callerId: String, targetId: String) {
        val obj = JSONObject().apply {
            put("callerId", callerId)
            put("targetId", targetId)
        }
        socket?.emit("call_reject", obj)
    }

    fun sendCallEnd(senderId: String, targetId: String) {
        val obj = JSONObject().apply {
            put("senderId", senderId)
            put("targetId", targetId)
        }
        socket?.emit("call_end", obj)
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }
}
