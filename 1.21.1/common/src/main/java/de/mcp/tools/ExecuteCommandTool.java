package de.mcp.tools;

import com.google.gson.JsonObject;
import de.mcp.api.McpTool;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.CompletableFuture;

public class ExecuteCommandTool implements McpTool {

    @Override
    public String getName() {
        return "execute_command";
    }

    @Override
    public String getDescription() {
        return "Führt einen Minecraft-Befehl als Client-Spieler aus (z. B. /gamemode, /tp, /give, /time).";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();

        JsonObject cmdProp = new JsonObject();
        cmdProp.addProperty("type", "string");
        cmdProp.addProperty("description", "Der auszuführende Befehl (mit oder ohne führenden Slash).");
        properties.add("command", cmdProp);

        schema.add("properties", properties);
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) throws Exception {
        if (!arguments.has("command")) {
            JsonObject err = new JsonObject();
            err.addProperty("error", "Parameter 'command' is required.");
            return err;
        }

        String rawCmd = arguments.get("command").getAsString().trim();
        String cmd = rawCmd.startsWith("/") ? rawCmd.substring(1) : rawCmd;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            JsonObject err = new JsonObject();
            err.addProperty("error", "Player not connected to a world.");
            return err;
        }

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        client.execute(() -> {
            try {
                client.player.networkHandler.sendCommand(cmd);
                JsonObject res = new JsonObject();
                res.addProperty("success", true);
                res.addProperty("executed_command", "/" + cmd);
                future.complete(res);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future.get();
    }
}
