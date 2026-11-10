package de.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.mcp.api.McpTool;
import de.mcp.log.InGameLogSniffer;

import java.util.List;

public class GetLatestLogsTool implements McpTool {

    @Override
    public String getName() {
        return "get_latest_logs";
    }

    @Override
    public String getDescription() {
        return "Liest die neuesten Minecraft- und Mod-Logeinträge aus dem Speicher (besonders nützlich zum Finden von Exceptions, Fehlern und Warnungen).";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();

        JsonObject limitProp = new JsonObject();
        limitProp.addProperty("type", "integer");
        limitProp.addProperty("description", "Maximale Anzahl an Einträgen (Standard: 30, Max: 100).");
        properties.add("limit", limitProp);

        JsonObject levelProp = new JsonObject();
        levelProp.addProperty("type", "string");
        levelProp.addProperty("enum", "[\"INFO\", \"WARN\", \"ERROR\"]");
        levelProp.addProperty("description", "Mindest-Loglevel (Standard: INFO).");
        properties.add("min_level", levelProp);

        schema.add("properties", properties);
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) throws Exception {
        int limit = arguments.has("limit") ? Math.min(100, Math.max(1, arguments.get("limit").getAsInt())) : 30;
        String minLevel = arguments.has("min_level") ? arguments.get("min_level").getAsString() : "INFO";

        List<InGameLogSniffer.LogEntry> logs = InGameLogSniffer.getRecentLogs(limit, minLevel);
        JsonObject result = new JsonObject();
        JsonArray entries = new JsonArray();

        for (InGameLogSniffer.LogEntry entry : logs) {
            JsonObject item = new JsonObject();
            item.addProperty("timestamp", entry.timestamp);
            item.addProperty("level", entry.level);
            item.addProperty("logger", entry.loggerName);
            item.addProperty("message", entry.message);
            if (entry.throwable != null) {
                item.addProperty("throwable", entry.throwable);
            }
            entries.add(item);
        }

        result.add("logs", entries);
        result.addProperty("count", entries.size());
        return result;
    }
}
