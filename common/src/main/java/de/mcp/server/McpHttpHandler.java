package de.mcp.server;

import com.google.gson.JsonObject;
import de.mcp.config.McpConfig;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

public class McpHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        // CORS preflight
        if (req.method().equals(HttpMethod.OPTIONS)) {
            FullHttpResponse res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            addCorsHeaders(res);
            ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
            return;
        }

        String uri = req.uri();

        // Check authentication
        if (McpConfig.getInstance().enableAuth && !uri.equals("/mcp/health")) {
            String authHeader = req.headers().get(HttpHeaderNames.AUTHORIZATION);
            String expected = "Bearer " + McpConfig.getInstance().authToken;
            if (authHeader == null || !authHeader.trim().equals(expected)) {
                sendJsonResponse(ctx, HttpResponseStatus.UNAUTHORIZED, "{\"error\":\"Unauthorized. Invalid or missing Bearer token.\"}");
                return;
            }
        }

        if (uri.startsWith("/mcp/health")) {
            JsonObject health = new JsonObject();
            health.addProperty("status", "ok");
            health.addProperty("minecraft_version", "1.21.1");
            health.addProperty("active_sse_clients", SseConnectionManager.getActiveClientCount());
            sendJsonResponse(ctx, HttpResponseStatus.OK, health.toString());
            return;
        }

        if (uri.startsWith("/mcp/sse") && req.method().equals(HttpMethod.GET)) {
            // Upgrade to SSE text/event-stream
            DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream");
            response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            addCorsHeaders(response);
            ctx.writeAndFlush(response);

            SseConnectionManager.registerClient(ctx.channel());

            // Send initial ping event
            SseConnectionManager.broadcastEvent("endpoint", "http://127.0.0.1:" + McpConfig.getInstance().serverPort + "/mcp/rpc");
            return;
        }

        if (uri.startsWith("/mcp/rpc") && req.method().equals(HttpMethod.POST)) {
            ByteBuf content = req.content();
            String requestBody = content.toString(CharsetUtil.UTF_8);

            String responseBody = McpProtocolDispatcher.handleJsonRpc(requestBody);
            if (responseBody != null) {
                sendJsonResponse(ctx, HttpResponseStatus.OK, responseBody);
            } else {
                // Empty notification response
                FullHttpResponse res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED);
                addCorsHeaders(res);
                ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
            }
            return;
        }

        sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND, "{\"error\":\"Endpoint not found: " + uri + "\"}");
    }

    private void sendJsonResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String json) {
        ByteBuf buffer = Unpooled.copiedBuffer(json, CharsetUtil.UTF_8);
        FullHttpResponse res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buffer);
        res.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        res.headers().set(HttpHeaderNames.CONTENT_LENGTH, buffer.readableBytes());
        addCorsHeaders(res);
        ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
    }

    private void addCorsHeaders(io.netty.handler.codec.http.HttpResponse res) {
        res.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        res.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, OPTIONS");
        res.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Authorization, Content-Type");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("[Minecraft-MCP] Network exception: " + cause.getMessage());
        ctx.close();
    }
}
