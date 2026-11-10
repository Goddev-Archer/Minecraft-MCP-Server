package de.mcp.log;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

public class InGameLogSniffer {
    public static class LogEntry {
        public final long timestamp;
        public final String level;
        public final String loggerName;
        public final String message;
        public final String throwable;

        public LogEntry(long timestamp, String level, String loggerName, String message, String throwable) {
            this.timestamp = timestamp;
            this.level = level;
            this.loggerName = loggerName;
            this.message = message;
            this.throwable = throwable;
        }

        @Override
        public String toString() {
            return String.format("[%s] [%s] %s%s", level, loggerName, message, throwable != null ? "\n" + throwable : "");
        }
    }

    private static final int MAX_ENTRIES = 300;
    private static final ConcurrentLinkedDeque<LogEntry> BUFFER = new ConcurrentLinkedDeque<>();
    private static boolean initialized = false;

    public static void initialize() {
        if (initialized) return;
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            Configuration configuration = context.getConfiguration();
            PatternLayout layout = PatternLayout.createDefaultLayout();
            
            Appender appender = new AbstractAppender("MinecraftMcpLogSniffer", null, layout, false, null) {
                @Override
                public void append(LogEvent event) {
                    String thrown = null;
                    if (event.getThrown() != null) {
                        thrown = event.getThrown().toString();
                    }
                    LogEntry entry = new LogEntry(
                            event.getTimeMillis(),
                            event.getLevel().name(),
                            event.getLoggerName(),
                            event.getMessage().getFormattedMessage(),
                            thrown
                    );
                    BUFFER.addLast(entry);
                    while (BUFFER.size() > MAX_ENTRIES) {
                        BUFFER.pollFirst();
                    }
                }
            };
            appender.start();
            configuration.addAppender(appender);
            for (LoggerConfig loggerConfig : configuration.getLoggers().values()) {
                loggerConfig.addAppender(appender, null, null);
            }
            configuration.getRootLogger().addAppender(appender, null, null);
            context.updateLoggers();
            initialized = true;
        } catch (Exception e) {
            System.err.println("[Minecraft-MCP] Failed to attach LogSniffer appender: " + e.getMessage());
        }
    }

    public static List<LogEntry> getRecentLogs(int limit, String minLevel) {
        List<LogEntry> all = new ArrayList<>(BUFFER);
        Collections.reverse(all);

        List<LogEntry> filtered = new ArrayList<>();
        Level targetLevel = minLevel != null ? Level.toLevel(minLevel, Level.INFO) : Level.INFO;

        for (LogEntry entry : all) {
            Level entryLevel = Level.toLevel(entry.level, Level.INFO);
            if (entryLevel.isMoreSpecificThan(targetLevel) || entryLevel.equals(targetLevel)) {
                filtered.add(entry);
                if (filtered.size() >= limit) break;
            }
        }
        Collections.reverse(filtered);
        return filtered;
    }
}
