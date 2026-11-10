package de.mcp.tools;

import com.google.gson.JsonObject;
import de.mcp.api.McpTool;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class RaycastTargetTool implements McpTool {

    @Override
    public String getName() {
        return "raycast_target";
    }

    @Override
    public String getDescription() {
        return "Ermittelt das Ziel (Block oder Entity), das der Spieler aktuell im Fadenkreuz anvisiert, inklusive BlockState-Properties.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) throws Exception {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            JsonObject err = new JsonObject();
            err.addProperty("error", "Player or world not available.");
            return err;
        }

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        client.execute(() -> {
            try {
                HitResult hit = client.crosshairTarget;
                JsonObject result = new JsonObject();

                if (hit == null || hit.getType() == HitResult.Type.MISS) {
                    result.addProperty("hit", false);
                    result.addProperty("type", "miss");
                    future.complete(result);
                    return;
                }

                result.addProperty("hit", true);

                if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult) {
                    BlockHitResult blockHit = (BlockHitResult) hit;
                    BlockPos pos = blockHit.getBlockPos();
                    BlockState state = client.world.getBlockState(pos);

                    result.addProperty("type", "block");
                    result.addProperty("block_id", Registries.BLOCK.getId(state.getBlock()).toString());

                    JsonObject posObj = new JsonObject();
                    posObj.addProperty("x", pos.getX());
                    posObj.addProperty("y", pos.getY());
                    posObj.addProperty("z", pos.getZ());
                    result.add("position", posObj);

                    result.addProperty("side_hit", blockHit.getSide().asString());

                    // BlockState properties
                    JsonObject props = new JsonObject();
                    for (Map.Entry<Property<?>, Comparable<?>> entry : state.getEntries().entrySet()) {
                        props.addProperty(entry.getKey().getName(), entry.getValue().toString());
                    }
                    result.add("properties", props);

                } else if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult) {
                    EntityHitResult entityHit = (EntityHitResult) hit;
                    Entity entity = entityHit.getEntity();

                    result.addProperty("type", "entity");
                    result.addProperty("entity_id", Registries.ENTITY_TYPE.getId(entity.getType()).toString());
                    result.addProperty("name", entity.getName().getString());
                    result.addProperty("uuid", entity.getUuidAsString());

                    JsonObject posObj = new JsonObject();
                    posObj.addProperty("x", Math.round(entity.getX() * 100.0) / 100.0);
                    posObj.addProperty("y", Math.round(entity.getY() * 100.0) / 100.0);
                    posObj.addProperty("z", Math.round(entity.getZ() * 100.0) / 100.0);
                    result.add("position", posObj);

                    if (entity instanceof LivingEntity) {
                        LivingEntity living = (LivingEntity) entity;
                        result.addProperty("health", living.getHealth());
                        result.addProperty("max_health", living.getMaxHealth());
                    }
                }

                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future.get();
    }
}
