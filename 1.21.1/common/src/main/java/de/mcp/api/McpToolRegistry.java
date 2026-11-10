package de.mcp.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class McpToolRegistry {
    private static final Map<String, McpTool> TOOLS = new ConcurrentHashMap<>();

    public static void registerTool(McpTool tool) {
        TOOLS.put(tool.getName(), tool);
    }

    public static McpTool getTool(String name) {
        return TOOLS.get(name);
    }

    public static Collection<McpTool> getAllTools() {
        return TOOLS.values();
    }

    public static JsonArray listToolsAsJson() {
        JsonArray array = new JsonArray();
        for (McpTool tool : TOOLS.values()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", tool.getName());
            obj.addProperty("description", tool.getDescription());
            obj.add("inputSchema", tool.getInputSchema());
            array.add(obj);
        }
        return array;
    }

    public static JsonObject executeTool(String toolName, JsonObject arguments) {
        McpTool tool = TOOLS.get(toolName);
        JsonObject result = new JsonObject();
        JsonArray contentArray = new JsonArray();

        if (tool == null) {
            result.addProperty("isError", true);
            JsonObject textObj = new JsonObject();
            textObj.addProperty("type", "text");
            textObj.addProperty("text", "Error: Unknown tool '" + toolName + "'");
            contentArray.add(textObj);
            result.add("content", contentArray);
            return result;
        }

        try {
            JsonObject execResult = tool.execute(arguments != null ? arguments : new JsonObject());
            if (execResult.has("content")) {
                return execResult;
            }
            JsonObject textObj = new JsonObject();
            textObj.addProperty("type", "text");
            textObj.addProperty("text", execResult.toString());
            contentArray.add(textObj);
            result.add("content", contentArray);
            result.addProperty("isError", false);
            return result;
        } catch (Exception e) {
            result.addProperty("isError", true);
            JsonObject textObj = new JsonObject();
            textObj.addProperty("type", "text");
            textObj.addProperty("text", "Execution error in tool '" + toolName + "': " + e.getMessage());
            contentArray.add(textObj);
            result.add("content", contentArray);
            return result;
        }
    }
}
