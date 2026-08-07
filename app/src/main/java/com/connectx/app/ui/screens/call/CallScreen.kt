package com.connectx.app.ui.screens.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.connectx.app.webrtc.CallState
import com.connectx.app.webrtc.CallType
import com.connectx.app.webrtc.WebRtcClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    val webRtcClient: WebRtcClient
) : ViewModel()

@Composable
fun CallScreen(
    onNavigateBack: () -> Unit,
    viewModel: CallViewModel = hiltViewModel()
) {
    val callState by viewModel.webRtcClient.callState.collectAsState()
    val session by viewModel.webRtcClient.currentSession.collectAsState()
    val isMuted by viewModel.webRtcClient.isMuted.collectAsState()
    val isVideoEnabled by viewModel.webRtcClient.isVideoEnabled.collectAsState()
    val isSpeakerOn by viewModel.webRtcClient.isSpeakerOn.collectAsState()
    val isFrontCamera by viewModel.webRtcClient.isFrontCamera.collectAsState()

    val peerName = session?.peerName ?: "Unknown Contact"
    val isVideo = session?.callType == CallType.VIDEO

    LaunchedEffect(callState) {
        if (callState == CallState.ENDED) {
            onNavigateBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(48.dp))
                Surface(
                    modifier = Modifier.size(100.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(24.dp),
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(peerName, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (callState) {
                        CallState.OUTGOING -> "Calling..."
                        CallState.INCOMING -> "Incoming Call..."
                        CallState.CONNECTED -> "00:42 • WebRTC Encrypted"
                        else -> "Connecting..."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.LightGray
                )
            }

            if (isVideo) {
                Card(
                    modifier = Modifier
                        .size(120.dp, 160.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Color.DarkGray)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (isVideoEnabled) "Self Camera View" else "Camera Off", color = Color.White)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.webRtcClient.toggleMute() },
                    modifier = Modifier
                        .size(56.dp)
                        .background(if (isMuted) Color.Red else Color.DarkGray, CircleShape)
                ) {
                    Icon(
                        if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute",
                        tint = Color.White
                    )
                }

                if (isVideo) {
                    IconButton(
                        onClick = { viewModel.webRtcClient.toggleVideo() },
                        modifier = Modifier
                            .size(56.dp)
                            .background(if (!isVideoEnabled) Color.Red else Color.DarkGray, CircleShape)
                    ) {
                        Icon(
                            if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "Video",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = { viewModel.webRtcClient.switchCamera() },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.DarkGray, CircleShape)
                    ) {
                        Icon(Icons.Default.Cameraswitch, contentDescription = "Switch Camera", tint = Color.White)
                    }
                }

                IconButton(
                    onClick = { viewModel.webRtcClient.toggleSpeaker() },
                    modifier = Modifier
                        .size(56.dp)
                        .background(if (isSpeakerOn) MaterialTheme.colorScheme.primary else Color.DarkGray, CircleShape)
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Speaker", tint = Color.White)
                }

                IconButton(
                    onClick = { viewModel.webRtcClient.endCall() },
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.Red, CircleShape)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White)
                }
            }
        }
    }
}
