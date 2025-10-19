# ServerSync

> A high-performance BungeeCord plugin for dynamic minigame server orchestration with Redis, RabbitMQ, and Pterodactyl Panel integration.

[![Java](https://img.shields.io/badge/Java-8+-orange.svg)](https://www.oracle.com/java/)
[![BungeeCord](https://img.shields.io/badge/BungeeCord-Compatible-brightgreen.svg)](https://www.spigotmc.org/wiki/bungeecord/)
[![License](https://img.shields.io/badge/License-Private-red.svg)]()

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Requirements](#requirements)
- [Installation](#installation)
- [Configuration](#configuration)
- [API Integration](#api-integration)
- [Commands](#commands)
- [Data Structures](#data-structures)
- [Monitoring & Logging](#monitoring--logging)
- [Troubleshooting](#troubleshooting)
- [Performance Tuning](#performance-tuning)
- [Contributing](#contributing)

## Overview

ServerSync is an enterprise-grade orchestration plugin designed for high-availability Minecraft proxy networks. It dynamically manages minigame server instances by integrating with cloud infrastructure, providing seamless player routing, automatic server registration, and intelligent load balancing across multiple proxy instances.

### Key Capabilities

- **Dynamic Server Registration**: Automatically registers and unregisters game servers based on demand
- **Multi-Proxy Support**: Synchronized player counting across distributed proxy infrastructure
- **Intelligent Load Balancing**: Routes players to optimal servers using configurable strategies
- **Health Monitoring**: Continuous server health checks with automatic recovery
- **Cloud Integration**: Native support for Pterodactyl Panel and Docker-based deployments
- **Real-time Notifications**: Discord webhook integration for operational visibility

## Features

### Core Features

| Feature | Description |
|---------|-------------|
| **Redis Integration** | Persistent state management and cross-proxy synchronization |
| **RabbitMQ Messaging** | Event-driven architecture for server lifecycle management |
| **Pterodactyl API** | Automatic server discovery and metadata retrieval |
| **Load Balancing** | Multiple strategies: Least Players, Random, Round Robin |
| **Health Checks** | Periodic server availability monitoring with auto-cleanup |
| **Discord Logging** | Real-time operational events and error tracking |
| **Multi-Proxy Ready** | Supports horizontal scaling with unique proxy identification |

### Supported Minigames

- Bed Wars
- Sky Wars
- TNT Run
- Spleef
- Paintball
- Arcade (extensible)

## Architecture

### System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Minecraft Network                        │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                  │
│  │ Proxy 1  │  │ Proxy 2  │  │ Proxy N  │                  │
│  │ServerSync│  │ServerSync│  │ServerSync│                  │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘                  │
│       │             │             │                          │
└───────┼─────────────┼─────────────┼──────────────────────────┘
        │             │             │
        ├─────────────┴─────────────┴──────────┐
        │                                       │
   ┌────▼─────┐                         ┌──────▼──────┐
   │  Redis   │                         │  RabbitMQ   │
   │  Cache   │◄────────────────────────┤  Messaging  │
   └────┬─────┘                         └──────▲──────┘
        │                                      │
   ┌────▼──────────────────────────────────────┴──────┐
   │            Cloud Controller                       │
   │  ┌─────────────────────────────────────────────┐ │
   │  │         Pterodactyl Panel                   │ │
   │  │  ┌──────────┐  ┌──────────┐  ┌──────────┐ │ │
   │  │  │ Game 1   │  │ Game 2   │  │ Game N   │ │ │
   │  │  │ Server   │  │ Server   │  │ Server   │ │ │
   │  │  └──────────┘  └──────────┘  └──────────┘ │ │
   │  └─────────────────────────────────────────────┘ │
   └───────────────────────────────────────────────────┘
```

### Event Flow

#### 1. Server Spawn Flow

```
1. Cloud Controller spawns server via Pterodactyl Panel
   │
   ▼
2. Docker container starts and initializes
   │
   ▼
3. Game server publishes ready event to RabbitMQ queue
   │
   ▼
4. ServerSync receives 'server_ready' message
   │
   ▼
5. Plugin queries Pterodactyl API or Redis for server details
   │
   ▼
6. Server automatically registered to BungeeCord network
   │
   ▼
7. Server becomes available for player connections ✓
```

#### 2. Server Removal Flow

```
1. Game server becomes empty (no players)
   │
   ▼
2. Server publishes empty event to RabbitMQ queue
   │
   ▼
3. ServerSync receives 'server_empty' message
   │
   ▼
4. Server unregistered from BungeeCord network
   │
   ▼
5. Cloud controller receives shutdown signal
   │
   ▼
6. Container gracefully terminated ✓
```

#### 3. Health Check Flow

Runs every 10-30 seconds (configurable):

```
1. Health check task executes
   │
   ▼
2. Ping all registered managed servers
   │
   ▼
3. Query Redis for authoritative server list
   │
   ▼
4. Compare local state with Redis state
   │
   ├─► Remove offline/stale servers
   └─► Register new servers from Redis
   │
   ▼
5. Proxy server list synchronized ✓
```

## Requirements

### Runtime Requirements

- **Java**: 8 or higher
- **BungeeCord**: Latest stable version recommended
- **Redis**: 5.0+ (tested with 6.x and 7.x)
- **RabbitMQ**: 3.8+ (optional but recommended)
- **Pterodactyl Panel**: 1.x (optional)

### Network Requirements

- Redis must be accessible from all proxy instances
- RabbitMQ must be accessible from all proxy instances
- Pterodactyl Panel API must be accessible from proxy instances
- Game servers must be reachable from proxy instances

### Permissions

The plugin requires the following permissions for administrative commands:

- `serversync.admin` - Full administrative access
- `serversync.play` - Use /play command (default for all players)

## Installation

### Step 1: Download and Install

1. Download the latest `serversync-1.0-SNAPSHOT.jar` from releases
2. Place the JAR file in your BungeeCord `plugins/` directory
3. Restart your BungeeCord proxy

### Step 2: Initial Configuration

On first run, the plugin generates a default `config.yml` in `plugins/ServerSync/`:

```
plugins/
└── ServerSync/
    └── config.yml
```

### Step 3: Configure Redis Connection

Edit `config.yml` and update the Redis connection details:

```yaml
redis:
  host: "your-redis-host"
  port: 6379
  password: "your-redis-password"
  timeout: 2000
```

### Step 4: Configure Proxy Identity

**CRITICAL**: Each proxy must have a unique identifier:

```yaml
proxy:
  id: "proxy-1"  # Change to proxy-2, proxy-3, etc.
  name: "US-East-Proxy-1"
```

### Step 5: Optional Services

Configure RabbitMQ (recommended for production):

```yaml
rabbitmq:
  host: "your-rabbitmq-host"
  port: 5672
  username: "mcuser"
  password: "mcpass"
  enabled: true
```

Configure Pterodactyl Panel integration:

```yaml
pterodactyl:
  enabled: true
  panel-url: "https://panel.yourdomain.com"
  api-key: "ptla_your_api_key_here"
  node-id: 1
```

### Step 6: Restart and Verify

Restart the proxy and check the console for successful initialization:

```
╔════════════════════════════════════╗
║     ServerSync is starting...     ║
╚════════════════════════════════════╝
[ServerSync] Connected to Redis at your-redis-host:6379
[ServerSync] Connected to RabbitMQ at your-rabbitmq-host:5672
[ServerSync] Pterodactyl API client initialized
╔════════════════════════════════════╗
║  ServerSync started successfully! ║
╚════════════════════════════════════╝
```

## Configuration

### Complete Configuration Reference

```yaml
# Proxy Identification (REQUIRED for multi-proxy setups)
proxy:
  id: "proxy-1"              # Unique identifier for this proxy
  name: "EU-Proxy-1"         # Human-readable name for logging

# Redis Configuration
redis:
  host: "localhost"          # Redis server hostname
  port: 6379                 # Redis server port
  password: ""               # Redis authentication password (empty if none)
  timeout: 2000              # Connection timeout in milliseconds

# RabbitMQ Configuration
rabbitmq:
  host: "localhost"          # RabbitMQ server hostname
  port: 5672                 # RabbitMQ server port
  username: "guest"          # RabbitMQ username
  password: "guest"          # RabbitMQ password
  enabled: true              # Enable/disable RabbitMQ integration
  queues:
    server-ready: "server_ready"       # Queue for server ready events
    server-empty: "server_empty"       # Queue for server empty events
    spawn-request: "spawn_request"     # Queue for spawn requests
    player-count: "player_count"       # Queue for player count updates

# Discord Webhook Configuration
discord:
  enabled: true              # Enable/disable Discord notifications
  webhook-url: "https://discord.com/api/webhooks/..."
  bot-name: "ServerSync"     # Display name for webhook
  bot-avatar: ""             # Optional avatar URL
  log-events:
    startup: true            # Log plugin startup
    shutdown: true           # Log plugin shutdown
    server-registered: true  # Log server registration
    server-unregistered: true # Log server removal
    server-ready: true       # Log server ready events
    health-check: false      # Log periodic health checks
    errors: true             # Log error events
    warnings: true           # Log warning events

# Pterodactyl Panel Configuration
pterodactyl:
  enabled: true              # Enable/disable Pterodactyl integration
  panel-url: "https://panel.example.com"
  api-key: "ptla_..."        # Pterodactyl API key
  node-id: 1                 # Node ID to query
  default-ip: "172.18.0.1"   # Fallback IP for Docker bridge network
  default-port-range:
    start: 25565             # Fallback port range start
    end: 25665               # Fallback port range end

# Synchronization Settings
sync-interval: 5             # Sync interval in seconds
health-check-interval: 10    # Health check interval in seconds

# Redis Key Patterns
server-keys:
  - "minecraft:servers:bedwars"
  - "minecraft:servers:skywars"
  - "minecraft:servers:tntrun"
  - "minecraft:servers:spleef"
  - "minecraft:servers:paintball"
  - "minecraft:servers:arcade"

# Server Registration Settings
registration:
  auto-register: true        # Auto-register servers on startup
  name-format: "{type}-{id}" # Server name format
  motd-format: "{type} Arena #{id}" # MOTD format
  restricted: false          # Server access restriction

# Load Balancing Settings
load-balancing:
  enabled: true              # Enable load balancing
  strategy: "LEAST_PLAYERS"  # Strategy: LEAST_PLAYERS, RANDOM, ROUND_ROBIN
  auto-spawn-on-full: true   # Request new server when all are full

# Logging Options
logging:
  enabled: true              # Enable plugin logging
  verbose: false             # Enable verbose logging
```

### Configuration Strategies

#### Strategy: LEAST_PLAYERS

Routes players to the server with the fewest players, optimizing resource utilization.

**Best for**: General use cases, balanced load distribution

#### Strategy: RANDOM

Routes players randomly to available servers.

**Best for**: Equal distribution without preference, testing scenarios

#### Strategy: ROUND_ROBIN

Routes players sequentially through available servers.

**Best for**: Predictable distribution patterns, sequential testing

## API Integration

### Redis Data Structures

ServerSync uses the following Redis key patterns:

#### Server Registry

**Key Pattern**: `minecraft:servers:{minigame}:{server_id}`

**Type**: Hash

**Fields**:
```json
{
  "name": "bedwars-1",
  "type": "bedwars",
  "ip": "172.18.0.10",
  "port": "25565",
  "status": "online",
  "players": "0",
  "maxPlayers": "16",
  "map": "Aquarium"
}
```

#### Player Count (Multi-Proxy)

**Key Pattern**: `minecraft:proxy:{proxy_id}:players`

**Type**: String (JSON)

**Value**:
```json
{
  "proxyId": "proxy-1",
  "playerCount": 42,
  "timestamp": 1698765432000
}
```

### RabbitMQ Message Formats

#### Server Ready Event

**Queue**: `server_ready`

**Payload**:
```json
{
  "serverId": "bedwars-1",
  "type": "bedwars",
  "ip": "172.18.0.10",
  "port": 25565,
  "timestamp": 1698765432000
}
```

#### Server Empty Event

**Queue**: `server_empty`

**Payload**:
```json
{
  "serverId": "bedwars-1",
  "type": "bedwars",
  "timestamp": 1698765432000
}
```

#### Spawn Request Event

**Queue**: `spawn_request`

**Payload**:
```json
{
  "type": "bedwars",
  "requestedBy": "proxy-1",
  "reason": "all_servers_full",
  "timestamp": 1698765432000
}
```

#### Player Count Update

**Queue**: `player_count`

**Payload**:
```json
{
  "proxyId": "proxy-1",
  "playerCount": 42,
  "timestamp": 1698765432000
}
```

### Pterodactyl API Integration

ServerSync queries the Pterodactyl API to retrieve server details:

**Endpoint**: `GET /api/client/servers/{server_id}`

**Authentication**: Bearer token in `Authorization` header

**Response** (simplified):
```json
{
  "attributes": {
    "name": "bedwars-1",
    "identifier": "a1b2c3d4",
    "allocation": {
      "ip": "172.18.0.10",
      "port": 25565
    }
  }
}
```

## Commands

### Player Commands

#### `/play <minigame>`

Connects the player to an available server for the specified minigame.

**Usage**:
```
/play bedwars
/play skywars
/play tntrun
```

**Permission**: `serversync.play` (default: all players)

**Behavior**:
- Selects best server using configured load balancing strategy
- Automatically requests new server if all are full (when `auto-spawn-on-full: true`)
- Provides feedback if no servers are available

**Examples**:
```
/play bedwars
> Connecting to bedwars-3... (2/16 players)

/play skywars
> All servers are full! Requesting a new server...
```

### Administrative Commands

#### `/serversync`

Main administrative command for managing the plugin.

**Permission**: `serversync.admin`

**Subcommands**:

##### `/serversync reload`

Reloads the plugin configuration without restarting.

**Usage**: `/serversync reload`

**Output**: `Configuration reloaded successfully!`

##### `/serversync status`

Displays current plugin status and statistics.

**Usage**: `/serversync status`

**Output**:
```
=== ServerSync Status ===
Managed Servers: 5
Redis: Connected
RabbitMQ: Connected
Pterodactyl: Enabled
=======================
```

##### `/serversync sync`

Forces an immediate synchronization with Redis.

**Usage**: `/serversync sync`

**Output**: `Manual synchronization triggered.`

##### `/serversync list [minigame]`

Lists all managed servers or servers for a specific minigame.

**Usage**:
```
/serversync list
/serversync list bedwars
```

**Output**:
```
=== Managed Servers ===
bedwars-1: 12/16 players
bedwars-2: 8/16 players
skywars-1: 5/12 players
=======================
```

## Data Structures

### Internal Server Representation

```java
public class ServerData {
    private String name;          // Server identifier (e.g., "bedwars-1")
    private String type;          // Minigame type (e.g., "bedwars")
    private String ip;            // Server IP address
    private int port;             // Server port
    private String status;        // Status: online, starting, offline
    private int players;          // Current player count
    private int maxPlayers;       // Maximum player capacity
    private String map;           // Current map name
    private long lastSeen;        // Last heartbeat timestamp
}
```

### Health Check States

| State | Description | Action |
|-------|-------------|--------|
| `ONLINE` | Server responding to pings | No action |
| `OFFLINE` | Server not responding | Remove from network |
| `STALE` | No heartbeat for > 60s | Mark for removal |
| `STARTING` | Recently spawned | Wait for ready event |

## Monitoring & Logging

### Discord Notifications

When Discord webhook is enabled, ServerSync sends notifications for:

#### Startup Events

```
🟢 ServerSync Started
Proxy: EU-Proxy-1 (proxy-1)
Managed Servers: 5
Status: All systems operational
```

#### Server Registration

```
📥 Server Registered
Server: bedwars-3
Type: Bed Wars
Address: 172.18.0.15:25567
Players: 0/16
```

#### Error Events

```
🔴 Redis Connection Failed
Cannot connect to Redis at 91.98.71.248:6379
Plugin functionality degraded
```

### Console Logging

ServerSync provides structured console logging:

```
[INFO] [ServerSync] Connected to Redis at redis.example.com:6379
[INFO] [ServerSync] Starting health check task (interval: 10s)
[INFO] [ServerSync] Registered server: bedwars-1 (172.18.0.10:25565)
[WARN] [ServerSync] Server bedwars-2 is not responding to pings
[ERROR] [ServerSync] Failed to connect to RabbitMQ: Connection refused
```

### Metrics and Health Checks

ServerSync tracks the following operational metrics:

- Total managed servers
- Servers per minigame type
- Player distribution across servers
- Redis connection status
- RabbitMQ connection status
- Pterodactyl API availability

Access metrics via `/serversync status` command.

## Troubleshooting

### Common Issues

#### Issue: Plugin fails to start with Redis error

**Symptoms**: 
```
[SEVERE] Cannot connect to Redis! Plugin will be disabled.
```

**Solutions**:
1. Verify Redis is running: `redis-cli ping` should return `PONG`
2. Check Redis host/port in configuration
3. Verify Redis password if authentication is enabled
4. Ensure firewall allows connection on Redis port
5. Test connectivity: `telnet redis-host 6379`

#### Issue: Servers not automatically registering

**Symptoms**: Game servers start but don't appear in `/serversync list`

**Solutions**:
1. Verify RabbitMQ connection is established
2. Check that game servers are publishing to correct queue
3. Ensure `auto-register: true` in configuration
4. Verify server keys match pattern in `server-keys` config
5. Check Redis for server data: `redis-cli hgetall minecraft:servers:bedwars:1`

#### Issue: Players can't connect to servers

**Symptoms**: Connection timeout or "Can't connect to server"

**Solutions**:
1. Verify server IP/port are correct in Redis
2. Check Docker network configuration for bridge IP
3. Test direct connection: `telnet server-ip server-port`
4. Verify Pterodactyl Panel port allocations
5. Check BungeeCord server list: `/serversync list`

#### Issue: Duplicate servers across proxies

**Symptoms**: Same server appears multiple times

**Solutions**:
1. Ensure each proxy has unique `proxy.id` in configuration
2. Verify only one instance manages each server
3. Check Redis keys for duplicate entries
4. Restart all proxy instances to clear state

#### Issue: High memory usage

**Symptoms**: Proxy using excessive memory over time

**Solutions**:
1. Reduce `sync-interval` to avoid frequent updates
2. Increase `health-check-interval`
3. Limit number of `server-keys` patterns
4. Check for memory leaks in Redis connection pool
5. Ensure old servers are being cleaned up properly

### Debug Mode

Enable verbose logging for detailed diagnostic information:

```yaml
logging:
  enabled: true
  verbose: true
```

**Warning**: Verbose mode generates significant log output. Use only for troubleshooting.

### Health Check Validation

Manually trigger health check to diagnose synchronization issues:

```
/serversync sync
```

Monitor console output for detailed sync process information.

## Performance Tuning

### Redis Connection Pool

Optimize Redis performance by tuning pool settings in code:

```java
poolConfig.setMaxTotal(20);      // Maximum connections
poolConfig.setMaxIdle(10);       // Maximum idle connections
poolConfig.setMinIdle(5);        // Minimum idle connections
```

**Recommendations**:
- Small networks (1-2 proxies): Default settings sufficient
- Medium networks (3-5 proxies): Increase `maxTotal` to 30-40
- Large networks (6+ proxies): Consider Redis Cluster and increase to 50+

### Sync Intervals

Balance between responsiveness and resource usage:

| Network Size | Sync Interval | Health Check Interval |
|--------------|---------------|----------------------|
| Small (<100 players) | 10s | 30s |
| Medium (100-500 players) | 5s | 15s |
| Large (500+ players) | 3s | 10s |

### RabbitMQ Tuning

For high-traffic deployments:

```java
factory.setRequestedHeartbeat(30);        // Heartbeat interval
factory.setNetworkRecoveryInterval(10000); // Auto-recovery interval
factory.setConnectionTimeout(5000);        // Connection timeout
```

### Load Balancing Optimization

Choose strategy based on use case:

- **LEAST_PLAYERS**: Best for even distribution and resource efficiency
- **RANDOM**: Lowest overhead, good for simple deployments
- **ROUND_ROBIN**: Predictable but may cause uneven loads

## Contributing

### Development Setup

1. Clone the repository
2. Import as Maven project in your IDE
3. Configure BungeeCord as runtime dependency
4. Set up local Redis and RabbitMQ instances for testing

### Building from Source

```bash
mvn clean package
```

Output JAR: `target/serversync-1.0-SNAPSHOT.jar`

### Code Style

- Follow Java naming conventions
- Use meaningful variable and method names
- Add JavaDoc comments for public APIs
- Keep methods focused and concise
- Handle exceptions appropriately

### Testing

Before submitting changes:

1. Test with clean BungeeCord installation
2. Verify Redis connectivity with various configurations
3. Test RabbitMQ event handling
4. Validate multi-proxy synchronization
5. Check for memory leaks during extended operation

---

## License

This project is private and proprietary. Unauthorized distribution or modification is prohibited.

## Support

For issues, feature requests, or questions:

1. Check [Troubleshooting](#troubleshooting) section
2. Review [Configuration](#configuration) documentation
3. Enable verbose logging for diagnostic information
4. Contact the development team with detailed logs

---

**Built with ❤️ for high-performance Minecraft networks**

