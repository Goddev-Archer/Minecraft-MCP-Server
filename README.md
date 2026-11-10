# Minecraft MCP Server (Fabric & NeoForge)

Ein nativer **Model Context Protocol (MCP)** Server integriert direkt in Minecraft 1.21.1 als **Client-Mod**.

Ermöglicht es KI-Agenten (wie Antigravity, Claude Desktop, Cursor etc.):
1. **Minecraft aktiv zu spielen:** Wahrnehmung von Blöcken, Entitäten, Inventar und Spielerstatus.
2. **Features & Mods autonom zu testen:** Ausführen von Befehlen, NBT-Inspektion, Log-Sniffer für Crashes/Exceptions.
3. **In-Game KI-Support:** Spieler können direkt im Minecraft-Chat mit `/ai <frage>` mit der KI chatten, ohne das Spiel zu verlassen!

---

## 🚀 Schnellstart

### 1. Mod in dein Modpack einfügen
Kopiere die erzeugte JAR-Datei in den `mods`-Ordner deines Minecraft 1.21.1 Profils / Modpacks:
```
fabric/build/libs/minecraft-mcp-server-fabric-1.0.0.jar
```
*(Die Mod ist rein clientseitig und funktioniert sowohl im Singleplayer als auch auf beliebigen Multiplayer-Servern).*

### 2. In-Game Befehle
Sobald du im Spiel bist:
* `/ai <Deine Frage>` — Sendet deine Frage samt akutem Spielkontext (Block im Fadenkreuz, Koordinaten, Biome, letzte Warnings/Errors) an die KI.
* `/mcp status` — Zeigt Port, Status und verbundene KI-Agenten an.
* `/mcp token` — Zeigt dein persönliches Authentifizierungs-Token an.
* `/mcp log [n]` — Zeigt die letzten `n` Log-Meldungen direkt im Chat an.

---

## 🤖 KI-Agenten anbinden

### Konfiguration für Claude Desktop / Antigravity (`mcpServers`)

Füge folgenden Eintrag zu deiner MCP-Konfiguration (`claude_desktop_config.json` oder Antigravity MCP Settings) hinzu:

```json
{
  "mcpServers": {
    "minecraft": {
      "command": "node",
      "args": [
        "C:/dev/Minecraft-MCP-Server/bridge-cli/index.js"
      ]
    }
  }
}
```

> **Hinweis:** Die Bridge liest das Token und den Port automatisch aus deiner Minecraft-Konfiguration (`config/minecraft_mcp.json`). Alternativ kannst du `--token <dein_token>` als Argument übergeben.

---

## 🛠️ Verfügbare MCP Tools für Agenten

* `get_player_state`: Liefert Koordinaten (X, Y, Z, Yaw, Pitch), Leben, Hunger, Rüstung, Inventar und aktive Statuseffekte.
* `raycast_target`: Erkennt den Block oder die Entity im Fadenkreuz inklusive BlockState-Properties (z. B. `lit`, `facing`, `powered`).
* `scan_blocks`: Scannt Blöcke in einem wählbaren Radius (mit Keyword-Filtern für Erze, Kisten, Maschinen).
* `inspect_nbt`: Liest detaillierte NBT-Daten von TileEntities oder Items in der Hand aus (ideal für Tech-Mods).
* `execute_command`: Führt Minecraft-Befehle aus (`/give`, `/tp`, `/gamemode`).
* `send_chat`: Schreibt Antworten in das Minecraft-Chat-HUD des Spielers.
* `get_latest_logs`: Liest Log4j-Einträge aus, um Warnings und Exceptions direkt zu diagnostizieren.
