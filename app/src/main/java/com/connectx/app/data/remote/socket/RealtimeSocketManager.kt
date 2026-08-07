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
    private var socket: Socket? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _incomingMessages = MutableSharedFlow<SocketMessage>()
    val incomingMessages: SharedFlow<SocketMessage> = _incomingMessages

    private val _incomingCallOffer = MutableSharedFlow<JSONObject>()
    val incomingCallOffer: SharedFlow<JSONObject> = _incomingCallOffer

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
            }
            val cleanUrl = if (wsUrl.startsWith("wss://")) wsUrl.replace("wss://", "https://") 
                           else if (wsUrl.startsWith("ws://")) wsUrl.replace("ws://", "http://") 
                           else wsUrl

            socket = IO.socket(cleanUrl, options)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d("SocketManager", "Connected to Socket server: $cleanUrl")
                socket?.emit("register_user", userId)
            }

            socket?.on("receive_message") { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    val obj = args[0] as JSONObject
                    val msg = SocketMessage(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        chatId = obj.optString("senderId", "user_201"),
                        senderId = obj.optString("senderId", "user_201"),
                        senderName = obj.optString("senderName", "Friend"),
                        content = obj.optString("content", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                    scope.launch { _incomingMessages.emit(msg) }
                }
            }

            socket?.on("call_offer") { args ->
                if (args.isNotEmpty() && args[0] is JSONObject) {
                    scope.launch { _incomingCallOffer.emit(args[0] as JSONObject) }
                }
            }

            socket?.connect()
        } catch (e: Exception) {
            Log.e("SocketManager", "Socket connection error: ${e.message}")
        }
    }

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

    fun sendCallOffer(callerId: String, callerName: String, targetId: String, callType: String) {
        val obj = JSONObject().apply {
            put("callerId", callerId)
            put("callerName", callerName)
            put("targetId", targetId)
            put("callType", callType)
        }
        socket?.emit("call_offer", obj)
    }
}
