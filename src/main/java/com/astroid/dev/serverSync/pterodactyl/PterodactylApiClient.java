package com.astroid.dev.serverSync.pterodactyl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PterodactylApiClient {

    private final Logger logger;
    private final String panelUrl;
    private final String apiKey;
    private final int nodeId;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final boolean enabled;

    public PterodactylApiClient(Logger logger, String panelUrl, String apiKey, int nodeId, boolean enabled) {
        this.logger = logger;
        this.panelUrl = panelUrl.endsWith("/") ? panelUrl.substring(0, panelUrl.length() - 1) : panelUrl;
        this.apiKey = apiKey;
        this.nodeId = nodeId;
        this.enabled = enabled;
        this.gson = new Gson();

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Get server details by server ID (async)
     */
    public CompletableFuture<Optional<ServerDetails>> getServerDetailsAsync(String serverId) {
        if (!enabled) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return getServerDetails(serverId);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Fout bij ophalen server details voor ID " + serverId, e);
                return Optional.empty();
            }
        });
    }

    /**
     * Get server details by server ID using Pterodactyl API
     * Docs: https://dashflo.net/docs/api/pterodactyl/v1/#req_get-servers
     */
    public Optional<ServerDetails> getServerDetails(String serverId) throws IOException {
        if (!enabled) {
            return Optional.empty();
        }

        try {
            // Method 1: Try by internal ID
            Optional<ServerDetails> byId = getServerById(serverId);
            if (byId.isPresent()) {
                return byId;
            }

            // Method 2: Try by external ID (Docker container ID often used)
            Optional<ServerDetails> byExternalId = getServerByExternalId(serverId);
            if (byExternalId.isPresent()) {
                return byExternalId;
            }

            // Method 3: Search all servers for matching identifier
            return searchServerByIdentifier(serverId);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Pterodactyl API fout voor server " + serverId, e);
            return Optional.empty();
        }
    }

    /**
     * Get server by internal Pterodactyl ID
     */
    private Optional<ServerDetails> getServerById(String serverId) throws IOException {
        String url = panelUrl + "/api/application/servers/" + serverId + "?include=allocations";

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return Optional.empty();
            }

            if (response.body() == null) {
                return Optional.empty();
            }

            String body = response.body().string();
            JsonObject json = gson.fromJson(body, JsonObject.class);

            return parseServerDetails(json);
        }
    }

    /**
     * Get server by external ID
     */
    private Optional<ServerDetails> getServerByExternalId(String externalId) throws IOException {
        String url = panelUrl + "/api/application/servers?filter[external_id]=" + externalId + "&include=allocations";

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return Optional.empty();
            }

            if (response.body() == null) {
                return Optional.empty();
            }

            String body = response.body().string();
            JsonObject json = gson.fromJson(body, JsonObject.class);
            JsonArray data = json.getAsJsonArray("data");

            if (data != null && data.size() > 0) {
                return parseServerDetails(data.get(0).getAsJsonObject());
            }
        }

        return Optional.empty();
    }

    /**
     * Search for server by identifier/name
     */
    private Optional<ServerDetails> searchServerByIdentifier(String identifier) throws IOException {
        String url = panelUrl + "/api/application/servers?include=allocations";

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return Optional.empty();
            }

            if (response.body() == null) {
                return Optional.empty();
            }

            String body = response.body().string();
            JsonObject json = gson.fromJson(body, JsonObject.class);
            JsonArray data = json.getAsJsonArray("data");

            if (data == null) {
                return Optional.empty();
            }

            // Search through all servers for matching identifier
            for (int i = 0; i < data.size(); i++) {
                JsonObject serverObj = data.get(i).getAsJsonObject();
                JsonObject attributes = serverObj.getAsJsonObject("attributes");

                if (attributes == null) continue;

                String serverId = attributes.has("id") ? String.valueOf(attributes.get("id").getAsInt()) : null;
                String serverIdentifier = attributes.has("identifier") ? attributes.get("identifier").getAsString() : null;
                String externalId = attributes.has("external_id") && !attributes.get("external_id").isJsonNull()
                        ? attributes.get("external_id").getAsString() : null;

                // Match by ID, identifier, or external ID
                if (identifier.equals(serverId) || identifier.equals(serverIdentifier) || identifier.equals(externalId)) {
                    return parseServerDetails(serverObj);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Parse server details from JSON response
     */
    private Optional<ServerDetails> parseServerDetails(JsonObject serverObj) {
        try {
            JsonObject attributes = serverObj.has("attributes")
                    ? serverObj.getAsJsonObject("attributes")
                    : serverObj;

            String name = attributes.has("name") ? attributes.get("name").getAsString() : null;
            String identifier = attributes.has("identifier") ? attributes.get("identifier").getAsString() : null;

            // Get primary allocation
            JsonObject relationships = attributes.has("relationships")
                    ? attributes.getAsJsonObject("relationships") : null;

            if (relationships != null && relationships.has("allocations")) {
                JsonArray allocations = relationships
                        .getAsJsonObject("allocations")
                        .getAsJsonArray("data");

                if (allocations != null && allocations.size() > 0) {
                    // Find primary allocation or use first one
                    JsonObject primaryAlloc = null;

                    for (int i = 0; i < allocations.size(); i++) {
                        JsonObject alloc = allocations.get(i).getAsJsonObject();
                        JsonObject allocAttrs = alloc.getAsJsonObject("attributes");

                        if (allocAttrs.has("is_default") && allocAttrs.get("is_default").getAsBoolean()) {
                            primaryAlloc = allocAttrs;
                            break;
                        }
                    }

                    // If no default found, use first allocation
                    if (primaryAlloc == null) {
                        primaryAlloc = allocations.get(0).getAsJsonObject().getAsJsonObject("attributes");
                    }

                    if (primaryAlloc != null) {
                        String ip = primaryAlloc.get("ip").getAsString();
                        int port = primaryAlloc.get("port").getAsInt();

                        logger.info(String.format("Pterodactyl API: Server %s gevonden - %s:%d", identifier, ip, port));
                        return Optional.of(new ServerDetails(name, ip, port, identifier));
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Fout bij parsen Pterodactyl server data", e);
        }

        return Optional.empty();
    }

    /**
     * Server details data class
     */
    public static class ServerDetails {
        private final String name;
        private final String ip;
        private final int port;
        private final String identifier;

        public ServerDetails(String name, String ip, int port, String identifier) {
            this.name = name;
            this.ip = ip;
            this.port = port;
            this.identifier = identifier;
        }

        public String getName() {
            return name;
        }

        public String getIp() {
            return ip;
        }

        public int getPort() {
            return port;
        }

        public String getIdentifier() {
            return identifier;
        }

        @Override
        public String toString() {
            return String.format("%s (%s:%d)", name, ip, port);
        }
    }

    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
