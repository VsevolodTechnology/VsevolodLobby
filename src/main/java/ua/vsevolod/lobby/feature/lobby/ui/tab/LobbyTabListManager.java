package ua.vsevolod.lobby.feature.lobby.ui.tab;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.server.ServerTickMonitorEvent;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.integration.spark.SparkService;
import ua.vsevolod.lobby.util.Text;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class LobbyTabListManager {

    private final AtomicReference<Double> lastTickMs = new AtomicReference<>(50.0);
    private final AtomicReference<DateTimeFormatter> timeFormatter =
            new AtomicReference<>(DateTimeFormatter.ofPattern("HH:mm"));
    private String currentTimeFormatPattern = "HH:mm";

    public LobbyTabListManager(GlobalEventHandler events) {
        events.addListener(ServerTickMonitorEvent.class, event ->
                lastTickMs.set(event.getTickMonitor().getTickTime())
        );
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
            } catch (IllegalArgumentException ignored) {
                // keep previous formatter
            }
        }
        return timeFormatter.get();
    }

    private void update(Player player, TabConfig cfg, String formattedMspt, String time, int online) {
        boolean bypass = LobbyConfig.Settings.BYPASS_USERS.contains(player.getUsername());
        String msptPart = bypass
                ? cfg.msptBypassTemplate().replace("{mspt}", formattedMspt)
                : "";

        Component header = renderBlock(cfg.header(), player, time, online, msptPart);
        Component footer = renderBlock(cfg.footer(), player, time, online, msptPart);

        player.sendPlayerListHeaderAndFooter(header, footer);
    }

    private static Component renderBlock(List<String> lines, Player player, String time, int online, String msptPart) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(substitute(lines.get(i), player, time, online, msptPart));
        }
        return Text.raw(sb.toString());
    }

    private static String substitute(String line, Player player, String time, int online, String msptPart) {
        return line
                .replace("{ping}", Integer.toString(player.getLatency()))
                .replace("{online}", Integer.toString(online))
                .replace("{time}", time)
                .replace("{mspt}", msptPart)
                .replace("{player}", player.getUsername());
    }
}
