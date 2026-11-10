package de.mcp.tools;

import com.google.gson.JsonObject;
import de.mcp.api.McpTool;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;

import java.io.File;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

public class TakeScreenshotTool implements McpTool {

    @Override
    public String getName() {
        return "take_screenshot";
    }

    @Override
    public String getDescription() {
        return "Nimmt einen Screenshot der aktuellen Spielansicht auf. Kann optional vor dem Bild den Blickwinkel (yaw, pitch) anpassen und liefert den Dateipfad sowie Bildmaße zurück.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();

        JsonObject fileProp = new JsonObject();
        fileProp.addProperty("type", "string");
        fileProp.addProperty("description", "Optionaler Dateiname (Standard: mcp_screenshot_TIMESTAMP.png).");
        properties.add("filename", fileProp);

        JsonObject yawProp = new JsonObject();
        yawProp.addProperty("type", "number");
        yawProp.addProperty("description", "Optionaler Blickwinkel Yaw (horizontale Drehung), wohin der Spieler vor der Aufnahme schauen soll.");
        properties.add("yaw", yawProp);

        JsonObject pitchProp = new JsonObject();
        pitchProp.addProperty("type", "number");
        pitchProp.addProperty("description", "Optionaler Blickwinkel Pitch (Neigung -90 bis 90).");
        properties.add("pitch", pitchProp);

        JsonObject b64Prop = new JsonObject();
        b64Prop.addProperty("type", "boolean");
        b64Prop.addProperty("description", "Ob der Base64-String des Bildes im JSON zurückgegeben werden soll (Standard: false).");
        properties.add("include_base64", b64Prop);

        schema.add("properties", properties);
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject arguments) throws Exception {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            JsonObject err = new JsonObject();
            err.addProperty("error", "Player not connected to a world.");
            return err;
        }

        CompletableFuture<JsonObject> future = new CompletableFuture<>();

        client.execute(() -> {
            try {
                if (arguments.has("yaw")) {
                    client.player.setYaw(arguments.get("yaw").getAsFloat());
                }
                if (arguments.has("pitch")) {
                    client.player.setPitch(arguments.get("pitch").getAsFloat());
                }

                File screenshotsDir = new File(client.runDirectory, "screenshots");
                if (!screenshotsDir.exists()) {
                    screenshotsDir.mkdirs();
                }

                String filename;
                if (arguments.has("filename") && !arguments.get("filename").getAsString().trim().isEmpty()) {
                    filename = arguments.get("filename").getAsString().trim();
                    if (!filename.toLowerCase().endsWith(".png")) {
                        filename += ".png";
                    }
                } else {
                    String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
                    filename = "mcp_" + timeStamp + ".png";
                }

                File targetFile = new File(screenshotsDir, filename);

                NativeImage nativeImage = ScreenshotRecorder.takeScreenshot(client.getFramebuffer());
                try {
                    nativeImage.writeTo(targetFile);
                } finally {
                    nativeImage.close();
                }

                JsonObject res = new JsonObject();
                res.addProperty("success", true);
                res.addProperty("file_path", targetFile.getAbsolutePath());
                res.addProperty("file_name", targetFile.getName());
                res.addProperty("file_size_bytes", targetFile.length());
                res.addProperty("width", client.getWindow().getFramebufferWidth());
                res.addProperty("height", client.getWindow().getFramebufferHeight());

                JsonObject posObj = new JsonObject();
                posObj.addProperty("x", Math.round(client.player.getX() * 100.0) / 100.0);
                posObj.addProperty("y", Math.round(client.player.getY() * 100.0) / 100.0);
                posObj.addProperty("z", Math.round(client.player.getZ() * 100.0) / 100.0);
                posObj.addProperty("yaw", client.player.getYaw());
                posObj.addProperty("pitch", client.player.getPitch());
                res.add("camera", posObj);

                if (arguments.has("include_base64") && arguments.get("include_base64").getAsBoolean()) {
                    byte[] bytes = Files.readAllBytes(targetFile.toPath());
                    res.addProperty("base64", Base64.getEncoder().encodeToString(bytes));
                }

                future.complete(res);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        return future.get();
    }
}
