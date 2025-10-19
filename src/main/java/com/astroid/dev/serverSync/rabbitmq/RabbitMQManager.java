package com.astroid.dev.serverSync.rabbitmq;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RabbitMQManager {

    private final Logger logger;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final Gson gson;

    private Connection connection;
    private Channel channel;
    private final Map<String, String> consumerTags = new ConcurrentHashMap<>();
    private boolean connected = false;

    public RabbitMQManager(Logger logger, String host, int port, String username, String password) {
        this.logger = logger;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.gson = new Gson();
    }

    /**
     * Connect to RabbitMQ server
     */
    public boolean connect() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(host);
            factory.setPort(port);
            factory.setUsername(username);
            factory.setPassword(password);

            // Automatic recovery settings
            factory.setAutomaticRecoveryEnabled(true);
            factory.setNetworkRecoveryInterval(10000); // 10 seconds
            factory.setConnectionTimeout(5000);
            factory.setRequestedHeartbeat(30);

            connection = factory.newConnection();
            channel = connection.createChannel();

            connected = true;
            logger.info(String.format("Verbonden met RabbitMQ op %s:%d", host, port));
            return true;

        } catch (IOException | TimeoutException e) {
            logger.log(Level.SEVERE, "Kan geen verbinding maken met RabbitMQ: " + e.getMessage(), e);
            connected = false;
            return false;
        }
    }

    /**
     * Disconnect from RabbitMQ
     */
    public void disconnect() {
        try {
            // Cancel all consumers
            for (String tag : consumerTags.values()) {
                try {
                    if (channel != null && channel.isOpen()) {
                        channel.basicCancel(tag);
                    }
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Fout bij annuleren van consumer", e);
                }
            }
            consumerTags.clear();

            // Close channel and connection
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
            if (connection != null && connection.isOpen()) {
                connection.close();
            }

            connected = false;
            logger.info("RabbitMQ connectie gesloten");

        } catch (IOException | TimeoutException e) {
            logger.log(Level.WARNING, "Fout bij sluiten RabbitMQ connectie", e);
        }
    }

    /**
     * Subscribe to a queue and process messages
     */
    public boolean subscribe(String queueName, Consumer<JsonObject> messageHandler) {
        if (!connected || channel == null || !channel.isOpen()) {
            logger.warning("Kan niet subscriben - RabbitMQ niet verbonden");
            return false;
        }

        try {
            // Declare queue (idempotent)
            channel.queueDeclare(queueName, true, false, false, null);

            // CRITICAL FIX: Set prefetch to 1 - process ONE message at a time
            // Without this, RabbitMQ sends all messages at once and they can overlap
            // causing the second server to fail registration
            channel.basicQos(1);
            logger.info("[RabbitMQ] QoS ingesteld voor queue '" + queueName + "': prefetch=1 (één bericht per keer)");

            // Create consumer
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                try {
                    String message = new String(delivery.getBody(), "UTF-8");
                    long deliveryTag = delivery.getEnvelope().getDeliveryTag();

                    logger.info(String.format("[RabbitMQ] Ontvangen van '%s' (tag=%d): %s",
                            queueName, deliveryTag, message));

                    JsonObject json = gson.fromJson(message, JsonObject.class);

                    // Process message - handler is responsible for error handling
                    messageHandler.accept(json);

                    // IMPORTANT: Acknowledge AFTER processing completes
                    // This ensures next message only comes after current is fully handled
                    channel.basicAck(deliveryTag, false);
                    logger.info(String.format("[RabbitMQ] ✓ ACK verzonden (tag=%d)", deliveryTag));

                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Fout bij verwerken RabbitMQ bericht van queue " + queueName, e);

                    // Reject message and don't requeue
                    try {
                        long deliveryTag = delivery.getEnvelope().getDeliveryTag();
                        channel.basicNack(deliveryTag, false, false);
                        logger.warning(String.format("[RabbitMQ] ✗ NACK verzonden (tag=%d)", deliveryTag));
                    } catch (IOException ioException) {
                        logger.log(Level.SEVERE, "Fout bij nack van bericht", ioException);
                    }
                }
            };

            CancelCallback cancelCallback = consumerTag -> {
                logger.warning("Consumer geannuleerd voor queue: " + queueName);
                consumerTags.remove(queueName);
            };

            // Start consuming
            String consumerTag = channel.basicConsume(queueName, false, deliverCallback, cancelCallback);
            consumerTags.put(queueName, consumerTag);

            logger.info("Subscribing op RabbitMQ queue: " + queueName);
            return true;

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Fout bij subscriben op queue " + queueName, e);
            return false;
        }
    }

    /**
     * Publish a message to a queue
     */
    public boolean publish(String queueName, JsonObject message) {
        if (!connected || channel == null || !channel.isOpen()) {
            logger.warning("Kan niet publishen - RabbitMQ niet verbonden");
            return false;
        }

        try {
            // Declare queue (idempotent)
            channel.queueDeclare(queueName, true, false, false, null);

            // Publish message
            String messageJson = gson.toJson(message);
            channel.basicPublish("", queueName,
                    MessageProperties.PERSISTENT_TEXT_PLAIN,
                    messageJson.getBytes("UTF-8"));

            return true;

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Fout bij publishen naar queue " + queueName, e);
            return false;
        }
    }

    /**
     * Publish player count update to cloud controller
     *
     * @param queueName Queue name (player_count)
     * @param serverId Server ID
     * @param serverType Server type (bedwars, skywars, etc)
     * @param playerCount Current player count
     * @return true if successful
     */
    public boolean publishPlayerCount(String queueName, String serverId, String serverType, int playerCount) {
        JsonObject message = new JsonObject();
        message.addProperty("server_id", serverId);
        message.addProperty("type", serverType);
        message.addProperty("player_count", playerCount);

        return publish(queueName, message);
    }

    /**
     * Check if connected to RabbitMQ
     */
    public boolean isConnected() {
        return connected && connection != null && connection.isOpen() &&
                channel != null && channel.isOpen();
    }

    /**
     * Reconnect to RabbitMQ with exponential backoff
     */
    public boolean reconnect(int maxAttempts) {
        int attempts = 0;
        int backoff = 1000; // Start with 1 second

        while (attempts < maxAttempts) {
            attempts++;
            logger.info(String.format("Reconnect poging %d/%d...", attempts, maxAttempts));

            if (connect()) {
                return true;
            }

            try {
                Thread.sleep(backoff);
                backoff = Math.min(backoff * 2, 30000); // Max 30 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }
}

