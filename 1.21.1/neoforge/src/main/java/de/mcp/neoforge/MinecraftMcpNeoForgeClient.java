package de.mcp.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import de.mcp.MinecraftMcpMod;
import de.mcp.chat.InGameChatManager;
import de.mcp.config.McpConfig;
import de.mcp.input.InputSimulationManager;
import de.mcp.log.InGameLogSniffer;
import de.mcp.server.McpNettyServer;
import de.mcp.server.SseConnectionManager;
import net.minecraft.client.MinecraftClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

@Mod("minecraft_mcp")
public class MinecraftMcpNeoForgeClient {

    public MinecraftMcpNeoForgeClient(IEventBus modEventBus, Dist dist) {
        if (dist.isClient()) {
            // Initialize common mod logic
            MinecraftMcpMod.initClient(FMLPaths.CONFIGDIR.get());

            // Register client commands
            NeoForge.EVENT_BUS.addListener(this::onRegisterClientCommands);

            // Register client tick for physical input simulation
            NeoForge.EVENT_BUS.addListener(this::onClientTick);
        }
    }

    private void onClientTick(ClientTickEvent.Pre event) {
        InputSimulationManager.onClientTick(MinecraftClient.getInstance());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher dispatcher = (CommandDispatcher) event.getDispatcher();

        // /ai <prompt>
        dispatcher.register(literal("ai")
            .then(argument("prompt", StringArgumentType.greedyString())
                .executes(context -> {
                    String prompt = StringArgumentType.getString(context, "prompt");
                    InGameChatManager.handleAiPrompt(prompt);
                    return 1;
                })
            )
        );

        // /mcp status | token | log
        dispatcher.register(literal("mcp")
            .then(literal("status")
                .executes(context -> {
                    InGameChatManager.sendFeedback("§b=== [Minecraft MCP Server Status] ===");
                    InGameChatManager.sendFeedback("§7Status: " + (McpNettyServer.isRunning() ? "§aOnline (Port " + McpConfig.getInstance().serverPort + ")§r" : "§cOffline§r"));
                    InGameChatManager.sendFeedback("§7Verbundene Agenten (SSE): §e" + SseConnectionManager.getActiveClientCount());
                    InGameChatManager.sendFeedback("§7RPC Endpunkt: §bhttp://127.0.0.1:" + McpConfig.getInstance().serverPort + "/mcp/rpc");
                    return 1;
                })
            )
            .then(literal("token")
                .executes(context -> {
                    InGameChatManager.sendFeedback("§b[Minecraft-MCP] Auth Token: §6" + McpConfig.getInstance().authToken);
                    return 1;
                })
            )
            .then(literal("log")
                .executes(context -> {
                    printLogs(10);
                    return 1;
                })
                .then(argument("lines", IntegerArgumentType.integer(1, 50))
                    .executes(context -> {
                        int lines = IntegerArgumentType.getInteger(context, "lines");
                        printLogs(lines);
                        return 1;
                    })
                )
            )
        );
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
