# HyStreamerAlerts

A Hytale server plugin that displays real-time stream alerts (follows, subscriptions, donations, raids) directly in-game using [Botrix](https://botrix.live/) WebSocket integration.

![Hytale](https://img.shields.io/badge/Hytale-Server%20Plugin-blue)
![License](https://img.shields.io/badge/license-MIT-green)
![Java](https://img.shields.io/badge/Java-25-orange)

## Features

- **Real-time Alerts** - Instant in-game notifications when viewers interact with your stream
- **Multiple Alert Types**:
  - 👤 New Followers
  - ⭐ Subscriptions (with month count)
  - 🎁 Gift Subs
  - 💰 Donations/Tips
  - 🎉 Raids (with viewer count)
- **Botrix Integration** - Works with Kick and other platforms supported by Botrix
- **Per-Player Configuration** - Each player can set up their own stream alerts
- **Persistent Settings** - Your broadcast ID and preferences are saved between sessions
- **Auto-Reconnect** - Automatically reconnects if the WebSocket connection drops

## Screenshots

*Coming soon*

## Installation

1. Download the latest release from [Releases](../../releases)
2. Place `HyStreamerAlerts.jar` in your Hytale server's `mods` folder
3. Start/restart your server
4. Configure in-game using the commands below

## Requirements

- Hytale Server (latest version)
- Java 25+
- A [Botrix](https://botrix.live/) account with alerts configured

## Getting Your Botrix Broadcast ID

1. Go to [Botrix.live](https://botrix.live/) and log in
2. Navigate to your Alerts page
3. Copy your alert URL - it looks like: `https://botrix.live/alerts?bid=YOUR_BROADCAST_ID`
4. The `bid` parameter (e.g., `tuj0pxW5J9WrNF7JXuoZ5g`) is your Broadcast ID

## Commands

All commands use the `/sa` prefix (Streamer Alerts):

| Command | Description |
|---------|-------------|
| `/sa` | Show help menu |
| `/sa on` | Enable alerts and auto-connect if broadcast ID is set |
| `/sa off` | Disable alerts |
| `/sa setbid <id>` | Set your Botrix broadcast ID |
| `/sa connect` | Manually connect to Botrix WebSocket |
| `/sa disconnect` | Disconnect from Botrix |
| `/sa status` | Show current connection status |
| `/sa help` | Show help menu |

## Quick Start

```
1. /sa setbid YOUR_BROADCAST_ID
2. /sa on
```

That's it! You'll now receive in-game alerts when someone follows, subscribes, donates, or raids your stream.

## How It Works

```
┌─────────────┐     WebSocket      ┌─────────────┐     Alert      ┌─────────────┐
│   Botrix    │ ──────────────────▶│ HyStreamer  │ ─────────────▶ │   Player    │
│   Server    │                    │   Alerts    │                │  In-Game    │
└─────────────┘                    └─────────────┘                └─────────────┘
       ▲
       │ Stream Events
       │
┌─────────────┐
│    Kick/    │
│   Twitch    │
└─────────────┘
```

1. Your streaming platform sends events to Botrix
2. Botrix broadcasts events via WebSocket
3. HyStreamerAlerts receives the events
4. Alert is displayed as an in-game event title

## Alert Display

Alerts appear as Hytale event titles with:
- **Title**: Alert type (e.g., "New Follower!", "New Subscriber!")
- **Subtitle**: Details (e.g., "Username just followed!", "Username subscribed for 3 months!")

## Configuration

Player settings are stored in `plugins/HyStreamerAlerts/alerts.json`:

```json
{
  "enabledPlayers": [
    "player-uuid-1",
    "player-uuid-2"
  ],
  "broadcastIds": {
    "player-uuid-1": "broadcast-id-1",
    "player-uuid-2": "broadcast-id-2"
  }
}
```

## Supported Platforms

Through Botrix integration:
- ✅ Kick
- ✅ Other platforms supported by Botrix

## Building from Source

```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/HyStreamerAlerts.git
cd HyStreamerAlerts

# Build with Gradle
./gradlew build

# Output JAR will be in build/libs/
```

### Requirements for Building
- Java 25 JDK
- Hytale Server JAR in the expected location (see `build.gradle`)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Credits

- **Botrix** - For providing the alert WebSocket service
- **Hypixel Studios** - For creating Hytale

## Support

- 🐛 [Report a Bug](../../issues/new?template=bug_report.md)
- 💡 [Request a Feature](../../issues/new?template=feature_request.md)
- 💬 [Discussions](../../discussions)

## Changelog

### v1.0.0
- Initial release
- Botrix WebSocket integration
- Support for follows, subs, gift subs, donations, and raids
- Per-player configuration
- Auto-reconnect functionality

---

Made with ❤️ for Hytale streamers
