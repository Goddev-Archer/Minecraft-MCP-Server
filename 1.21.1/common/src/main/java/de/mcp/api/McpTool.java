package de.mcp.api;

import com.google.gson.JsonObject;

public interface McpTool {
    String getName();
    String getDescription();
    JsonObject getInputSchema();
    JsonObject execute(JsonObject arguments) throws Exception;
}
