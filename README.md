# Minecraft MCP Server (Fabric & NeoForge)

Ein nativer **Model Context Protocol (MCP)** Server integriert direkt in Minecraft als **Client-Mod**.

Ermöglicht es KI-Agenten (wie Antigravity, Claude Desktop, Cursor etc.):
1. **Minecraft aktiv zu spielen:** Wahrnehmung von Blöcken, Entitäten, Inventar und Spielerstatus.
2. **Features & Mods autonom zu testen:** Ausführen von Befehlen, NBT-Inspektion, Log-Sniffer für Crashes/Exceptions.
3. **In-Game KI-Support:** Spieler können direkt im Minecraft-Chat mit `/ai <frage>` mit der KI chatten, ohne das Spiel zu verlassen!

---

## 📦 Verfügbare Minecraft-Versionen & Downloads

Auf Root-Ebene stehen die Quellcodes und vorkompilierten JAR-Dateien für die unterstützten Minecraft-Versionen bereit:

| Minecraft-Version | Modloader | Fertige JAR-Datei (im Ordner `builds/`) | Quellcode-Ordner |
| :--- | :--- | :--- | :--- |
| **1.21.1** | **Fabric** | [`builds/minecraft-mcp-server-fabric-1.21.1.jar`](builds/minecraft-mcp-server-fabric-1.21.1.jar) | [`1.21.1/fabric`](1.21.1/fabric) |
| **1.21.1** | **NeoForge** | [`builds/minecraft-mcp-server-neoforge-1.21.1.jar`](builds/minecraft-mcp-server-neoforge-1.21.1.jar) | [`1.21.1/neoforge`](1.21.1/neoforge) |
| **1.21.4** | **Fabric** | [`builds/minecraft-mcp-server-fabric-1.21.4.jar`](builds/minecraft-mcp-server-fabric-1.21.4.jar) | [`1.21.4/fabric`](1.21.4/fabric) |
| **1.21.4** | **NeoForge** | [`builds/minecraft-mcp-server-neoforge-1.21.4.jar`](builds/minecraft-mcp-server-neoforge-1.21.4.jar) | [`1.21.4/neoforge`](1.21.4/neoforge) |

*(Die Mod ist rein clientseitig und funktioniert sowohl im Singleplayer als auch auf beliebigen Multiplayer-Servern).*

---

## 📁 Repository-Struktur auf Root-Ebene

```text
Minecraft-MCP-Server/
├── 1.21.1/               # Minecraft 1.21.1 Mod-Projekt (common, fabric, neoforge)
├── 1.21.4/               # Minecraft 1.21.4 Mod-Projekt (common, fabric, neoforge)
├── bridge-cli/           # Universal Node.js MCP Stdio Bridge
├── builds/               # Vorkompilierte JARs aller Versionen für Modpacks
├── launch-test-client.bat # Interaktiver Starter für Test-Clients (1.21.1 & 1.21.4)
├── launch-test-client.ps1 # PowerShell Starter für Test-Clients
├── build.gradle          # Root-Buildscript (Composite Build für alle Versionen)
├── settings.gradle       # Root-Settings (bindet 1.21.1 und 1.21.4 ein)
└── README.md
```

---

## 🚀 Schnellstart

### 1. Mod in dein Modpack einfügen
Kopiere die passende JAR-Datei aus dem `builds/`-Ordner in den `mods`-Ordner deines Minecraft 1.21.1- oder 1.21.4-Profils:
* Für 1.21.1 Fabric: `builds/minecraft-mcp-server-fabric-1.21.1.jar`
* Für 1.21.1 NeoForge: `builds/minecraft-mcp-server-neoforge-1.21.1.jar`
* Für 1.21.4 Fabric: `builds/minecraft-mcp-server-fabric-1.21.4.jar`
* Für 1.21.4 NeoForge: `builds/minecraft-mcp-server-neoforge-1.21.4.jar`

### 2. Test-Client direkt starten
Über das Skript auf Root-Ebene kannst du den jeweiligen Test-Client direkt starten:
```powershell
.\launch-test-client.bat
# oder per PowerShell:
.\launch-test-client.ps1 -Version 1.21.4
```

### 3. In-Game Befehle
Sobald du im Spiel bist:
* `/ai <Deine Frage>` — Sendet deine Frage samt akutem Spielkontext (Block im Fadenkreuz, Koordinaten, Biome, letzte Warnings/Errors) an die verbundene KI.
* `/mcp status` — Zeigt Port, Status und verbundene KI-Agenten an.
* `/mcp token` — Zeigt dein persönliches Authentifizierungs-Token an.
* `/mcp log [n]` — Zeigt die letzten `n` Log-Meldungen direkt im Chat an.

---

## 🤖 KI-Agenten anbinden

### Konfiguration für Claude Desktop / Antigravity (`mcpServers`)

Füge folgenden Eintrag zu deiner MCP-Konfiguration (`claude_desktop_config.json` oder `~/.gemini/config/mcp_config.json`) hinzu:

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

> **Hinweis:** Die Bridge liest das Token und den Port automatisch aus deiner Minecraft-Konfiguration (`config/minecraft_mcp.json` des jeweiligen Profils). Alternativ kannst du `--token <dein_token>` oder die Umgebungsvariable `MINECRAFT_MCP_TOKEN` übergeben.

---

## 🛠️ Verfügbare MCP Tools für Agenten

* `get_player_state`: Liefert Koordinaten (X, Y, Z, Yaw, Pitch), Leben, Hunger, Rüstung, Inventar und aktive Statuseffekte.
* `raycast_target`: Erkennt den Block oder die Entity im Fadenkreuz inklusive BlockState-Properties (z. B. `lit`, `facing`, `powered`).
* `scan_blocks`: Scannt Blöcke in einem wählbaren Radius (mit Keyword-Filtern für Erze, Kisten, Maschinen).
* `inspect_nbt`: Liest detaillierte NBT-Daten von TileEntities oder Items in der Hand aus (ideal für Tech-Mods).
* `execute_command`: Führt Minecraft-Befehle aus (`/give`, `/tp`, `/gamemode`).
* `send_chat`: Schreibt Antworten in das Minecraft-Chat-HUD des Spielers.
* `get_latest_logs`: Liest Log4j-Einträge aus, um Warnings und Exceptions direkt zu diagnostizieren.

---

## 🔨 Bauen aus den Quellen

```powershell
# Alle Versionen (1.21.1 und 1.21.4) bauen und nach builds/ kopieren:
.\gradlew copyJars

# Nur Version 1.21.1 bauen:
.\gradlew build1211

# Nur Version 1.21.4 bauen:
.\gradlew build1214
```
