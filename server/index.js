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
    cors: { origin: "*", methods: ["GET", "POST", "PUT"] },
    pingTimeout: 60000,
    pingInterval: 25000
});

const PORT = process.env.PORT || 3000;
const JWT_SECRET = process.env.JWT_SECRET || "connectx_super_secret_jwt_key_2026";

app.use(cors());
app.use(express.json({ limit: '10mb' }));

// Persistent user registry (survives Render restarts via JSON file)
const usersFilePath = path.join('/tmp', 'connectx_users.json');

function loadUsers() {
    try {
        if (fs.existsSync(usersFilePath)) {
            const data = fs.readFileSync(usersFilePath, 'utf8');
            const arr = JSON.parse(data);
            const map = new Map();
            arr.forEach(u => map.set(u.id, u));
            return map;
        }
    } catch (e) { console.error('Failed to load users:', e.message); }
    return new Map();
}

function saveUsers(map) {
    try {
        fs.writeFileSync(usersFilePath, JSON.stringify(Array.from(map.values())), 'utf8');
    } catch (e) { console.error('Failed to save users:', e.message); }
}

const registeredUsers = loadUsers();

// Health check
app.get('/', (req, res) => {
    res.json({
        status: 'online',
        service: 'ConnectX Backend Server',
        version: '2.0.0',
        users: registeredUsers.size,
        timestamp: new Date().toISOString()
    });
});

app.get('/api/health', (req, res) => {
    res.json({ status: 'OK', serverTime: new Date(), users: registeredUsers.size });
});

// ─────────────────────────────────────────────
// AUTH ENDPOINTS
// ─────────────────────────────────────────────

app.post('/api/auth/login', (req, res) => {
    const { email, password, phone, otp, googleIdToken } = req.body;
    const userEmail = email || `${phone || 'user'}@connectx.io`;
    // Deterministic userId so same credentials always produce same ID
    const userId = "usr_" + Buffer.from(userEmail).toString('hex').substring(0, 12);
    const userName = userEmail.split('@')[0];

    const userObj = {
        id: userId,
        name: userName,
        email: userEmail,
        phoneNumber: phone || null,
        avatarUrl: null,
        statusMessage: "Hey there! I am using ConnectX.",
        isOnline: true,
        lastSeen: new Date().toISOString()
    };

    registeredUsers.set(userId, userObj);
    saveUsers(registeredUsers);

    // Notify all connected sockets about the newly logged-in user
    io.emit('user_registered', userObj);

    const accessToken = jwt.sign({ userId, email: userEmail }, JWT_SECRET, { expiresIn: '7d' });
    const refreshToken = jwt.sign({ userId, email: userEmail }, JWT_SECRET, { expiresIn: '30d' });

    res.json({
        accessToken,
        refreshToken,
        userId,
        email: userEmail,
        name: userName,
        phone: phone || null,
        photoUrl: null
    });
});

app.post('/api/auth/otp/send', (req, res) => {
    res.json({ success: true, message: "OTP sent (demo mode)" });
});

app.post('/api/auth/otp/verify', (req, res) => {
    const { phone } = req.body;
    const userEmail = `${phone}@connectx.io`;
    const userId = "usr_" + Buffer.from(userEmail).toString('hex').substring(0, 12);
    const accessToken = jwt.sign({ userId, phone }, JWT_SECRET, { expiresIn: '7d' });

    const userObj = {
        id: userId,
        name: `User_${phone ? phone.slice(-4) : '0000'}`,
        email: userEmail,
        phoneNumber: phone,
        avatarUrl: null,
        isOnline: true,
        lastSeen: new Date().toISOString()
    };
    registeredUsers.set(userId, userObj);
    saveUsers(registeredUsers);
    io.emit('user_registered', userObj);

    res.json({
        accessToken,
        refreshToken: accessToken,
        userId,
        email: userEmail,
        name: userObj.name,
        phone,
        photoUrl: null
    });
});

app.post('/api/auth/refresh', (req, res) => {
    const { token } = req.query;
    try {
        const decoded = jwt.verify(token, JWT_SECRET);
        const accessToken = jwt.sign({ userId: decoded.userId, email: decoded.email }, JWT_SECRET, { expiresIn: '7d' });
        res.json({ accessToken, refreshToken: token, userId: decoded.userId, email: decoded.email, name: decoded.email ? decoded.email.split('@')[0] : 'User' });
    } catch (e) {
        res.status(401).json({ error: "Invalid refresh token" });
    }
});

// Get all registered users
app.get('/api/users', (req, res) => {
    const users = Array.from(registeredUsers.values());
    res.json(users);
});

// Update user presence
app.put('/api/user/presence', (req, res) => {
    const { userId, isOnline } = req.body;
    const user = registeredUsers.get(userId);
    if (user) {
        user.isOnline = isOnline;
        user.lastSeen = new Date().toISOString();
        registeredUsers.set(userId, user);
        saveUsers(registeredUsers);
        io.emit('user_status', { userId, isOnline, lastSeen: user.lastSeen });
    }
    res.json({ success: true });
});

// ─────────────────────────────────────────────
// SOCKET.IO — REAL-TIME MESSAGING & WEBRTC SIGNALING
// ─────────────────────────────────────────────

// Track: userId → socketId mapping
const userSocketMap = new Map();  // userId -> socketId
const socketUserMap = new Map();  // socketId -> userId

io.on('connection', (socket) => {
    console.log(`[Socket] Connected: ${socket.id}`);

    // ── User Registration ──
    socket.on('register_user', (userId) => {
        userSocketMap.set(userId, socket.id);
        socketUserMap.set(socket.id, userId);
        socket.join(userId);
        console.log(`[Socket] User ${userId} registered on socket ${socket.id}`);
        // Notify others this user is online
        socket.broadcast.emit('user_status', { userId, isOnline: true, lastSeen: new Date().toISOString() });
        // Send current online users list to the newly connected user
        socket.emit('online_users', Array.from(userSocketMap.keys()));
    });

    // ── Chat Messages ──
    socket.on('send_message', (data) => {
        console.log(`[Message] ${data.senderId} → ${data.chatId}: ${data.content}`);
        // Forward to recipient's room (chatId = recipient userId)
        socket.to(data.chatId).emit('receive_message', data);
        // Also send back to sender's other devices if any
        socket.to(data.senderId).emit('message_sent', data);
    });

    socket.on('typing', (data) => {
        socket.to(data.chatId).emit('typing_status', data);
    });

    // ── WebRTC Call Signaling ──

    // Step 1: Caller sends invite to callee
    socket.on('call_offer', (data) => {
        // data: { callerId, callerName, targetId, callType }
        console.log(`[Call] Offer from ${data.callerId} to ${data.targetId} (${data.callType})`);
        io.to(data.targetId).emit('call_offer', data);
    });

    // Step 2: Caller sends SDP offer to callee
    socket.on('sdp_offer', (data) => {
        // data: { callerId, targetId, sdp }
        console.log(`[SDP] Offer from ${data.callerId} to ${data.targetId}`);
        io.to(data.targetId).emit('sdp_offer', data);
    });

    // Step 3: Callee sends SDP answer back to caller
    socket.on('sdp_answer', (data) => {
        // data: { callerId, targetId, sdp }
        console.log(`[SDP] Answer from ${data.targetId} to ${data.callerId}`);
        io.to(data.callerId).emit('sdp_answer', data);
    });

    // Step 4: Both sides exchange ICE candidates (trickle ICE)
    socket.on('ice_candidate', (data) => {
        // data: { senderId, targetId, candidate }
        io.to(data.targetId).emit('ice_candidate', data);
    });

    // Call control events
    socket.on('call_reject', (data) => {
        console.log(`[Call] Rejected by ${data.targetId}`);
        io.to(data.callerId).emit('call_reject', data);
    });

    socket.on('call_end', (data) => {
        console.log(`[Call] Ended by ${data.senderId}`);
        io.to(data.targetId).emit('call_end', data);
    });

    socket.on('call_busy', (data) => {
        io.to(data.callerId).emit('call_busy', data);
    });

    socket.on('call_timeout', (data) => {
        io.to(data.callerId).emit('call_timeout', data);
    });

    // ── Push To Talk ──
    socket.on('ptt_stream_start', (data) => {
        socket.to(data.channelId).emit('ptt_stream_start', data);
    });
    socket.on('ptt_stream_end', (data) => {
        socket.to(data.channelId).emit('ptt_stream_end', data);
    });

    // ── Disconnection ──
    socket.on('disconnect', () => {
        const userId = socketUserMap.get(socket.id);
        if (userId) {
            userSocketMap.delete(userId);
            socketUserMap.delete(socket.id);
            console.log(`[Socket] User ${userId} disconnected`);
            // Mark offline in registry
            const user = registeredUsers.get(userId);
            if (user) {
                user.isOnline = false;
                user.lastSeen = new Date().toISOString();
                registeredUsers.set(userId, user);
                saveUsers(registeredUsers);
            }
            io.emit('user_status', { userId, isOnline: false, lastSeen: new Date().toISOString() });
        }
    });
});

server.listen(PORT, () => {
    console.log(`ConnectX Server v2.0 running on port ${PORT}`);
    console.log(`Loaded ${registeredUsers.size} registered users from disk`);
});
