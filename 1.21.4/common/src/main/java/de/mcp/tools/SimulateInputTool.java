package de.mcp.tools;

import com.google.gson.JsonObject;
import de.mcp.api.McpTool;
import de.mcp.input.InputSimulationManager;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.CompletableFuture;

public class SimulateInputTool implements McpTool {

    @Override
    public String getName() {
        return "simulate_input";
    }

    @Override
    public String getDescription() {
        return "Simuliert echte physische Tastendrücke (Vorwärts, Rückwärts, Seitwärts, Springen, Schleichen, Angreifen, Benutzen). Nutzt die native Minecraft-Physik, verhindert Glitches durch Blöcke und ist zu 100% kompatibel mit Mods und Anti-Cheat.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();

        JsonObject actionProp = new JsonObject();
        actionProp.addProperty("type", "string");
        actionProp.addProperty("description", "Aktion: 'forward', 'back', 'left', 'right', 'jump', 'sneak', 'sprint', 'attack', 'use' oder 'release_all'");
        properties.add("action", actionProp);

        JsonObject durationProp = new JsonObject();
        durationProp.addProperty("type", "integer");
        durationProp.addProperty("description", "Dauer in Minecraft-Ticks (20 Ticks = 1 Sekunde). Standard: 10 Ticks.");
        properties.add("duration_ticks", durationProp);

        JsonObject yawProp = new JsonObject();
        yawProp.addProperty("type", "number");
        yawProp.addProperty("description", "Optionaler Blickwinkel Yaw (horizontale Drehung), wohin der Spieler vor der Bewegung schauen soll.");
        properties.add("yaw", yawProp);

        JsonObject pitchProp = new JsonObject();
        pitchProp.addProperty("type", "number");
        pitchProp.addProperty("description", "Optionaler Blickwinkel Pitch (Neigung -90 bis 90).");
        properties.add("pitch", pitchProp);

        schema.add("properties", properties);
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) throws Exception {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            JsonObject err = new JsonObject();
            err.addProperty("error", "Player not connected to a world.");
            return err;
        }

        String action = arguments.has("action") ? arguments.get("action").getAsString() : "forward";
        int durationTicks = arguments.has("duration_ticks") ? arguments.get("duration_ticks").getAsInt() : 10;
        if (durationTicks <= 0) durationTicks = 1;
        if (durationTicks > 200) durationTicks = 200; // Max 10 Sekunden

        if ("release_all".equalsIgnoreCase(action)) {
            client.execute(InputSimulationManager::releaseAll);
            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("message", "All simulated keys released.");
            return res;
        }

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        final int finalDuration = durationTicks;
        client.execute(() -> {
            try {
                if (arguments.has("yaw")) {
                    client.player.setYaw(arguments.get("yaw").getAsFloat());
                }
                if (arguments.has("pitch")) {
                    client.player.setPitch(arguments.get("pitch").getAsFloat());
                }

                double startX = client.player.getX();
                double startY = client.player.getY();
                double startZ = client.player.getZ();

                InputSimulationManager.holdKey(action, finalDuration);

                JsonObject res = new JsonObject();
                res.addProperty("success", true);
                res.addProperty("action", action);
                res.addProperty("duration_ticks", finalDuration);
                res.addProperty("duration_seconds", finalDuration / 20.0);

                JsonObject startPos = new JsonObject();
                startPos.addProperty("x", Math.round(startX * 100.0) / 100.0);
                startPos.addProperty("y", Math.round(startY * 100.0) / 100.0);
                startPos.addProperty("z", Math.round(startZ * 100.0) / 100.0);
                res.add("start_position", startPos);

                future.complete(res);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future.get();
    }
}
