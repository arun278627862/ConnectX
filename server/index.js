const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');
const jwt = require('jsonwebtoken');
const path = require('path');
const fs = require('fs');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
    cors: {
        origin: "*",
        methods: ["GET", "POST"]
    }
});

const PORT = process.env.PORT || 3000;
const JWT_SECRET = process.env.JWT_SECRET || "connectx_super_secret_jwt_key_2026";

app.use(cors());
app.use(express.json());

// Ensure uploads folder exists
const uploadsDir = path.join(__dirname, 'uploads');
if (!fs.existsSync(uploadsDir)) {
    fs.mkdirSync(uploadsDir);
}
app.use('/uploads', express.static(uploadsDir));

// Health check endpoint
app.get('/', (req, res) => {
    res.json({
        status: 'online',
        service: 'ConnectX Backend Server',
        version: '1.0.0',
        timestamp: new Date().toISOString()
    });
});

app.get('/api/health', (req, res) => {
    res.json({ status: 'OK', serverTime: new Date() });
});

// Authentication endpoints
app.post('/api/auth/login', (req, res) => {
    const { email, password, phone, otp, googleIdToken } = req.body;
    const userEmail = email || `${phone || 'user'}@connectx.io`;
    const userId = "usr_" + Math.random().toString(36).substring(2, 9);
    
    const accessToken = jwt.sign({ userId, email: userEmail }, JWT_SECRET, { expiresIn: '7d' });
    const refreshToken = jwt.sign({ userId, email: userEmail }, JWT_SECRET, { expiresIn: '30d' });

    res.json({
        accessToken,
        refreshToken,
        userId,
        email: userEmail,
        name: userEmail.split('@')[0],
        phone: phone || "+1 555-0199",
        photoUrl: null
    });
});

app.post('/api/auth/otp/send', (req, res) => {
    res.json({ success: true, message: "OTP sent successfully" });
});

app.post('/api/auth/otp/verify', (req, res) => {
    const { phone } = req.body;
    const userId = "usr_" + Math.random().toString(36).substring(2, 9);
    const accessToken = jwt.sign({ userId, phone }, JWT_SECRET, { expiresIn: '7d' });
    res.json({
        accessToken,
        refreshToken: accessToken,
        userId,
        email: `${phone}@connectx.io`,
        name: `User_${phone.slice(-4)}`,
        phone,
        photoUrl: null
    });
});

app.post('/api/auth/refresh', (req, res) => {
    const { token } = req.query;
    try {
        const decoded = jwt.verify(token, JWT_SECRET);
        const accessToken = jwt.sign({ userId: decoded.userId, email: decoded.email }, JWT_SECRET, { expiresIn: '7d' });
        res.json({
            accessToken,
            refreshToken: token,
            userId: decoded.userId,
            email: decoded.email,
            name: decoded.email.split('@')[0]
        });
    } catch (e) {
        res.status(401).json({ error: "Invalid refresh token" });
    }
});

// WebSockets & WebRTC Signaling
const connectedUsers = new Map(); // socketId -> userId

io.on('connection', (socket) => {
    console.log(`[Socket] Connected: ${socket.id}`);

    socket.on('register_user', (userId) => {
        connectedUsers.set(socket.id, userId);
        socket.join(userId);
        console.log(`[Socket] Registered user ${userId} to socket ${socket.id}`);
        io.emit('user_status', { userId, status: 'online' });
    });

    // Real-time Chat message forwarding
    socket.on('send_message', (data) => {
        console.log(`[Message] From ${data.senderId} to ${data.chatId}: ${data.content}`);
        io.to(data.chatId).emit('receive_message', data);
        socket.to(data.chatId).emit('typing_status', { chatId: data.chatId, isTyping: false });
    });

    socket.on('typing', (data) => {
        socket.to(data.chatId).emit('typing_status', data);
    });

    // WebRTC Signaling (Call Offer, Answer, ICE Candidates)
    socket.on('call_offer', (data) => {
        console.log(`[WebRTC Offer] From ${data.callerId} to ${data.targetId}`);
        io.to(data.targetId).emit('call_offer', data);
    });

    socket.on('call_answer', (data) => {
        console.log(`[WebRTC Answer] From ${data.targetId} to ${data.callerId}`);
        io.to(data.callerId).emit('call_answer', data);
    });

    socket.on('ice_candidate', (data) => {
        io.to(data.targetId).emit('ice_candidate', data);
    });

    socket.on('end_call', (data) => {
        io.to(data.targetId).emit('call_ended', data);
    });

    // Push To Talk (PTT) Live Audio Streaming
    socket.on('ptt_stream_start', (data) => {
        socket.to(data.channelId).emit('ptt_stream_start', data);
    });

    socket.on('ptt_audio_chunk', (data) => {
        socket.to(data.channelId).emit('ptt_audio_chunk', data);
    });

    socket.on('ptt_stream_stop', (data) => {
        socket.to(data.channelId).emit('ptt_stream_stop', data);
    });

    socket.on('disconnect', () => {
        const userId = connectedUsers.get(socket.id);
        if (userId) {
            connectedUsers.delete(socket.id);
            io.emit('user_status', { userId, status: 'offline' });
        }
        console.log(`[Socket] Disconnected: ${socket.id}`);
    });
});

server.listen(PORT, () => {
    console.log(`==================================================`);
    console.log(`ConnectX Server running on port ${PORT}`);
    console.log(`HTTP API: http://localhost:${PORT}/api`);
    console.log(`WebSocket: ws://localhost:${PORT}`);
    console.log(`==================================================`);
});
