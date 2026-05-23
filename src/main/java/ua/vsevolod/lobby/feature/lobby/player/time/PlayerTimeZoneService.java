package ua.vsevolod.lobby.feature.lobby.player.time;

import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import ua.vsevolod.lobby.feature.lobby.player.prefs.PlayerPreferencesService;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a player's time zone from their IP (via the free ip-api.com service) and tracks
 * who opted in to "show time in my zone" in the settings menu.
 *
 * <p>The opt-in flag lives in {@link PlayerPreferencesService} — every per-player setting goes
 * through the same write-behind cache and disconnect-flush. IP geolocation itself is best-effort
 * and fully async — a failure (offline, rate-limited, VPN, local address) just means the player
 * keeps the server's configured zone.</p>
 */
public final class PlayerTimeZoneService {

    private static volatile PlayerTimeZoneService instance;

    public static PlayerTimeZoneService get() {
        return instance;
    }

    private static final Pattern TIMEZONE = Pattern.compile("\"timezone\"\\s*:\\s*\"([^\"]+)\"");

    /** Resolved IANA zone per online player (best-effort). */
    private final java.util.Map<UUID, ZoneId> zones = new ConcurrentHashMap<>();

    private final PlayerPreferencesService prefs;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public PlayerTimeZoneService(PlayerPreferencesService prefs) {
        this.prefs = prefs;
        instance = this;
    }

    public void register(GlobalEventHandler events) {
        events.addListener(PlayerSpawnEvent.class, event -> {
            if (event.isFirstSpawn()) resolve(event.getPlayer());
        });
        events.addListener(PlayerDisconnectEvent.class,
                event -> zones.remove(event.getPlayer().getUuid()));
    }

    // ── Opt-in state (backed by PlayerPreferences) ────────────────────────────

    public boolean isIpMode(UUID uuid) {
        return prefs.get(uuid).timeByIpEnabled();
    }

    /** Toggle a player's preference. Write-behind cache handles the actual disk I/O. */
    public void setIpMode(UUID uuid, boolean enabled) {
        prefs.saveTimeByIpEnabled(uuid, enabled);
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

}
