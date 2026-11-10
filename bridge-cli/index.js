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

// Auto-discover token from standard .minecraft config if not provided
if (!token) {
  const possiblePaths = [
    path.join(process.cwd(), 'run', 'config', 'minecraft_mcp.json'),
    path.join(process.cwd(), 'config', 'minecraft_mcp.json'),
    process.platform === 'win32'
      ? path.join(os.homedir(), 'AppData', 'Roaming', '.minecraft', 'config', 'minecraft_mcp.json')
      : path.join(os.homedir(), '.minecraft', 'config', 'minecraft_mcp.json')
  ];

  for (const p of possiblePaths) {
    if (fs.existsSync(p)) {
      try {
        const config = JSON.parse(fs.readFileSync(p, 'utf8'));
        if (config.authToken) {
          token = config.authToken;
          if (config.serverPort) port = config.serverPort;
          break;
        }
      } catch (ignored) {}
    }
  }
}

// Forward JSON-RPC request from stdin to Minecraft HTTP server
function sendRpc(requestJson) {
  const options = {
    hostname: '127.0.0.1',
    port: port,
    path: '/mcp/rpc',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Content-Length': Buffer.byteLength(requestJson),
      ...(token ? { 'Authorization': `Bearer ${token}` } : {})
    }
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
          error: {
            code: -32603,
            message: `Minecraft MCP HTTP Error ${res.statusCode}: ${data}`
          }
        };
        process.stdout.write(JSON.stringify(errObj) + '\n');
      }
    });
  });

  req.on('error', (err) => {
    const errObj = {
      jsonrpc: '2.0',
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
      // Broadcast events can be logged or processed by agent
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
    // Retry quietly
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
