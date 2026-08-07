package com.connectx.app.ui.screens.ptt

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PttViewModel @Inject constructor() : ViewModel() {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    fun startLiveStream() {
        _isRecording.value = true
    }

    fun stopLiveStream() {
        _isRecording.value = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PttScreen(
    onNavigateBack: () -> Unit,
    viewModel: PttViewModel = hiltViewModel()
) {
    val isRecording by viewModel.isRecording.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Push To Talk (Walkie-Talkie)") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Live Channel: General", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (isRecording) "TRANSMITTING LIVE VOICE..." else "HOLD BUTTON TO TALK",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isRecording) Color.Red else Color.Gray
                )
            }

            Surface(
                modifier = Modifier
                    .size(180.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                viewModel.startLiveStream()
                                tryAwaitRelease()
                                viewModel.stopLiveStream()
                            }
                        )
                    },
                shape = CircleShape,
                color = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary,
                tonalElevation = 12.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Push To Talk",
                        modifier = Modifier.size(72.dp),
                        tint = Color.White
                    )
                }
            }

            Text("Release button to stop transmitting.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
