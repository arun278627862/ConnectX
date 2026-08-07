package com.connectx.app.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.connectx.app.data.local.entity.MessageEntity
import com.connectx.app.data.local.entity.MessageType
import com.connectx.app.data.repository.ConnectXRepository
import com.connectx.app.webrtc.CallType
import com.connectx.app.webrtc.WebRtcClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatDetailViewModel @Inject constructor(
    private val repository: ConnectXRepository,
    private val webRtcClient: WebRtcClient,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val chatId: String = checkNotNull(savedStateHandle["chatId"])

    val messages = repository.getMessagesForChat(chatId).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val pinnedMessages = repository.getPinnedMessages(chatId).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun sendMessage(text: String, type: MessageType = MessageType.TEXT) {
        if (text.isBlank() && type == MessageType.TEXT) return
        viewModelScope.launch {
            repository.sendMessage(chatId = chatId, content = text, type = type)
        }
    }

    fun togglePin(msgId: String, currentPin: Boolean) {
        viewModelScope.launch {
            repository.togglePin(msgId, currentPin)
        }
    }

    fun toggleStar(msgId: String, currentStar: Boolean) {
        viewModelScope.launch {
            repository.toggleStar(msgId, currentStar)
        }
    }

    fun addReaction(msgId: String, reaction: String) {
        viewModelScope.launch {
            repository.addReaction(msgId, reaction)
        }
    }

    fun deleteForMe(msgId: String) {
        viewModelScope.launch {
            repository.deleteMessageForMe(msgId)
        }
    }

    fun deleteForEveryone(msgId: String) {
        viewModelScope.launch {
            repository.deleteMessageForEveryone(msgId)
        }
    }

    fun startCall(peerName: String, type: CallType) {
        webRtcClient.startCall(peerName, null, type)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateCallScreen: () -> Unit,
    viewModel: ChatDetailViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val pinnedMessages by viewModel.pinnedMessages.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var showAttachmentMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(viewModel.chatId, fontWeight = FontWeight.Bold)
                        Text("Online | Typing...", style = MaterialTheme.typography.bodySmall, color = Color.Green)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.startCall(viewModel.chatId, CallType.VOICE)
                        onNavigateCallScreen()
                    }) {
                        Icon(Icons.Default.Call, contentDescription = "Voice Call")
                    }
                    IconButton(onClick = {
                        viewModel.startCall(viewModel.chatId, CallType.VIDEO)
                        onNavigateCallScreen()
                    }) {
                        Icon(Icons.Default.Videocam, contentDescription = "Video Call")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (pinnedMessages.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(4.dp)
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Pinned: ${pinnedMessages.last().content}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                reverseLayout = false
            ) {
                items(messages) { msg ->
                    MessageItem(
                        message = msg,
                        onPin = { viewModel.togglePin(msg.id, msg.isPinned) },
                        onStar = { viewModel.toggleStar(msg.id, msg.isStarred) },
                        onReact = { reaction -> viewModel.addReaction(msg.id, reaction) },
                        onDeleteForMe = { viewModel.deleteForMe(msg.id) },
                        onDeleteForEveryone = { viewModel.deleteForEveryone(msg.id) }
                    )
                }
            }

            if (showAttachmentMenu) {
                Surface(
                    tonalElevation = 8.dp,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        IconButton(onClick = {
                            viewModel.sendMessage("Shared Document.pdf", MessageType.PDF)
                            showAttachmentMenu = false
                        }) {
                            Icon(Icons.Default.Description, contentDescription = "PDF")
                        }
                        IconButton(onClick = {
                            viewModel.sendMessage("Photo_capture.jpg", MessageType.IMAGE)
                            showAttachmentMenu = false
                        }) {
                            Icon(Icons.Default.Image, contentDescription = "Image")
                        }
                        IconButton(onClick = {
                            viewModel.sendMessage("Voice_Note.mp3", MessageType.VOICE_NOTE)
                            showAttachmentMenu = false
                        }) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice Note")
                        }
                        IconButton(onClick = {
                            viewModel.sendMessage("Location: 37.7749,-122.4194", MessageType.LOCATION)
                            showAttachmentMenu = false
                        }) {
                            Icon(Icons.Default.LocationOn, contentDescription = "Location")
                        }
                    }
                }
            }

            Surface(tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showAttachmentMenu = !showAttachmentMenu }) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach")
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Message...") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun MessageItem(
    message: MessageEntity,
    onPin: () -> Unit,
    onStar: () -> Unit,
    onReact: (String) -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val isMe = message.senderId == "user_101"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.clickable { showMenu = true }
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                if (message.isPinned) {
                    Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(12.dp))
                }
                Text(message.content, style = MaterialTheme.typography.bodyLarge)
                if (message.reactions.isNotEmpty()) {
                    Text(message.reactions, style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    text = "${message.status.name} • ${if (message.isStarred) "★" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text(if (message.isPinned) "Unpin" else "Pin") }, onClick = { onPin(); showMenu = false })
            DropdownMenuItem(text = { Text(if (message.isStarred) "Unstar" else "Star") }, onClick = { onStar(); showMenu = false })
            DropdownMenuItem(text = { Text("React ❤️") }, onClick = { onReact("❤️"); showMenu = false })
            DropdownMenuItem(text = { Text("Delete For Me") }, onClick = { onDeleteForMe(); showMenu = false })
            DropdownMenuItem(text = { Text("Delete For Everyone") }, onClick = { onDeleteForEveryone(); showMenu = false })
        }
    }
}
