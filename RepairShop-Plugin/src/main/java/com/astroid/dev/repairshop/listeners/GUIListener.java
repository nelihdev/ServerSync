package com.astroid.dev.repairshop.listeners;

import com.astroid.dev.repairshop.RepairShop;
import com.astroid.dev.repairshop.gui.RepairGUI;
import com.astroid.dev.repairshop.managers.ConfigManager;
import com.astroid.dev.repairshop.managers.RepairManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class GUIListener implements Listener {

    private final RepairShop plugin;

    public GUIListener(RepairShop plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        if (!(event.getInventory().getHolder() instanceof RepairGUI)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        RepairGUI gui = (RepairGUI) event.getInventory().getHolder();
        ConfigManager configManager = plugin.getConfigManager();
        RepairManager repairManager = plugin.getRepairManager();

        int slot = event.getRawSlot();

        // Clicked in player's inventory
        if (slot >= event.getInventory().getSize()) {
            event.setCancelled(true);
            return;
        }

        int itemSlot = configManager.getItemToRepairSlot();
        int repairButtonSlot = configManager.getRepairButtonSlot();

        // Allow placing/removing item in the repair slot
        if (slot == itemSlot) {
            event.setCancelled(false);

            // Schedule update after item is placed/removed
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                ItemStack item = event.getInventory().getItem(itemSlot);
                gui.setItemToRepair(item);

                Sound sound = configManager.getSound("item-place");
                if (sound != null && item != null && item.getType() != Material.AIR) {
                    player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                }
            }, 1L);
            return;
        }

        // Cancel clicks on other slots
        event.setCancelled(true);

        // Handle repair button click
        if (slot == repairButtonSlot) {
            handleRepairClick(player, gui);
        } else {
            // Play click sound for other buttons
            Sound sound = configManager.getSound("button-click");
            if (sound != null) {
                player.playSound(player.getLocation(), sound, 1.0f, 0.8f);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof RepairGUI)) {
            return;
        }

        ConfigManager configManager = plugin.getConfigManager();
        int itemSlot = configManager.getItemToRepairSlot();

        // Only allow dragging in the item slot
        for (int slot : event.getRawSlots()) {
            if (slot < event.getInventory().getSize() && slot != itemSlot) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof RepairGUI)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        RepairGUI gui = (RepairGUI) event.getInventory().getHolder();
        ConfigManager configManager = plugin.getConfigManager();

        // Return item to player
        ItemStack item = event.getInventory().getItem(configManager.getItemToRepairSlot());
        if (item != null && item.getType() != Material.AIR) {
            player.getInventory().addItem(item);
        }
    }

    private void handleRepairClick(Player player, RepairGUI gui) {
        ConfigManager configManager = plugin.getConfigManager();
        RepairManager repairManager = plugin.getRepairManager();

        ItemStack itemToRepair = gui.getItemToRepair();

        // Check if item exists
        if (itemToRepair == null || itemToRepair.getType() == Material.AIR) {
            player.sendMessage(configManager.getMessageWithPrefix("place-item"));
            playFailSound(player);
            return;
        }

        // Check if item can be repaired
        if (!repairManager.canRepair(itemToRepair)) {
            player.sendMessage(configManager.getMessageWithPrefix("item-already-repaired"));
            playFailSound(player);
            return;
        }

        // Calculate cost
        int cost = repairManager.calculateRepairCost(itemToRepair);

        // Check if player can afford
        if (!repairManager.canAfford(player, cost)) {
            String currencyType = configManager.getCurrencyType().toString().toLowerCase();
            String messageKey = currencyType.equals("experience") ? "not-enough-xp" : "not-enough-gold";
            player.sendMessage(configManager.getMessageWithPrefix(messageKey)
                    .replace("%cost%", String.valueOf(cost)));
            playFailSound(player);
            return;
        }

        // Take payment
        if (!repairManager.takePayment(player, cost)) {
            player.sendMessage(configManager.getMessageWithPrefix("not-enough-gold"));
            playFailSound(player);
            return;
        }

        // Repair item
        repairManager.repairItem(itemToRepair);

        // Send success message
        player.sendMessage(configManager.getMessageWithPrefix("repair-success"));

        // Play success sound
        Sound sound = configManager.getSound("repair-success");
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }

        // Update GUI
        gui.refresh();
    }

    private void playFailSound(Player player) {
        Sound sound = plugin.getConfigManager().getSound("repair-fail");
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }
    }
}

