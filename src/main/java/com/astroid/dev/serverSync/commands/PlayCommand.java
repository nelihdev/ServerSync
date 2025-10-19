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
            sender.sendMessage(ChatColor.RED + "\u2716 " + ChatColor.BOLD + "Error! " + ChatColor.RED + "Only players can use this command!");
            return;
        }

        ProxiedPlayer player = (ProxiedPlayer) sender;

        if (args.length == 0) {
            sendGameMenu(player);
            return;
        }

        String minigameType = args[0].toLowerCase();

        // Find available servers for this minigame type
        List<ServerInfo> availableServers = findServersForMinigame(minigameType);

        if (availableServers.isEmpty()) {
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "\u2716 " + ChatColor.BOLD + "Error! " + ChatColor.RED + "No " + formatMinigameName(minigameType) + " servers are available right now.");

            // Auto-spawn request if enabled
            if (autoSpawnOnFull && rabbitMQManager.isConnected()) {
                sendSpawnRequest(minigameType, 8); // Default 8 players
                player.sendMessage(ChatColor.YELLOW + "\u26A1 A new server is starting up! Please try again shortly...");
            }
            player.sendMessage("");
            return;
        }

        // Select best server based on load balancing strategy
        ServerInfo targetServer = selectBestServer(availableServers);

        if (targetServer == null) {
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "\u2716 " + ChatColor.BOLD + "Error! " + ChatColor.RED + "Unable to find a suitable server.");
            player.sendMessage("");
            return;
        }

        // Connect player to server
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "\u27A4 " + ChatColor.BOLD + "Sending you to " + formatMinigameName(minigameType) + "!");
        player.sendMessage("");
        player.connect(targetServer);
    }

    /**
     * Send game menu to player
     */
    private void sendGameMenu(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC");
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "                    Available Games");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "/play bedwars " + ChatColor.GRAY + "\u00BB " + ChatColor.WHITE + "Defend your bed and destroy enemies!");
        sender.sendMessage(ChatColor.YELLOW + "/play skywars " + ChatColor.GRAY + "\u00BB " + ChatColor.WHITE + "Battle on floating islands!");
        sender.sendMessage(ChatColor.YELLOW + "/play tntrun " + ChatColor.GRAY + "\u00BB " + ChatColor.WHITE + "Don't stop running or you'll fall!");
        sender.sendMessage(ChatColor.YELLOW + "/play spleef " + ChatColor.GRAY + "\u00BB " + ChatColor.WHITE + "Break blocks and make others fall!");
        sender.sendMessage(ChatColor.YELLOW + "/play paintball " + ChatColor.GRAY + "\u00BB " + ChatColor.WHITE + "Shoot your opponents with paint!");
        sender.sendMessage(ChatColor.YELLOW + "/play arcade " + ChatColor.GRAY + "\u00BB " + ChatColor.WHITE + "Random fun minigames!");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Select a game to join!");
        sender.sendMessage(ChatColor.GREEN + "\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC");
    }

    /**
     * Format minigame name for display
     */
    private String formatMinigameName(String minigameType) {
        switch (minigameType.toLowerCase()) {
            case "bedwars":
                return "Bed Wars";
            case "skywars":
                return "Sky Wars";
            case "tntrun":
                return "TNT Run";
            case "spleef":
                return "Spleef";
            case "paintball":
                return "Paintball";
            case "arcade":
                return "Arcade";
            default:
                return minigameType.substring(0, 1).toUpperCase() + minigameType.substring(1);
        }
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
