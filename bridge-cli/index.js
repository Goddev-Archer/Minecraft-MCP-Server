#!/usr/bin/env node

const http = require('http');
const readline = require('readline');
const fs = require('fs');
const path = require('path');
const os = require('os');

// Parse arguments & environment
let port = 25585;
let token = process.env.MINECRAFT_MCP_TOKEN || null;

for (let i = 2; i < process.argv.length; i++) {
  if (process.argv[i] === '--port' && process.argv[i + 1]) {
    port = parseInt(process.argv[++i], 10);
  } else if (process.argv[i] === '--token' && process.argv[i + 1]) {
    token = process.argv[++i];
  }
}

// Auto-discover token from standard .minecraft / workspace configs / mod launchers
function discoverToken() {
  const possiblePaths = [
    path.join(__dirname, '..', '1.21.1', 'run', 'config', 'minecraft_mcp.json'),
    path.join(__dirname, '..', '1.21.4', 'run', 'config', 'minecraft_mcp.json'),
    path.join(__dirname, '..', 'run', 'config', 'minecraft_mcp.json'),
    path.join(__dirname, '..', 'config', 'minecraft_mcp.json'),
    path.join(process.cwd(), 'run', 'config', 'minecraft_mcp.json'),
    path.join(process.cwd(), 'config', 'minecraft_mcp.json'),
    process.platform === 'win32'
      ? path.join(os.homedir(), 'AppData', 'Roaming', '.minecraft', 'config', 'minecraft_mcp.json')
      : path.join(os.homedir(), '.minecraft', 'config', 'minecraft_mcp.json')
  ];

  // Also scan launcher profiles (Modrinth, Prism, CurseForge)
  if (process.platform === 'win32') {
    const launcherDirs = [
      path.join(os.homedir(), 'AppData', 'Roaming', 'ModrinthApp', 'profiles'),
      path.join(os.homedir(), 'AppData', 'Roaming', 'PrismLauncher', 'instances'),
      path.join(os.homedir(), 'curseforge', 'minecraft', 'Instances')
    ];
    for (const ld of launcherDirs) {
      if (fs.existsSync(ld)) {
        try {
          const profiles = fs.readdirSync(ld);
          for (const prof of profiles) {
            possiblePaths.push(path.join(ld, prof, 'config', 'minecraft_mcp.json'));
            possiblePaths.push(path.join(ld, prof, '.minecraft', 'config', 'minecraft_mcp.json'));
          }
        } catch (ignored) {}
      }
    }
  }

  for (const p of possiblePaths) {
    if (fs.existsSync(p)) {
      try {
        const config = JSON.parse(fs.readFileSync(p, 'utf8'));
        if (config.authToken) {
          token = config.authToken;
          if (config.serverPort) port = config.serverPort;
          return token;
        }
      } catch (ignored) {}
    }
  }
  return null;
}

if (!token) {
  discoverToken();
}

// Built-in tool definitions for offline capability discovery
const FALLBACK_TOOLS = [
  {
    name: "get_player_state",
    description: "Liefert den aktuellen Zustand des Spielers (Leben, Hunger, Position, Dimension, Blickrichtung, Inventar, Effekte).",
    inputSchema: {
      type: "object",
      properties: {}
    }
  },
  {
    name: "raycast_target",
    description: "Ermittelt das Ziel (Block oder Entity), das der Spieler aktuell im Fadenkreuz anvisiert, inklusive BlockState-Properties.",
    inputSchema: {
      type: "object",
      properties: {}
    }
  },
  {
    name: "scan_blocks",
    description: "Scannt die Blöcke in einem Radius um den Spieler. Nützlich zur Orientierung, Suche nach Erzen, Maschinen oder Kisten.",
    inputSchema: {
      type: "object",
      properties: {
        radius: {
          type: "integer",
          description: "Radius in Blöcken (Standard: 5, Max: 12)."
        },
        filter: {
          type: "array",
          description: "Optionale Filter-Begriffe (z. B. [\"ore\", \"chest\", \"machine\"]).",
          items: { type: "string" }
        }
      }
    }
  },
  {
    name: "execute_command",
    description: "Führt einen Minecraft-Befehl als Client-Spieler aus (z. B. /gamemode, /tp, /give, /time).",
    inputSchema: {
      type: "object",
      properties: {
        command: {
          type: "string",
          description: "Der auszuführende Befehl (mit oder ohne führenden Slash)."
        }
      },
      required: ["command"]
    }
  },
  {
    name: "send_chat",
    description: "Sendet eine Chat-Nachricht in das Spiel. Kann entweder lokal als KI-Antwort im HUD angezeigt oder als öffentlicher Chat gesendet werden.",
    inputSchema: {
      type: "object",
      properties: {
        message: {
          type: "string",
          description: "Der zu sendende Text."
        },
        target: {
          type: "string",
          enum: ["client_hud", "server_chat"],
          description: "'client_hud' für lokale Anzeige (Standard), 'server_chat' für öffentlichen Chat."
        }
      },
      required: ["message"]
    }
  },
  {
    name: "get_latest_logs",
    description: "Liest die neuesten Minecraft- und Mod-Logeinträge aus dem Speicher (besonders nützlich zum Finden von Exceptions, Fehlern und Warnungen).",
    inputSchema: {
      type: "object",
      properties: {
        limit: {
          type: "integer",
          description: "Maximale Anzahl an Einträgen (Standard: 30, Max: 100)."
        },
        min_level: {
          type: "string",
          enum: ["INFO", "WARN", "ERROR"],
          description: "Mindest-Loglevel (Standard: INFO)."
        }
      }
    }
  },
  {
    name: "inspect_nbt",
    description: "Liest NBT- und Komponentendaten eines anvisierten Blocks, einer Entity oder des Items in der Hand aus. Ideal zum Debuggen komplexer Mod-Maschinen.",
    inputSchema: {
      type: "object",
      properties: {
        target: {
          type: "string",
          enum: ["target_block", "main_hand"],
          description: "Ziel der Inspektion (target_block oder main_hand)."
        }
      }
    }
  },
  {
    name: "simulate_input",
    description: "Simuliert echte physische Tastendrücke (Vorwärts, Rückwärts, Seitwärts, Springen, Schleichen, Angreifen, Benutzen). Nutzt die native Minecraft-Physik, verhindert Glitches durch Blöcke und ist zu 100% kompatibel mit Mods und Anti-Cheat.",
    inputSchema: {
      type: "object",
      properties: {
        action: {
          type: "string",
          description: "Aktion: 'forward', 'back', 'left', 'right', 'jump', 'sneak', 'sprint', 'attack', 'use' oder 'release_all'"
        },
        duration_ticks: {
          type: "integer",
          description: "Dauer in Minecraft-Ticks (20 Ticks = 1 Sekunde). Standard: 10 Ticks."
        },
        yaw: {
          type: "number",
          description: "Optionaler Blickwinkel Yaw (horizontale Drehung), wohin der Spieler vor der Bewegung schauen soll."
        },
        pitch: {
          type: "number",
          description: "Optionaler Blickwinkel Pitch (Neigung -90 bis 90)."
        }
      }
    }
  },
  {
    name: "take_screenshot",
    description: "Nimmt einen Screenshot der aktuellen Spielansicht auf. Kann optional vor dem Bild den Blickwinkel (yaw, pitch) anpassen und liefert den Dateipfad sowie Bildmaße zurück.",
    inputSchema: {
      type: "object",
      properties: {
        filename: {
          type: "string",
          description: "Optionaler Dateiname (Standard: mcp_screenshot_TIMESTAMP.png)."
        },
        yaw: {
          type: "number",
          description: "Optionaler Blickwinkel Yaw (horizontale Drehung), wohin der Spieler vor der Aufnahme schauen soll."
        },
        pitch: {
          type: "number",
          description: "Optionaler Blickwinkel Pitch (Neigung -90 bis 90)."
        },
        include_base64: {
          type: "boolean",
          description: "Ob der Base64-String des Bildes im JSON zurückgegeben werden soll (Standard: false)."
        }
      }
    }
  }
];

const FALLBACK_RESOURCES = [
  {
    uri: "minecraft://logs/recent",
    name: "Recent Minecraft Logs",
    mimeType: "text/plain"
  },
  {
    uri: "minecraft://player/state",
    name: "Current Player State",
    mimeType: "application/json"
  }
];

// Handle JSON-RPC requests
function sendRpc(requestJson) {
  let parsed = null;
  try {
    parsed = JSON.parse(requestJson);
  } catch (err) {
    process.stdout.write(JSON.stringify({
      jsonrpc: '2.0',
      id: null,
      error: { code: -32700, message: 'Parse error: ' + err.message }
    }) + '\n');
    return;
  }

  // Notifications do not expect a response
  if (parsed.method === 'notifications/initialized') {
    return;
  }

  if (!token) {
    discoverToken();
  }

  const reqId = parsed.id !== undefined ? parsed.id : null;

  const options = {
    hostname: '127.0.0.1',
    port: port,
    path: '/mcp/rpc',
    method: 'POST',
    agent: false,
    headers: {
      'Content-Type': 'application/json',
      'Content-Length': Buffer.byteLength(requestJson),
      'Connection': 'close',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {})
    },
    timeout: 3000
  };

  const req = http.request(options, (res) => {
    let data = '';
    res.on('data', (chunk) => { data += chunk; });
    res.on('end', () => {
      if (res.statusCode >= 200 && res.statusCode < 300) {
        if (data.trim().length > 0) {
          process.stdout.write(data + '\n');
        }
      } else {
        const errObj = {
          jsonrpc: '2.0',
          id: reqId,
          error: {
            code: -32603,
            message: `Minecraft MCP HTTP Error ${res.statusCode}: ${data}`
          }
        };
        process.stdout.write(JSON.stringify(errObj) + '\n');
      }
    });
  });

  req.on('timeout', () => {
    req.destroy(new Error('Connection timed out'));
  });

  req.on('error', (err) => {
    // Provide graceful fallbacks when Minecraft is not currently running
    if (parsed.method === 'initialize') {
      const initRes = {
        jsonrpc: '2.0',
        id: reqId,
        result: {
          protocolVersion: '2024-11-05',
          capabilities: {
            tools: {},
            resources: {}
          },
          serverInfo: {
            name: 'minecraft-mcp-server',
            version: '1.0.0'
          }
        }
      };
      process.stdout.write(JSON.stringify(initRes) + '\n');
      return;
    }

    if (parsed.method === 'tools/list') {
      const toolsRes = {
        jsonrpc: '2.0',
        id: reqId,
        result: {
          tools: FALLBACK_TOOLS
        }
      };
      process.stdout.write(JSON.stringify(toolsRes) + '\n');
      return;
    }

    if (parsed.method === 'resources/list') {
      const resRes = {
        jsonrpc: '2.0',
        id: reqId,
        result: {
          resources: FALLBACK_RESOURCES
        }
      };
      process.stdout.write(JSON.stringify(resRes) + '\n');
      return;
    }

    if (parsed.method === 'ping') {
      const pingRes = {
        jsonrpc: '2.0',
        id: reqId,
        result: {}
      };
      process.stdout.write(JSON.stringify(pingRes) + '\n');
      return;
    }

    if (parsed.method === 'tools/call') {
      const toolErrRes = {
        jsonrpc: '2.0',
        id: reqId,
        result: {
          content: [
            {
              type: 'text',
              text: `[Minecraft-MCP] Minecraft ist aktuell noch nicht gestartet oder die Mod antwortet nicht auf Port ${port}. Bitte starte Minecraft mit der Mod (z. B. via launch-test-client.bat oder im Modpack), um diesen Befehl auszuführen.`
            }
          ],
          isError: true
        }
      };
      process.stdout.write(JSON.stringify(toolErrRes) + '\n');
      return;
    }

    const errObj = {
      jsonrpc: '2.0',
      id: reqId,
      error: {
        code: -32603,
        message: `Could not connect to Minecraft MCP Server on port ${port}. Is Minecraft running with the mod loaded? (${err.message})`
      }
    };
    process.stdout.write(JSON.stringify(errObj) + '\n');
  });

  req.write(requestJson);
  req.end();
}

// Connect to SSE stream for live events and in-game prompts
function connectSse() {
  if (!token) {
    discoverToken();
  }

  const options = {
    hostname: '127.0.0.1',
    port: port,
    path: '/mcp/sse',
    method: 'GET',
    headers: {
      'Accept': 'text/event-stream',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {})
    }
  };

  const sseReq = http.request(options, (res) => {
    res.on('data', (chunk) => {
      const str = chunk.toString();
      if (str.includes('event: client_chat_prompt')) {
        process.stderr.write(`[Minecraft-MCP-Bridge] Incoming in-game AI prompt from player!\n`);
      }
    });
    res.on('end', () => {
      setTimeout(connectSse, 3000);
    });
  });

  sseReq.on('error', () => {
    setTimeout(connectSse, 5000);
  });

  sseReq.end();
}

// Stdio Reader Loop
const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
  terminal: false
});

rl.on('line', (line) => {
  const trimmed = line.trim();
  if (trimmed.length > 0) {
    sendRpc(trimmed);
  }
});

rl.on('close', () => {
  process.exit(0);
});

// Start background SSE listener
connectSse();
