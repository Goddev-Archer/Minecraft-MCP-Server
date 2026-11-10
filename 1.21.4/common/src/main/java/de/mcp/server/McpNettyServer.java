package de.mcp.server;

import de.mcp.config.McpConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

public class McpNettyServer {
    private static EventLoopGroup bossGroup;
    private static EventLoopGroup workerGroup;
    private static Channel serverChannel;
    private static boolean running = false;

    public static synchronized void start(int port) {
        if (running) return;

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(4);

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     ChannelPipeline p = ch.pipeline();
                     p.addLast(new HttpServerCodec());
                     p.addLast(new HttpObjectAggregator(10 * 1024 * 1024)); // 10MB max for screenshots/large payloads
                     p.addLast(new McpHttpHandler());
                 }
             });

            serverChannel = b.bind("127.0.0.1", port).sync().channel();
            running = true;
            System.out.println("=================================================");
            System.out.println("[Minecraft-MCP] Server listening on http://127.0.0.1:" + port);
            System.out.println("[Minecraft-MCP] SSE Endpoint:  http://127.0.0.1:" + port + "/mcp/sse");
            System.out.println("[Minecraft-MCP] RPC Endpoint:  http://127.0.0.1:" + port + "/mcp/rpc");
            System.out.println("[Minecraft-MCP] Auth Token:    " + McpConfig.getInstance().authToken);
            System.out.println("=================================================");
        } catch (Exception e) {
            System.err.println("[Minecraft-MCP] Failed to start Netty server: " + e.getMessage());
            stop();
        }
    }

    public static synchronized void stop() {
        if (!running) return;
        running = false;
        try {
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
            }
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }
            System.out.println("[Minecraft-MCP] Server stopped successfully.");
        } catch (Exception e) {
            System.err.println("[Minecraft-MCP] Error during shutdown: " + e.getMessage());
        }
    }

    public static boolean isRunning() {
        return running;
    }
}
