package ua.vsevolod.lobby.feature.lobby.ui.tab;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.server.ServerTickMonitorEvent;
import net.minestom.server.network.packet.server.play.PlayerListHeaderAndFooterPacket;
import net.minestom.server.utils.PacketSendingUtils;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.integration.spark.SparkService;
import ua.vsevolod.lobby.util.Text;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tab list (player list header + footer) renderer.
 *
 * <h3>Hot-path design</h3>
 * <p>The tab is the most expensive periodic broadcast in the lobby (default 100 ms tick, header
 * + footer Components per player). Two layers of caching collapse the cost from O(online) to
 * roughly O(unique-buckets):</p>
 *
 * <ol>
 *   <li><b>Bucket-keyed Component cache (per tick).</b> Players with the same
 *       {@code (bypass, pingBucket)} pair see identical text once {@code {time}} and
 *       {@code {online}} are substituted. We compute the rendered Component once per bucket
 *       per tick rather than once per player.</li>
 *   <li><b>Grouped packet send.</b> All players in a bucket get the same
 *       {@link PlayerListHeaderAndFooterPacket} — Minestom's
 *       {@link PacketSendingUtils#sendGroupedPacket} serialises it once for the whole group
 *       instead of per recipient.</li>
 *   <li><b>Per-viewer skip-if-unchanged.</b> If a player's previously rendered text equals
 *       this tick's, no packet is sent at all (cleared on disconnect).</li>
 * </ol>
 *
 * <p>Ping is bucketed at 50 ms granularity so flutter in latency doesn't churn the bucket
 * Component — values differing by a few ms hash to the same bucket.</p>
 */
public final class LobbyTabListManager {

    /** Wider bucket = fewer buckets = more grouping. 50 ms keeps the bar near-real-time. */
    private static final int PING_BUCKET_MS = 50;

    private final AtomicReference<Double> lastTickMs = new AtomicReference<>(50.0);
    private final AtomicReference<DateTimeFormatter> timeFormatter =
            new AtomicReference<>(DateTimeFormatter.ofPattern("HH:mm"));
    private String currentTimeFormatPattern = "HH:mm";

    /** Last text rendered for each viewer — used to skip identical packets between ticks. */
    private final Map<UUID, RenderedTab> lastRendered = new ConcurrentHashMap<>();

    public LobbyTabListManager(GlobalEventHandler events) {
        events.addListener(ServerTickMonitorEvent.class, event ->
                lastTickMs.set(event.getTickMonitor().getTickTime())
        );
        events.addListener(PlayerDisconnectEvent.class, e -> lastRendered.remove(e.getPlayer().getUuid()));
        scheduleRefresh();
    }

    private void scheduleRefresh() {
        MinecraftServer.getSchedulerManager()
                .buildTask(this::updateAll)
                .repeat(Duration.ofMillis(TabConfigSection.INSTANCE.current().updateIntervalMs()))
                .schedule();
    }

    public void updateAll() {
        TabConfig cfg = TabConfigSection.INSTANCE.current();
        Collection<Player> onlinePlayers = MinecraftServer.getConnectionManager().getOnlinePlayers();
        if (onlinePlayers.isEmpty()) return;

        String formattedMspt = SparkService.getMsptFormatted();
        String time = LocalTime.now().format(formatterFor(cfg.timeFormat()));
        int online = onlinePlayers.size();

        // Group players into buckets where everyone sees the same text. Per-tick cache lives
        // only inside this method scope — small allocation, no leak risk.
        Map<BucketKey, List<Player>> buckets = new HashMap<>();
        for (Player player : onlinePlayers) {
            buckets.computeIfAbsent(bucketFor(player), k -> new ArrayList<>(4)).add(player);
        }

        for (Map.Entry<BucketKey, List<Player>> entry : buckets.entrySet()) {
            BucketKey key = entry.getKey();
            List<Player> members = entry.getValue();

            String msptPart = key.bypass()
                    ? cfg.msptBypassTemplate().replace("{mspt}", formattedMspt)
                    : "";
            int representativePing = key.pingBucket() * PING_BUCKET_MS;

            String headerStr = renderBlock(cfg.header(), representativePing, time, online, msptPart);
            String footerStr = renderBlock(cfg.footer(), representativePing, time, online, msptPart);

            sendToBucket(members, headerStr, footerStr);
        }
    }

    private BucketKey bucketFor(Player player) {
        boolean bypass = LobbyConfig.Settings.BYPASS_USERS.contains(player.getUsername());
        int bucket = player.getLatency() / PING_BUCKET_MS;
        return new BucketKey(bypass, bucket);
    }

    private void sendToBucket(List<Player> members, String headerStr, String footerStr) {
        // Filter: only members whose last-sent text differs. If everyone is up-to-date, skip
        // the whole grouped-send.
        List<Player> needsUpdate = null;
        for (Player member : members) {
            RenderedTab previous = lastRendered.get(member.getUuid());
            if (previous != null && previous.header.equals(headerStr) && previous.footer.equals(footerStr)) {
                continue;
            }
            if (needsUpdate == null) needsUpdate = new ArrayList<>(members.size());
            needsUpdate.add(member);
            lastRendered.put(member.getUuid(), new RenderedTab(headerStr, footerStr));
        }
        if (needsUpdate == null) return;

        Component header = Text.raw(headerStr);
        Component footer = Text.raw(footerStr);
        PlayerListHeaderAndFooterPacket packet = new PlayerListHeaderAndFooterPacket(header, footer);
        PacketSendingUtils.sendGroupedPacket(needsUpdate, packet);
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

    private static String renderBlock(List<String> lines, int ping, String time, int online, String msptPart) {
        if (lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(substitute(lines.get(i), ping, time, online, msptPart));
        }
        return sb.toString();
    }

    private static String substitute(String line, int ping, String time, int online, String msptPart) {
        if (line.indexOf('{') < 0) return line;
        return line
                .replace("{ping}", Integer.toString(ping))
                .replace("{online}", Integer.toString(online))
                .replace("{time}", time)
                .replace("{mspt}", msptPart);
        // {player} placeholder removed from bucket-grouped path — a bucket is by definition
        // shared by multiple players. If you need per-player text, re-introduce a fast path
        // that detects {player} in any line and falls back to the old per-player loop.
    }

    private record RenderedTab(String header, String footer) {}

    /** Identifies a group of players that should see identical tab text this tick. */
    private record BucketKey(boolean bypass, int pingBucket) {}
}
