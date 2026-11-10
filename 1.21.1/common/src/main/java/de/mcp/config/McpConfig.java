package de.mcp.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.UUID;

public class McpConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static McpConfig INSTANCE;

    public int serverPort = 25585;
    public String authToken = UUID.randomUUID().toString();
    public boolean enableAuth = true;
    public String inGameAiPrefix = "[§bAI-Assistant§r] ";
    public int maxLogBufferSize = 200;

    public static McpConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new McpConfig();
        }
        return INSTANCE;
    }

    public static void load(Path configDir) {
        File configFile = configDir.resolve("minecraft_mcp.json").toFile();
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                INSTANCE = GSON.fromJson(reader, McpConfig.class);
            } catch (Exception e) {
                System.err.println("[Minecraft-MCP] Failed to load config, using defaults: " + e.getMessage());
                INSTANCE = new McpConfig();
            }
        } else {
            INSTANCE = new McpConfig();
            INSTANCE.save(configDir);
        }
    }

    public void save(Path configDir) {
        File configFile = configDir.resolve("minecraft_mcp.json").toFile();
        try {
            configFile.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            System.err.println("[Minecraft-MCP] Failed to save config: " + e.getMessage());
        }
    }
}
