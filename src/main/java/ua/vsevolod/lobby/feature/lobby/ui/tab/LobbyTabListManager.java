package ua.vsevolod.lobby.feature.lobby.ui.tab;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.server.ServerTickMonitorEvent;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.integration.spark.SparkService;
import ua.vsevolod.lobby.util.Text;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class LobbyTabListManager {

    private final AtomicReference<Double> lastTickMs = new AtomicReference<>(50.0);
    private final AtomicReference<DateTimeFormatter> timeFormatter =
            new AtomicReference<>(DateTimeFormatter.ofPattern("HH:mm"));
    private String currentTimeFormatPattern = "HH:mm";

    /** Last-pushed (header, footer) string per viewer; equal-text packets are skipped. */
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
        String formattedMspt = SparkService.getMsptFormatted();
        String time = LocalTime.now().format(formatterFor(cfg.timeFormat()));
        int online = MinecraftServer.getConnectionManager().getOnlinePlayers().size();

        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            update(player, cfg, formattedMspt, time, online);
        }
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

    private void update(Player player, TabConfig cfg, String formattedMspt, String time, int online) {
        boolean bypass = LobbyConfig.Settings.BYPASS_USERS.contains(player.getUsername());
        String msptPart = bypass
                ? cfg.msptBypassTemplate().replace("{mspt}", formattedMspt)
                : "";

        String header = renderBlock(cfg.header(), player, time, online, msptPart);
        String footer = renderBlock(cfg.footer(), player, time, online, msptPart);

        RenderedTab previous = lastRendered.get(player.getUuid());
        if (previous != null && previous.header.equals(header) && previous.footer.equals(footer)) {
            return;
        }
        lastRendered.put(player.getUuid(), new RenderedTab(header, footer));

        Component headerComponent = Text.raw(header);
        Component footerComponent = Text.raw(footer);
        player.sendPlayerListHeaderAndFooter(headerComponent, footerComponent);
    }

    private static String renderBlock(List<String> lines, Player player, String time, int online, String msptPart) {
        if (lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(substitute(lines.get(i), player, time, online, msptPart));
        }
        return sb.toString();
    }

    private static String substitute(String line, Player player, String time, int online, String msptPart) {
        if (line.indexOf('{') < 0) return line;   // fast path: no placeholders
        return line
                .replace("{ping}", Integer.toString(player.getLatency()))
                .replace("{online}", Integer.toString(online))
                .replace("{time}", time)
                .replace("{mspt}", msptPart)
                .replace("{player}", player.getUsername());
    }

    private record RenderedTab(String header, String footer) {}
}
