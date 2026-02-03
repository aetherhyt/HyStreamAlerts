# HyStreamerAlerts - CurseForge Description

## Short Description (for summary)
Display real-time stream alerts in Hytale! Get notified in-game when viewers follow, subscribe, donate, or raid your stream via Botrix integration.

---

## Full Description (HTML/BBCode for CurseForge)

### Overview

**HyStreamerAlerts** brings your stream alerts directly into Hytale! Never miss a follower, subscriber, or donation while playing - alerts appear as beautiful in-game event titles.

Powered by **Botrix** WebSocket integration, this plugin supports Kick and other streaming platforms.

---

### ✨ Features

🔔 **Real-time Alerts** - Instant notifications, no delay

📺 **Multiple Alert Types:**
- 👤 New Followers
- ⭐ Subscriptions (shows month count!)
- 🎁 Gift Subs (shows amount!)
- 💰 Donations & Tips
- 🎉 Raids (shows viewer count!)

⚡ **Easy Setup** - Just 2 commands to get started

💾 **Persistent Settings** - Your config saves between sessions

🔄 **Auto-Reconnect** - Never miss an alert due to connection issues

👥 **Multi-Player Support** - Each player configures their own stream

---

### 📋 Requirements

- Hytale Server
- Java 25+
- [Botrix](https://botrix.live/) account

---

### 🚀 Quick Start

**Step 1:** Get your Botrix Broadcast ID from your alert URL:
`https://botrix.live/alerts?bid=YOUR_ID_HERE`

**Step 2:** In-game, run:
```
/sa setbid YOUR_ID_HERE
/sa on
```

**Done!** You'll now see alerts in-game!

---

### 📝 Commands

| Command | What it does |
|---------|--------------|
| `/sa` | Show help |
| `/sa on` | Enable alerts |
| `/sa off` | Disable alerts |
| `/sa setbid <id>` | Set Botrix ID |
| `/sa connect` | Connect to Botrix |
| `/sa disconnect` | Disconnect |
| `/sa status` | Check status |

---

### 🎮 How Alerts Look

When someone interacts with your stream, you'll see:

**Follow Alert:**
> 🎯 **New Follower!**
> *Username just followed!*

**Subscription Alert:**
> 🎯 **New Subscriber!**
> *Username subscribed for 3 months!*

**Donation Alert:**
> 🎯 **Donation!**
> *Username donated $5.00!*

**Raid Alert:**
> 🎯 **Incoming Raid!**
> *Username is raiding with 150 viewers!*

---

### 🔧 Supported Platforms

Through Botrix:
- ✅ Kick
- ✅ Other Botrix-supported platforms

---

### 📁 Installation

1. Download the JAR file
2. Place in your server's `mods` folder
3. Restart the server
4. Configure with `/sa setbid` command

---

### ❓ FAQ

**Q: Where do I get my Broadcast ID?**
A: Log into Botrix.live, go to Alerts, and copy the `bid` parameter from your alert URL.

**Q: Can multiple players use this?**
A: Yes! Each player sets up their own broadcast ID.

**Q: What if I disconnect?**
A: The plugin auto-reconnects within 5 seconds.

**Q: Does this work with Twitch?**
A: It works with any platform that Botrix supports.

---

### 🐛 Issues & Support

Found a bug? Have a suggestion?
→ [GitHub Issues](https://github.com/YOUR_USERNAME/HyStreamerAlerts/issues)

---

### 📜 License

MIT License - Free to use and modify!

---

**Made with ❤️ for Hytale streamers**
