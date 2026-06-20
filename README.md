# 🟣 Sculk

**An AI-powered companion plugin for Minecraft 26.1.2 (Spigot)**

Sculk brings a living, breathing AI entity into your Minecraft world — a mystical presence that remembers players, builds relationships, assigns quests, accepts sacrifices, and uses in-game tools to interact with the world. Powered by any OpenAI-compatible LLM API.

---

## ✨ Features

### 🧠 AI Chat with Memory
- Talk to Sculk via chat (`sculk` keyword) or dedicated commands (`/ask`, `/sculk ask`)
- Toggle "Chat Mode" to have **all** your messages directed to Sculk (`/sculk toggle`)
- Per-player persistent conversation history stored as JSON profiles
- Configurable system prompt and personality via `config.yml` and `lore.txt`

### 💜 Dynamic Relationship System
- Each player has an **affection score** from **-100** to **+100**
- The AI dynamically decides how relationships change based on player behavior
- High affection unlocks powerful tools (healing, gifts, buffs)
- Low affection makes Sculk hostile, cold, and uncooperative
- 2-minute cooldown on positive affection gains to prevent gift-spamming (bypassed for high-value offerings)

### 🔥 Sacrifice System
- Hold an item and ask Sculk to accept it as a sacrifice
- The AI evaluates the item's worth and decides the affection change
- **Transactional safety** — items are only consumed if the affection change succeeds
- If cooldown blocks the change, your item stays in your hand

### ⚔️ Quest System
- Sculk can assign **KILL_MOB** or **COLLECT_ITEM** quests
- Kill progress is tracked automatically via death events
- Collection quests scan your inventory and consume items on completion
- Action bar progress indicators with sound effects
- One active quest per player

### 📍 Landmarks & Teleportation
- Save your current location as a named landmark
- Teleport back to saved landmarks via conversation
- Landmarks persist across sessions

### 🧰 AI Tool System (18 Tools)

Sculk has access to in-game tools that it can invoke autonomously during conversations:

| Tool | Affection Required | Description |
|------|--------------------|-------------|
| `heal_player` | 30+ | Full health + hunger restore |
| `gift_item_to_player` | 20+ | Drop items at player's feet |
| `apply_potion_effect` | 10+ | Apply any potion effect |
| `play_sound` | 0+ | Cosmetic sound effects |
| `spawn_particles` | 0+ | Particle bursts |
| `execute_console_command` | 0+ | Whitelisted commands only |
| `save_landmark` | 0+ | Save location as landmark |
| `teleport_to_landmark` | 0+ | Teleport to saved landmark |
| `start_quest` | 0+ | Assign a new quest |
| `check_quest_status` | Any | Check/update quest progress |
| `complete_quest` | Any | Clear completed quest |
| `modify_relationship` | Any | Change affection score |
| `sacrifice_held_item` | Any | Consume held item + modify affection |
| `remember_player_fact` | Any | Save long-term memory about player |
| `kick_player` | 0+ | Kick a player (requires `sculk.sudo`) |
| `teleport_player` | 0+ | TP player to player (requires `sculk.sudo.teleport`) |
| `get_server_status` | 0+ | TPS, RAM, chunks, entities (requires `sculk.sudo.monitor`) |
| `broadcast_announcement` | 0+ | Server-wide announcement (requires `sculk.sudo.broadcast`) |

### 📜 Context Awareness
- Sculk knows your **name, location, biome, health, hunger, and held item**
- It knows the **time of day, weather, and online player count**
- All context is injected into the AI prompt for immersive responses

### 🎨 Rich Text Formatting
- AI responses are parsed from Markdown to MiniMessage
- Supports **bold**, *italic*, __underline__, ~~strikethrough~~, and `code`
- Customizable prefix, thinking indicator, and error messages
- Sculk-themed particle and sound effects on activation

---

## 📦 Installation

### Requirements
- **Minecraft Server:** Spigot 26.1.2 (or compatible fork)
- **Java:** 25+
- **LLM API:** Any OpenAI-compatible endpoint (e.g., DeepSeek, OpenAI, Ollama, LM Studio, vLLM)

### Steps

1. **Build the plugin:**
   ```bash
   ./gradlew build
   ```
   The compiled JAR will be at `build/libs/Sculk-1.0-SNAPSHOT.jar`.

2. **Install the JAR** into your server's `plugins/` folder.

3. **Start the server** once to generate default config files.

4. **Configure** `plugins/sculk/config.yml` with your API details (see below).

5. **Restart** or `/sculk reload`.

---

## ⚙️ Configuration

### `config.yml`

```yaml
api:
  # Your OpenAI-compatible endpoint
  url: "https://api.deepseek.com/chat/completions"
  
  # API key (leave blank for local LLMs without auth)
  token: "your-api-key-here"
  
  # Model identifier
  model: "deepseek-v4-flash"
  
  # Request timeout (increase for slow local models)
  timeout-seconds: 60
  
  # Conversation turns remembered per player (0 = no memory)
  chat-history-size: 5
  
  # Seconds between queries per player (0 = no cooldown)
  cooldown-seconds: 10
  
  # System prompt defining Sculk's personality
  system-prompt: "You are a mystical Sculk helper in Minecraft..."

formatting:
  prefix: '<white>\<<\/white><dark_purple>Sculk<\/dark_purple><white>><\/white> '
  thinking-message: "<dark_purple><obfuscated>k</obfuscated> Sculk is listening... <obfuscated>k</obfuscated></dark_purple>"
  error-message: "<red>Sculk whispers: The void is silent... (Request failed)</red>"

feedback:
  play-sound: true
  spawn-particles: true

context:
  enable-player-context: true
  enable-server-context: true

actions:
  enable-actions: true
  # IMPORTANT: These are PREFIX matches, not exact matches!
  allowed-commands:
    - "summon firework_rocket"
    - "effect give"
    - "particle"
    - "give %player% cookie"

lore:
  enable-lore: true
```

### `lore.txt`

A free-form text file injected into every system prompt. Use it to define:
- Server-specific lore and rules
- Sculk's backstory and personality details
- World-specific context

---

## 🔑 Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `sculk.use` | `true` | Use `/sculk ask`, `/ask`, `/sculk toggle`, and chat triggers |
| `sculk.admin` | `op` | Use `/sculk reload` to reload configuration |
| `sculk.sudo` | `op` | Allow AI to use `kick_player` tool |
| `sculk.sudo.teleport` | `op` | Allow AI to use `teleport_player` tool |
| `sculk.sudo.monitor` | `op` | Allow AI to use `get_server_status` tool |
| `sculk.sudo.broadcast` | `op` | Allow AI to use `broadcast_announcement` tool |

---

## 💬 Commands

| Command | Description |
|---------|-------------|
| `/sculk ask <question>` | Send a one-off question to Sculk |
| `/ask <question>` | Shorthand alias for `/sculk ask` |
| `/sculk toggle` | Toggle Chat Mode (all messages → Sculk) |
| `/sculk chat` | Alias for toggle |
| `/sculk reload` | Reload `config.yml` and `lore.txt` (requires `sculk.admin`) |

---

## 💜 How Affection Works

```
-100 ◄──────────── 0 ──────────────► +100
 HOSTILE        NEUTRAL          DEVOTED
```

- **Starting value:** 0 (neutral)
- **Modified dynamically** by the AI based on player interactions
- **Positive gains** have a 2-minute cooldown to prevent spam (high-value sacrifices bypass this)
- **Negative changes** have no cooldown — insult Sculk at your own risk
- **Persists** across sessions in the player's JSON profile

### Affection Tiers (Approximate)

| Range | Relationship | Behavior |
|-------|-------------|----------|
| 50 to 100 | Devoted | Warmly helpful, willing to heal and gift valuable items |
| 20 to 49 | Friendly | Cooperative, will gift items and assist |
| 0 to 19 | Neutral | Polite but reserved |
| -30 to -1 | Cold | Suspicious, refuses most tools |
| -100 to -31 | Hostile | Actively antagonistic, may try to harm player |

---

## 🔥 Sacrifice Guide

1. **Hold an item** in your main hand
2. **Ask Sculk** to accept it (e.g., "I offer you this diamond")
3. The AI evaluates the item and decides the affection change
4. If accepted, 1 item is consumed from your hand

### Approximate Values (AI-decided)

| Item | Typical Points | Cooldown Bypass |
|------|---------------|-----------------|
| Dirt, Cobblestone, Gravel | -5 to -10 (insulting) | N/A |
| Coal, Raw Iron, Food | +3 to +5 | No |
| Gold, Iron Ingots | +5 to +8 | No |
| Diamonds, Emeralds | +10 to +15 | Yes |
| Netherite, Beacons | +20 to +30 | Yes |

> **Note:** The AI decides values dynamically — results may vary based on context, relationship, and mood.

---

## 📂 Data Storage

Player data is stored in `plugins/sculk/players/<UUID>.json`:

```json
{
  "history": [...],        // Recent conversation messages
  "facts": [...],          // Long-term memory about the player
  "landmarks": {...},      // Saved locations
  "affection": 15,         // Current relationship score
  "last_affection_gain": 1718834567890,  // Cooldown timestamp
  "active_quest": {        // Current quest (null if none)
    "type": "KILL_MOB",
    "target": "ZOMBIE",
    "target_amount": 10,
    "current_amount": 3,
    "description": "Hunt 10 zombies in the deep dark."
  }
}
```

---

## 🏗️ Architecture

```
dev.emkacz.sculk/
├── Sculk.java              # Plugin lifecycle, state maps, profile I/O
├── command/
│   └── SculkCommand.java   # /sculk and /ask command routing
├── listener/
│   └── ChatListener.java   # AI brain: HTTP client, tools, quests, context
└── util/
    └── MarkdownParser.java  # Markdown → MiniMessage conversion
```

### Key Design Decisions

- **Never blocks the main thread** — all API calls use `HttpClient.sendAsync()` with `CompletableFuture`
- **Context gathering and tool execution run on the main thread** (required for Bukkit API calls like inventory/entity manipulation)
- **Recursive tool loop** supports multi-step AI chains (e.g., check quest → complete quest → gift reward) up to 5 depth
- **Adventure API (Kyori)** loaded via `plugin.yml` libraries, used exclusively for text rendering

---

## 🛡️ Security Notes

> [!WARNING]
> **`allowed-commands` uses prefix matching.** An entry like `"give"` would allow the AI to run ANY `give` command. Keep prefixes specific.

> [!WARNING]
> **Your API token is stored in plain text** in `config.yml`. Ensure proper file permissions on your server.

- Privileged tools are double-gated: permission check at tool **definition** (AI doesn't even see the tool) AND at **execution** time
- The AI cannot execute arbitrary commands — only whitelisted prefixes
- MiniMessage sanitizes most injection vectors in chat output

---

## 🔧 Building from Source

```bash
# Clone
git clone <repository-url>
cd Sculk

# Build
./gradlew build

# Output
ls build/libs/Sculk-*.jar
```

### Tech Stack
- **Java 25** (toolchain enforced)
- **Gradle** with Kotlin DSL
- **Spigot API 26.1.1-R0.1-SNAPSHOT**
- **Kyori Adventure 4.17.0** (MiniMessage + BukkitAudiences)
- **Gson** (Spigot-provided, not shaded)
- **java.net.http.HttpClient** (JDK built-in)

---

## 📝 License

This project is licensed under the **GNU General Public License v3.0** (GPLv3). See the [LICENSE](LICENSE) file for details.

---

*Made with ❤️ by emkacz*
