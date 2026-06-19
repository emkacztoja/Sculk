# GEMINI.md: AI Collaboration Guide (Minecraft 26.1.2)

This document provides persistent, baseline configurations and context for the Gemini CLI agent interacting with this modern Minecraft workspace.

## 1. Project Overview & Context
- **Name:** Sculk
- **Purpose:** An ultra-lightweight, high-performance Paper plugin designed for Minecraft 26.1.2. It hooks the global chat engine, parsing player context and passing payloads asynchronously to a self-hosted local LLM API gateway without compromising engine TPS.
- **Runtime Environment:** Java 25+, Maven 3.9+, Paper API v1.26.1-R0.1-SNAPSHOT (Targeting contemporary 2026 builds).

## 2. Global Development Directives
- **Modern Async Architecture:** You must absolutely NEVER block the primary execution loop. All network operations (`java.net.http.HttpClient`) must run on completely isolated virtual worker paths or via non-blocking `CompletableFuture` task structures.
- **Component-Driven Text Flow:** Do not utilize deprecated `ChatColor` or raw string manipulation sequences. All text formatting must strictly leverage the modern **Adventure API (`Component`)** and `MiniMessage` parsing frameworks.
- **Namespace Integrity:** All generated classes, structural logic, and event utilities must reside tightly inside the `dev.emkacz.sculk` target package mapping.

## 3. Technology Stack & Coding Standards
- **Implicit Dependency Gating:** Do not shade heavy external JSON parsers. Leverage the native `com.google.gson` structure which is natively pre-shaded inside the Paper 26 server environment.
- **Thread-Safe Packet Filtering:** Intercept incoming text using the modern `AsyncChatEvent` rather than the legacy Spigot player chat events. Bind listeners using standard monitor prioritization guidelines (`EventPriority.MONITOR`, `ignoreCancelled = true`).

## 4. Default Core Specifications

### `pom.xml` Target Definitions
```xml
<properties>
    <java.version>25</java.version>
</properties>
<dependencies>
    <dependency>
        <groupId>io.papermc.paper</groupId>
        <artifactId>paper-api</artifactId>
        <version>1.26.1-R0.1-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### Modernized `ChatListener.java` Architecture Style
```java
package dev.emkacz.sculk.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String rawText = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (rawText.toLowerCase().contains("sculk")) {
            // Process async request and broadcast back using Component objects
            // Component parsed = MiniMessage.miniMessage().deserialize("<purple>[Sculk]</purple> " + response);
            // plugin.getServer().sendMessage(parsed);
        }
    }
}
```

## 5. Target Endpoint Mapping
- **API URL Configuration:** `https://ai.emkacz.dev/v1/chat/completions`
- **Default Connection Context:** Outgoing packages pass standard OpenAI-compatible JSON formats containing predefined system instructions and context structures mapped directly to the local inference layer.
```