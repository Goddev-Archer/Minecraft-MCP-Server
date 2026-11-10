package de.mcp.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.mcp.api.McpToolRegistry;
import de.mcp.config.McpConfig;
import de.mcp.log.InGameLogSniffer;
import de.mcp.server.SseConnectionManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.List;

public class InGameChatManager {

    public static void sendFeedback(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.inGameHud != null) {
            client.execute(() -> {
                client.inGameHud.getChatHud().addMessage(Text.literal(message));
            });
        }
    }

    public static void sendAiMessage(String message) {
        String prefix = McpConfig.getInstance().inGameAiPrefix;
        sendFeedback(prefix + message);
    }

    public static void handleAiPrompt(String userPrompt) {
        sendAiMessage("§7Frage empfangen: \"" + userPrompt + "\"§r");

        MinecraftClient client = MinecraftClient.getInstance();
        JsonObject context = new JsonObject();

        if (client != null && client.player != null) {
            // Include player context
            context.addProperty("player_name", client.player.getName().getString());
            context.addProperty("dimension", client.player.getWorld().getRegistryKey().getValue().toString());
            context.addProperty("x", Math.round(client.player.getX() * 100.0) / 100.0);
            context.addProperty("y", Math.round(client.player.getY() * 100.0) / 100.0);
            context.addProperty("z", Math.round(client.player.getZ() * 100.0) / 100.0);

            // Targeted block/entity context
            try {
                JsonObject target = McpToolRegistry.executeTool("raycast_target", new JsonObject());
                context.add("target", target);
            } catch (Exception ignored) {}

            // Held item
            context.addProperty("main_hand", client.player.getMainHandStack().getItem().toString());
        }

        // Recent error logs
        List<InGameLogSniffer.LogEntry> errors = InGameLogSniffer.getRecentLogs(5, "WARN");
        JsonArray errorArray = new JsonArray();
        for (InGameLogSniffer.LogEntry entry : errors) {
            errorArray.add(entry.toString());
        }
        context.add("recent_warnings_errors", errorArray);

        // Package query event for connected MCP agent
        JsonObject eventPayload = new JsonObject();
        eventPayload.addProperty("prompt", userPrompt);
        eventPayload.add("context", context);
        eventPayload.addProperty("timestamp", System.currentTimeMillis());

        int clientCount = SseConnectionManager.getActiveClientCount();
        if (clientCount > 0) {
            SseConnectionManager.broadcastEvent("client_chat_prompt", eventPayload);
            sendAiMessage("§aAnfrage an " + clientCount + " verbundene(n) KI-Agenten gesendet...§r");
        } else {
            sendAiMessage("§eKein MCP-Agent verbunden.§r");
            sendAiMessage("§7Starte deinen Agenten (z. B. Antigravity / Claude) und verbinde dich mit:§r");
            sendAiMessage("§bhttp://127.0.0.1:" + McpConfig.getInstance().serverPort + "/mcp/rpc§r (Token: §6" + McpConfig.getInstance().authToken + "§r)");
        }
    }
}
