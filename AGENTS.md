# AGENTS.md

Operational guide for AI coding agents (OpenCode, Codex, Cursor, Aider, Devin, Gemini CLI, …) working on **Sculk**.

> If anything here conflicts with `README.md`, **trust `README.md`** — this file is a build- and code-style reference, not a product reference.

---

## 1. Project Snapshot

- **Name:** Sculk
- **What it is:** A Spigot 26.1.2 plugin that wires any OpenAI-compatible LLM (DeepSeek, OpenAI, Ollama, LM Studio, vLLM, …) into Minecraft as an in-game AI companion with memory, affection, and 18 in-game tools.
- **Language:** Java 25 (toolchain enforced — do not lower).
- **Build:** Gradle with **Kotlin DSL** (`build.gradle.kts`, `settings.gradle.kts`). Not Maven.
- **Server API:** **Spigot** 26.1.1-R0.1-SNAPSHOT (compileOnly). The project runs on Spigot, not Paper — do not introduce Paper-specific APIs.
- **Chat API:** Kyori Adventure 4.17.0 (`adventure-api`, `adventure-text-minimessage`, `adventure-platform-bukkit` 4.3.3) loaded at runtime via `plugin.yml` `libraries:` block.
- **HTTP:** JDK built-in `java.net.http.HttpClient`. **Do not** add OkHttp / Retrofit.
- **JSON:** `com.google.gson` (provided by Spigot, not shaded). **Do not** shade Gson.
- **License:** GPL-3.0.

### Build / run

```bash
# Build (output: build/libs/Sculk-1.1.0-dev.jar or similar)
./gradlew build

# Clean
./gradlew clean
```

Local dev server lives in `DevServer/` (gitignored) — Spigot 26.1.2 jar, libraries, and worlds. Drop the freshly built JAR into `DevServer/plugins/`.

---

## 2. Code Map

```
src/main/java/dev/emkacz/sculk/
├── Sculk.java              Plugin lifecycle, state maps, profile I/O
├── ai/
│   └── AIService.java      HTTP client, context, recursive completion loop
├── action/
│   └── ActionManager.java  18 tool schemas + executors (~1,200 lines)
├── command/
│   └── SculkCommand.java   /sculk, /ask, /sculk status, /sculk clear
├── lang/
│   └── LanguageManager.java i18n loader + fallback chain
├── listener/
│   ├── ChatListener.java   async chat trigger
│   └── QuestListener.java  KILL_MOB progress via EntityDeathEvent
└── util/
    └── MarkdownParser.java bold, italic, underline, strike, code
```

Resources:
- `src/main/resources/config.yml` (fully commented)
- `src/main/resources/lang/messages_{en,pl,de,es,fr}.yml`
- `src/main/resources/lore_{en,pl,de,es,fr}.txt` (server-customizable backstory)
- `src/main/resources/plugin.yml` (declares `libraries:` for Adventure)

---

## 3. Hard Rules (don't break these)

1. **Never block the main thread.** All LLM network I/O goes through `HttpClient.sendAsync()` → `CompletableFuture`. Context gathering (entity scan, biome, time) and tool execution (`Bukkit.dispatchCommand`, inventory, particles, teleport, …) **must** be scheduled onto the main thread via `BukkitScheduler.runTask(...)`. Profile I/O uses `runTaskAsynchronously`.
2. **No Paper APIs.** This is a Spigot plugin. Stay on `org.bukkit.*` and `org.spigotmc.*`. If you find yourself reaching for `io.papermc.paper.*`, stop.
3. **No ChatColor.** All player-facing text is built with `Component` (Kyori Adventure) and serialized with `MiniMessage`. LLM output flows through `MarkdownParser` → `MiniMessage` before render.
4. **No new HTTP / JSON deps.** JDK `HttpClient` and Spigot-provided Gson are it.
5. **Package boundary.** Everything lives under `dev.emkacz.sculk` — `ai/`, `action/`, `command/`, `lang/`, `listener/`, `util/`. New subpackages must follow the same role-based split (don't dump utility classes into root).
6. **Bounded recursion.** The tool-call loop in `AIService` is capped by `api.max-tool-turns` (default 5). Do not remove the cap or move it.
7. **Three-layer security on privileged tools** — preserve all three:
   - Permission gate at tool *declaration* (the AI must not even see `kick_player` / `teleport_player` / `get_server_status` / `broadcast_announcement` unless the player has the matching perm).
   - Affection gate at *execution* (most tools return an error JSON if the player's score is below their threshold).
   - Command-prefix allowlist for `execute_console_command` (it's a **prefix** match, not exact — keep the README's warning intact).

---

## 4. Conventions

- **Immutability where cheap.** Profiles are stored as `JsonObject` and synchronized on for read-during-write. Save with a `deepCopy()` under `synchronized (profile)`.
- **Defensive `has(key)` reads.** When reading a profile field whose absence is meaningful (e.g. `affection`, `last_affection_gain`), guard with `profile.has(...)` rather than throwing.
- **i18n first.** New user-visible strings go into `LanguageManager` + the five `messages_*.yml` files. Never hard-code English in command/listener code.
- **Tool schema style.** `ActionManager` declares tool schemas inline in a single `List<Map<String, Object>>`. Follow the existing pattern — don't introduce a reflection-based tool registry. Each tool is one `execute*` private method + one schema block.
- **DSML fallback.** `AIService` lines ~30–33 define `|DSML|…|DSML|` regexes so non-tool-calling models can still invoke tools. Keep that path; it's load-bearing for local LLMs (Ollama, LM Studio).
- **Sacrifice is transactional.** `sacrifice_held_item` checks the affection cooldown *before* decrementing the hand. If the change is blocked, the item stays. Do not change that order.
- **Quest collection is atomic.** `check_quest_status` / `complete_quest` scan inventory first, only consume if the target amount is met.

---

## 5. Player Profile Shape

Stored at `plugins/sculk/players/<UUID>.json`. Initialize every new profile with **all** of these keys (the cache default in `Sculk.getPlayerProfile()` and the reset in `clearPlayerProfile()` must agree):

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

`affection` is `int`, clamped `[-100, +100]`. `last_affection_gain` is `long` epoch ms (0 = no prior positive change). `active_quest` is either `null` or `{type, target, target_amount, current_amount, description}`.

---

## 6. Working Agreements

- **Read before edit.** The plugin is small but tightly coupled; touch one method, expect to update a listener / tool schema / i18n key in the same change.
- **Match the surrounding style.** 4-space indent, brace-on-same-line, `final` on the class only (not on locals), `java.util.*` import preferred over single-type imports for `Map` / `List`.
- **Don't shadow.** `plugin.yml` declares the three Adventure artifacts as runtime libs; do not add them to `dependencies { implementation(...) }`.
- **Toolchain.** Java 25 only. Don't bump, don't lower — `build.gradle.kts` enforces it.
- **When the README disagrees with this file, README wins on product behavior, this file wins on build/style.**

---

## 7. Quick Sanity Checklist Before You Commit

- [ ] `./gradlew build` is green.
- [ ] No Paper-only imports (`io.papermc.*`).
- [ ] No `ChatColor` usage; no raw `String.format` for user-facing text.
- [ ] No new shaded deps in `build.gradle.kts`.
- [ ] Privileged tools still gated three ways (perm + affection + prefix).
- [ ] Fresh-player profile contains all six keys (see §5).
- [ ] New strings added to **all five** `messages_*.yml` files (en/pl/de/es/fr).
- [ ] No edits to `wiki/` or `DevServer/` — both are gitignored working dirs.
