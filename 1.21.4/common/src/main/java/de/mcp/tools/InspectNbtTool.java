package de.mcp.tools;

import com.google.gson.JsonObject;
import de.mcp.api.McpTool;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.concurrent.CompletableFuture;

public class InspectNbtTool implements McpTool {

    @Override
    public String getName() {
        return "inspect_nbt";
    }

    @Override
    public String getDescription() {
        return "Liest NBT- und Komponentendaten eines anvisierten Blocks, einer Entity oder des Items in der Hand aus. Ideal zum Debuggen komplexer Mod-Maschinen.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();

        JsonObject targetProp = new JsonObject();
        targetProp.addProperty("type", "string");
        targetProp.addProperty("enum", "[\"target_block\", \"main_hand\"]");
        targetProp.addProperty("description", "Ziel der Inspektion (target_block oder main_hand).");
        properties.add("target", targetProp);

        schema.add("properties", properties);
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) throws Exception {
        String target = arguments.has("target") ? arguments.get("target").getAsString() : "target_block";

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            JsonObject err = new JsonObject();
            err.addProperty("error", "Player or world not available.");
            return err;
        }

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        client.execute(() -> {
            try {
                JsonObject res = new JsonObject();

                if ("main_hand".equalsIgnoreCase(target)) {
                    ItemStack stack = client.player.getMainHandStack();
                    if (stack.isEmpty()) {
                        res.addProperty("error", "Main hand is empty.");
                    } else {
                        res.addProperty("item", stack.getItem().toString());
                        res.addProperty("count", stack.getCount());
                        res.addProperty("components", stack.getComponents().toString());
                    }
                    future.complete(res);
                    return;
                }

                // target_block
                HitResult hit = client.crosshairTarget;
                if (hit instanceof BlockHitResult) {
                    BlockHitResult blockHit = (BlockHitResult) hit;
                    BlockPos pos = blockHit.getBlockPos();
                    BlockEntity be = client.world.getBlockEntity(pos);

                    res.addProperty("block_pos", pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
                    if (be != null) {
                        NbtCompound nbt = be.createNbt(client.world.getRegistryManager());
                        res.addProperty("has_block_entity", true);
                        res.addProperty("nbt", nbt.asString());
                    } else {
                        res.addProperty("has_block_entity", false);
                        res.addProperty("message", "Block at position has no TileEntity/BlockEntity.");
                    }
                } else {
                    res.addProperty("error", "Crosshair is not aiming at a block.");
                }

                future.complete(res);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future.get();
    }
}
