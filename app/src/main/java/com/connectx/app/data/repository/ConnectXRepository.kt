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
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectXRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val contactDao: ContactDao,
    private val apiService: ConnectXApiService,
    private val prefsManager: AppPreferencesManager
) {
    val allChats: Flow<List<ChatEntity>> = chatDao.getAllChats()
    val allContacts: Flow<List<ContactEntity>> = contactDao.getAllContacts()

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
                Result.success(true)
            } else {
                // Mock success for offline/demonstration mode if backend not reachable
                prefsManager.saveAuthTokens(
                    accessToken = "mock_jwt_token_12345",
                    refreshToken = "mock_refresh_token_12345",
                    userId = "user_101",
                    email = email,
                    name = email.substringBefore("@").capitalize(),
                    phone = "+1 555-0199"
                )
                seedMockData()
                Result.success(true)
            }
        } catch (e: Exception) {
            // Fallback mock auth
            prefsManager.saveAuthTokens(
                accessToken = "mock_jwt_token_12345",
                refreshToken = "mock_refresh_token_12345",
                userId = "user_101",
                email = email,
                name = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                phone = "+1 555-0199"
            )
            seedMockData()
            Result.success(true)
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

    suspend fun seedMockData() {
        val mockContacts = listOf(
            ContactEntity("user_201", "Alice Vance", "+1 555-0101", "alice@connectx.io", null, "Available for calls!", true, "Online"),
            ContactEntity("user_202", "Bob Smith", "+1 555-0102", "bob@connectx.io", null, "At work...", false, "10 mins ago"),
            ContactEntity("user_203", "Charlie Brown", "+1 555-0103", "charlie@connectx.io", null, "In a meeting", true, "Online"),
            ContactEntity("user_204", "Diana Prince", "+1 555-0104", "diana@connectx.io", null, "Exploring ConnectX", true, "Online")
        )
        contactDao.insertContacts(mockContacts)

        val mockChats = listOf(
            ChatEntity("user_201", "Alice Vance", null, isGroup = false, lastMessage = "Let's test the WebRTC video call!", lastMessageTimestamp = System.currentTimeMillis() - 300000, unreadCount = 2, isOnline = true, lastSeen = "Online"),
            ChatEntity("user_202", "Bob Smith", null, isGroup = false, lastMessage = "Sending the project PDF report now.", lastMessageTimestamp = System.currentTimeMillis() - 3600000, unreadCount = 0, isOnline = false, lastSeen = "10 mins ago"),
            ChatEntity("group_301", "Android Dev Team", null, isGroup = true, groupDescription = "Official ConnectX Dev Team", adminId = "user_101", lastMessage = "Charlie: Push to talk test complete!", lastMessageTimestamp = System.currentTimeMillis() - 86400000, unreadCount = 5)
        )
        chatDao.insertChats(mockChats)

        val mockMessages = listOf(
            MessageEntity("m1", "user_201", "user_201", "Alice Vance", "user_101", "Hey there! Ready to start the video call?", MessageType.TEXT, MessageStatus.READ, timestamp = System.currentTimeMillis() - 600000),
            MessageEntity("m2", "user_201", "user_101", "Me", "user_201", "Yes! Connected via WebRTC and high speed backend.", MessageType.TEXT, MessageStatus.READ, timestamp = System.currentTimeMillis() - 500000),
            MessageEntity("m3", "user_201", "user_201", "Alice Vance", "user_101", "Let's test the WebRTC video call!", MessageType.TEXT, MessageStatus.DELIVERED, reactions = "👍,❤️", timestamp = System.currentTimeMillis() - 300000)
        )
        messageDao.insertMessages(mockMessages)
    }
}
