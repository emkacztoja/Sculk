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
- Three trigger modes: `contains` (any message with the keyword), `prefix` (`sculk <msg>` only), `mention` (`@sculk <msg>` only)

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
- Auto-notification on quest completion (no need to ask)
- One active quest per player

### 📍 Landmarks & Teleportation
- Save your current location as a named landmark
- Teleport back to saved landmarks via conversation
- Landmarks persist across sessions

### 🧰 AI Tool System (18 Tools)

Sculk has access to in-game tools that it can invoke autonomously during conversations:

| Tool | Affection Required | Permission | Description |
|------|--------------------|------------|-------------|
| `heal_player` | 30+ | — | Full health + hunger restore |
| `gift_item_to_player` | 20+ | — | Drop items at player's feet |
| `apply_potion_effect` | 10+ | — | Apply any potion effect |
| `play_sound` | 0+ | — | Cosmetic sound effects |
| `spawn_particles` | 0+ | — | Particle bursts |
| `execute_console_command` | 0+ | — | Whitelisted commands only |
| `save_landmark` | 0+ | — | Save location as landmark |
| `teleport_to_landmark` | 0+ | — | Teleport to saved landmark |
| `start_quest` | 0+ | — | Assign a new quest |
| `check_quest_status` | 0+ | — | Check/update quest progress |
| `complete_quest` | 0+ | — | Clear completed quest |
| `modify_relationship` | 0+ | — | Change affection score |
| `sacrifice_held_item` | 0+ | — | Consume held item + modify affection |
| `remember_player_fact` | 0+ | — | Save long-term memory about player |
| `kick_player` | 0+ | `sculk.sudo` | Kick a player |
| `teleport_player` | 0+ | `sculk.sudo.teleport` | TP player to player |
| `get_server_status` | 0+ | `sculk.sudo.monitor` | TPS, RAM, chunks, entities |
| `broadcast_announcement` | 0+ | `sculk.sudo.broadcast` | Server-wide announcement |

All affection thresholds are configurable under `actions.thresholds.<tool_name>` in `config.yml`.

### 📜 Context Awareness
- Sculk knows your **name, location, biome, health, hunger, and held item**
- It knows the **time of day, weather, and online player count**
- Configurable radius for nearby-entity awareness
- All context is injected into the AI prompt for immersive responses

### 🎨 Rich Text Formatting
- AI responses are parsed from Markdown to MiniMessage
- Supports **bold**, *italic*, __underline__, ~~strikethrough~~, and `code`
- Customizable prefix, thinking indicator, and error messages
- Sculk-themed particle and sound effects on activation
- Configurable response delivery: `private` (only asker) or `broadcast` (all players)

### 📊 Usage Logging
- Every API call logs prompt / completion / total tokens to `plugins/sculk/usage.log`
- CSV format for easy spreadsheet / dashboard import

---

## 📦 Installation

### Requirements
- **Minecraft Server:** Spigot 26.1.2 (or compatible fork)
- **Java:** 25+
- **LLM API:** Any OpenAI-compatible endpoint (e.g., DeepSeek, OpenAI, Ollama, LM Studio, vLLM)

### Steps

1. **Build the plugin** (or grab a release JAR):
   ```bash
   ./gradlew build
   ```
   The compiled JAR will be at `build/libs/Sculk-1.1.0-dev.jar`.

2. **Install the JAR** into your server's `plugins/` folder.

3. **Start the server** once to generate default config files.

4. **Configure** `plugins/sculk/config.yml` with your API details (see below).

5. **Restart** or `/sculk reload`.

---

## ⚙️ Configuration

### `config.yml`

```yaml
# Global language for Sculk replies (en, pl, de, es, fr)
default-language: "en"

# How the chat trigger matches messages:
#   "contains"  — message contains the keyword (default; may false-positive on words like "sculk farm")
#   "prefix"    — message STARTS with the keyword followed by a space ("sculk help me")
#   "mention"   — message is "@sculk <rest>" (or "@Sculk", case-insensitive)
trigger-mode: "contains"
trigger-keyword: "sculk"
mention-keyword: "sculk"

# How response is delivered:
#   "broadcast" — to all players on the server
#   "private"   — only to the asking player (recommended)
response-mode: "private"

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
  cooldown-seconds: 0

  # Seconds between positive affection gains per player (sacrifice protection)
  positive-affection-cooldown-seconds: 120

  # Max concurrent in-flight LLM requests across the whole server
  max-concurrent-requests: 4

  # Max recursive tool invocation loop depth
  max-tool-turns: 5

  # System prompt defining Sculk's personality
  system-prompt: "You are a mystical Sculk helper in Minecraft. Keep replies brief, under 2 sentences."

# Per-tool affection thresholds. The AI tool is hidden/denied below this score.
# Anything not listed here defaults to 0.
actions:
  enable-actions: true
  # IMPORTANT: These are PREFIX matches, not exact matches!
  allowed-commands:
    - "summon firework_rocket"
    - "effect give"
    - "particle"
    - "give %player% cookie"
  thresholds:
    heal_player: 30
    gift_item_to_player: 20
    apply_potion_effect: 10

formatting:
  prefix: '<white>\<</white><dark_purple>Sculk</dark_purple><white>></white> '
  thinking-message: "<dark_purple><obfuscated>k</obfuscated> <dark_purple>Sculk is listening...</dark_purple> <dark_purple><obfuscated>k</obfuscated></dark_purple>"
  error-message: "<red>Sculk whispers: The void is silent... (Request failed)</red>"
  thinking-actionbar-ticks: 20  # how often the "listening" indicator refreshes (lower = smoother, higher = cheaper)

feedback:
  play-sound: true
  spawn-particles: true

context:
  enable-player-context: true
  enable-server-context: true
  nearby-entities-radius: 10

lore:
  enable-lore: true
```

### `lore.txt`

A free-form text file injected into every system prompt. Use it to define:
- Server-specific lore and rules
- Sculk's backstory and personality details
- Sacrifice value tables (loaded by the AI as reference — see the default `lore_en.txt`)
- World-specific context

### `usage.log`

Append-only CSV written to `plugins/sculk/usage.log` after every LLM call:

```
timestamp,player,turn,prompt_tokens,completion_tokens,total_tokens,model
2026-06-24T01:23:45Z,emkacz,1,812,57,869,deepseek-v4-flash
```

Import into a spreadsheet or pipe into a metrics dashboard to monitor spend.

---

## 🔑 Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `sculk.use` | `true` | Use `/sculk ask`, `/ask`, `/sculk toggle`, and chat triggers |
| `sculk.admin` | `op` | Use `/sculk reload`, `/sculk clear`, `/sculk profile`, `/sculk status <other>` |
| `sculk.sudo` | `op` | Allow AI to use `kick_player` tool |
| `sculk.sudo.teleport` | `op` | Allow AI to use `teleport_player` tool |
| `sculk.sudo.monitor` | `op` | Allow AI to use `get_server_status` tool |
| `sculk.sudo.broadcast` | `op` | Allow AI to use `broadcast_announcement` tool |
| `sculk.immune` | `op` | Cannot be targeted by `kick_player` / `teleport_player` tools |

> Grant `sculk.immune` to admins and other players who should never be kicked or teleported by the AI.

---

## 💬 Commands

| Command | Description |
|---------|-------------|
| `/sculk` (no args) | Show your own Sculk status (alias of `/sculk status`) |
| `/sculk ask <question>` | Send a one-off question to Sculk |
| `/ask <question>` | Shorthand alias for `/sculk ask` |
| `/sculk toggle` | Toggle Chat Mode (all messages → Sculk) |
| `/sculk chat` | Alias for toggle |
| `/sculk status [player]` | Show your (or another player's) affection, quest, landmarks |
| `/sculk profile <player>` | (Admin) Dump the full player profile as JSON |
| `/sculk clear [player]` | (Admin) Wipe a player's memory, facts, and landmarks |
| `/sculk reload` | (Admin) Reload `config.yml` and `lore.txt` |

---

## 💜 How Affection Works

```
-100 ◄──────────── 0 ──────────────► +100
 HOSTILE        NEUTRAL          DEVOTED
```

- **Starting value:** 0 (neutral)
- **Modified dynamically** by the AI based on player interactions
- **Positive gains** have a configurable cooldown (default 2 min) to prevent spam (high-value sacrifices bypass this)
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

### Approximate Values (AI-decided — see `lore_en.txt` for the canonical table)

| Item | Typical Points | Cooldown Bypass |
|------|---------------|-----------------|
| Dirt, Cobblestone, Gravel | -5 to -10 (insulting) | N/A |
| Coal, Raw Iron, Food | +3 to +5 | No |
| Gold, Iron Ingots | +5 to +8 | No |
| Diamonds, Emeralds | +10 to +15 | Yes |
| Netherite, Beacons | +20 to +30 | Yes |

---

## 📂 Data Storage

Player data is stored at `plugins/sculk/players/<UUID>.json`:

```json
{
  "history": [],
  "facts": [],
  "landmarks": {},
  "affection": 0,
  "last_affection_gain": 0,
  "active_quest": null
}
```

> **Note:** `last_affection_gain` and `active_quest` are seeded to defaults for new profiles.

---

## 🏗️ Architecture

```
dev.emkacz.sculk/
├── Sculk.java              Plugin lifecycle, state maps, profile I/O, rate limit
├── ai/
│   └── AIService.java      HTTP client, context, recursive completion loop, usage log
├── action/
│   ├── ActionManager.java  Tool dispatch + tool registry
│   └── ToolDefinition.java Declarative tool schema + executor record
├── command/
│   └── SculkCommand.java   /sculk, /ask, /sculk status, /sculk profile, /sculk clear
├── lang/
│   └── LanguageManager.java i18n loader + fallback chain
├── listener/
│   ├── ChatListener.java   async chat trigger (contains / prefix / mention)
│   └── QuestListener.java  KILL_MOB progress + auto-complete notify
└── util/
    ├── MarkdownParser.java Markdown → MiniMessage conversion
    ├── CommandPrefixMatcher.java prefix allowlist (testable)
    └── AffectionCooldown.java cooldown math (testable)
```

### Key Design Decisions

- **Never blocks the main thread** — all API calls use `HttpClient.sendAsync()` with `CompletableFuture`
- **Context gathering and tool execution run on the main thread** (required for Bukkit API calls like inventory/entity manipulation)
- **Recursive tool loop** supports multi-step AI chains (e.g., check quest → complete quest → gift reward) up to 5 depth
- **Adventure API (Kyori)** loaded via `plugin.yml` libraries, used exclusively for text rendering
- **Global request semaphore** (`api.max-concurrent-requests`) prevents the LLM from being DOSed by burst traffic
- **Profile saves are dirty-flagged** — multiple in-memory mutations between saves are coalesced into a single async disk write

---

## 🛡️ Security Notes

> [!WARNING]
> **`allowed-commands` uses prefix matching.** An entry like `"give"` would allow the AI to run ANY `give` command. Keep prefixes specific.

> [!WARNING]
> **Your API token is stored in plain text** in `config.yml`. Ensure proper file permissions on your server. Consider setting the token via an environment variable injected into the config in your deployment pipeline.

> [!WARNING]
> **Player profiles contain chat history and personal facts.** The `plugins/sculk/players/` folder is created with restricted permissions where the OS allows. Treat the contents as private.

- **Three-layer gating on privileged tools:** permission check at tool *declaration* (AI doesn't see the tool) + affection check at *execution* + `sculk.immune` for protected targets
- **The AI cannot execute arbitrary commands** — only whitelisted prefixes
- **Server-wide request limit** prevents burst-flooding the LLM API
- **MiniMessage sanitizes most injection vectors** in chat output

---

## 🔧 Building from Source

```bash
# Clone
git clone <repository-url>
cd Sculk

# Build (output: build/libs/Sculk-1.1.0-dev.jar)
./gradlew build

# Run tests
./gradlew test
```

### Tech Stack

- **Java 25** (toolchain enforced)
- **Gradle 9.5.1** with Kotlin DSL
- **Spigot API 26.1.1-R0.1-SNAPSHOT**
- **Kyori Adventure 4.17.0** (MiniMessage + BukkitAudiences)
- **Gson** (Spigot-provided, not shaded)
- **java.net.http.HttpClient** (JDK built-in)
- **JUnit 5** for unit tests

### CI

Every push and PR runs `./gradlew build test` on JDK 25. Tagged `v*` releases get a JAR attached to a GitHub Release.

---

## 📝 License

This project is licensed under the **GNU General Public License v3.0** (GPLv3). See the [LICENSE](LICENSE) file for details.

---

*Made with ❤️ by emkacz*
