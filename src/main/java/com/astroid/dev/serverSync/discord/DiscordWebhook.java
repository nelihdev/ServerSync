package com.astroid.dev.serverSync.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Discord Webhook Manager for logging events to Discord
 */
public class DiscordWebhook {

    private final Logger logger;
    private final String webhookUrl;
    private final boolean enabled;
    private final ExecutorService executor;
    private final String botName;
    private final String botAvatar;

    public DiscordWebhook(Logger logger, String webhookUrl, boolean enabled, String botName, String botAvatar) {
        this.logger = logger;
        this.webhookUrl = webhookUrl;
        this.enabled = enabled;
        this.botName = botName;
        this.botAvatar = botAvatar;
        this.executor = Executors.newFixedThreadPool(2);
    }

    /**
     * Send a simple message to Discord
     */
    public void sendMessage(String message) {
        if (!enabled || webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        executor.submit(() -> {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("content", message);
                if (botName != null && !botName.isEmpty()) {
                    payload.addProperty("username", botName);
                }
                if (botAvatar != null && !botAvatar.isEmpty()) {
                    payload.addProperty("avatar_url", botAvatar);
                }

                sendWebhook(payload);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to send Discord message", e);
            }
        });
    }

    /**
     * Send an embed message to Discord
     */
    public void sendEmbed(String title, String description, int color) {
        if (!enabled || webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        executor.submit(() -> {
            try {
                JsonObject embed = new JsonObject();
                embed.addProperty("title", title);
                embed.addProperty("description", description);
                embed.addProperty("color", color);
                embed.addProperty("timestamp", Instant.now().toString());

                JsonArray embeds = new JsonArray();
                embeds.add(embed);

                JsonObject payload = new JsonObject();
                payload.add("embeds", embeds);
                if (botName != null && !botName.isEmpty()) {
                    payload.addProperty("username", botName);
                }
                if (botAvatar != null && !botAvatar.isEmpty()) {
                    payload.addProperty("avatar_url", botAvatar);
                }

                sendWebhook(payload);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to send Discord embed", e);
            }
        });
    }

    /**
     * Log server registration event
     */
    public void logServerRegistration(String serverName, String serverId, String type, String address) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "✅ Server Registered");
        embed.addProperty("description",
            String.format("**Server:** %s\n**ID:** %s\n**Type:** %s\n**Address:** %s",
                serverName, serverId, type, address));
        embed.addProperty("color", 0x00FF00); // Green
        embed.addProperty("timestamp", Instant.now().toString());

        sendEmbedObject(embed);
    }

    /**
     * Log server unregistration event
     */
    public void logServerUnregistration(String serverName) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "❌ Server Unregistered");
        embed.addProperty("description", String.format("**Server:** %s", serverName));
        embed.addProperty("color", 0xFF0000); // Red
        embed.addProperty("timestamp", Instant.now().toString());

        sendEmbedObject(embed);
    }

    /**
     * Log server ready event
     */
    public void logServerReady(String serverName) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "🟢 Server Ready");
        embed.addProperty("description", String.format("**Server:** %s is now accepting players", serverName));
        embed.addProperty("color", 0x00AA00); // Dark Green
        embed.addProperty("timestamp", Instant.now().toString());

        sendEmbedObject(embed);
    }

    /**
     * Log plugin startup
     */
    public void logPluginStartup(int managedServers) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "🚀 ServerSync Started");
        embed.addProperty("description", String.format("Plugin started successfully\n**Managed Servers:** %d", managedServers));
        embed.addProperty("color", 0x0099FF); // Blue
        embed.addProperty("timestamp", Instant.now().toString());

        sendEmbedObject(embed);
    }

    /**
     * Log plugin shutdown
     */
    public void logPluginShutdown() {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "🛑 ServerSync Stopped");
        embed.addProperty("description", "Plugin stopped");
        embed.addProperty("color", 0xFF9900); // Orange
        embed.addProperty("timestamp", Instant.now().toString());

        sendEmbedObject(embed);
    }

    /**
     * Log error
     */
    public void logError(String title, String error) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "⚠️ " + title);
        embed.addProperty("description", error);
        embed.addProperty("color", 0xFF0000); // Red
        embed.addProperty("timestamp", Instant.now().toString());

        sendEmbedObject(embed);
    }

    /**
     * Log warning
     */
    public void logWarning(String title, String warning) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "⚠️ " + title);
        embed.addProperty("description", warning);
        embed.addProperty("color", 0xFFAA00); // Yellow/Orange
        embed.addProperty("timestamp", Instant.now().toString());

        sendEmbedObject(embed);
    }

    /**
     * Log health check results
     */
    public void logHealthCheck(int totalServers, int healthyServers, int addedServers, int removedServers) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "🏥 Health Check Completed");
        embed.addProperty("description",
            String.format("**Total Servers:** %d\n**Healthy:** %d\n**Added:** %d\n**Removed:** %d",
                totalServers, healthyServers, addedServers, removedServers));
        embed.addProperty("color", 0x00AAFF); // Light Blue
        embed.addProperty("timestamp", Instant.now().toString());

        sendEmbedObject(embed);
    }

    /**
     * Send embed object
     */
    private void sendEmbedObject(JsonObject embed) {
        if (!enabled || webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        executor.submit(() -> {
            try {
                JsonArray embeds = new JsonArray();
                embeds.add(embed);

                JsonObject payload = new JsonObject();
                payload.add("embeds", embeds);
                if (botName != null && !botName.isEmpty()) {
                    payload.addProperty("username", botName);
                }
                if (botAvatar != null && !botAvatar.isEmpty()) {
                    payload.addProperty("avatar_url", botAvatar);
                }

                sendWebhook(payload);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to send Discord webhook", e);
            }
        });
    }

    /**
     * Send webhook HTTP request
     */
    private void sendWebhook(JsonObject payload) {
        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            byte[] jsonBytes = payload.toString().getBytes(StandardCharsets.UTF_8);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonBytes);
                os.flush();
            }

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                logger.warning("Discord webhook returned status: " + responseCode);
            }

            connection.disconnect();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to send webhook", e);
        }
    }

    /**
     * Shutdown executor
     */
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}

