package com.astroid.dev.serverSync.commands;

import com.astroid.dev.serverSync.handlers.ServerRegistrationHandler;
import com.astroid.dev.serverSync.rabbitmq.RabbitMQManager;
import com.google.gson.JsonObject;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.*;
import java.util.stream.Collectors;

public class PlayCommand extends Command {

    private final ServerRegistrationHandler registrationHandler;
    private final RabbitMQManager rabbitMQManager;
    private final String spawnRequestQueue;
    private final String loadBalancingStrategy;
    private final boolean autoSpawnOnFull;
    private int roundRobinIndex = 0;

    public PlayCommand(ServerRegistrationHandler registrationHandler,
                       RabbitMQManager rabbitMQManager,
                       String spawnRequestQueue,
                       String loadBalancingStrategy,
                       boolean autoSpawnOnFull) {
        super("play", "serversync.play");
        this.registrationHandler = registrationHandler;
        this.rabbitMQManager = rabbitMQManager;
        this.spawnRequestQueue = spawnRequestQueue;
        this.loadBalancingStrategy = loadBalancingStrategy;
        this.autoSpawnOnFull = autoSpawnOnFull;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return;
        }

        ProxiedPlayer player = (ProxiedPlayer) sender;

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /play <minigame>");
            player.sendMessage(ChatColor.GRAY + "Available minigames: bedwars, skywars, tntrun, spleef, paintball, arcade");
            return;
        }

        String minigameType = args[0].toLowerCase();

        // Find available servers for this minigame type
        List<ServerInfo> availableServers = findServersForMinigame(minigameType);

        if (availableServers.isEmpty()) {
            player.sendMessage(ChatColor.RED + "There are currently no " + minigameType + " servers available.");

            // Auto-spawn request if enabled
            if (autoSpawnOnFull && rabbitMQManager.isConnected()) {
                sendSpawnRequest(minigameType, 8); // Default 8 players
                player.sendMessage(ChatColor.YELLOW + "A new server is being started, please try again shortly...");
            }
            return;
        }

        // Select best server based on load balancing strategy
        ServerInfo targetServer = selectBestServer(availableServers);

        if (targetServer == null) {
            player.sendMessage(ChatColor.RED + "No suitable server found.");
            return;
        }

        // Check if server is full
        int currentPlayers = targetServer.getPlayers().size();

        // Connect player to server
        player.sendMessage(ChatColor.GREEN + "Connecting to " + minigameType + " server...");
        player.connect(targetServer);
    }

    /**
     * Find all servers for a specific minigame type
     */
    private List<ServerInfo> findServersForMinigame(String minigameType) {
        Map<String, ServerInfo> allServers = ProxyServer.getInstance().getServers();
        Set<String> managedServers = registrationHandler.getManagedServers();

        return allServers.entrySet().stream()
                .filter(entry -> managedServers.contains(entry.getKey()))
                .filter(entry -> entry.getKey().toLowerCase().startsWith(minigameType + "-"))
                .filter(entry -> registrationHandler.isServerReady(entry.getKey())) // Only READY servers
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    /**
     * Select best server based on load balancing strategy
     */
    private ServerInfo selectBestServer(List<ServerInfo> servers) {
        if (servers.isEmpty()) {
            return null;
        }

        switch (loadBalancingStrategy.toUpperCase()) {
            case "LEAST_PLAYERS":
                return servers.stream()
                        .min(Comparator.comparingInt(s -> s.getPlayers().size()))
                        .orElse(null);

            case "RANDOM":
                return servers.get(new Random().nextInt(servers.size()));

            case "ROUND_ROBIN":
                ServerInfo server = servers.get(roundRobinIndex % servers.size());
                roundRobinIndex = (roundRobinIndex + 1) % servers.size();
                return server;

            default:
                return servers.get(0);
        }
    }

    /**
     * Send spawn request to RabbitMQ
     */
    private void sendSpawnRequest(String minigameType, int players) {
        JsonObject message = new JsonObject();
        message.addProperty("type", minigameType);
        message.addProperty("players", players);
        message.addProperty("timestamp", System.currentTimeMillis());

        rabbitMQManager.publish(spawnRequestQueue, message);
    }
}
