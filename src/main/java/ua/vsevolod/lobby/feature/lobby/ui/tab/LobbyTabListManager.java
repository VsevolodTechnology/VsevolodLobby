package ua.vsevolod.lobby.feature.lobby.ui.tab;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.server.ServerTickMonitorEvent;
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket;
import net.minestom.server.network.packet.server.play.PlayerListHeaderAndFooterPacket;
import net.minestom.server.utils.PacketSendingUtils;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.player.time.PlayerTimeZoneService;
import ua.vsevolod.lobby.integration.spark.SparkService;
import ua.vsevolod.lobby.util.Text;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import net.minestom.server.timer.Task;

/**
 * Tab list (player list header + footer) renderer + per-player latency icon refresher.
 *
 * <h3>Header/footer rendering</h3>
 * <p>The {@code {ping}} placeholder is rendered per-player (each player sees their own actual
 * latency, not a bucketed approximation). To keep the per-tick cost low, all
 * <i>non-ping</i> substitutions ({@code {time}}, {@code {online}}, {@code {mspt}}) are done
 * once per {@code bypass} flag — yielding two templates per tick. Per-player work is then
 * limited to a single {@code String.replace} for {@code {ping}} and a skip-if-unchanged check
 * against the previously rendered text.</p>
 *
 * <h3>Latency icon (per-player ping bars)</h3>
 * <p>Minestom auto-broadcasts {@code UPDATE_LATENCY} only on keepalive responses
 * (~20 s cadence), which made the TAB ping icon look frozen at the value last seen at join.
 * We add a 2 s broadcast that batches all changed latencies into a single
 * {@link PlayerInfoUpdatePacket} so the icon tracks reality closely without flooding the
 * network. Unchanged latencies are skipped — packets are only sent when a player's reading
 * actually moved.</p>
 */
public final class LobbyTabListManager {

    /** How often to push UPDATE_LATENCY broadcasts so the TAB ping bars stay fresh. */
    private static final long LATENCY_BROADCAST_PERIOD_MS = 2_000;

    private final AtomicReference<Double> lastTickMs = new AtomicReference<>(50.0);
    private final AtomicReference<DateTimeFormatter> timeFormatter =
            new AtomicReference<>(DateTimeFormatter.ofPattern("HH:mm"));
    private String currentTimeFormatPattern = "HH:mm";

    /** Last text rendered for each viewer — used to skip identical packets between ticks. */
    private final Map<UUID, RenderedTab> lastRendered = new ConcurrentHashMap<>();

    /** Last latency broadcast for each viewer — avoid resending an unchanged value. */
    private final Map<UUID, Integer> lastBroadcastLatency = new ConcurrentHashMap<>();

    private Task refreshTask;

    public LobbyTabListManager(GlobalEventHandler events) {
        events.addListener(ServerTickMonitorEvent.class, event ->
                lastTickMs.set(event.getTickMonitor().getTickTime())
        );
        events.addListener(PlayerDisconnectEvent.class, e -> {
            UUID id = e.getPlayer().getUuid();
            lastRendered.remove(id);
            lastBroadcastLatency.remove(id);
        });
        TabConfig.addListener(this::rescheduleRefresh);
        scheduleRefresh();
        scheduleLatencyBroadcast();
    }

    public void rescheduleRefresh() {
        if (refreshTask != null) refreshTask.cancel();
        scheduleRefresh();
    }

    private void scheduleRefresh() {
        refreshTask = MinecraftServer.getSchedulerManager()
                .buildTask(this::updateAll)
                .repeat(Duration.ofMillis(TabConfig.get().updateIntervalMs))
                .schedule();
    }

    private void scheduleLatencyBroadcast() {
        MinecraftServer.getSchedulerManager()
                .buildTask(this::broadcastLatencies)
                .repeat(Duration.ofMillis(LATENCY_BROADCAST_PERIOD_MS))
                .schedule();
    }

    public void updateAll() {
        TabConfig cfg = TabConfig.get();
        Collection<Player> onlinePlayers = MinecraftServer.getConnectionManager().getOnlinePlayers();
        if (onlinePlayers.isEmpty()) return;

        String state = cfg.stateFor(SparkService.getMspt());
        DateTimeFormatter fmt = formatterFor(cfg.timeFormat);
        String defaultTime = LocalTime.now(zoneFor(cfg.timeZone)).format(fmt)
                + " <dark_gray>(" + cfg.timeZoneLabel + ")";
        int online = onlinePlayers.size();
        PlayerTimeZoneService tz = PlayerTimeZoneService.get();

        // Header/footer rendered once; {ping} and {time} are per-player (substituted below).
        String headerTpl = renderTemplate(cfg.header, online, state);
        String footerTpl = renderTemplate(cfg.footer, online, state);

        for (Player player : onlinePlayers) {
            String pingStr = Integer.toString(player.getLatency());
            String timeStr = playerTime(player, tz, fmt, defaultTime);
            String headerStr = headerTpl.indexOf('{') < 0 ? headerTpl
                    : headerTpl.replace("{ping}", pingStr).replace("{time}", timeStr);
            String footerStr = footerTpl.indexOf('{') < 0 ? footerTpl
                    : footerTpl.replace("{ping}", pingStr).replace("{time}", timeStr);

            RenderedTab previous = lastRendered.get(player.getUuid());
            if (previous != null && previous.header.equals(headerStr) && previous.footer.equals(footerStr)) {
                continue;
            }
            lastRendered.put(player.getUuid(), new RenderedTab(headerStr, footerStr));

            Component header = Text.raw(headerStr);
            Component footer = Text.raw(footerStr);
            player.sendPacket(new PlayerListHeaderAndFooterPacket(header, footer));
        }
    }

    /**
     * Send a batched {@code UPDATE_LATENCY} for every player whose latency changed since the
     * last broadcast. Unchanged players are omitted so we don't generate idle packet noise.
     */
    private void broadcastLatencies() {
        Collection<Player> onlinePlayers = MinecraftServer.getConnectionManager().getOnlinePlayers();
        if (onlinePlayers.isEmpty()) return;

        List<PlayerInfoUpdatePacket.Entry> entries = null;
        for (Player player : onlinePlayers) {
            int latency = player.getLatency();
            Integer previous = lastBroadcastLatency.get(player.getUuid());
            if (previous != null && previous == latency) continue;
            lastBroadcastLatency.put(player.getUuid(), latency);

            // For UPDATE_LATENCY only the `latency` field is serialised — other fields are
            // unread by clients but the record requires them, so pass benign defaults.
            if (entries == null) entries = new ArrayList<>();
            entries.add(new PlayerInfoUpdatePacket.Entry(
                    player.getUuid(),
                    player.getUsername(),
                    List.of(),
                    true,
                    latency,
                    player.getGameMode(),
                    null,
                    null,
                    0,
                    true
            ));
        }
        if (entries == null) return;
        PacketSendingUtils.broadcastPlayPacket(new PlayerInfoUpdatePacket(
                EnumSet.of(PlayerInfoUpdatePacket.Action.UPDATE_LATENCY), entries));
    }

    private DateTimeFormatter formatterFor(String pattern) {
        if (!pattern.equals(currentTimeFormatPattern)) {
            try {
                timeFormatter.set(DateTimeFormatter.ofPattern(pattern));
                currentTimeFormatPattern = pattern;
            } catch (IllegalArgumentException ignored) { /* keep previous */ }
        }
        return timeFormatter.get();
    }

    private ZoneId cachedZone = ZoneId.systemDefault();
    private String cachedZoneId = "";

    /** Resolves the configured {@code timeZone}; falls back to the previous valid zone. */
    private ZoneId zoneFor(String zoneId) {
        if (zoneId != null && !zoneId.equals(cachedZoneId)) {
            try {
                cachedZone = ZoneId.of(zoneId);
                cachedZoneId = zoneId;
            } catch (Exception ignored) { /* keep previous */ }
        }
        return cachedZone;
    }

    /**
     * Substitutes everything but {@code {ping}} (kept verbatim for the per-player pass).
     * Lines with no placeholder are appended as-is — avoids the StringBuilder allocations
     * inside {@code String.replace} for the (common) static lines.
     */
    private static String renderTemplate(List<String> lines, int online, String state) {
        if (lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            String line = lines.get(i);
            if (line.indexOf('{') < 0) { sb.append(line); continue; }
            sb.append(line
                    .replace("{online}", Integer.toString(online))
                    .replace("{state}", state));
        }
        return sb.toString();
    }

    /** Per-player time: their IP zone if opted in and resolved, else the configured default. */
    private static String playerTime(Player player, PlayerTimeZoneService tz,
                                     DateTimeFormatter fmt, String defaultTime) {
        if (tz != null && tz.isIpMode(player.getUuid())) {
            java.util.Optional<ZoneId> zone = tz.zoneOf(player.getUuid());
            if (zone.isPresent()) {
                return LocalTime.now(zone.get()).format(fmt)
                        + " <dark_gray>(" + offsetLabel(zone.get()) + ")";
            }
        }
        return defaultTime;
    }

    /** Short UTC-offset label for a zone, e.g. {@code UTC+3}. */
    private static String offsetLabel(ZoneId zone) {
        int seconds = zone.getRules().getOffset(java.time.Instant.now()).getTotalSeconds();
        if (seconds == 0) return "UTC";
        int hours = seconds / 3600;
        int minutes = Math.abs(seconds % 3600) / 60;
        String label = "UTC" + (seconds > 0 ? "+" : "-") + Math.abs(hours);
        return minutes == 0 ? label : label + ":" + String.format("%02d", minutes);
    }

    private record RenderedTab(String header, String footer) {}
}
