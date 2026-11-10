package de.mcp.server;

import com.google.gson.Gson;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.util.CharsetUtil;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SseConnectionManager {
    private static final Gson GSON = new Gson();
    private static final Set<Channel> CLIENTS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void registerClient(Channel channel) {
        CLIENTS.add(channel);
        channel.closeFuture().addListener((ChannelFutureListener) future -> CLIENTS.remove(channel));
        System.out.println("[Minecraft-MCP] New SSE client connected: " + channel.remoteAddress());
    }

    public static int getActiveClientCount() {
        return CLIENTS.size();
    }

    public static void broadcastEvent(String eventType, Object data) {
        if (CLIENTS.isEmpty()) return;

        String json = data instanceof String ? (String) data : GSON.toJson(data);
        String ssePayload = "event: " + eventType + "\ndata: " + json + "\n\n";

        for (Channel client : CLIENTS) {
            if (client.isActive() && client.isWritable()) {
                HttpContent content = new DefaultHttpContent(Unpooled.copiedBuffer(ssePayload, CharsetUtil.UTF_8));
                client.writeAndFlush(content);
            }
        }
    }
}
