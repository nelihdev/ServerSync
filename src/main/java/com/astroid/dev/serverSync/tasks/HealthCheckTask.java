package com.astroid.dev.serverSync.tasks;

import com.astroid.dev.serverSync.handlers.ServerRegistrationHandler;
import com.astroid.dev.serverSync.rabbitmq.RabbitMQManager;
import com.astroid.dev.serverSync.redis.RedisManager;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HealthCheckTask implements Runnable {

    private final Logger logger;
    private final RedisManager redisManager;
    private final RabbitMQManager rabbitMQManager;
    private final ServerRegistrationHandler registrationHandler;
    private final List<String> serverKeys;
    private final boolean verboseLogging;
    private final String playerCountQueue;
    private final String proxyId;

    public HealthCheckTask(Logger logger, RedisManager redisManager,
                           RabbitMQManager rabbitMQManager,
                           ServerRegistrationHandler registrationHandler,
                           List<String> serverKeys, boolean verboseLogging,
                           String playerCountQueue, String proxyId) {
        this.logger = logger;
        this.redisManager = redisManager;
        this.rabbitMQManager = rabbitMQManager;
        this.registrationHandler = registrationHandler;
        this.serverKeys = serverKeys;
        this.verboseLogging = verboseLogging;
        this.playerCountQueue = playerCountQueue;
        this.proxyId = proxyId;
    }

    @Override
    public void run() {
        try {
            if (!redisManager.isConnected()) {
                if (verboseLogging) {
                    logger.warning("Health check overgeslagen: Redis niet verbonden");
                }
                return;
            }

            performHealthCheck();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Fout tijdens health check", e);
        }
    }

    private void performHealthCheck() {
        Set<String> managedServers = registrationHandler.getManagedServers();
        Map<String, ServerInfo> proxyServers = ProxyServer.getInstance().getServers();

        int pingChecks = 0;
        int removedServers = 0;
        int gracePeriodActive = 0;
        int serversReady = 0;

        // Check each managed server
        for (String serverName : new HashSet<>(managedServers)) {
            ServerInfo serverInfo = proxyServers.get(serverName);

            if (serverInfo == null) {
                // Server was removed externally
                registrationHandler.clearManagedServers();
                if (verboseLogging) {
                    logger.warning("Server " + serverName + " extern verwijderd, cache gecleared");
                }
                continue;
            }

            // Check if server is in grace period (recently registered)
            if (registrationHandler.isInGracePeriod(serverName)) {
                long remaining = registrationHandler.getRemainingGracePeriod(serverName);

                // ACTIEF proberen te pingen tijdens grace period
                // Dit zorgt dat servers SNEL als ready worden gemarkeerd zodra ze reageren
                serverInfo.ping((result, error) -> {
                    if (error == null && result != null) {
                        // Server is online en reageert op pings!
                        registrationHandler.markServerReady(serverName);
                        logger.info(String.format("✓ Server %s READY tijdens grace period (ping succesvol)", serverName));

                        // Publish player count during grace period too
                        int playerCount = result.getPlayers().getOnline();
                        publishPlayerCount(serverName, playerCount);
                    } else {
                        if (verboseLogging) {
                            logger.info(String.format("Server %s in grace period - nog niet ready (%d sec resterend)",
                                    serverName, remaining));
                        }
                    }
                });

                gracePeriodActive++;
                continue;
            }

            // Server is na grace period - check of ie ready is geworden
            if (!registrationHandler.isServerReady(serverName)) {
                // EXTRA CHECK: Probeer nog één keer te pingen voordat we verwijderen
                // Dit voorkomt race conditions waar de server net ready wordt
                serverInfo.ping((result, error) -> {
                    if (error == null && result != null) {
                        // Server reageert! Mark als ready in plaats van verwijderen
                        registrationHandler.markServerReady(serverName);
                        logger.info(String.format("✓ Server %s werd net ready (ping succesvol vlak voor verwijdering)", serverName));

                        int playerCount = result.getPlayers().getOnline();
                        publishPlayerCount(serverName, playerCount);
                    } else {
                        // Server reageert echt niet - nu pas verwijderen
                        logger.warning(String.format("Server %s is niet klaar na grace period en reageert niet op ping - wordt verwijderd", serverName));
                        registrationHandler.unregisterServer(serverName);
                    }
                });

                // Tellen als grace period (we geven 1 extra kans)
                gracePeriodActive++;
                continue;
            }

            // Server is ready - blijf pingen om te checken of ie nog online is
            serverInfo.ping((result, error) -> {
                if (error != null) {
                    // Server is offline
                    logger.warning(String.format("Server %s is offline: %s", serverName, error.getMessage()));
                    registrationHandler.unregisterServer(serverName);
                } else {
                    // Server is nog steeds online
                    int playerCount = result.getPlayers().getOnline();

                    // Publish player count to cloud controller for scaling decisions
                    publishPlayerCount(serverName, playerCount);

                    if (verboseLogging) {
                        logger.info(String.format("Server %s health check OK (players: %d/%d)",
                                serverName,
                                playerCount,
                                result.getPlayers().getMax()));
                    }
                }
            });

            pingChecks++;
            serversReady++;
        }

        // Synchronize with Redis server list
        synchronizeWithRedis();

        if (verboseLogging || gracePeriodActive > 0 || removedServers > 0) {
            logger.info(String.format("Health check: %d ready, %d grace period, %d offline verwijderd",
                    serversReady, gracePeriodActive, removedServers));
        }
    }

    /**
     * Synchronize proxy server list with Redis
     */
    private void synchronizeWithRedis() {
        try {
            // Get all servers from Redis (legacy structure)
            List<RedisManager.ServerData> redisServers = redisManager.getAllServerData(serverKeys);
            Set<String> redisServerNames = new HashSet<>();

            for (RedisManager.ServerData serverData : redisServers) {
                redisServerNames.add(serverData.getName());
            }

            // Get managed servers
            Set<String> managedServers = registrationHandler.getManagedServers();

            // BELANGRIJK: Alleen verwijderen als Redis sync enabled is EN server niet in Redis
            // Maar NIET verwijderen als de server recent via RabbitMQ is toegevoegd
            for (String managedServer : new HashSet<>(managedServers)) {
                if (!redisServerNames.contains(managedServer)) {
                    // Check if server info exists in proxy (might be from RabbitMQ)
                    Map<String, ServerInfo> proxyServers = ProxyServer.getInstance().getServers();
                    ServerInfo serverInfo = proxyServers.get(managedServer);

                    if (serverInfo != null) {
                        // Server exists in proxy but not in Redis
                        // This is normal for RabbitMQ-registered servers
                        // Don't remove it!
                        if (verboseLogging) {
                            logger.info("Server " + managedServer + " niet in Redis maar wel actief - behouden");
                        }
                    }
                }
            }

            // Add servers that are in Redis but not in proxy
            // DISABLED: We rely on RabbitMQ events, not Redis polling
            // This prevents duplicate registration logic
            if (verboseLogging) {
                logger.info(String.format("Redis sync: %d Redis servers, %d beheerde servers",
                        redisServerNames.size(), managedServers.size()));
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Fout bij synchroniseren met Redis", e);
        }
    }

    /**
     * Publish player count update to cloud controller
     * Includes proxy ID to prevent double counting in multi-proxy setups
     */
    private void publishPlayerCount(String serverName, int playerCount) {
        if (rabbitMQManager == null || !rabbitMQManager.isConnected()) {
            return; // Skip if RabbitMQ not available
        }

        // Extract server ID and type from server name
        // Format: bedwars-66, skywars-123, etc.
        String[] parts = serverName.split("-");
        if (parts.length < 2) {
            return; // Invalid server name format
        }

        String serverType = parts[0];
        String serverId = parts[1];

        // Publish player count update
        rabbitMQManager.publishPlayerCount(playerCountQueue, serverId, serverType, playerCount);
    }
}
