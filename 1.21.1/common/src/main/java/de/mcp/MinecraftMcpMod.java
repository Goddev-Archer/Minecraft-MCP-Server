package de.mcp;

import de.mcp.api.McpToolRegistry;
import de.mcp.config.McpConfig;
import de.mcp.log.InGameLogSniffer;
import de.mcp.server.McpNettyServer;
import de.mcp.tools.ExecuteCommandTool;
import de.mcp.tools.GetLatestLogsTool;
import de.mcp.tools.GetPlayerStateTool;
import de.mcp.tools.InspectNbtTool;
import de.mcp.tools.RaycastTargetTool;
import de.mcp.tools.ScanBlocksTool;
import de.mcp.tools.SendChatTool;
import de.mcp.tools.SimulateInputTool;
import de.mcp.tools.TakeScreenshotTool;

import java.nio.file.Path;

public class MinecraftMcpMod {
    public static final String MOD_ID = "minecraft_mcp";

    public static void initClient(Path configDir) {
        System.out.println("[Minecraft-MCP] Initializing Client MCP Server...");

        // 1. Config
        McpConfig.load(configDir);

        // 2. Log Sniffer
        InGameLogSniffer.initialize();

        // 3. Register MCP Tools
        McpToolRegistry.registerTool(new GetPlayerStateTool());
        McpToolRegistry.registerTool(new RaycastTargetTool());
        McpToolRegistry.registerTool(new ScanBlocksTool());
        McpToolRegistry.registerTool(new ExecuteCommandTool());
        McpToolRegistry.registerTool(new SendChatTool());
        McpToolRegistry.registerTool(new GetLatestLogsTool());
        McpToolRegistry.registerTool(new InspectNbtTool());
        McpToolRegistry.registerTool(new SimulateInputTool());
        McpToolRegistry.registerTool(new TakeScreenshotTool());

        // 4. Start Netty Server
        McpNettyServer.start(McpConfig.getInstance().serverPort);

        // 5. Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(McpNettyServer::stop, "Minecraft-MCP-Shutdown"));
    }
}
