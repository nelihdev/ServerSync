package com.astroid.dev.serverSync.redis;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

import java.util.*;

public class RedisManager {

    private JedisPool jedisPool;
    private final String host;
    private final int port;
    private final String password;
    private final int timeout;
    private final Gson gson;

    public RedisManager(String host, int port, String password, int timeout) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.timeout = timeout;
        this.gson = new Gson();
    }

    public boolean connect() {
        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(20);
            poolConfig.setMaxIdle(10);
            poolConfig.setMinIdle(5);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestOnReturn(true);
            poolConfig.setTestWhileIdle(true);

            if (password == null || password.isEmpty()) {
                jedisPool = new JedisPool(poolConfig, host, port, timeout);
            } else {
                jedisPool = new JedisPool(poolConfig, host, port, timeout, password);
            }

            // Test connection
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
            }

            return true;
        } catch (JedisException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void disconnect() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }

    public Map<String, String> getServerData(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hgetAll(key);
        } catch (JedisException e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    public Set<String> getAllServers(List<String> serverKeys) {
        Set<String> allServers = new HashSet<>();

        try (Jedis jedis = jedisPool.getResource()) {
            for (String pattern : serverKeys) {
                // Haal alle keys op die aan het pattern voldoen
                Set<String> keys = jedis.keys(pattern + ":*");

                for (String key : keys) {
                    // Haal server data op
                    Map<String, String> serverData = jedis.hgetAll(key);

                    if (serverData != null && !serverData.isEmpty()) {
                        String serverName = serverData.get("name");
                        if (serverName != null && !serverName.isEmpty()) {
                            allServers.add(serverName);
                        }
                    }
                }
            }
        } catch (JedisException e) {
            e.printStackTrace();
        }

        return allServers;
    }

    public List<ServerData> getAllServerData(List<String> serverKeys) {
        List<ServerData> servers = new ArrayList<>();

        try (Jedis jedis = jedisPool.getResource()) {
            for (String pattern : serverKeys) {
                // Haal alle keys op die aan het pattern voldoen
                Set<String> keys = jedis.keys(pattern + ":*");

                for (String key : keys) {
                    // Haal server data op
                    Map<String, String> serverData = jedis.hgetAll(key);

                    if (serverData != null && !serverData.isEmpty()) {
                        String name = serverData.get("name");
                        String host = serverData.get("host");
                        String portStr = serverData.get("port");
                        String gameType = serverData.get("gameType");

                        if (name != null && host != null && portStr != null) {
                            try {
                                int port = Integer.parseInt(portStr);
                                servers.add(new ServerData(name, host, port, gameType));
                            } catch (NumberFormatException e) {
                                // Invalid port, skip
                            }
                        }
                    }
                }
            }
        } catch (JedisException e) {
            e.printStackTrace();
        }

        return servers;
    }

    /**
     * Get list of server IDs from Redis key (format: minecraft:servers:{type})
     * Value is a JSON array: ["42", "43", "44"]
     */
    public List<String> getServerIds(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            String value = jedis.get(key);
            if (value == null || value.isEmpty()) {
                return new ArrayList<>();
            }

            JsonArray jsonArray = JsonParser.parseString(value).getAsJsonArray();
            List<String> serverIds = new ArrayList<>();

            jsonArray.forEach(element -> serverIds.add(element.getAsString()));

            return serverIds;
        } catch (JedisException | com.google.gson.JsonSyntaxException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public boolean isConnected() {
        if (jedisPool == null || jedisPool.isClosed()) {
            return false;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.ping();
            return true;
        } catch (JedisException e) {
            return false;
        }
    }

    public static class ServerData {
        private final String name;
        private final String host;
        private final int port;
        private final String gameType;

        public ServerData(String name, String host, int port, String gameType) {
            this.name = name;
            this.host = host;
            this.port = port;
            this.gameType = gameType;
        }

        public String getName() {
            return name;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getGameType() {
            return gameType;
        }
    }
}
