package de.mcp.fabric;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import de.mcp.MinecraftMcpMod;
import de.mcp.chat.InGameChatManager;
import de.mcp.config.McpConfig;
import de.mcp.input.InputSimulationManager;
import de.mcp.log.InGameLogSniffer;
import de.mcp.server.McpNettyServer;
import de.mcp.server.SseConnectionManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.util.List;

public class MinecraftMcpFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Initialize common mod logic
        MinecraftMcpMod.initClient(FabricLoader.getInstance().getConfigDir());

        // Register Fabric client tick for physical input simulation
        ClientTickEvents.START_CLIENT_TICK.register(InputSimulationManager::onClientTick);

        // Register client-side commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // /ai <prompt>
            dispatcher.register(ClientCommandManager.literal("ai")
                .then(ClientCommandManager.argument("prompt", StringArgumentType.greedyString())
                    .executes(context -> {
                        String prompt = StringArgumentType.getString(context, "prompt");
                        InGameChatManager.handleAiPrompt(prompt);
                        return 1;
                    })
                )
            );

            // /mcp status | token | log
            dispatcher.register(ClientCommandManager.literal("mcp")
                .then(ClientCommandManager.literal("status")
                    .executes(context -> {
                        InGameChatManager.sendFeedback("§b=== [Minecraft MCP Server Status] ===");
                        InGameChatManager.sendFeedback("§7Status: " + (McpNettyServer.isRunning() ? "§aOnline (Port " + McpConfig.getInstance().serverPort + ")§r" : "§cOffline§r"));
                        InGameChatManager.sendFeedback("§7Verbundene Agenten (SSE): §e" + SseConnectionManager.getActiveClientCount());
                        InGameChatManager.sendFeedback("§7RPC Endpunkt: §bhttp://127.0.0.1:" + McpConfig.getInstance().serverPort + "/mcp/rpc");
                        return 1;
                    })
                )
                .then(ClientCommandManager.literal("token")
                    .executes(context -> {
                        InGameChatManager.sendFeedback("§b[Minecraft-MCP] Auth Token: §6" + McpConfig.getInstance().authToken);
                        return 1;
                    })
                )
                .then(ClientCommandManager.literal("log")
                    .executes(context -> {
                        printLogs(10);
                        return 1;
                    })
                    .then(ClientCommandManager.argument("lines", IntegerArgumentType.integer(1, 50))
                        .executes(context -> {
                            int lines = IntegerArgumentType.getInteger(context, "lines");
                            printLogs(lines);
                            return 1;
                        })
                    )
                )
            );
        });
    }

    private void printLogs(int lines) {
        List<InGameLogSniffer.LogEntry> logs = InGameLogSniffer.getRecentLogs(lines, "INFO");
        InGameChatManager.sendFeedback("§6=== [Letzte " + logs.size() + " Log-Einträge] ===");
        for (InGameLogSniffer.LogEntry entry : logs) {
            String color = "§7";
            if ("ERROR".equals(entry.level)) color = "§c";
            else if ("WARN".equals(entry.level)) color = "§e";
            InGameChatManager.sendFeedback(color + "[" + entry.level + "] " + entry.message);
        }
    }
}
