# ConnectX - Production Architectural & System Logics Documentation

ConnectX is a modern, real-time end-to-end communication platform consisting of an **Android Application** (built with Kotlin, Jetpack Compose, Clean Architecture, MVVM, Room, and WebRTC) and a **Live Node.js Backend Server** (deployed on Render with Socket.IO signaling).

---

## 🏗 System Architecture & Technology Stack

### 1. Android Application (`/app`)
- **UI Framework**: Jetpack Compose with Material 3 Design System (Dark/Light mode support).
- **Architecture**: MVVM + Clean Architecture (Presentation, Domain/Repository, Data layer separation).
- **Dependency Injection**: Google Hilt (`@HiltViewModel`, `@Singleton`).
- **Database Persistence**: Room Database (`ConnectXDatabase`) storing `MessageEntity`, `ChatEntity`, `ContactEntity`, and `CallLogEntity`.
- **Preference Storage**: AndroidX DataStore (`AppPreferencesManager`) for JWT tokens and server configuration.
- **Real-Time Communication**: Socket.IO Client (`io.socket:socket.io-client:2.1.0`) for real-time messaging, typing indicators, and user discovery.
- **Peer-to-Peer Calling**: WebRTC for real-time encrypted Voice and Video calling.

### 2. Live Node.js Backend Server (`/server`)
- **Runtime**: Node.js + Express.js + Socket.IO Server (`index.js`).
- **Authentication**: JWT-based REST authentication (`/api/auth/login`, `/api/auth/otp/verify`).
- **User Registry**: Live in-memory registered users database (`registeredUsers` Map) exposed via `GET /api/users`.
- **Live Deployment**: Hosted on Render at `https://connectx-5kk8.onrender.com`.

---

## ⚡ Core Operational & Business Logics

### 🔐 1. Authentication & Permission Logic
- **Strict Server Authentication**:
  - The login flow validates credentials against `POST /api/auth/login` on `https://connectx-5kk8.onrender.com`.
  - On valid credentials, `accessToken`, `refreshToken`, and dynamic `userId` are stored securely in Android DataStore.
  - On authentication failure or connection error, login is rejected and an error banner is displayed on screen.
- **Automatic System Permissions**:
  - Upon app launch, `MainActivity.kt` uses `ActivityResultLauncher` to request `CAMERA`, `RECORD_AUDIO`, `READ_CONTACTS`, and `POST_NOTIFICATIONS` permissions.

---

### 🌐 2. Live Multi-Device Discovery Logic
- **Directory Registration**:
  - When User A logs in on Phone 1, the backend registers User A into `registeredUsers` and assigns a deterministic ID based on their credentials.
  - The server immediately broadcasts a `user_registered` event via WebSockets to all connected sockets.
- **Client Dynamic Contact Insertion**:
  - Phone 2 listens to `socketManager.newUserDiscovered`.
  - As soon as User A logs in, Phone 2 receives the socket event and automatically inserts User A into its local Room database (`contactDao` & `chatDao`).
  - User A appears live in Phone 2's **Chats** and **Contacts** tabs without requiring an app restart.

---

### 💬 3. Real-Time Chat & Message Routing Logic
- **Dynamic Sender Attribution**:
  - Outgoing messages are tagged with the logged-in user's dynamic `currentUserId`.
- **Message Transmission**:
  - `ConnectXRepository.sendMessage()` saves the message locally as `MessageStatus.SENT` and emits `send_message` over Socket.IO.
  - Render server receives `send_message` and targets `io.to(data.chatId).emit('receive_message', data)`.
- **Incoming Message Handling**:
  - `RealtimeSocketManager` receives `receive_message`.
  - The repository maps `chatId = msg.senderId` and inserts the message into Room DB, making incoming text messages appear instantly in the chat thread.

---

### 📞 4. WebRTC Voice & Video Call Signaling Logic
- **Call Offer Emission**:
  - Tapping **Voice Call** or **Video Call** triggers `repository.startCall(targetUserId, targetUserName, type)`.
  - The app starts local WebRTC state and emits a `call_offer` JSON payload to the server.
- **Automatic Incoming Call Launcher**:
  - `MainActivity.kt` collects `webRtcClient.callState`.
  - When the receiving phone receives `incomingCallOffer` over WebSockets, `callState` transitions to `CallState.INCOMING`.
  - `MainActivity` automatically navigates to `CallScreen.kt`, launching the **Incoming Voice / Video Call Screen**.
- **Call Acceptance**:
  - Tapping the green **Accept Call** button transitions `callState` to `CallState.CONNECTED`.

---

## 🛠 Repository Links & Artifacts

- **GitHub Repository**: [https://github.com/arun278627862/ConnectX.git](https://github.com/arun278627862/ConnectX.git)
- **Live Render Backend URL**: `https://connectx-5kk8.onrender.com`
- **Output Production APK**: `app/build/outputs/apk/debug/app-debug.apk`
