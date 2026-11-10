package de.mcp.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.mcp.api.McpToolRegistry;
import de.mcp.log.InGameLogSniffer;

import java.util.List;

public class McpProtocolDispatcher {
    private static final Gson GSON = new Gson();
    public static final String PROTOCOL_VERSION = "2024-11-05";

    public static String handleJsonRpc(String requestJson) {
        try {
            JsonObject request = JsonParser.parseString(requestJson).getAsJsonObject();
            JsonElement id = request.get("id");
            String method = request.has("method") ? request.get("method").getAsString() : "";
            JsonObject params = request.has("params") && request.get("params").isJsonObject()
                    ? request.getAsJsonObject("params")
                    : new JsonObject();

            JsonObject response = new JsonObject();
            response.addProperty("jsonrpc", "2.0");
            if (id != null) {
                response.add("id", id);
            }

            switch (method) {
                case "initialize": {
                    JsonObject result = new JsonObject();
                    result.addProperty("protocolVersion", PROTOCOL_VERSION);

                    JsonObject serverInfo = new JsonObject();
                    serverInfo.addProperty("name", "minecraft-mcp-server");
                    serverInfo.addProperty("version", "1.0.0");
                    result.add("serverInfo", serverInfo);

                    JsonObject capabilities = new JsonObject();
                    capabilities.add("tools", new JsonObject());
                    capabilities.add("resources", new JsonObject());
                    result.add("capabilities", capabilities);

                    response.add("result", result);
                    break;
                }

                case "notifications/initialized":
                    return null; // Notifications do not have responses

                case "ping": {
                    response.add("result", new JsonObject());
                    break;
                }

                case "tools/list": {
                    JsonObject result = new JsonObject();
                    result.add("tools", McpToolRegistry.listToolsAsJson());
                    response.add("result", result);
                    break;
                }

                case "tools/call": {
                    String toolName = params.has("name") ? params.get("name").getAsString() : "";
                    JsonObject arguments = params.has("arguments") && params.get("arguments").isJsonObject()
                            ? params.getAsJsonObject("arguments")
                            : new JsonObject();

                    JsonObject toolResult = McpToolRegistry.executeTool(toolName, arguments);
                    response.add("result", toolResult);
                    break;
                }

                case "resources/list": {
                    JsonObject result = new JsonObject();
                    JsonArray resources = new JsonArray();

                    JsonObject logResource = new JsonObject();
                    logResource.addProperty("uri", "minecraft://logs/recent");
                    logResource.addProperty("name", "Recent Minecraft Logs");
                    logResource.addProperty("mimeType", "text/plain");
                    resources.add(logResource);

                    JsonObject stateResource = new JsonObject();
                    stateResource.addProperty("uri", "minecraft://player/state");
                    stateResource.addProperty("name", "Current Player State");
                    stateResource.addProperty("mimeType", "application/json");
                    resources.add(stateResource);

                    result.add("resources", resources);
                    response.add("result", result);
                    break;
                }

                case "resources/read": {
                    String uri = params.has("uri") ? params.get("uri").getAsString() : "";
                    JsonObject result = new JsonObject();
                    JsonArray contents = new JsonArray();

                    if ("minecraft://logs/recent".equals(uri)) {
                        List<InGameLogSniffer.LogEntry> logs = InGameLogSniffer.getRecentLogs(50, "INFO");
                        StringBuilder sb = new StringBuilder();
                        for (InGameLogSniffer.LogEntry entry : logs) {
                            sb.append(entry.toString()).append("\n");
                        }
                        JsonObject content = new JsonObject();
                        content.addProperty("uri", uri);
                        content.addProperty("mimeType", "text/plain");
                        content.addProperty("text", sb.toString());
                        contents.add(content);
                    } else if ("minecraft://player/state".equals(uri)) {
                        JsonObject toolExec = McpToolRegistry.executeTool("get_player_state", new JsonObject());
                        JsonObject content = new JsonObject();
                        content.addProperty("uri", uri);
                        content.addProperty("mimeType", "application/json");
                        content.addProperty("text", toolExec.toString());
                        contents.add(content);
                    } else {
                        JsonObject error = new JsonObject();
                        error.addProperty("code", -32602);
                        error.addProperty("message", "Unknown resource URI: " + uri);
                        response.add("error", error);
                        return response.toString();
                    }

                    result.add("contents", contents);
                    response.add("result", result);
                    break;
                }

                default: {
                    JsonObject error = new JsonObject();
                    error.addProperty("code", -32601);
                    error.addProperty("message", "Method not found: " + method);
                    response.add("error", error);
                    break;
                }
            }

            return response.toString();
        } catch (Exception e) {
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("jsonrpc", "2.0");
            JsonObject error = new JsonObject();
            error.addProperty("code", -32603);
            error.addProperty("message", "Internal error: " + e.getMessage());
            errorResponse.add("error", error);
            return errorResponse.toString();
        }
    }
}
