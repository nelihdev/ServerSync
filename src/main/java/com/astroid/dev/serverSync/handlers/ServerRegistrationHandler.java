package com.astroid.dev.serverSync.handlers;

import com.astroid.dev.serverSync.discord.DiscordWebhook;
import com.astroid.dev.serverSync.pterodactyl.PterodactylApiClient;
import com.astroid.dev.serverSync.redis.RedisManager;
import com.google.gson.JsonObject;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerRegistrationHandler {

    private final Logger logger;
    private final RedisManager redisManager;
    private final PterodactylApiClient pterodactylClient;
    private final String nameFormat;
    private final String motdFormat;
    private final boolean restricted;
    private final Plugin plugin;
    private final String defaultIp;
    private final DiscordWebhook discordWebhook;
    private final Configuration config;

    // Thread-safe set to track which servers were added by the plugin
    private final Set<String> managedServers = ConcurrentHashMap.newKeySet();
    // Track servers that are ready to accept players
    private final Set<String> readyServers = ConcurrentHashMap.newKeySet();
    // Track server registration time for grace period
    private final Map<String, Long> serverRegistrationTime = new ConcurrentHashMap<>();
    private static final long GRACE_PERIOD_MS = 30000; // 30 seconds grace period

    public ServerRegistrationHandler(Logger logger, RedisManager redisManager,
                                    PterodactylApiClient pterodactylClient,
                                    String nameFormat, String motdFormat, boolean restricted,
                                    String defaultIp, Plugin plugin, DiscordWebhook discordWebhook,
                                    Configuration config) {
        this.logger = logger;
        this.redisManager = redisManager;
        this.pterodactylClient = pterodactylClient;
        this.plugin = plugin;
        this.nameFormat = nameFormat;
        this.motdFormat = motdFormat;
        this.restricted = restricted;
        this.defaultIp = defaultIp;
        this.discordWebhook = discordWebhook;
        this.config = config;
    }

    /**
     * Handle server_ready event from RabbitMQ
     */
    public void handleServerReady(JsonObject message) {
        try {
            if (!message.has("server_id") || !message.has("type")) {
                logger.warning("Invalid server_ready message: missing fields");
                return;
            }

            String serverId = message.get("server_id").getAsString();
            String type = message.get("type").getAsString();

            logger.info("════════════════════════════════════════");
            logger.info("  NEW SERVER_READY EVENT RECEIVED");
            logger.info("════════════════════════════════════════");
            logger.info("Raw message: " + message.toString());
            logger.info(String.format("→ Server ID: %s", serverId));
            logger.info("Current managed servers: " + managedServers.size());

            // Check if message contains IP and port directly
            if (message.has("ip") && message.has("port")) {
                String ip = message.get("ip").getAsString();
                int port = message.get("port").getAsInt();
                logger.info(String.format("→ IP:Port from message: %s:%d", ip, port));
                registerServer(serverId, type, ip, port);
            } else if (message.has("port")) {
                // Message has only port - use default IP
                int port = message.get("port").getAsInt();
                registerServer(serverId, type, defaultIp, port);
            } else {
                // Try to get port from Pterodactyl API (but use default IP for Docker)
                pterodactylClient.getServerDetailsAsync(serverId).thenAccept(optDetails -> {
                    if (optDetails.isPresent()) {
                        PterodactylApiClient.ServerDetails details = optDetails.get();
                        // IMPORTANT: Always use default IP (Docker bridge), not the IP from Pterodactyl
                        registerServer(serverId, type, defaultIp, details.getPort());
                        logger.info(String.format("Pterodactyl port used, but with Docker IP: %s:%d",
                            defaultIp, details.getPort()));
                    } else {
                        // Fallback: try to get from Redis
                        logger.warning("Cannot get server details from Pterodactyl, trying Redis");
                        registerServerFromRedis(serverId, type);
                    }
                });
            }

            logger.info("════════════════════════════════════════");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing server_ready event", e);
        }
    }

    /**
     * Handle server_empty event from RabbitMQ
     */
    public void handleServerEmpty(JsonObject message) {
        try {
            if (!message.has("server_id") || !message.has("type")) {
                logger.warning("Invalid server_empty message: missing fields");
                return;
            }

            String serverId = message.get("server_id").getAsString();
            String type = message.get("type").getAsString();

            String serverName = formatServerName(type, serverId);
            unregisterServer(serverName);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing server_empty event", e);
        }
    }

    /**
     * Register server from Redis data
     */
    private void registerServerFromRedis(String serverId, String type) {
        try {
            // Try to find server in Redis with pattern minecraft:server:{server_id}:details
            String redisKey = "minecraft:server:" + serverId + ":details";
            Map<String, String> serverData = redisManager.getServerData(redisKey);

            String ip = defaultIp; // Use default IP
            int port = 25565; // Default Minecraft port

            if (!serverData.isEmpty()) {
                // Use Redis data if available
                if (serverData.get("port") != null) {
                    try {
                        port = Integer.parseInt(serverData.get("port"));
                    } catch (NumberFormatException e) {
                        logger.warning("Invalid port in Redis, using default: " + port);
                    }
                }
            } else {
                // Fallback: calculate port from server ID
                try {
                    int serverId_int = Integer.parseInt(serverId);
                    port = 25565 + serverId_int; // Example: server 42 = port 25607
                } catch (NumberFormatException e) {
                    // Use default port
                }
                logger.info(String.format("No Redis data for server %s, using default %s:%d",
                    serverId, ip, port));
            }

            registerServer(serverId, type, ip, port);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error retrieving server data from Redis", e);
        }
    }

    /**
     * Register a server to BungeeCord
     */
    public void registerServer(String serverId, String type, String ip, int port) {
        try {
            String serverName = formatServerName(type, serverId);

            // Check if already registered
            if (ProxyServer.getInstance().getServers().containsKey(serverName)) {
                logger.warning("│ ⚠ Server " + serverName + " is ALREADY REGISTERED!");
                return;
            }

            logger.info("┌─────────────────────────────────────");
            logger.info("│ REGISTER SERVER");
            logger.info("├─────────────────────────────────────");
            logger.info(String.format("│ Server ID:   %s", serverId));
            logger.info(String.format("│ Server Name: %s", serverName));
            logger.info(String.format("│ Type:        %s", type));
            logger.info(String.format("│ Address:     %s:%d", ip, port));
            logger.info("│ Status: Not yet registered, proceeding...");

            String motd = motdFormat
                .replace("{type}", type)
                .replace("{id}", serverId);

            InetSocketAddress address = new InetSocketAddress(ip, port);
            ServerInfo serverInfo = ProxyServer.getInstance().constructServerInfo(
                serverName,
                address,
                motd,
                restricted
            );

            ProxyServer.getInstance().getServers().put(serverName, serverInfo);
            managedServers.add(serverName);

            // Track registration time for grace period
            serverRegistrationTime.put(serverName, System.currentTimeMillis());

            logger.info("│ ✓✓✓ SERVER SUCCESSFULLY REGISTERED ✓✓✓");
            logger.info(String.format("│ Name: %s", serverName));
            logger.info(String.format("│ Address: %s:%d", ip, port));
            logger.info(String.format("│ Type: %s", type));
            logger.info(String.format("│ Total managed: %d", managedServers.size()));
            logger.info("└─────────────────────────────────────");

            // Log to Discord
            if (discordWebhook != null && discordWebhook.isEnabled() &&
                config.getBoolean("discord.log-events.server-registered", true)) {
                discordWebhook.logServerRegistration(serverName, serverId, type, ip + ":" + port);
            }

            // NEW FEATURE: Immediate ping check for faster ready detection
            // Start async ping task directly after registration
            scheduleImmediatePingCheck(serverName, serverInfo);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error registering server " + serverId, e);
            if (discordWebhook != null && discordWebhook.isEnabled() &&
                config.getBoolean("discord.log-events.errors", true)) {
                discordWebhook.logError("Server Registration Failed",
                    String.format("Failed to register server %s: %s", serverId, e.getMessage()));
            }
        }
    }

    /**
     * Schedule immediate ping check for newly registered server
     * This allows faster detection of server readiness
     */
    private void scheduleImmediatePingCheck(final String serverName, final ServerInfo serverInfo) {
        final AtomicInteger attempts = new AtomicInteger(0);
        final int maxAttempts = 15; // 15 x 2s = 30 seconds aggressive pinging

        // Schedule repeating task
        ProxyServer.getInstance().getScheduler().schedule(plugin, new Runnable() {
            @Override
            public void run() {
                // Stop if max attempts reached or server already ready
                if (attempts.get() >= maxAttempts || readyServers.contains(serverName)) {
                    return;
                }

                // Ping server
                serverInfo.ping((result, error) -> {
                    if (error == null && result != null) {
                        // Success! Server is ready
                        markServerReady(serverName);
                        logger.info(String.format("✓✓ FAST DETECTION: %s is ready after ~%d seconds!",
                                serverName, attempts.get() * 2));
                    }
                });

                attempts.incrementAndGet();
            }
        }, 2, 2, TimeUnit.SECONDS);  // Start after 2s, repeat every 2s
    }

    /**
     * Mark server as ready (has passed health check)
     */
    public void markServerReady(String serverName) {
        if (!readyServers.contains(serverName)) {
            readyServers.add(serverName);
            logger.info(String.format("✓ Server %s is now READY and accepting players", serverName));

            // Log to Discord
            if (discordWebhook != null && discordWebhook.isEnabled() &&
                config.getBoolean("discord.log-events.server-ready", true)) {
                discordWebhook.logServerReady(serverName);
            }
        }
    }

    /**
     * Check if server is ready to accept players
     */
    public boolean isServerReady(String serverName) {
        return readyServers.contains(serverName);
    }

    /**
     * Unregister a server from BungeeCord
     */
    public void unregisterServer(String serverName) {
        try {
            Map<String, ServerInfo> servers = ProxyServer.getInstance().getServers();

            if (!servers.containsKey(serverName)) {
                logger.info("Server " + serverName + " is not registered");
                return;
            }

            // Only remove if it's managed by this plugin
            if (!managedServers.contains(serverName)) {
                logger.warning("Server " + serverName + " is not managed by this plugin");
                return;
            }

            servers.remove(serverName);
            managedServers.remove(serverName);
            serverRegistrationTime.remove(serverName);
            readyServers.remove(serverName); // Also remove from ready list

            logger.info(String.format("✗ Server removed: %s", serverName));

            // Log to Discord
            if (discordWebhook != null && discordWebhook.isEnabled() &&
                config.getBoolean("discord.log-events.server-unregistered", true)) {
                discordWebhook.logServerUnregistration(serverName);
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error removing server " + serverName, e);
            if (discordWebhook != null && discordWebhook.isEnabled() &&
                config.getBoolean("discord.log-events.errors", true)) {
                discordWebhook.logError("Server Unregistration Failed",
                    String.format("Failed to unregister server %s: %s", serverName, e.getMessage()));
            }
        }
    }

    /**
     * Format server name based on config pattern
     */
    private String formatServerName(String type, String serverId) {
        return nameFormat
            .replace("{type}", type)
            .replace("{id}", serverId);
    }

    /**
     * Get all managed servers
     */
    public Set<String> getManagedServers() {
        return new HashSet<>(managedServers);
    }

    /**
     * Check if a server is managed by this plugin
     */
    public boolean isManaged(String serverName) {
        return managedServers.contains(serverName);
    }

    /**
     * Clear all managed servers
     */
    public void clearManagedServers() {
        managedServers.clear();
    }

    /**
     * Check if server is in grace period (recently added)
     */
    public boolean isInGracePeriod(String serverName) {
        Long registrationTime = serverRegistrationTime.get(serverName);
        if (registrationTime == null) {
            return false;
        }

        long elapsed = System.currentTimeMillis() - registrationTime;
        return elapsed < GRACE_PERIOD_MS;
    }

    /**
     * Get remaining grace period in seconds
     */
    public long getRemainingGracePeriod(String serverName) {
        Long registrationTime = serverRegistrationTime.get(serverName);
        if (registrationTime == null) {
            return 0;
        }

        long elapsed = System.currentTimeMillis() - registrationTime;
        long remaining = GRACE_PERIOD_MS - elapsed;
        return Math.max(0, remaining / 1000);
    }
}
