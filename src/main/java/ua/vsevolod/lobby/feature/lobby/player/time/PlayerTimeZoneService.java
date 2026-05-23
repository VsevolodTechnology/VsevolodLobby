package ua.vsevolod.lobby.feature.lobby.player.time;

import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a player's time zone from their IP (via the free ip-api.com service) and tracks
 * who opted in to "show time in my zone" in the settings menu.
 *
 * <p>Self-contained: the opt-in set is persisted to its own flat file ({@code storage/time-by-ip.txt}),
 * so this feature needs no changes to {@code PlayerPreferences} or the player-data stores.
 * IP geolocation is best-effort and fully async — a failure (offline, rate-limited, VPN, local
 * address) just means the player keeps the server's configured zone.</p>
 */
public final class PlayerTimeZoneService {

    private static volatile PlayerTimeZoneService instance;

    public static PlayerTimeZoneService get() {
        return instance;
    }

    private static final Path FILE = Paths.get("storage", "time-by-ip.txt");
    private static final Pattern TIMEZONE = Pattern.compile("\"timezone\"\\s*:\\s*\"([^\"]+)\"");

    /** Resolved IANA zone per online player (best-effort). */
    private final java.util.Map<UUID, ZoneId> zones = new ConcurrentHashMap<>();
    /** Players who enabled "time in my zone" — persisted across restarts. */
    private final Set<UUID> ipMode = ConcurrentHashMap.newKeySet();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public PlayerTimeZoneService() {
        instance = this;
        loadOptInFile();
    }

    public void register(GlobalEventHandler events) {
        events.addListener(PlayerSpawnEvent.class, event -> {
            if (event.isFirstSpawn()) resolve(event.getPlayer());
        });
        events.addListener(PlayerDisconnectEvent.class,
                event -> zones.remove(event.getPlayer().getUuid()));
    }

    // ── Opt-in state ──────────────────────────────────────────────────────────

    public boolean isIpMode(UUID uuid) {
        return ipMode.contains(uuid);
    }

    /** Toggle a player's preference and persist the opt-in set. */
    public synchronized void setIpMode(UUID uuid, boolean enabled) {
        if (enabled) ipMode.add(uuid);
        else ipMode.remove(uuid);
        saveOptInFile();
    }

    /** Best-effort resolved zone for a player (empty until the async lookup lands, or on failure). */
    public Optional<ZoneId> zoneOf(UUID uuid) {
        return Optional.ofNullable(zones.get(uuid));
    }

    // ── IP geolocation ────────────────────────────────────────────────────────

    private void resolve(Player player) {
        UUID uuid = player.getUuid();
        if (zones.containsKey(uuid)) return;

        String ip = remoteIp(player);
        if (ip == null || isLocal(ip)) return; // local / proxy on same host — keep default zone

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://ip-api.com/json/" + ip + "?fields=status,timezone"))
                .timeout(Duration.ofSeconds(6))
                .GET()
                .build();

        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) return;
                    Matcher m = TIMEZONE.matcher(response.body());
                    if (!m.find()) return;
                    try {
                        zones.put(uuid, ZoneId.of(m.group(1)));
                    } catch (Exception ignored) { /* unknown zone id — keep default */ }
                })
                .exceptionally(ex -> null); // network down / rate-limited — silently keep default
    }

    private static String remoteIp(Player player) {
        if (player.getPlayerConnection().getRemoteAddress() instanceof InetSocketAddress addr
                && addr.getAddress() != null) {
            return addr.getAddress().getHostAddress();
        }
        return null;
    }

    /** Loopback / LAN ranges — geolocation would be meaningless (proxy on the same host). */
    private static boolean isLocal(String ip) {
        return ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.")
                || ip.startsWith("169.254.") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1")
                || ip.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*");
    }

    // ── Opt-in file persistence ───────────────────────────────────────────────

    private void loadOptInFile() {
        if (!Files.exists(FILE)) return;
        try {
            for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                try { ipMode.add(UUID.fromString(trimmed)); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.err.println("[PlayerTimeZone] Failed to read " + FILE + ": " + e.getMessage());
        }
    }

    private void saveOptInFile() {
        try {
            Files.createDirectories(FILE.getParent());
            List<String> lines = new ArrayList<>(ipMode.size());
            for (UUID id : ipMode) lines.add(id.toString());
            Files.write(FILE, lines, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[PlayerTimeZone] Failed to write " + FILE + ": " + e.getMessage());
        }
    }
}
