package xyz.overdyn.feature.lobby.ui.sidebar;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.scoreboard.Sidebar;
import xyz.overdyn.config.LobbyConfig;
import xyz.overdyn.config.server.ServerInfo;
import xyz.overdyn.config.server.ServerRegistry;
import xyz.overdyn.util.Text;

import java.time.Duration;
import java.util.List;

public final class LobbySidebar {

    private final PerViewerSidebar sidebar;
    private final TitleAnimation titleAnimation;

    public LobbySidebar() {
        this.sidebar = new PerViewerSidebar(Text.c("&#FF9700&lOVERDYN"));
        this.titleAnimation = new TitleAnimation();
        buildLayout();
        startAnimationTask();
    }

    private void buildLayout() {
        int score = 15;

        line("blank_top", "", score--);
        line("welcome", "  &7" + LobbyConfig.Lobby.WELCOME, score--);
        line("blank_a", "", score--);

        line("desc_1", " &7- &#FFF2E0Скорее &eвыбирай &#FFF2E0режим", score--);
        line("desc_2", " &7- &#FFF2E0для &bигры &#FFF2E0на сервере", score--);
        line("desc_3", " &7- &#FFF2E0и начинай свой &#EA1B40путь", score--);

        line("blank_b", "", score--);

        line("modes_header", "&#FF9700↶ &#FFF2E0Режимы онлайн &#FF9700↷", score--);

        List<ServerInfo> servers = ServerRegistry.LOBBY_SERVERS;
        for (int i = 0; i < servers.size(); i++) {
            ServerInfo server = servers.get(i);
            line(serverLineId(server), formatServerLine(server), score--);
        }

        line("blank_c", "", score--);
        line("ping", "&6➜ &fВаш пинг&7: &a0", score--);
    }

    private void startAnimationTask() {
        MinecraftServer.getSchedulerManager()
                .buildTask(() -> {

                    sidebar.setTitle(Text.c("&7« " + titleAnimation.nextFrame() + "&f &7»"));

                    updateOnlineServers();
                    updateAllPing();

                })
                .repeat(Duration.ofMillis(800))
                .schedule();
    }

    private void updateAllPing() {
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            updatePing(player);
        }
    }

    private void line(String id, String text, int score) {
        Sidebar.ScoreboardLine line = new Sidebar.ScoreboardLine(id, Text.c(text), score, Sidebar.NumberFormat.blank());
        sidebar.createLine(line);
    }

    private String serverLineId(ServerInfo server) {
        return "server_" + server.id().toLowerCase();
    }

    private String formatServerLine(ServerInfo server) {
        String onlineText = switch (server.status()) {
            case ONLINE -> "&#EA1B40" + server.online();
            case SOON -> "&#EA1B40" + LobbyConfig.Sections.SOON;
            case OFFLINE -> "&#EA1B40Выключен";
        };

        return "  &7• &#FFF2E0" + server.worldName() + "&7: " + onlineText;
    }

    public void show(Player player) {
        sidebar.addViewer(player);
        updatePing(player);
    }

    public void hide(Player player) {
        sidebar.removeViewer(player);
    }

    public void updatePing(Player player) {
        sidebar.updateLineContent(
                "ping",
                Text.c("&6➜ &f"+"Ваш пинг&7: &a" + player.getLatency()),
                player
        );
    }

    public void updateOnlineServers() {
        for (ServerInfo server : ServerRegistry.LOBBY_SERVERS) {
            sidebar.updateLineContent(
                    serverLineId(server),
                    Text.c(formatServerLine(server))
            );
        }

        int online = MinecraftServer.getConnectionManager().getOnlinePlayerCount();
        // если захочешь, можно добавить отдельную строку общего онлайна
    }
}
