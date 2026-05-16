package ua.vsevolod.lobby.feature.lobby.ui.sidebar;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.scoreboard.Sidebar;
import ua.vsevolod.lobby.config.server.ServerInfo;
import ua.vsevolod.lobby.config.server.ServerRegistry;
import ua.vsevolod.lobby.util.Text;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class LobbySidebar {

    /** Line IDs are fixed at construction; only their content reloads from config. */
    private static final String ID_BLANK_TOP    = "blank_top";
    private static final String ID_WELCOME      = "welcome";
    private static final String ID_BLANK_A      = "blank_a";
    private static final String ID_DESC         = "desc_";   // suffix with index
    private static final String ID_BLANK_B      = "blank_b";
    private static final String ID_MODES_HEADER = "modes_header";
    private static final String ID_BLANK_C      = "blank_c";
    private static final String ID_PING         = "ping";

    private final PerViewerSidebar sidebar;
    private final AtomicInteger animationIndex = new AtomicInteger(0);

    public LobbySidebar() {
        this.sidebar = new PerViewerSidebar(Text.c("&#FF9700&lOVERDYN"));
        buildLayout();
        startAnimationTask();
        startRefreshTask();
    }

    private void buildLayout() {
        SidebarConfig cfg = SidebarConfigSection.INSTANCE.current();
        int score = 15;

        registerLine(ID_BLANK_TOP, "", score--);
        registerLine(ID_WELCOME, cfg.welcomeText(), score--);
        registerLine(ID_BLANK_A, "", score--);

        for (int i = 0; i < cfg.descriptionLines().size(); i++) {
            registerLine(ID_DESC + i, cfg.descriptionLines().get(i), score--);
        }

        registerLine(ID_BLANK_B, "", score--);
        registerLine(ID_MODES_HEADER, cfg.modesHeader(), score--);

        for (ServerInfo server : ServerRegistry.LOBBY_SERVERS) {
            registerLine(serverLineId(server), formatServerLine(server, cfg), score--);
        }

        registerLine(ID_BLANK_C, "", score--);
        registerLine(ID_PING, cfg.pingTemplate().replace("{ping}", "0"), score--);
    }

    private void registerLine(String id, String text, int score) {
        sidebar.createLine(new Sidebar.ScoreboardLine(id, Text.c(text), score, Sidebar.NumberFormat.blank()));
    }

    private void startAnimationTask() {
        SidebarConfig cfg = SidebarConfigSection.INSTANCE.current();
        MinecraftServer.getSchedulerManager()
                .buildTask(this::tickAnimation)
                .repeat(Duration.ofMillis(cfg.titleAnimationIntervalMs()))
                .schedule();
    }

    private void startRefreshTask() {
        SidebarConfig cfg = SidebarConfigSection.INSTANCE.current();
        MinecraftServer.getSchedulerManager()
                .buildTask(this::refreshAll)
                .repeat(Duration.ofMillis(cfg.refreshIntervalMs()))
                .schedule();
    }

    private void tickAnimation() {
        SidebarConfig cfg = SidebarConfigSection.INSTANCE.current();
        List<String> frames = cfg.titleFrames();
        if (frames.isEmpty()) return;
        int i = animationIndex.getAndUpdate(old -> (old + 1) % frames.size());
        String rendered = cfg.titleFrameTemplate().replace("{frame}", frames.get(i));
        sidebar.setTitle(Text.raw(rendered));
    }

    private void refreshAll() {
        SidebarConfig cfg = SidebarConfigSection.INSTANCE.current();

        sidebar.updateLineContent(ID_WELCOME, Text.raw(cfg.welcomeText()));
        for (int i = 0; i < cfg.descriptionLines().size(); i++) {
            sidebar.updateLineContent(ID_DESC + i, Text.raw(cfg.descriptionLines().get(i)));
        }
        sidebar.updateLineContent(ID_MODES_HEADER, Text.raw(cfg.modesHeader()));

        for (ServerInfo server : ServerRegistry.LOBBY_SERVERS) {
            sidebar.updateLineContent(serverLineId(server), Text.raw(formatServerLine(server, cfg)));
        }

        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            sidebar.updateLineContent(
                    ID_PING,
                    Text.raw(cfg.pingTemplate().replace("{ping}", Integer.toString(player.getLatency()))),
                    player
            );
        }
    }

    public void show(Player player) {
        sidebar.addViewer(player);
        SidebarConfig cfg = SidebarConfigSection.INSTANCE.current();
        sidebar.updateLineContent(
                ID_PING,
                Text.raw(cfg.pingTemplate().replace("{ping}", Integer.toString(player.getLatency()))),
                player
        );
    }

    public void hide(Player player) {
        sidebar.removeViewer(player);
    }

    public void updatePing(Player player) {
        SidebarConfig cfg = SidebarConfigSection.INSTANCE.current();
        sidebar.updateLineContent(
                ID_PING,
                Text.raw(cfg.pingTemplate().replace("{ping}", Integer.toString(player.getLatency()))),
                player
        );
    }

    private static String serverLineId(ServerInfo server) {
        return "server_" + server.id().toLowerCase();
    }

    private static String formatServerLine(ServerInfo server, SidebarConfig cfg) {
        String status = switch (server.status()) {
            case ONLINE -> cfg.statusOnline().replace("{count}", String.valueOf(server.online()));
            case SOON -> cfg.statusSoon();
            case OFFLINE -> cfg.statusOffline();
        };
        return cfg.serverLineTemplate()
                .replace("{world}", server.worldName())
                .replace("{status}", status);
    }
}
