package com.astroid.dev.serverSync;

import com.astroid.dev.serverSync.commands.PlayCommand;
import com.astroid.dev.serverSync.commands.ServerSyncCommand;
import com.astroid.dev.serverSync.discord.DiscordWebhook;
import com.astroid.dev.serverSync.handlers.ServerRegistrationHandler;
import com.astroid.dev.serverSync.pterodactyl.PterodactylApiClient;
import com.astroid.dev.serverSync.rabbitmq.RabbitMQManager;
import com.astroid.dev.serverSync.redis.RedisManager;
import com.astroid.dev.serverSync.sync.ServerSynchronizer;
import com.astroid.dev.serverSync.tasks.HealthCheckTask;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class ServerSync extends Plugin {

    private RedisManager redisManager;
    private RabbitMQManager rabbitMQManager;
    private PterodactylApiClient pterodactylClient;
    private ServerSynchronizer serverSynchronizer;
    private ServerRegistrationHandler registrationHandler;
    private DiscordWebhook discordWebhook;
    private Configuration config;

    private int syncTaskId = -1;
    private int healthCheckTaskId = -1;
    private int syncInterval;
    private int healthCheckInterval;

    @Override
    public void onEnable() {
        // Plugin startup logic
        getLogger().info("╔════════════════════════════════════╗");
        getLogger().info("║     ServerSync is starting...     ║");
        getLogger().info("╚════════════════════════════════════╝");

        // Load configuration
        loadConfiguration();

        // Initialize Discord webhook
        initializeDiscordWebhook();

        // Initialize Redis
        if (!initializeRedis()) {
            getLogger().severe("Cannot connect to Redis! Plugin will be disabled.");
            if (discordWebhook != null && discordWebhook.isEnabled()) {
                discordWebhook.logError("Redis Connection Failed", "Cannot connect to Redis! Plugin will be disabled.");
            }
            return;
        }

        // Initialize RabbitMQ
        if (!initializeRabbitMQ()) {
            getLogger().warning("RabbitMQ not available - dynamic server registration is disabled");
            if (discordWebhook != null && discordWebhook.isEnabled()) {
                discordWebhook.logWarning("RabbitMQ Connection Failed", "RabbitMQ not available - dynamic server registration is disabled");
            }
        }

        // Initialize Pterodactyl API client
        initializePterodactyl();

        // Initialize handlers
        initializeHandlers();

        // Initialize synchronizer
        initializeSynchronizer();

        // Start periodic tasks
        startSyncTask();
        startHealthCheckTask();

        // Register commands
        registerCommands();

        getLogger().info("╔════════════════════════════════════╗");
        getLogger().info("║  ServerSync started successfully! ║");
        getLogger().info("╚════════════════════════════════════╝");

        // Log to Discord
        if (discordWebhook != null && discordWebhook.isEnabled() &&
            config.getBoolean("discord.log-events.startup", true)) {
            discordWebhook.logPluginStartup(getManagedServerCount());
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("ServerSync is stopping...");

        // Log to Discord
        if (discordWebhook != null && discordWebhook.isEnabled() &&
            config.getBoolean("discord.log-events.shutdown", true)) {
            discordWebhook.logPluginShutdown();
        }

        // Stop periodic tasks
        if (syncTaskId != -1) {
            getProxy().getScheduler().cancel(syncTaskId);
        }
        if (healthCheckTaskId != -1) {
            getProxy().getScheduler().cancel(healthCheckTaskId);
        }

        // Close connections
        if (rabbitMQManager != null) {
            rabbitMQManager.disconnect();
        }
        if (redisManager != null) {
            redisManager.disconnect();
        }
        if (pterodactylClient != null) {
            pterodactylClient.shutdown();
        }
        if (discordWebhook != null) {
            discordWebhook.shutdown();
        }

        getLogger().info("ServerSync stopped!");
    }

    private void loadConfiguration() {
        try {
            // Create plugin directory if it doesn't exist
            if (!getDataFolder().exists()) {
                getDataFolder().mkdir();
            }

            File configFile = new File(getDataFolder(), "config.yml");

            // Copy default config if it doesn't exist
            if (!configFile.exists()) {
                try (InputStream in = getResourceAsStream("config.yml")) {
                    Files.copy(in, configFile.toPath());
                }
            }

            // Load configuration
            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
            syncInterval = config.getInt("sync-interval", 5);
            healthCheckInterval = config.getInt("health-check-interval", 30);

        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Error loading configuration", e);
        }
    }

    private void initializeDiscordWebhook() {
        boolean enabled = config.getBoolean("discord.enabled", false);
        String webhookUrl = config.getString("discord.webhook-url", "");
        String botName = config.getString("discord.bot-name", "ServerSync");
        String botAvatar = config.getString("discord.bot-avatar", "");

        discordWebhook = new DiscordWebhook(getLogger(), webhookUrl, enabled, botName, botAvatar);

        if (enabled) {
            getLogger().info("✓ Discord webhook initialized");
        } else {
            getLogger().info("Discord webhook is disabled");
        }
    }

    private boolean initializeRedis() {
        String host = config.getString("redis.host", "localhost");
        int port = config.getInt("redis.port", 6379);
        String password = config.getString("redis.password", "");
        int timeout = config.getInt("redis.timeout", 2000);

        redisManager = new RedisManager(host, port, password, timeout);
        boolean connected = redisManager.connect();

        if (connected) {
            getLogger().info(String.format("✓ Connected to Redis at %s:%d", host, port));
        } else {
            getLogger().severe(String.format("✗ Cannot connect to Redis at %s:%d", host, port));
        }

        return connected;
    }

    private boolean initializeRabbitMQ() {
        boolean enabled = config.getBoolean("rabbitmq.enabled", true);
        if (!enabled) {
            getLogger().info("RabbitMQ is disabled in configuration");
            return false;
        }

        String host = config.getString("rabbitmq.host", "localhost");
        int port = config.getInt("rabbitmq.port", 5672);
        String username = config.getString("rabbitmq.username", "guest");
        String password = config.getString("rabbitmq.password", "guest");

        rabbitMQManager = new RabbitMQManager(getLogger(), host, port, username, password);
        boolean connected = rabbitMQManager.connect();

        if (connected) {
            getLogger().info(String.format("✓ Connected to RabbitMQ at %s:%d", host, port));
            setupRabbitMQListeners();
            return true;
        } else {
            getLogger().warning(String.format("✗ Cannot connect to RabbitMQ at %s:%d", host, port));
            return false;
        }
    }

    private void setupRabbitMQListeners() {
        String serverReadyQueue = config.getString("rabbitmq.queues.server-ready", "server_ready");
        String serverEmptyQueue = config.getString("rabbitmq.queues.server-empty", "server_empty");

        // Subscribe to server_ready events
        // IMPORTANT: Process synchronously to ensure proper ACK timing
        // RabbitMQ with basicQos(1) will wait for ACK before sending next message
        rabbitMQManager.subscribe(serverReadyQueue, message -> {
            try {
                if (registrationHandler != null) {
                    getLogger().info("[ServerSync] Processing server_ready event...");
                    registrationHandler.handleServerReady(message);
                    getLogger().info("[ServerSync] ✓ server_ready processed");
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error processing server_ready", e);
                if (discordWebhook != null && discordWebhook.isEnabled() &&
                    config.getBoolean("discord.log-events.errors", true)) {
                    discordWebhook.logError("Error Processing server_ready", e.getMessage());
                }
            }
        });

        // Subscribe to server_empty events
        rabbitMQManager.subscribe(serverEmptyQueue, message -> {
            try {
                if (registrationHandler != null) {
                    getLogger().info("[ServerSync] Processing server_empty event...");
                    registrationHandler.handleServerEmpty(message);
                    getLogger().info("[ServerSync] ✓ server_empty processed");
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error processing server_empty", e);
                if (discordWebhook != null && discordWebhook.isEnabled() &&
                    config.getBoolean("discord.log-events.errors", true)) {
                    discordWebhook.logError("Error Processing server_empty", e.getMessage());
                }
            }
        });

        getLogger().info("✓ RabbitMQ listeners registered");
    }

    private void initializePterodactyl() {
        boolean enabled = config.getBoolean("pterodactyl.enabled", true);
        String panelUrl = config.getString("pterodactyl.panel-url", "");
        String apiKey = config.getString("pterodactyl.api-key", "");
        int nodeId = config.getInt("pterodactyl.node-id", 1);

        pterodactylClient = new PterodactylApiClient(getLogger(), panelUrl, apiKey, nodeId, enabled);

        if (enabled) {
            getLogger().info("✓ Pterodactyl API client initialized");
        }
    }

    private void initializeHandlers() {
        String nameFormat = config.getString("registration.name-format", "{type}-{id}");
        String motdFormat = config.getString("registration.motd-format", "{type} Arena #{id}");
        boolean restricted = config.getBoolean("registration.restricted", false);
        String defaultIp = config.getString("pterodactyl.default-ip", "172.18.0.1");

        registrationHandler = new ServerRegistrationHandler(
                getLogger(),
                redisManager,
                pterodactylClient,
                nameFormat,
                motdFormat,
                restricted,
                defaultIp,
                this,  // Pass plugin reference for scheduler
                discordWebhook,  // Pass Discord webhook
                config  // Pass config for log settings
        );

        getLogger().info("✓ Server Registration Handler initialized (default IP: " + defaultIp + ")");
    }

    private void initializeSynchronizer() {
        List<String> serverKeys = config.getStringList("server-keys");
        boolean loggingEnabled = config.getBoolean("logging.enabled", true);
        boolean verboseLogging = config.getBoolean("logging.verbose", false);

        serverSynchronizer = new ServerSynchronizer(
                this,
                redisManager,
                serverKeys,
                loggingEnabled,
                verboseLogging
        );

        getLogger().info("✓ Server Synchronizer initialized with " + serverKeys.size() + " server keys");
    }

    private void startSyncTask() {
        // Stop existing task if running
        if (syncTaskId != -1) {
            getProxy().getScheduler().cancel(syncTaskId);
        }

        // Start new async task
        syncTaskId = getProxy().getScheduler().schedule(this, () -> {
            try {
                serverSynchronizer.synchronize();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error during synchronization", e);
            }
        }, 0, syncInterval, TimeUnit.SECONDS).getId();

        getLogger().info("✓ Synchronization task started (interval: " + syncInterval + "s)");
    }

    private void startHealthCheckTask() {
        boolean verboseLogging = config.getBoolean("logging.verbose", false);
        List<String> serverKeys = config.getStringList("server-keys");
        String playerCountQueue = config.getString("rabbitmq.queues.player-count", "player_count");
        String proxyId = config.getString("proxy.id", "proxy-1");

        HealthCheckTask healthCheckTask = new HealthCheckTask(
                getLogger(),
                redisManager,
                rabbitMQManager,
                registrationHandler,
                serverKeys,
                verboseLogging,
                playerCountQueue,
                proxyId
        );

        healthCheckTaskId = getProxy().getScheduler().schedule(
                this,
                healthCheckTask,
                healthCheckInterval,
                healthCheckInterval,
                TimeUnit.SECONDS
        ).getId();

        getLogger().info("✓ Health Check task started (interval: " + healthCheckInterval + "s)");
    }

    private void registerCommands() {
        // Admin command
        getProxy().getPluginManager().registerCommand(this, new ServerSyncCommand(this));

        // Play command
        if (config.getBoolean("load-balancing.enabled", true)) {
            String strategy = config.getString("load-balancing.strategy", "LEAST_PLAYERS");
            boolean autoSpawn = config.getBoolean("load-balancing.auto-spawn-on-full", true);
            String spawnQueue = config.getString("rabbitmq.queues.spawn-request", "spawn_request");

            getProxy().getPluginManager().registerCommand(this,
                    new PlayCommand(registrationHandler, rabbitMQManager, spawnQueue, strategy, autoSpawn));

            getLogger().info("✓ Commands registered (/serversync, /play)");
        } else {
            getLogger().info("✓ Command registered (/serversync)");
        }
    }

    public void reloadConfiguration() {
        loadConfiguration();

        // Reinitialize Redis if connection details have changed
        if (redisManager != null) {
            redisManager.disconnect();
        }

        if (initializeRedis()) {
            initializeHandlers();
            initializeSynchronizer();
            startSyncTask();
            startHealthCheckTask();
            getLogger().info("Configuration and connections reloaded");
        } else {
            getLogger().severe("Could not restore Redis connection after reload");
        }
    }

    public void forceSynchronize() {
        getProxy().getScheduler().runAsync(this, () -> {
            try {
                serverSynchronizer.synchronize();
                getLogger().info("Manual synchronization completed");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error during manual synchronization", e);
            }
        });
    }

    public boolean isRedisConnected() {
        return redisManager != null && redisManager.isConnected();
    }

    public boolean isRabbitMQConnected() {
        return rabbitMQManager != null && rabbitMQManager.isConnected();
    }

    public int getManagedServerCount() {
        return registrationHandler != null ? registrationHandler.getManagedServers().size() : 0;
    }

    public Set<String> getManagedServers() {
        return registrationHandler != null ? registrationHandler.getManagedServers() : Collections.emptySet();
    }

    public int getSyncInterval() {
        return syncInterval;
    }

    public int getHealthCheckInterval() {
        return healthCheckInterval;
    }

    public DiscordWebhook getDiscordWebhook() {
        return discordWebhook;
    }
}
