package kr.yuumi97.core.pack;

import com.sun.net.httpserver.HttpServer;
import kr.yuumi97.core.util.TextUtil;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public final class ResourcePackServer implements Listener {

    private static final UUID PACK_UUID = UUID.nameUUIDFromBytes("yuumi97land-season1-pack".getBytes(StandardCharsets.UTF_8));

    private final JavaPlugin plugin;
    private final Map<UUID, String> loginHostByPlayer = new ConcurrentHashMap<>();
    private HttpServer httpServer;
    private java.util.concurrent.ExecutorService httpExecutor;
    private byte[] packBytes;
    private String packSha1Hex;
    private int httpPort;
    private boolean enabled;

    public ResourcePackServer(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        this.enabled = plugin.getConfig().getBoolean("resource-pack.enabled", true);
        this.httpPort = plugin.getConfig().getInt("resource-pack.http-port", 25566);
        if (!enabled) return;

        try {
            Path serverRoot = plugin.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
            Path zipPath = serverRoot.resolve("resourcepack").resolve("Yuumi97Pack.zip");
            if (!Files.exists(zipPath)) {
                plugin.getLogger().warning("[ResourcePack] Yuumi97Pack.zip not found at " + zipPath);
                this.enabled = false;
                return;
            }

            this.packBytes = Files.readAllBytes(zipPath);
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(this.packBytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            this.packSha1Hex = sb.toString();

            this.httpServer = HttpServer.create(new InetSocketAddress("0.0.0.0", httpPort), 0);
            this.httpServer.createContext("/Yuumi97Pack.zip", exchange -> {
                try {
                    exchange.getResponseHeaders().set("Content-Type", "application/zip");
                    exchange.sendResponseHeaders(200, packBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(packBytes);
                    }
                } finally {
                    exchange.close();
                }
            });
            this.httpExecutor = Executors.newFixedThreadPool(2);
            this.httpServer.setExecutor(httpExecutor);
            this.httpServer.start();
            plugin.getLogger().info("[ResourcePack] Serving Yuumi97Pack.zip (" + packBytes.length + " bytes, SHA1=" + packSha1Hex + ") on port " + httpPort);
        } catch (Exception e) {
            plugin.getLogger().warning("[ResourcePack] Failed to start embedded HTTP server on port " + httpPort + ": " + e.getMessage());
            this.enabled = false;
        }
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
        if (httpExecutor != null) { httpExecutor.shutdownNow(); httpExecutor = null; }
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        String rawHost = event.getHostname();
        if (rawHost != null && !rawHost.isBlank()) {
            // Strip SRV/Forge extra tokens and port (:25565)
            String cleaned = rawHost.split("\0")[0];
            int colonIdx = cleaned.lastIndexOf(':');
            if (colonIdx > 0 && !cleaned.startsWith("[")) {
                cleaned = cleaned.substring(0, colonIdx);
            }
            if (cleaned.endsWith(".")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
            if (!cleaned.isBlank()) {
                loginHostByPlayer.put(event.getPlayer().getUniqueId(), cleaned);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled || packBytes == null || packSha1Hex == null) return;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            sendPackToPlayer(player);
        }, 25L);
    }

    public void sendPackToPlayer(Player player) {
        if (!enabled || packSha1Hex == null) return;
        try {
            String host = resolveBestHostFor(player);
            URI uri = URI.create("http://" + host + ":" + httpPort + "/Yuumi97Pack.zip");
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(PACK_UUID, uri, packSha1Hex);
            boolean required = plugin.getConfig().getBoolean("resource-pack.required", false);
            String prompt = plugin.getConfig().getString("resource-pack.prompt-message", "🐱 유미의 97랜드 공식 리소스팩을 적용합니다!");

            ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                    .packs(info)
                    .prompt(TextUtil.mm(prompt))
                    .required(required)
                    .build();
            player.sendResourcePacks(request);
        } catch (Exception e) {
            plugin.getLogger().warning("[ResourcePack] Could not send resource pack to " + player.getName() + ": " + e.getMessage());
        }
    }

    private String resolveBestHostFor(Player player) {
        InetSocketAddress addr = player.getAddress();
        if (addr != null && addr.getAddress() != null) {
            InetAddress inet = addr.getAddress();
            // Host playing on the exact same PC -> always use 127.0.0.1 to avoid router NAT loopback failure!
            if (inet.isLoopbackAddress()) {
                return "127.0.0.1";
            }
        }

        String configuredHost = plugin.getConfig().getString("resource-pack.public-host", "").trim();
        if (!configuredHost.isEmpty()) {
            return configuredHost;
        }

        // Otherwise automatically reuse the exact address/domain the friend typed into their multiplayer screen!
        String usedHost = loginHostByPlayer.get(player.getUniqueId());
        if (usedHost != null && !usedHost.isBlank()) {
            return usedHost;
        }
        return "127.0.0.1";
    }
}
