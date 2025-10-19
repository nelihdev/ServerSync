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
                sender.sendMessage(ChatColor.GREEN + "\u2714 " + ChatColor.BOLD + "Success! " + ChatColor.GREEN + "Configuration has been reloaded.");
                break;

            case "sync":
                plugin.forceSynchronize();
                sender.sendMessage(ChatColor.GREEN + "\u2714 " + ChatColor.BOLD + "Success! " + ChatColor.GREEN + "Manual synchronization started.");
                break;

            case "status":
                sendStatus(sender);
                break;

            case "list":
                sendManagedServers(sender);
                break;

            case "info":
                sendInfo(sender);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "\u2716 " + ChatColor.BOLD + "Error! " + ChatColor.RED + "Unknown subcommand.");
                sendHelp(sender);
                break;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC");
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "                 ServerSync Commands");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "/serversync reload " + ChatColor.GRAY + "\u00BB " + ChatColor.WHITE + "Reload the configuration");
        sender.sendMessage(ChatColor.YELLOW + "/serversync sync " + ChatColor.GRAY + "\u00BB " + ChatColor.WHITE + "Force manual synchronization");
        sender.sendMessage(ChatColor.YELLOW + "/serversync status " + ChatColor.GRAY + "\u00BB " + ChatColor.WHITE + "View connection status");
        sender.sendMessage(ChatColor.YELLOW + "/serversync list " + ChatColor.GRAY + "\u00BB " + ChatColor.WHITE + "List all managed servers");
        sender.sendMessage(ChatColor.YELLOW + "/serversync info " + ChatColor.GRAY + "\u00BB " + ChatColor.WHITE + "View plugin information");
        sender.sendMessage(ChatColor.GREEN + "\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC");
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC");
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "                   ServerSync Status");
        sender.sendMessage("");

        // Redis status
        boolean redisConnected = plugin.isRedisConnected();
        sender.sendMessage(ChatColor.YELLOW + "Redis Connection: " +
                (redisConnected ? ChatColor.GREEN + "\u25CF " + ChatColor.BOLD + "ONLINE" : ChatColor.RED + "\u25CF " + ChatColor.BOLD + "OFFLINE"));

        // RabbitMQ status
        boolean rabbitConnected = plugin.isRabbitMQConnected();
        sender.sendMessage(ChatColor.YELLOW + "RabbitMQ Connection: " +
                (rabbitConnected ? ChatColor.GREEN + "\u25CF " + ChatColor.BOLD + "ONLINE" : ChatColor.RED + "\u25CF " + ChatColor.BOLD + "OFFLINE"));

        sender.sendMessage("");

        // Server count
        sender.sendMessage(ChatColor.YELLOW + "Managed Servers: " + ChatColor.WHITE + ChatColor.BOLD +
                plugin.getManagedServerCount());

        // Intervals
        sender.sendMessage(ChatColor.YELLOW + "Sync Interval: " + ChatColor.WHITE + ChatColor.BOLD +
                plugin.getSyncInterval() + "s");
        sender.sendMessage(ChatColor.YELLOW + "Health Check Interval: " + ChatColor.WHITE + ChatColor.BOLD +
                plugin.getHealthCheckInterval() + "s");

        sender.sendMessage(ChatColor.GREEN + "\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC");
    }

    private void sendManagedServers(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC");
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "                  Managed Servers");
        sender.sendMessage("");

        if (plugin.getManagedServerCount() == 0) {
            sender.sendMessage(ChatColor.GRAY + "  No servers are currently being managed.");
        } else {
            for (String serverName : plugin.getManagedServers()) {
                sender.sendMessage(ChatColor.YELLOW + "  \u25AA " + ChatColor.WHITE + serverName);
            }
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Total: " + ChatColor.WHITE + plugin.getManagedServerCount() + ChatColor.GRAY + " server(s)");
        sender.sendMessage(ChatColor.GREEN + "\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC");
    }

    private void sendInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC");
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "                  ServerSync v1.0.0");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Author: " + ChatColor.WHITE + "AstroidMC Development Team");
        sender.sendMessage(ChatColor.YELLOW + "Description: " + ChatColor.WHITE + "Dynamic minigame server management");
        sender.sendMessage(ChatColor.YELLOW + "Website: " + ChatColor.WHITE + "www.astroidmc.com");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "\u00A9 2025 AstroidMC. All rights reserved.");
        sender.sendMessage(ChatColor.GRAY + "This software is proprietary and confidential.");
        sender.sendMessage(ChatColor.GREEN + "\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC\u25AC");
    }
}
