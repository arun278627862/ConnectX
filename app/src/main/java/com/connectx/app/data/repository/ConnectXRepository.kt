package com.connectx.app.data.repository

import com.connectx.app.data.local.dao.CallLogDao
import com.connectx.app.data.local.dao.ChatDao
import com.connectx.app.data.local.dao.ContactDao
import com.connectx.app.data.local.dao.MessageDao
import com.connectx.app.data.local.entity.CallLogEntity
import com.connectx.app.data.local.entity.ChatEntity
import com.connectx.app.data.local.entity.ContactEntity
import com.connectx.app.data.local.entity.MessageEntity
import com.connectx.app.data.local.entity.MessageStatus
import com.connectx.app.data.local.entity.MessageType
import com.connectx.app.data.local.preferences.AppPreferencesManager
import com.connectx.app.data.remote.api.ConnectXApiService
import com.connectx.app.data.remote.api.LoginRequest
import com.connectx.app.data.remote.socket.RealtimeSocketManager
import com.connectx.app.webrtc.CallType
import com.connectx.app.webrtc.WebRtcClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectXRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val contactDao: ContactDao,
    private val callLogDao: CallLogDao,
    private val apiService: ConnectXApiService,
    private val prefsManager: AppPreferencesManager,
    private val socketManager: RealtimeSocketManager,
    private val webRtcClient: WebRtcClient
) {
    var currentUserId: String = "user_101"
        private set

    val allChats: Flow<List<ChatEntity>> = chatDao.getAllChats()
    val allContacts: Flow<List<ContactEntity>> = contactDao.getAllContacts()
    val allCallLogs: Flow<List<CallLogEntity>> = callLogDao.getAllCallLogs()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var callStartTimestamp: Long = 0L

    init {
        // ── Incoming text messages ──
        scope.launch {
            socketManager.incomingMessages.collect { msg ->
                val entity = MessageEntity(
                    id = msg.id,
                    chatId = msg.senderId,
                    senderId = msg.senderId,
                    senderName = msg.senderName,
                    receiverId = currentUserId,
                    content = msg.content,
                    type = MessageType.TEXT,
                    status = MessageStatus.READ,
                    timestamp = msg.timestamp
                )
                messageDao.insertMessage(entity)
                chatDao.updateLastMessage(msg.senderId, msg.content, msg.timestamp)
            }
        }

        // ── Incoming call invite (ring the phone) ──
        scope.launch {
            socketManager.incomingCallOffer.collect { json ->
                val callerId = json.optString("callerId", "")
                val callerName = json.optString("callerName", "ConnectX Caller")
                val callTypeStr = json.optString("callType", "VOICE")
                val type = if (callTypeStr == "VIDEO") CallType.VIDEO else CallType.VOICE
                webRtcClient.receiveIncomingCall(callerId, callerName, null, type)
            }
        }

        // ── SDP Offer received (callee side) — create PeerConnection + answer ──
        scope.launch {
            socketManager.sdpOfferFlow.collect { json ->
                val callerId = json.optString("callerId", "")
                val sdpJson = json.optString("sdp", "")
                webRtcClient.onRemoteSdpOffer(callerId, sdpJson)
            }
        }

        // ── SDP Answer received (caller side) — complete negotiation ──
        scope.launch {
            socketManager.sdpAnswerFlow.collect { json ->
                val sdpJson = json.optString("sdp", "")
                webRtcClient.onRemoteSdpAnswer(sdpJson)
            }
        }

        // ── ICE Candidate received (both sides) ──
        scope.launch {
            socketManager.iceCandidateFlow.collect { json ->
                val candidateJson = json.optString("candidate", "")
                webRtcClient.onRemoteIceCandidate(candidateJson)
            }
        }

        // ── Callee rejected the call ──
        scope.launch {
            socketManager.callRejectFlow.collect { json ->
                val targetId = json.optString("targetId", "")
                val peerName = webRtcClient.currentSession.value?.peerName ?: "User"
                // Save missed call log
                callLogDao.insertCallLog(
                    CallLogEntity(
                        id = "cl_${System.currentTimeMillis()}",
                        callerName = peerName,
                        callerAvatar = null,
                        callType = webRtcClient.currentSession.value?.callType?.name ?: "VOICE",
                        isIncoming = false,
                        isMissed = true,
                        durationSeconds = 0,
                        timestamp = System.currentTimeMillis()
                    )
                )
                webRtcClient.endCall()
            }
        }

        // ── Remote side ended the call ──
        scope.launch {
            socketManager.callEndFlow.collect { json ->
                val duration = if (callStartTimestamp > 0L)
                    ((System.currentTimeMillis() - callStartTimestamp) / 1000).toInt()
                else 0
                val peerName = webRtcClient.currentSession.value?.peerName ?: "User"
                callLogDao.insertCallLog(
                    CallLogEntity(
                        id = "cl_${System.currentTimeMillis()}",
                        callerName = peerName,
                        callerAvatar = null,
                        callType = webRtcClient.currentSession.value?.callType?.name ?: "VOICE",
                        isIncoming = webRtcClient.currentSession.value?.isIncoming ?: true,
                        isMissed = duration == 0,
                        durationSeconds = duration,
                        timestamp = System.currentTimeMillis()
                    )
                )
                callStartTimestamp = 0L
                webRtcClient.endCall()
            }
        }

        // ── WebRtcClient SDP Offer → send over socket ──
        scope.launch {
            webRtcClient.sdpOfferFlow.collect { (targetId, sdpJson) ->
                socketManager.sendSdpOffer(
                    callerId = currentUserId,
                    targetId = targetId,
                    sdpJson = sdpJson
                )
            }
        }

        // ── WebRtcClient SDP Answer → send over socket ──
        scope.launch {
            webRtcClient.sdpAnswerFlow.collect { (callerId, sdpJson) ->
                socketManager.sendSdpAnswer(
                    callerId = callerId,
                    targetId = currentUserId,
                    sdpJson = sdpJson
                )
            }
        }

        // ── WebRtcClient ICE candidates → send over socket ──
        scope.launch {
            webRtcClient.iceCandidateFlow.collect { (targetId, candidateJson) ->
                socketManager.sendIceCandidate(
                    senderId = currentUserId,
                    targetId = targetId,
                    candidateJson = candidateJson
                )
            }
        }

        // ── WebRtcClient ended call → notify peer ──
        scope.launch {
            webRtcClient.callEndedFlow.collect { targetId ->
                if (targetId.isNotEmpty()) {
                    socketManager.sendCallEnd(senderId = currentUserId, targetId = targetId)
                }
                val duration = if (callStartTimestamp > 0L)
                    ((System.currentTimeMillis() - callStartTimestamp) / 1000).toInt()
                else 0
                val peerName = webRtcClient.currentSession.value?.peerName ?: "User"
                callLogDao.insertCallLog(
                    CallLogEntity(
                        id = "cl_${System.currentTimeMillis()}",
                        callerName = peerName,
                        callerAvatar = null,
                        callType = webRtcClient.currentSession.value?.callType?.name ?: "VOICE",
                        isIncoming = webRtcClient.currentSession.value?.isIncoming ?: false,
                        isMissed = duration == 0,
                        durationSeconds = duration,
                        timestamp = System.currentTimeMillis()
                    )
                )
                callStartTimestamp = 0L
            }
        }

        // ── WebRtcClient rejected call → notify caller ──
        scope.launch {
            webRtcClient.callRejectedFlow.collect { callerId ->
                if (callerId.isNotEmpty()) {
                    socketManager.sendCallReject(callerId = callerId, targetId = currentUserId)
                }
            }
        }

        // ── Track call connected time ──
        scope.launch {
            webRtcClient.callState.collect { state ->
                if (state == com.connectx.app.webrtc.CallState.CONNECTED && callStartTimestamp == 0L) {
                    callStartTimestamp = System.currentTimeMillis()
                }
            }
        }

        // ── New user joined (live discovery) ──
        scope.launch {
            socketManager.newUserDiscovered.collect { json ->
                val userId = json.optString("id")
                val name = json.optString("name")
                val email = json.optString("email")
                val phone = json.optString("phoneNumber", "")
                if (userId.isNotEmpty() && userId != currentUserId) {
                    contactDao.insertContact(
                        ContactEntity(
                            id = userId,
                            name = name,
                            phoneNumber = phone,
                            email = email,
                            avatarUrl = null,
                            isOnline = true,
                            lastSeen = "Online"
                        )
                    )
                    chatDao.insertChat(
                        ChatEntity(
                            id = userId,
                            name = name,
                            avatarUrl = null,
                            isGroup = false,
                            lastMessage = "Tap to start chatting",
                            lastMessageTimestamp = System.currentTimeMillis(),
                            isOnline = true,
                            lastSeen = "Online"
                        )
                    )
                }
            }
        }

        // ── User status changes (online/offline) ──
        scope.launch {
            socketManager.userStatusUpdate.collect { json ->
                val userId = json.optString("userId")
                val isOnline = json.optBoolean("isOnline", false)
                val lastSeen = json.optString("lastSeen", "")
                if (userId.isNotEmpty()) {
                    contactDao.updateOnlineStatus(userId, isOnline, if (isOnline) "Online" else "Last seen recently")
                }
            }
        }
    }

    fun getMessagesForChat(chatId: String): Flow<List<MessageEntity>> = messageDao.getMessagesForChat(chatId)
    fun getPinnedMessages(chatId: String): Flow<List<MessageEntity>> = messageDao.getPinnedMessages(chatId)
    fun getStarredMessages(): Flow<List<MessageEntity>> = messageDao.getStarredMessages()

    suspend fun login(email: String, pass: String): Result<Boolean> {
        return try {
            val response = apiService.login(LoginRequest(email = email, password = pass))
            if (response.isSuccessful && response.body() != null) {
                val auth = response.body()!!
                currentUserId = auth.userId
                prefsManager.saveAuthTokens(
                    accessToken = auth.accessToken,
                    refreshToken = auth.refreshToken,
                    userId = auth.userId,
                    email = auth.email,
                    name = auth.name,
                    phone = auth.phone,
                    photoUrl = auth.photoUrl
                )
                socketManager.connect("https://connectx-5kk8.onrender.com", auth.userId)

                // Fetch all live users from server
                try {
                    val usersResp = apiService.getUsers()
                    if (usersResp.isSuccessful && usersResp.body() != null) {
                        val liveUsers = usersResp.body()!!.filter { it.id != auth.userId }
                        contactDao.insertContacts(liveUsers)
                        liveUsers.forEach { user ->
                            chatDao.insertChat(
                                ChatEntity(
                                    id = user.id,
                                    name = user.name,
                                    avatarUrl = user.avatarUrl,
                                    isGroup = false,
                                    lastMessage = "Tap to start chatting",
                                    lastMessageTimestamp = System.currentTimeMillis(),
                                    isOnline = user.isOnline,
                                    lastSeen = if (user.isOnline) "Online" else "Recently"
                                )
                            )
                        }
                    }
                } catch (ignored: Exception) {}

                Result.success(true)
            } else {
                Result.failure(Exception("Authentication failed"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot reach server: ${e.localizedMessage}"))
        }
    }

    suspend fun sendMessage(
        chatId: String,
        content: String,
        type: MessageType = MessageType.TEXT,
        mediaUrl: String? = null,
        mediaPath: String? = null,
        fileName: String? = null,
        fileSize: Long = 0,
        latitude: Double? = null,
        longitude: Double? = null,
        replyToMsgId: String? = null,
        replyToName: String? = null,
        replyToText: String? = null
    ) {
        val msgId = "msg_${System.currentTimeMillis()}"
        val message = MessageEntity(
            id = msgId,
            chatId = chatId,
            senderId = currentUserId,
            senderName = "Me",
            receiverId = chatId,
            content = content,
            type = type,
            status = MessageStatus.SENT,
            mediaUrl = mediaUrl,
            mediaLocalPath = mediaPath,
            fileName = fileName,
            fileSize = fileSize,
            latitude = latitude,
            longitude = longitude,
            replyToMessageId = replyToMsgId,
            replyToSenderName = replyToName,
            replyToContent = replyToText,
            timestamp = System.currentTimeMillis()
        )
        messageDao.insertMessage(message)
        chatDao.updateLastMessage(chatId, if (type == MessageType.TEXT) content else "Attachment", System.currentTimeMillis())
        socketManager.sendMessage(chatId = chatId, senderId = currentUserId, senderName = "Me", content = content)
    }

    fun startCall(targetUserId: String, targetUserName: String, type: CallType) {
        webRtcClient.startCall(
            callerId = currentUserId,
            targetId = targetUserId,
            targetName = targetUserName,
            targetAvatar = null,
            type = type
        )
        // Also send the ring invitation so the callee's phone shows incoming call screen
        socketManager.sendCallOffer(
            callerId = currentUserId,
            callerName = "Me",
            targetId = targetUserId,
            callType = type.name
        )
    }

    fun acceptCall() {
        webRtcClient.acceptCall()
        // After accepting, the callee creates a PeerConnection
        // SDP offer will arrive via sdpOfferFlow → handled automatically in init{}
    }

    fun rejectCall() {
        webRtcClient.rejectCall()
    }

    suspend fun togglePin(msgId: String, currentPin: Boolean) {
        messageDao.updatePinned(msgId, !currentPin)
    }

    suspend fun toggleStar(msgId: String, currentStar: Boolean) {
        messageDao.updateStarred(msgId, !currentStar)
    }

    suspend fun addReaction(msgId: String, reaction: String) {
        messageDao.updateReactions(msgId, reaction)
    }

    suspend fun deleteMessageForMe(msgId: String) {
        messageDao.deleteForMe(msgId)
    }

    suspend fun deleteMessageForEveryone(msgId: String) {
        messageDao.deleteForEveryone(msgId)
    }

    suspend fun createGroup(name: String, description: String, memberIds: List<String>) {
        val groupId = "group_${System.currentTimeMillis()}"
        val chat = ChatEntity(
            id = groupId,
            name = name,
            avatarUrl = null,
            isGroup = true,
            groupDescription = description,
            adminId = currentUserId,
            lastMessage = "Group created",
            lastMessageTimestamp = System.currentTimeMillis(),
            memberIds = memberIds.joinToString(",")
        )
        chatDao.insertChat(chat)
    }

    suspend fun seedMockData(currentUserId: String = "user_101") {
        val mockContacts = listOf(
            ContactEntity("user_alice", "Alice Vance", "+1 555-0101", "alice@connectx.io", null, "Available for calls!", true, "Online"),
            ContactEntity("user_bob", "Bob Smith", "+1 555-0102", "bob@connectx.io", null, "At work...", true, "Online"),
            ContactEntity("user_charlie", "Charlie Brown", "+1 555-0103", "charlie@connectx.io", null, "In a meeting", true, "Online"),
            ContactEntity("user_diana", "Diana Prince", "+1 555-0104", "diana@connectx.io", null, "Exploring ConnectX", true, "Online")
        )
        contactDao.insertContacts(mockContacts)

        val mockChats = listOf(
            ChatEntity("user_alice", "Alice Vance", null, isGroup = false, lastMessage = "Let me know when you're online!", lastMessageTimestamp = System.currentTimeMillis() - 300000, unreadCount = 0, isOnline = true, lastSeen = "Online"),
            ChatEntity("user_bob", "Bob Smith", null, isGroup = false, lastMessage = "Hey! Testing real-time connection.", lastMessageTimestamp = System.currentTimeMillis() - 3600000, unreadCount = 0, isOnline = true, lastSeen = "Online"),
            ChatEntity("group_301", "ConnectX Devs", null, isGroup = true, groupDescription = "Official ConnectX Live Chat", adminId = currentUserId, lastMessage = "Welcome to live real-time chat!", lastMessageTimestamp = System.currentTimeMillis() - 86400000, unreadCount = 0)
        )
        chatDao.insertChats(mockChats)

        val mockCallLogs = listOf(
            CallLogEntity("cl_1", "Alice Vance", null, "VIDEO", isIncoming = true, isMissed = false, durationSeconds = 145, timestamp = System.currentTimeMillis() - 1800000),
            CallLogEntity("cl_2", "Bob Smith", null, "VOICE", isIncoming = false, isMissed = false, durationSeconds = 62, timestamp = System.currentTimeMillis() - 7200000),
            CallLogEntity("cl_3", "Charlie Brown", null, "VOICE", isIncoming = true, isMissed = true, durationSeconds = 0, timestamp = System.currentTimeMillis() - 86400000)

        )
        mockCallLogs.forEach { callLogDao.insertCallLog(it) }
    }
}
