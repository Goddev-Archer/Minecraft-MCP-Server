package de.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.mcp.api.McpTool;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;

import java.util.concurrent.CompletableFuture;

public class GetPlayerStateTool implements McpTool {

    @Override
    public String getName() {
        return "get_player_state";
    }

    @Override
    public String getDescription() {
        return "Liefert den aktuellen Zustand des Spielers (Leben, Hunger, Position, Dimension, Blickrichtung, Inventar, Effekte).";
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
        if (client == null || client.player == null) {
            JsonObject err = new JsonObject();
            err.addProperty("error", "Player not currently in a Minecraft world.");
            return err;
        }

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        client.execute(() -> {
            try {
                ClientPlayerEntity player = client.player;
                JsonObject state = new JsonObject();

                state.addProperty("name", player.getName().getString());
                state.addProperty("dimension", player.getWorld().getRegistryKey().getValue().toString());
                state.addProperty("health", player.getHealth());
                state.addProperty("max_health", player.getMaxHealth());
                state.addProperty("food_level", player.getHungerManager().getFoodLevel());
                state.addProperty("saturation", player.getHungerManager().getSaturationLevel());

                JsonObject pos = new JsonObject();
                pos.addProperty("x", Math.round(player.getX() * 100.0) / 100.0);
                pos.addProperty("y", Math.round(player.getY() * 100.0) / 100.0);
                pos.addProperty("z", Math.round(player.getZ() * 100.0) / 100.0);
                pos.addProperty("yaw", Math.round(player.getYaw() * 10.0) / 10.0);
                pos.addProperty("pitch", Math.round(player.getPitch() * 10.0) / 10.0);
                state.add("position", pos);

                // Active potion effects
                JsonArray effects = new JsonArray();
                for (StatusEffectInstance effect : player.getStatusEffects()) {
                    JsonObject effObj = new JsonObject();
                    effObj.addProperty("id", effect.getEffectType().getIdAsString());
                    effObj.addProperty("duration_ticks", effect.getDuration());
                    effObj.addProperty("amplifier", effect.getAmplifier());
                    effects.add(effObj);
                }
                state.add("effects", effects);

                // Inventory summary
                JsonArray inventory = new JsonArray();
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (!stack.isEmpty()) {
                        JsonObject itemObj = new JsonObject();
                        itemObj.addProperty("slot", i);
                        itemObj.addProperty("item", stack.getItem().toString());
                        itemObj.addProperty("count", stack.getCount());
                        itemObj.addProperty("name", stack.getName().getString());
                        if (stack.isDamageable()) {
                            itemObj.addProperty("damage", stack.getDamage());
                            itemObj.addProperty("max_damage", stack.getMaxDamage());
                        }
                        inventory.add(itemObj);
                    }
                }
                state.add("inventory", inventory);

                // Main hand & off hand
                state.addProperty("main_hand", player.getMainHandStack().isEmpty() ? "empty" : player.getMainHandStack().getItem().toString());
                state.addProperty("off_hand", player.getOffHandStack().isEmpty() ? "empty" : player.getOffHandStack().getItem().toString());

                future.complete(state);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future.get();
    }
}
