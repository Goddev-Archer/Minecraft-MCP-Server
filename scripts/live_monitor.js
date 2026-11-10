const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 25585;
const TOKEN = process.env.MINECRAFT_MCP_TOKEN || '9bc135d8-d639-4f40-9c51-4d37f96baf90';
const LOG_FILE = path.join(__dirname, '..', 'live_activity.log');

let reqCounter = 1;

function callRpc(method, params) {
  return new Promise((resolve, reject) => {
    const payload = JSON.stringify({
      jsonrpc: '2.0',
      id: reqCounter++,
      method,
      params
    });

    const options = {
      hostname: '127.0.0.1',
      port: PORT,
      path: '/mcp/rpc',
      method: 'POST',
      agent: false,
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(payload),
        'Connection': 'close',
        'Authorization': `Bearer ${TOKEN}`
      },
      timeout: 2500
    };

    const req = http.request(options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const json = JSON.parse(data);
          if (json.error) {
            reject(new Error(json.error.message || 'RPC Error'));
          } else {
            resolve(json.result);
          }
        } catch (e) {
          reject(new Error(`Failed to parse response: ${data}`));
        }
      });
    });

    req.on('error', reject);
    req.on('timeout', () => {
      req.destroy();
      reject(new Error('RPC request timed out'));
    });
    req.write(payload);
    req.end();
  });
}

async function callTool(toolName, args = {}) {
  const result = await callRpc('tools/call', { name: toolName, arguments: args });
  if (result && result.content && result.content[0] && result.content[0].text) {
    try {
      return JSON.parse(result.content[0].text);
    } catch {
      return result.content[0].text;
    }
  }
  return result;
}

function stripColors(str) {
  if (typeof str !== 'string') return '';
  return str.replace(/§[0-9a-fk-or]/gi, '').trim();
}

function logActivity(text) {
  const time = new Date().toLocaleTimeString('de-DE');
  const line = `[${time}] ${text}`;
  console.log(line);
  try {
    fs.appendFileSync(LOG_FILE, line + '\n', 'utf8');
  } catch (err) {
    // ignore file write error
  }
}

async function main() {
  logActivity('Live-Überwachung initialisiert. Verbinde mit Minecraft MCP Server...');

  // Reset/touch log file
  fs.writeFileSync(LOG_FILE, `=== Minecraft Live-Überwachung gestartet am ${new Date().toLocaleString('de-DE')} ===\n`, 'utf8');

  // Announce in-game HUD
  try {
    await callTool('send_chat', {
      message: '§b[AI-Assistant]§r §aLive-Ueberwachung gestartet!§r §7(Schreibe §e"stop"§7 oder §e"abbruch"§7 in den Chat zum Beenden)§r',
      target: 'client_hud'
    });
  } catch (e) {
    console.error('Konnte HUD-Nachricht nicht senden:', e.message);
  }

  // Get initial state
  let lastLogTimestamp = Date.now();
  try {
    const logsData = await callTool('get_latest_logs', { limit: 10 });
    if (logsData && Array.isArray(logsData.logs) && logsData.logs.length > 0) {
      lastLogTimestamp = Math.max(...logsData.logs.map(l => l.timestamp || 0));
    }
  } catch (e) {
    console.error('Warnung beim Abrufen initialer Logs:', e.message);
  }

  let lastState = null;
  let lastTargetDesc = '';

  try {
    lastState = await callTool('get_player_state');
    if (lastState && lastState.position) {
      logActivity(`Spieler gefunden: ${lastState.name || 'Unbekannt'} an Position (${lastState.position.x.toFixed(1)}, ${lastState.position.y.toFixed(1)}, ${lastState.position.z.toFixed(1)})`);
    }
  } catch (e) {
    console.error('Warnung beim Abrufen des Spieler-Status:', e.message);
  }

  let isStopping = false;

  async function handleAiPromptInGame(userPrompt) {
    logActivity(`[AI-ANFRAGE] Verarbeite In-Game Frage: "${userPrompt}"`);
    const promptLower = userPrompt.toLowerCase().trim();

    try {
      // 1. Hand / Ausrüstung
      if (/hand|item|halte|waffen|ausr/i.test(promptLower)) {
        const state = await callTool('get_player_state');
        const mainHand = (typeof state.main_hand === 'object' ? state.main_hand.item : state.main_hand) || 'empty';
        const offHand = (typeof state.off_hand === 'object' ? state.off_hand.item : state.off_hand) || 'empty';
        let reply = `Du haeltst in der Haupthand: §e${mainHand}§r`;
        if (offHand && offHand !== 'empty') reply += ` und in der Offhand: §e${offHand}§r`;
        await callTool('send_chat', { message: `§b[AI-Assistant]§r ${reply}`, target: 'client_hud' });
        logActivity(`[AI-ANTWORT] -> Hand: ${mainHand}`);
        return;
      }

      // 2. Ziel / Block / Raycast
      if (/block|ziel|schaue|seh|fokus|target|was ist das/i.test(promptLower)) {
        const target = await callTool('raycast_target');
        if (target && target.hit) {
          if (target.type === 'block') {
            const reply = `Du schaust auf: §e${target.block_id}§r bei (§6${target.position?.x}, ${target.position?.y}, ${target.position?.z}§r) Seite: §b${target.side_hit}§r`;
            await callTool('send_chat', { message: `§b[AI-Assistant]§r ${reply}`, target: 'client_hud' });
            logActivity(`[AI-ANTWORT] -> Block: ${target.block_id}`);
          } else if (target.type === 'entity') {
            const reply = `Du visierst Entity an: §e${target.entity_name || target.entity_type}§r (Distanz: ${target.distance?.toFixed(1)}m)`;
            await callTool('send_chat', { message: `§b[AI-Assistant]§r ${reply}`, target: 'client_hud' });
            logActivity(`[AI-ANTWORT] -> Entity: ${target.entity_name || target.entity_type}`);
          }
        } else {
          await callTool('send_chat', { message: `§b[AI-Assistant]§r Kein Ziel im Fadenkreuz / ausser Reichweite.`, target: 'client_hud' });
          logActivity(`[AI-ANTWORT] -> Kein Ziel.`);
        }
        return;
      }

      // 3. Position / Koordinaten
      if (/wo bin|pos|koordinaten|ort|dimension/i.test(promptLower)) {
        const state = await callTool('get_player_state');
        const p = state.position || {};
        const reply = `Position: §eX: ${p.x?.toFixed(1)}, Y: ${p.y?.toFixed(1)}, Z: ${p.z?.toFixed(1)}§r (${state.dimension}) | Blick: Pitch ${p.pitch?.toFixed(0)}°, Yaw ${p.yaw?.toFixed(0)}°`;
        await callTool('send_chat', { message: `§b[AI-Assistant]§r ${reply}`, target: 'client_hud' });
        logActivity(`[AI-ANTWORT] -> Position: ${p.x}, ${p.y}, ${p.z}`);
        return;
      }

      // 4. Leben / Status / Effekte
      if (/leben|hp|hunger|effekt|potion|status/i.test(promptLower)) {
        const state = await callTool('get_player_state');
        let effectsStr = (state.effects || []).map(e => e.id.replace('minecraft:', '')).join(', ') || 'keine';
        const reply = `Status: §c${state.health}/20 HP§r | §6Hunger: ${state.food_level}/20§r | Effekte: §d${effectsStr}§r`;
        await callTool('send_chat', { message: `§b[AI-Assistant]§r ${reply}`, target: 'client_hud' });
        logActivity(`[AI-ANTWORT] -> Status: ${state.health} HP`);
        return;
      }

      // 5. Fallback Antwort
      const state = await callTool('get_player_state');
      const p = state?.position || {};
      const reply = `Ich hoere dich! Deine Frage: "§e${userPrompt}§r". Frag mich nach Hand, Block, Position oder Status!`;
      await callTool('send_chat', { message: `§b[AI-Assistant]§r ${reply}`, target: 'client_hud' });
      logActivity(`[AI-ANTWORT] -> Echo Fallback`);
    } catch (err) {
      logActivity(`[AI-FEHLER] Konnte In-Game Frage nicht beantworten: ${err.message}`);
    }
  }

  async function shutdown(reason) {
    if (isStopping) return;
    isStopping = true;
    logActivity(`Beende Live-Überwachung: ${reason}`);
    try {
      await callTool('send_chat', {
        message: '§b[AI-Assistant]§r §cLive-Ueberwachung beendet!§r §7(' + reason + ')§r',
        target: 'client_hud'
      });
    } catch {}
    logActivity('Task erfolgreich beendet.');
    process.exit(0);
  }

  process.on('SIGINT', () => shutdown('Manuell unterbrochen (SIGINT)'));
  process.on('SIGTERM', () => shutdown('Manuell unterbrochen (SIGTERM)'));

  // Main polling loop
  const TICK_INTERVAL = 1200; // 1.2s
  let isTickRunning = false;

  const intervalId = setInterval(async () => {
    if (isTickRunning || isStopping) return;
    isTickRunning = true;

    try {
      // 1. Check Logs & Chat
      const logsResult = await callTool('get_latest_logs', { limit: 15 });
      if (logsResult && Array.isArray(logsResult.logs)) {
        const newLogs = logsResult.logs.filter(l => l.timestamp > lastLogTimestamp);
        for (const log of newLogs) {
          if (log.timestamp > lastLogTimestamp) {
            lastLogTimestamp = log.timestamp;
          }

          const rawMsg = log.message || '';
          const cleaned = stripColors(rawMsg);

          // Check for /ai prompt from InGameChatManager
          if (cleaned.includes('Frage empfangen:')) {
            const match = cleaned.match(/Frage empfangen:\s*"(.*?)"/);
            if (match && match[1]) {
              const promptText = match[1];
              logActivity(`[CHAT PROMPT] Eingabe erkannt: "${promptText}"`);

              // Check if it's a stop command via /ai
              if (/\b(stop|abbruch|beenden|cancel|exit|!stop)\b/i.test(promptText)) {
                clearInterval(intervalId);
                await shutdown(`Stop via /ai empfangen: "${promptText}"`);
                return;
              }

              // Automatically answer in-game!
              await handleAiPromptInGame(promptText);
              continue;
            }
          }

          // Check if this is a chat message
          if (cleaned.includes('[CHAT]') || log.logger?.includes('class_338')) {
            logActivity(`[CHAT] ${cleaned}`);

            // Check for stop keywords in general chat
            const isStopMatch = /\b(stop|abbruch|beenden|cancel|exit|!stop)\b/i.test(cleaned);
            const isFromUser = cleaned.includes('G0dpr1m3_TV') || !cleaned.includes(':');

            if (isStopMatch && isFromUser) {
              clearInterval(intervalId);
              await shutdown(`Chat-Befehl von Spieler erkannt: "${cleaned}"`);
              return;
            }
          }
        }
      }

      // 2. Check Player State
      const state = await callTool('get_player_state');
      if (state && state.position) {
        if (!lastState) {
          lastState = state;
        } else {
          // Movement check (> 1.2 Blocks)
          const dist = Math.hypot(
            state.position.x - lastState.position.x,
            state.position.z - lastState.position.z,
            state.position.y - lastState.position.y
          );

          if (dist > 1.2) {
            logActivity(`[BEWEGUNG] Distanz: ${dist.toFixed(1)}m -> (${state.position.x.toFixed(1)}, ${state.position.y.toFixed(1)}, ${state.position.z.toFixed(1)}) | Blick: Pitch ${state.position.pitch?.toFixed(0)}°, Yaw ${state.position.yaw?.toFixed(0)}°`);
            lastState.position = state.position;
          }

          // Main hand / offhand check
          const mainHand = (typeof state.main_hand === 'object' ? state.main_hand.item : state.main_hand) || 'empty';
          const lastMainHand = (typeof lastState.main_hand === 'object' ? lastState.main_hand.item : lastState.main_hand) || 'empty';
          if (mainHand !== lastMainHand) {
            logActivity(`[ITEM] Haupt-Hand gewechselt zu: "${mainHand}" (vorher: "${lastMainHand}")`);
            lastState.main_hand = state.main_hand;
          }

          // Health / food check
          if (state.health !== lastState.health || state.food_level !== lastState.food_level) {
            logActivity(`[STATUS] Leben: ${state.health}/20 | Hunger: ${state.food_level}/20`);
            lastState.health = state.health;
            lastState.food_level = state.food_level;
          }
        }
      }

      // 3. Check Target (Raycast)
      try {
        const target = await callTool('raycast_target');
        let currentTargetDesc = 'Nichts';
        if (target && target.hit) {
          if (target.type === 'block') {
            currentTargetDesc = `Block: ${target.block_id} bei (${target.position?.x}, ${target.position?.y}, ${target.position?.z}) Seite: ${target.side_hit}`;
          } else if (target.type === 'entity') {
            currentTargetDesc = `Entity: ${target.entity_name || target.entity_type} (Distanz: ${target.distance?.toFixed(1)}m)`;
          }
        }

        if (currentTargetDesc !== lastTargetDesc && currentTargetDesc !== 'Nichts') {
          logActivity(`[FOKUS] Spieler visiert an: ${currentTargetDesc}`);
          lastTargetDesc = currentTargetDesc;
        }
      } catch (e) {
        // raycast error can happen if player is in menu/loading
      }

    } catch (err) {
      logActivity(`[FEHLER] Abfrage-Fehler: ${err.message}`);
    } finally {
      isTickRunning = false;
    }
  }, TICK_INTERVAL);
}

main().catch(err => {
  console.error('Fatal error in monitor:', err);
  process.exit(1);
});
