package com.astroid.dev.serverSync.sync;

import com.astroid.dev.serverSync.ServerSync;
import com.astroid.dev.serverSync.redis.RedisManager;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ServerSynchronizer {

    private final ServerSync plugin;
    private final RedisManager redisManager;
    private final List<String> serverKeys;
    private final boolean loggingEnabled;
    private final boolean verboseLogging;

    // Thread-safe set om bij te houden welke servers door de plugin zijn toegevoegd
    private final Set<String> managedServers = ConcurrentHashMap.newKeySet();

    public ServerSynchronizer(ServerSync plugin, RedisManager redisManager, List<String> serverKeys, boolean loggingEnabled, boolean verboseLogging) {
        this.plugin = plugin;
        this.redisManager = redisManager;
        this.serverKeys = serverKeys;
        this.loggingEnabled = loggingEnabled;
        this.verboseLogging = verboseLogging;
    }

    public void synchronize() {
        if (!redisManager.isConnected()) {
            log(Level.WARNING, "Redis is niet verbonden, synchronisatie overgeslagen");
            return;
        }

        try {
            // Haal alle server data op uit Redis
            List<RedisManager.ServerData> redisServers = redisManager.getAllServerData(serverKeys);

            // Maak een set van server namen uit Redis
            Set<String> redisServerNames = new HashSet<>();
            Map<String, RedisManager.ServerData> redisServerMap = new HashMap<>();

            for (RedisManager.ServerData serverData : redisServers) {
                redisServerNames.add(serverData.getName());
                redisServerMap.put(serverData.getName(), serverData);
            }

            // Haal huidige proxy servers op
            Map<String, ServerInfo> currentServers = ProxyServer.getInstance().getServers();

            // Voeg nieuwe servers toe
            for (String serverName : redisServerNames) {
                if (!currentServers.containsKey(serverName)) {
                    RedisManager.ServerData serverData = redisServerMap.get(serverName);
                    addServer(serverData);
                }
            }

            // Verwijder servers die niet meer in Redis staan
            Set<String> serversToRemove = new HashSet<>();
            for (String managedServer : managedServers) {
                if (!redisServerNames.contains(managedServer)) {
                    serversToRemove.add(managedServer);
                }
            }

            for (String serverName : serversToRemove) {
                removeServer(serverName);
            }

            if (verboseLogging) {
                log(Level.INFO, String.format("Synchronisatie voltooid: %d servers in Redis, %d beheerde servers",
                        redisServerNames.size(), managedServers.size()));
            }

        } catch (Exception e) {
            log(Level.SEVERE, "Fout tijdens server synchronisatie: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void addServer(RedisManager.ServerData serverData) {
        try {
            InetSocketAddress address = new InetSocketAddress(serverData.getHost(), serverData.getPort());
            ServerInfo serverInfo = ProxyServer.getInstance().constructServerInfo(
                    serverData.getName(),
                    address,
                    "Minigame Server - " + (serverData.getGameType() != null ? serverData.getGameType() : "Unknown"),
                    false
            );

            ProxyServer.getInstance().getServers().put(serverData.getName(), serverInfo);
            managedServers.add(serverData.getName());

            log(Level.INFO, String.format("Server toegevoegd: %s (%s:%d) [%s]",
                    serverData.getName(),
                    serverData.getHost(),
                    serverData.getPort(),
                    serverData.getGameType() != null ? serverData.getGameType() : "Unknown"));

        } catch (Exception e) {
            log(Level.SEVERE, "Fout bij toevoegen van server " + serverData.getName() + ": " + e.getMessage());
        }
    }

    private void removeServer(String serverName) {
        try {
            Map<String, ServerInfo> servers = ProxyServer.getInstance().getServers();
            servers.remove(serverName);
            managedServers.remove(serverName);

            log(Level.INFO, String.format("Server verwijderd: %s", serverName));

        } catch (Exception e) {
            log(Level.SEVERE, "Fout bij verwijderen van server " + serverName + ": " + e.getMessage());
        }
    }

    public Set<String> getManagedServers() {
        return new HashSet<>(managedServers);
    }

    private void log(Level level, String message) {
        if (loggingEnabled) {
            plugin.getLogger().log(level, message);
        }
    }
}

