package com.connectx.app.data.repository

import com.connectx.app.data.local.dao.ChatDao
import com.connectx.app.data.local.dao.ContactDao
import com.connectx.app.data.local.dao.MessageDao
import com.connectx.app.data.local.entity.ChatEntity
import com.connectx.app.data.local.entity.ContactEntity
import com.connectx.app.data.local.entity.MessageEntity
import com.connectx.app.data.local.entity.MessageStatus
import com.connectx.app.data.local.entity.MessageType
import com.connectx.app.data.local.preferences.AppPreferencesManager
import com.connectx.app.data.remote.api.ConnectXApiService
import com.connectx.app.data.remote.api.LoginRequest
import com.connectx.app.data.remote.socket.RealtimeSocketManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectXRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val contactDao: ContactDao,
    private val callLogDao: com.connectx.app.data.local.dao.CallLogDao,
    private val apiService: ConnectXApiService,
    private val prefsManager: AppPreferencesManager,
    private val socketManager: RealtimeSocketManager
) {
    val allChats: Flow<List<ChatEntity>> = chatDao.getAllChats()
    val allContacts: Flow<List<ContactEntity>> = contactDao.getAllContacts()
    val allCallLogs: Flow<List<com.connectx.app.data.local.entity.CallLogEntity>> = callLogDao.getAllCallLogs()

    init {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            socketManager.incomingMessages.collect { msg ->
                val entity = MessageEntity(
                    id = msg.id,
                    chatId = msg.chatId,
                    senderId = msg.senderId,
                    senderName = msg.senderName,
                    receiverId = "me",
                    content = msg.content,
                    type = MessageType.TEXT,
                    status = MessageStatus.READ,
                    timestamp = msg.timestamp
                )
                messageDao.insertMessage(entity)
                chatDao.updateLastMessage(msg.chatId, msg.content, msg.timestamp)
            }
        }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            socketManager.newUserDiscovered.collect { json ->
                val userId = json.optString("id")
                val name = json.optString("name")
                val email = json.optString("email")
                val phone = json.optString("phoneNumber", "+1 555-0199")
                if (userId.isNotEmpty()) {
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
                            lastMessage = "Connected live",
                            lastMessageTimestamp = System.currentTimeMillis(),
                            isOnline = true,
                            lastSeen = "Online"
                        )
                    )
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
                prefsManager.saveAuthTokens(
                    accessToken = auth.accessToken,
                    refreshToken = auth.refreshToken,
                    userId = auth.userId,
                    email = auth.email,
                    name = auth.name,
                    phone = auth.phone,
                    photoUrl = auth.photoUrl
                )
                socketManager.connect("wss://connectx-5kk8.onrender.com", auth.userId)
                
                // Fetch all registered live users from the server
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
                                    lastMessage = "Connected on ConnectX live",
                                    lastMessageTimestamp = System.currentTimeMillis(),
                                    isOnline = true,
                                    lastSeen = "Online"
                                )
                            )
                        }
                    }
                } catch (ignored: Exception) {}

                Result.success(true)
            } else {
                Result.failure(Exception("Authentication failed: Invalid credentials or server error"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot reach authentication server: ${e.localizedMessage}"))
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
            senderId = "user_101",
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
        chatDao.updateLastMessage(chatId, if (type == MessageType.TEXT) content else "Attachment: ${type.name}", System.currentTimeMillis())
        socketManager.sendMessage(chatId = chatId, senderId = "user_101", senderName = "Me", content = content)
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
            adminId = "user_101",
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
            com.connectx.app.data.local.entity.CallLogEntity("cl_1", "Alice Vance", null, "VIDEO", isIncoming = true, isMissed = false, durationSeconds = 145, timestamp = System.currentTimeMillis() - 1800000),
            com.connectx.app.data.local.entity.CallLogEntity("cl_2", "Bob Smith", null, "VOICE", isIncoming = false, isMissed = false, durationSeconds = 62, timestamp = System.currentTimeMillis() - 7200000),
            com.connectx.app.data.local.entity.CallLogEntity("cl_3", "Charlie Brown", null, "VOICE", isIncoming = true, isMissed = true, durationSeconds = 0, timestamp = System.currentTimeMillis() - 86400000)
        )
        mockCallLogs.forEach { callLogDao.insertCallLog(it) }
    }
}
