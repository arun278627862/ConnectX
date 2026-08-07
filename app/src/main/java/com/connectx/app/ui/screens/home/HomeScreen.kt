package com.connectx.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.connectx.app.data.local.entity.ChatEntity
import com.connectx.app.data.local.entity.ContactEntity
import com.connectx.app.data.local.preferences.AppPreferencesManager
import com.connectx.app.data.repository.ConnectXRepository
import com.connectx.app.webrtc.CallType
import com.connectx.app.webrtc.WebRtcClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ConnectXRepository,
    private val prefsManager: AppPreferencesManager,
    private val webRtcClient: WebRtcClient
) : ViewModel() {
    val chats = repository.allChats.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val contacts = repository.allContacts.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val callLogs = repository.allCallLogs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val authTokens = prefsManager.authTokensFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.connectx.app.data.local.preferences.AuthTokens()
    )

    val themeMode = prefsManager.themeModeFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "SYSTEM"
    )

    fun startCall(peerName: String, peerAvatar: String?, type: CallType) {
        webRtcClient.startCall(peerName, peerAvatar, type)
    }

    fun updateTheme(mode: String) {
        viewModelScope.launch {
            prefsManager.setThemeMode(mode)
        }
    }

    fun logout() {
        viewModelScope.launch {
            prefsManager.clearAuth()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateChat: (String) -> Unit,
    onNavigateCallScreen: () -> Unit,
    onNavigateGroupCreate: () -> Unit,
    onNavigatePtt: () -> Unit,
    onNavigateConfig: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val chats by viewModel.chats.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val authTokens by viewModel.authTokens.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when(selectedTab) { 0 -> "Chats"; 1 -> "Calls"; 2 -> "Contacts"; else -> "Settings" }) },
                actions = {
                    IconButton(onClick = onNavigatePtt) {
                        Icon(Icons.Default.Mic, contentDescription = "Push To Talk")
                    }
                    if (selectedTab == 0) {
                        IconButton(onClick = onNavigateGroupCreate) {
                            Icon(Icons.Default.GroupAdd, contentDescription = "New Group")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chats") },
                    label = { Text("Chats") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Call, contentDescription = "Calls") },
                    label = { Text("Calls") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Contacts, contentDescription = "Contacts") },
                    label = { Text("Contacts") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> ChatListTab(chats = chats, onChatClick = onNavigateChat)
                1 -> CallListTab(callLogs = viewModel.callLogs.collectAsState().value, contacts = contacts, onStartCall = { name, avatar, type ->
                    viewModel.startCall(name, avatar, type)
                    onNavigateCallScreen()
                })
                2 -> ContactListTab(contacts = contacts, onContactClick = { contact ->
                    onNavigateChat(contact.id)
                })
                3 -> SettingsTab(
                    userEmail = authTokens.userEmail ?: "",
                    userName = authTokens.userName ?: "ConnectX User",
                    themeMode = themeMode,
                    onThemeChange = viewModel::updateTheme,
                    onNavigateConfig = onNavigateConfig,
                    onLogout = {
                        viewModel.logout()
                        onLogout()
                    }
                )
            }
        }
    }
}

@Composable
fun ChatListTab(chats: List<ChatEntity>, onChatClick: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(chats) { chat ->
            ListItem(
                headlineContent = { Text(chat.name, fontWeight = FontWeight.Bold) },
                supportingContent = { Text(chat.lastMessage, maxLines = 1) },
                leadingContent = {
                    Box {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                if (chat.isGroup) Icons.Default.Group else Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        if (chat.isOnline) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color.Green)
                                    .align(Alignment.BottomEnd)
                            )
                        }
                    }
                },
                trailingContent = {
                    if (chat.unreadCount > 0) {
                        Badge { Text("${chat.unreadCount}") }
                    }
                },
                modifier = Modifier.clickable { onChatClick(chat.id) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun CallListTab(
    callLogs: List<com.connectx.app.data.local.entity.CallLogEntity>,
    contacts: List<ContactEntity>,
    onStartCall: (String, String?, CallType) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                "Recent Calls",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
        items(callLogs) { log ->
            ListItem(
                headlineContent = {
                    Text(
                        log.callerName,
                        fontWeight = FontWeight.Bold,
                        color = if (log.isMissed) Color.Red else Color.Unspecified
                    )
                },
                supportingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (log.isIncoming) Icons.Default.CallReceived else Icons.Default.CallMade,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (log.isMissed) Color.Red else Color.Gray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${log.callType} • ${if (log.isMissed) "Missed" else "${log.durationSeconds}s"}")
                    }
                },
                leadingContent = {
                    Icon(
                        if (log.callType == "VIDEO") Icons.Default.Videocam else Icons.Default.Call,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    IconButton(onClick = { onStartCall(log.callerName, log.callerAvatar, if (log.callType == "VIDEO") CallType.VIDEO else CallType.VOICE) }) {
                        Icon(Icons.Default.Call, contentDescription = "Redial", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
            HorizontalDivider()
        }

        item {
            Text(
                "Contacts",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
        items(contacts) { contact ->
            ListItem(
                headlineContent = { Text(contact.name, fontWeight = FontWeight.Bold) },
                supportingContent = { Text(contact.phoneNumber) },
                leadingContent = {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(40.dp))
                },
                trailingContent = {
                    Row {
                        IconButton(onClick = { onStartCall(contact.name, contact.avatarUrl, CallType.VOICE) }) {
                            Icon(Icons.Default.Call, contentDescription = "Voice Call", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { onStartCall(contact.name, contact.avatarUrl, CallType.VIDEO) }) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun ContactListTab(contacts: List<ContactEntity>, onContactClick: (ContactEntity) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(contacts) { contact ->
            ListItem(
                headlineContent = { Text(contact.name) },
                supportingContent = { Text(contact.statusMessage) },
                leadingContent = {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(40.dp))
                },
                modifier = Modifier.clickable { onContactClick(contact) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun SettingsTab(
    userEmail: String,
    userName: String,
    themeMode: String,
    onThemeChange: (String) -> Unit,
    onNavigateConfig: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(userName, style = MaterialTheme.typography.titleLarge)
                    Text(userEmail, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Text("App Preferences", style = MaterialTheme.typography.titleMedium)

        OutlinedCard(onClick = onNavigateConfig, modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Server & API Configuration") },
                supportingContent = { Text("Change Base API, WebSockets & STUN/TURN") },
                leadingContent = { Icon(Icons.Default.SettingsInputComponent, contentDescription = null) }
            )
        }

        Text("Theme Mode", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = themeMode == "SYSTEM",
                onClick = { onThemeChange("SYSTEM") },
                label = { Text("System") }
            )
            FilterChip(
                selected = themeMode == "LIGHT",
                onClick = { onThemeChange("LIGHT") },
                label = { Text("Light") }
            )
            FilterChip(
                selected = themeMode == "DARK",
                onClick = { onThemeChange("DARK") },
                label = { Text("Dark") }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(Icons.Default.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Logout")
        }
    }
}
