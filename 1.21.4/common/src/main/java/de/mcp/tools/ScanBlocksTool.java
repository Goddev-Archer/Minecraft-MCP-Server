package de.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.mcp.api.McpTool;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ScanBlocksTool implements McpTool {

    @Override
    public String getName() {
        return "scan_blocks";
    }

    @Override
    public String getDescription() {
        return "Scannt die Blöcke in einem Radius um den Spieler. Nützlich zur Orientierung, Suche nach Erzen, Maschinen oder Kisten.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();

        JsonObject radiusProp = new JsonObject();
        radiusProp.addProperty("type", "integer");
        radiusProp.addProperty("description", "Radius in Blöcken (Standard: 5, Max: 12).");
        properties.add("radius", radiusProp);

        JsonObject filterProp = new JsonObject();
        filterProp.addProperty("type", "array");
        filterProp.addProperty("description", "Optionale Filter-Begriffe (z. B. [\"ore\", \"chest\", \"machine\"]).");
        properties.add("filter", filterProp);

        schema.add("properties", properties);
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) throws Exception {
        int radius = arguments.has("radius") ? Math.min(12, Math.max(1, arguments.get("radius").getAsInt())) : 5;
        Set<String> filters = new HashSet<>();
        if (arguments.has("filter") && arguments.get("filter").isJsonArray()) {
            for (var elem : arguments.getAsJsonArray("filter")) {
                filters.add(elem.getAsString().toLowerCase());
            }
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            JsonObject err = new JsonObject();
            err.addProperty("error", "Player or world not available.");
            return err;
        }

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        client.execute(() -> {
            try {
                BlockPos center = client.player.getBlockPos();
                JsonArray blocks = new JsonArray();
                int maxResults = 300;

                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            BlockPos pos = center.add(dx, dy, dz);
                            BlockState state = client.world.getBlockState(pos);
                            if (state.isAir()) continue;

                            String blockId = Registries.BLOCK.getId(state.getBlock()).toString();

                            if (!filters.isEmpty()) {
                                boolean matches = false;
                                for (String f : filters) {
                                    if (blockId.toLowerCase().contains(f)) {
                                        matches = true;
                                        break;
                                    }
                                }
                                if (!matches) continue;
                            }

                            JsonObject blockObj = new JsonObject();
                            blockObj.addProperty("block_id", blockId);
                            blockObj.addProperty("x", pos.getX());
                            blockObj.addProperty("y", pos.getY());
                            blockObj.addProperty("z", pos.getZ());
                            blocks.add(blockObj);

                            if (blocks.size() >= maxResults) break;
                        }
                        if (blocks.size() >= maxResults) break;
                    }
                    if (blocks.size() >= maxResults) break;
                }

                JsonObject res = new JsonObject();
                res.add("blocks", blocks);
                res.addProperty("total_found", blocks.size());
                res.addProperty("center_x", center.getX());
                res.addProperty("center_y", center.getY());
                res.addProperty("center_z", center.getZ());
                future.complete(res);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future.get();
    }
}
