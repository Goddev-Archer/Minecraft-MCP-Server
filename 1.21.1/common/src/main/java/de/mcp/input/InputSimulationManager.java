package de.mcp.input;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class InputSimulationManager {
    private static final Map<String, AtomicInteger> ACTIVE_KEYS = new ConcurrentHashMap<>();

    public static void holdKey(String action, int ticks) {
        ACTIVE_KEYS.put(action.toLowerCase(), new AtomicInteger(ticks));
    }

    public static boolean isActionActive(String action) {
        AtomicInteger remaining = ACTIVE_KEYS.get(action.toLowerCase());
        return remaining != null && remaining.get() > 0;
    }

    public static void releaseAll() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.options != null) {
            setKey(client.options.forwardKey, false);
            setKey(client.options.backKey, false);
            setKey(client.options.leftKey, false);
            setKey(client.options.rightKey, false);
            setKey(client.options.jumpKey, false);
            setKey(client.options.sneakKey, false);
            setKey(client.options.sprintKey, false);
            setKey(client.options.attackKey, false);
            setKey(client.options.useKey, false);
        }
        ACTIVE_KEYS.clear();
    }

    public static void onClientTick(MinecraftClient client) {
        if (client == null || client.player == null || client.options == null) {
            return;
        }

        ACTIVE_KEYS.forEach((action, remainingTicks) -> {
            KeyBinding key = resolveKey(client, action);
            if (key != null) {
                int current = remainingTicks.get();
                if (current > 0) {
                    setKey(key, true);
                    remainingTicks.decrementAndGet();
                } else {
                    setKey(key, false);
                    ACTIVE_KEYS.remove(action);
                }
            }
        });
    }

    private static void setKey(KeyBinding key, boolean pressed) {
        if (key != null) {
            key.setPressed(pressed);
        }
    }

    private static KeyBinding resolveKey(MinecraftClient client, String action) {
        switch (action.toLowerCase()) {
            case "forward":
            case "w":
                return client.options.forwardKey;
            case "back":
            case "backward":
            case "s":
                return client.options.backKey;
            case "left":
            case "a":
                return client.options.leftKey;
            case "right":
            case "d":
                return client.options.rightKey;
            case "jump":
            case "space":
                return client.options.jumpKey;
            case "sneak":
            case "shift":
                return client.options.sneakKey;
            case "sprint":
            case "ctrl":
                return client.options.sprintKey;
            case "attack":
                return client.options.attackKey;
            case "use":
                return client.options.useKey;
            default:
                return null;
        }
    }
}
