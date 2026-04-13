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
import java.util.concurrent.atomic.AtomicReference;

public final class LobbyTabListManager {

    private final AtomicReference<Double> lastTickMs = new AtomicReference<>(50.0);

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm");

    public LobbyTabListManager(GlobalEventHandler events) {

        events.addListener(ServerTickMonitorEvent.class, event ->
                lastTickMs.set(event.getTickMonitor().getTickTime())
        );

        MinecraftServer.getSchedulerManager()
                .buildTask(this::updateAll)
                .repeat(Duration.ofMillis(100))
                .schedule();
    }

    public void update(Player player, String tickMs) {

        int ping = player.getLatency();
        int online = MinecraftServer.getConnectionManager().getOnlinePlayers().size();

        var mspt = LobbyConfig.Settings.BYPASS_USERS.contains(player.getUsername()) ? " " +LobbyConfig.Project.WHITE_COLOR_ORIGINAL+"MSPT: &e"+tickMs : "";

        String time = LocalTime.now().format(TIME);

        Component header = Text.c(
                "&#FF9700&lOVERDYN\n" +
                        "\n" +
                        "&#FFF2E0Вы находитесь в &eЛобби\n" +
                        "&#FFF2E0Пинг: &a" + ping + "мс &7⇄ &#FFF2E0Время: &a" + time + mspt+"\n" +
                        "\n" +
                        "&#FF9700↶ &#FFF2E0Список игроков &#FF9700↷"
        );

        Component footer = Text.c(
                "\n" +
                        "&#FFF2E0Магазин: &#FA0909&nᴡ&#FA1308&nᴡ&#FB1D08&nᴡ&#FB2707&n.&#FB3206&nʜ&#FC3C06&nᴏ&#FC4605&nᴛ&#FD5005&nᴡ&#FD5A04&nᴏ&#FD6403&nʀ&#FE6E03&nʟ&#FE7902&nᴅ&#FE8301&n.&#FF8D01&ns&#FF9700&nᴜ\n" +
                        "&#FFF2E0Дискорд: &#FA0909&nᴅ&#FA1109&nɪ&#FB1908&nꜱ&#FB2108&nᴄ&#FB2907&nᴏ&#FB3007&nʀ&#FC3806&nᴅ&#FC4006&n.&#FC4805&nɢ&#FD5005&nɢ&#FD5804&n/&#FD6004&nʜ&#FD6803&nᴏ&#FE7003&nᴛ&#FE7702&nᴡ&#FE7F02&nᴏ&#FE8701&nʀ&#FF8F01&nʟ&#FF9700&nᴅ\n" +
                        "&#FFF2E0Вконтакте: &#FA0909&nᴠ&#FA1308&nᴋ&#FB1D08&n.&#FB2707&nᴄ&#FB3206&nᴏ&#FC3C06&nᴍ&#FC4605&n/&#FD5005&nʜ&#FD5A04&nᴏ&#FD6403&nᴛ&#FE6E03&nᴡ&#FE7902&nᴏ&#FE8301&nʀ&#FF8D01&nʟ&#FF9700&nᴅ_ᴍᴄ\n"
        );

        player.sendPlayerListHeaderAndFooter(header, footer);
    }

    public void updateAll() {
        var tickMs = SparkService.getMsptFormatted();

        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            update(player, tickMs);
        }
    }
}
