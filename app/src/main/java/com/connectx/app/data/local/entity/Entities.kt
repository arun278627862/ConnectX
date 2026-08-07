package com.connectx.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MessageType {
    TEXT, EMOJI, IMAGE, VIDEO, PDF, ZIP, AUDIO, DOCUMENT, VOICE_NOTE, LOCATION
}

enum class MessageStatus {
    SENDING, SENT, DELIVERED, READ, FAILED
}

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val receiverId: String,
    val content: String,
    val type: MessageType,
    val status: MessageStatus,
    val mediaUrl: String? = null,
    val mediaLocalPath: String? = null,
    val fileSize: Long = 0,
    val fileName: String? = null,
    val durationSeconds: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAddress: String? = null,
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToContent: String? = null,
    val reactions: String = "", // Comma-separated reactions e.g. "❤️,👍"
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val isStarred: Boolean = false,
    val isDeletedForMe: Boolean = false,
    val isDeletedForEveryone: Boolean = false
)

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarUrl: String?,
    val isGroup: Boolean = false,
    val groupDescription: String? = null,
    val adminId: String? = null,
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val lastSeen: String = "",
    val isTyping: Boolean = false,
    val memberIds: String = "" // Comma-separated member IDs
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phoneNumber: String,
    val email: String,
    val avatarUrl: String?,
    val statusMessage: String = "Hey there! I am using ConnectX.",
    val isOnline: Boolean = false,
    val lastSeen: String = "Recently"
)
