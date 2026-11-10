package de.mcp.tools;

import com.google.gson.JsonObject;
import de.mcp.api.McpTool;
import de.mcp.chat.InGameChatManager;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.CompletableFuture;

public class SendChatTool implements McpTool {

    @Override
    public String getName() {
        return "send_chat";
    }

    @Override
    public String getDescription() {
        return "Sendet eine Chat-Nachricht in das Spiel. Kann entweder lokal als KI-Antwort im HUD angezeigt oder als öffentlicher Chat gesendet werden.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();

        JsonObject msgProp = new JsonObject();
        msgProp.addProperty("type", "string");
        msgProp.addProperty("description", "Der zu sendende Text.");
        properties.add("message", msgProp);

        JsonObject targetProp = new JsonObject();
        targetProp.addProperty("type", "string");
        targetProp.addProperty("enum", "[\"client_hud\", \"server_chat\"]");
        targetProp.addProperty("description", "'client_hud' für lokale Anzeige (Standard), 'server_chat' für öffentlichen Chat.");
        properties.add("target", targetProp);

        schema.add("properties", properties);
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) throws Exception {
        if (!arguments.has("message")) {
            JsonObject err = new JsonObject();
            err.addProperty("error", "Parameter 'message' is required.");
            return err;
        }

        String message = arguments.get("message").getAsString();
        String target = arguments.has("target") ? arguments.get("target").getAsString() : "client_hud";

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            JsonObject err = new JsonObject();
            err.addProperty("error", "Player not connected to a world.");
            return err;
        }

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        client.execute(() -> {
            try {
                if ("server_chat".equalsIgnoreCase(target)) {
                    client.player.networkHandler.sendChatMessage(message);
                } else {
                    InGameChatManager.sendAiMessage(message);
                }
                JsonObject res = new JsonObject();
                res.addProperty("success", true);
                res.addProperty("target", target);
                res.addProperty("message", message);
                future.complete(res);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future.get();
    }
}
