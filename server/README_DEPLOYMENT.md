# Free Cloud Deployment Guide for ConnectX Server

Follow these simple, step-by-step instructions to deploy the ConnectX backend live on the internet for **100% free**.

---

## Option 1: Render.com (Recommended - Free Tier)

Render provides a completely free tier with native WebSockets support.

### Steps:
1. Create a free account at [https://render.com](https://render.com).
2. Click **New +** -> **Web Service**.
3. Connect your GitHub / GitLab repository containing this `server/` folder (or push the repository to GitHub).
4. Configure the Web Service settings:
   - **Name**: `connectx-backend`
   - **Root Directory**: `server`
   - **Environment**: `Node`
   - **Build Command**: `npm install`
   - **Start Command**: `npm start`
   - **Instance Type**: `Free`
5. Click **Create Web Service**.
6. Once deployed, Render will provide your public live URLs:
   - **API Base URL**: `https://connectx-backend.onrender.com/api`
   - **WebSocket URL**: `wss://connectx-backend.onrender.com`

---

## Option 2: Railway.app (Free Tier / Trial)

1. Sign in to [https://railway.app](https://railway.app).
2. Click **New Project** -> **Deploy from GitHub repo**.
3. Select your repository and specify `server` directory.
4. Railway will automatically detect Node.js and deploy with a custom `.up.railway.app` domain.

---

## Option 3: Glitch.com (Instant 1-Click Sandbox)

1. Go to [https://glitch.com](https://glitch.com) and click **New Project** -> **Import from GitHub**.
2. Paste your repository link.
3. Glitch instantly runs your server at `https://your-project-name.glitch.me`.

---

## Connecting Your Android App to the Live Server

1. Open the **ConnectX APK** on your phone.
2. On the Login screen, click the **Settings icon** (top right) OR navigate to **Settings -> Server & API Configuration** inside the app.
3. Enter your live deployment URLs:
   - **Backend Base API URL**: `https://connectx-backend.onrender.com/api`
   - **WebSocket Signal URL**: `wss://connectx-backend.onrender.com`
4. Click **Save & Apply**. Your ConnectX Android app is now connected live over the internet!
