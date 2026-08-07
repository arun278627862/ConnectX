package com.connectx.app.ui.screens.call

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.connectx.app.webrtc.CallState
import com.connectx.app.webrtc.CallType
import com.connectx.app.webrtc.WebRtcClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import org.webrtc.SurfaceViewRenderer
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

    val peerName = session?.peerName ?: "Unknown Contact"
    val isVideo = session?.callType == CallType.VIDEO
    val isIncoming = session?.isIncoming == true

    // Live call timer
    var callDurationSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(callState) {
        if (callState == CallState.CONNECTED) {
            callDurationSeconds = 0
            while (true) {
                delay(1000)
                callDurationSeconds++
            }
        }
        if (callState == CallState.IDLE || callState == CallState.ENDED) {
            onNavigateBack()
        }
    }

    val formattedDuration = remember(callDurationSeconds) {
        val m = callDurationSeconds / 60
        val s = callDurationSeconds % 60
        "%02d:%02d".format(m, s)
    }

    // Pulsing animation for ringing state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D0D1A), Color(0xFF1A0533))
                )
            )
    ) {
        // ── Full-screen remote video (when in video call and connected) ──
        if (isVideo && callState == CallState.CONNECTED) {
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        viewModel.webRtcClient.setRemoteSurfaceRenderer(this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(64.dp))

            // ── Avatar / Status ──
            Box(contentAlignment = Alignment.Center) {
                if (callState != CallState.CONNECTED) {
                    Surface(
                        modifier = Modifier
                            .size(130.dp)
                            .scale(pulseScale),
                        shape = CircleShape,
                        color = Color(0xFF6C00E0).copy(alpha = 0.25f)
                    ) {}
                }
                Surface(
                    modifier = Modifier.size(100.dp),
                    shape = CircleShape,
                    color = Color(0xFF6C00E0)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(24.dp),
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = peerName,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when (callState) {
                    CallState.OUTGOING -> "Calling..."
                    CallState.INCOMING -> if (isVideo) "Incoming video call" else "Incoming voice call"
                    CallState.CONNECTED -> "🔒 $formattedDuration"
                    else -> "Connecting..."
                },
                style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFB0B0CC)),
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            // ── Local camera preview (picture-in-picture style, video calls only) ──
            if (isVideo) {
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 16.dp, bottom = 8.dp)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceViewRenderer(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(120.dp.value.toInt(), 160.dp.value.toInt())
                                viewModel.webRtcClient.setLocalSurfaceRenderer(this)
                            }
                        },
                        modifier = Modifier
                            .size(120.dp, 160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.DarkGray)
                    )
                    if (!isVideoEnabled) {
                        Box(
                            modifier = Modifier
                                .size(120.dp, 160.dp)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VideocamOff, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }

            // ── Call Controls ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                if (callState == CallState.INCOMING) {
                    // ── Incoming call — Accept / Reject ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp, vertical = 32.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    viewModel.webRtcClient.rejectCall()
                                    onNavigateBack()
                                },
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color(0xFFE53935), CircleShape)
                            ) {
                                Icon(Icons.Default.CallEnd, contentDescription = "Reject", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Decline", color = Color.White, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = { viewModel.webRtcClient.acceptCall() },
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color(0xFF43A047), CircleShape)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Accept", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Accept", color = Color.White, fontSize = 12.sp)
                        }
                    }
                } else {
                    // ── Active / Outgoing call controls ──
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Mute
                            CallControlButton(
                                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                label = if (isMuted) "Unmute" else "Mute",
                                tint = if (isMuted) Color(0xFFE53935) else Color.White,
                                background = Color(0xFF2A2A3D),
                                onClick = { viewModel.webRtcClient.toggleMute() }
                            )

                            // Speaker
                            CallControlButton(
                                icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                label = if (isSpeakerOn) "Speaker" else "Earpiece",
                                tint = if (isSpeakerOn) Color(0xFF6C00E0) else Color.White,
                                background = Color(0xFF2A2A3D),
                                onClick = { viewModel.webRtcClient.toggleSpeaker() }
                            )

                            if (isVideo) {
                                // Camera toggle
                                CallControlButton(
                                    icon = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                    label = if (isVideoEnabled) "Camera" else "Cam Off",
                                    tint = if (!isVideoEnabled) Color(0xFFE53935) else Color.White,
                                    background = Color(0xFF2A2A3D),
                                    onClick = { viewModel.webRtcClient.toggleVideo() }
                                )
                                // Switch camera
                                CallControlButton(
                                    icon = Icons.Default.Cameraswitch,
                                    label = "Flip",
                                    tint = Color.White,
                                    background = Color(0xFF2A2A3D),
                                    onClick = { viewModel.webRtcClient.switchCamera() }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // End Call — big red button
                        IconButton(
                            onClick = { viewModel.webRtcClient.endCall() },
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color(0xFFE53935), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.CallEnd,
                                contentDescription = "End Call",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text("End", color = Color.White, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    background: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .background(background, CircleShape)
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color(0xFFB0B0CC), fontSize = 11.sp)
    }
}
