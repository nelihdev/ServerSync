package com.astroid.dev.serverSync.commands;

import com.astroid.dev.serverSync.ServerSync;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

public class ServerSyncCommand extends Command {

    private final ServerSync plugin;

    public ServerSyncCommand(ServerSync plugin) {
        super("serversync", "serversync.admin", "ss");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.reloadConfiguration();
                sender.sendMessage(ChatColor.GREEN + "✓ ServerSync configuration reloaded!");
                break;

            case "sync":
                plugin.forceSynchronize();
                sender.sendMessage(ChatColor.GREEN + "✓ Manual synchronization started...");
                break;

            case "status":
                sendStatus(sender);
                break;

            case "list":
                sendManagedServers(sender);
                break;

            default:
                sendHelp(sender);
                break;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "╔════════ ServerSync Commands ════════╗");
        sender.sendMessage(ChatColor.YELLOW + "/serversync reload" + ChatColor.GRAY + " - Reload the configuration");
        sender.sendMessage(ChatColor.YELLOW + "/serversync sync" + ChatColor.GRAY + " - Force manual synchronization");
        sender.sendMessage(ChatColor.YELLOW + "/serversync status" + ChatColor.GRAY + " - Show plugin status");
        sender.sendMessage(ChatColor.YELLOW + "/serversync list" + ChatColor.GRAY + " - Show managed servers");
        sender.sendMessage(ChatColor.GOLD + "╚════════════════════════════════════╝");
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "╔════════ ServerSync Status ════════╗");

        // Redis status
        boolean redisConnected = plugin.isRedisConnected();
        sender.sendMessage(ChatColor.YELLOW + "Redis: " +
                (redisConnected ? ChatColor.GREEN + "✓ Connected" : ChatColor.RED + "✗ Offline"));

        // RabbitMQ status
        boolean rabbitConnected = plugin.isRabbitMQConnected();
        sender.sendMessage(ChatColor.YELLOW + "RabbitMQ: " +
                (rabbitConnected ? ChatColor.GREEN + "✓ Connected" : ChatColor.RED + "✗ Offline"));

        // Server count
        sender.sendMessage(ChatColor.YELLOW + "Managed Servers: " + ChatColor.WHITE +
                plugin.getManagedServerCount());

        // Intervals
        sender.sendMessage(ChatColor.YELLOW + "Sync Interval: " + ChatColor.WHITE +
                plugin.getSyncInterval() + "s");
        sender.sendMessage(ChatColor.YELLOW + "Health Check: " + ChatColor.WHITE +
                plugin.getHealthCheckInterval() + "s");

        sender.sendMessage(ChatColor.GOLD + "╚═══════════════════════════════════╝");
    }

    private void sendManagedServers(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "╔═══════ Managed Servers ═══════╗");

        if (plugin.getManagedServerCount() == 0) {
            sender.sendMessage(ChatColor.GRAY + "  No servers are being managed.");
        } else {
            for (String serverName : plugin.getManagedServers()) {
                sender.sendMessage(ChatColor.YELLOW + "  • " + ChatColor.WHITE + serverName);
            }
        }

        sender.sendMessage(ChatColor.GOLD + "╚════════════════════════════════╝");
    }
}
