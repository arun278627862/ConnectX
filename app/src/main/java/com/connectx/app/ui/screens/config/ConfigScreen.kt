package com.connectx.app.ui.screens.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.connectx.app.data.local.preferences.AppConfig
import com.connectx.app.data.local.preferences.AppPreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val prefsManager: AppPreferencesManager
) : ViewModel() {
    val appConfig = prefsManager.appConfigFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppConfig()
    )

    fun saveConfig(
        baseUrl: String,
        wsUrl: String,
        stun: String,
        turn: String,
        turnUser: String,
        turnPass: String
    ) {
        viewModelScope.launch {
            prefsManager.saveAppConfig(
                AppConfig(
                    apiBaseUrl = baseUrl,
                    webSocketUrl = wsUrl,
                    stunServerUrl = stun,
                    turnServerUrl = turn,
                    turnUsername = turnUser,
                    turnCredential = turnPass
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    onNavigateBack: () -> Unit,
    viewModel: ConfigViewModel = hiltViewModel()
) {
    val currentConfig by viewModel.appConfig.collectAsState()

    var baseUrl by remember(currentConfig) { mutableStateOf(currentConfig.apiBaseUrl) }
    var wsUrl by remember(currentConfig) { mutableStateOf(currentConfig.webSocketUrl) }
    var stunUrl by remember(currentConfig) { mutableStateOf(currentConfig.stunServerUrl) }
    var turnUrl by remember(currentConfig) { mutableStateOf(currentConfig.turnServerUrl) }
    var turnUser by remember(currentConfig) { mutableStateOf(currentConfig.turnUsername) }
    var turnPass by remember(currentConfig) { mutableStateOf(currentConfig.turnCredential) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backend Server Configuration") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    viewModel.saveConfig(baseUrl, wsUrl, stunUrl, turnUrl, turnUser, turnPass)
                    onNavigateBack()
                },
                icon = { Icon(Icons.Default.Save, contentDescription = "Save") },
                text = { Text("Save & Apply") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Configure your backend API and WebRTC servers dynamically without rebuilding the application.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Backend Base API URL") },
                placeholder = { Text("https://example.com/api") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = wsUrl,
                onValueChange = { wsUrl = it },
                label = { Text("WebSocket Signal URL") },
                placeholder = { Text("wss://example.com/socket") },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("STUN / TURN WebRTC ICE Configuration", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = stunUrl,
                onValueChange = { stunUrl = it },
                label = { Text("STUN Server URL") },
                placeholder = { Text("stun:stun.l.google.com:19302") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = turnUrl,
                onValueChange = { turnUrl = it },
                label = { Text("TURN Server URL") },
                placeholder = { Text("turn:turn.example.com:3478") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = turnUser,
                onValueChange = { turnUser = it },
                label = { Text("TURN Username") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = turnPass,
                onValueChange = { turnPass = it },
                label = { Text("TURN Password") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}
