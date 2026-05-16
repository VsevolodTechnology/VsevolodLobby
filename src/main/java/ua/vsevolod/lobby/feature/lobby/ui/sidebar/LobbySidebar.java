package ua.vsevolod.lobby.feature.lobby.ui.sidebar;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.scoreboard.Sidebar;
import ua.vsevolod.lobby.config.server.ServerInfo;
import ua.vsevolod.lobby.config.server.ServerRegistry;
import ua.vsevolod.lobby.util.Text;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class LobbySidebar {

    private static final String ID_BLANK_TOP    = "blank_top";
    private static final String ID_WELCOME      = "welcome";
    private static final String ID_BLANK_A      = "blank_a";
    private static final String ID_DESC         = "desc_";
    private static final String ID_BLANK_B      = "blank_b";
    private static final String ID_MODES_HEADER = "modes_header";
    private static final String ID_BLANK_C      = "blank_c";
    private static final String ID_PING         = "ping";

    private final PerViewerSidebar sidebar;
    private final AtomicInteger animationIndex = new AtomicInteger(0);

    /**
     * Last-rendered global-line texts → skip resend if unchanged.
     * <p>Concurrent because writes happen from both the refresh scheduler task and the player
     * event thread (via {@link #show(Player)} → {@code buildLayout}-style line registrations) —
     * a plain HashMap here would risk a CME under reload + join contention.
     */
    private final Map<String, String> lastGlobalText = new ConcurrentHashMap<>();

    /** Last-rendered per-player ping line. */
    private final Map<UUID, String> lastPingText = new ConcurrentHashMap<>();

    /** Last rendered title frame string — skip resend if unchanged. */
    private String lastTitle = "";

    /**
     * Tracks the {@code enabled} value we last reflected to viewers — so {@link #applyEnabledState}
     * is a no-op except on actual transitions (config reload). The previous code called
     * {@code sidebar.addViewer(player)} every refresh tick, which (per Minestom semantics) re-sends
     * every team-creation packet for every line — including the ping line with its <i>baseline</i>
     * prefix ("Ping: 0"). The per-viewer ping update then skipped because the rendered text equalled
     * the cached value, so the override never re-asserted and the client kept seeing "0".
     */
    private boolean lastEnabledApplied;

    public LobbySidebar() {
        this.sidebar = new PerViewerSidebar(Text.c("&#FF9700&lOVERDYN"));
        this.lastEnabledApplied = SidebarConfigSection.INSTANCE.current().enabled();
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
        lastGlobalText.put(id, text);
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
        if (!cfg.enabled()) return;
        List<String> frames = cfg.titleFrames();
        if (frames.isEmpty()) return;
        int i = animationIndex.getAndUpdate(old -> (old + 1) % frames.size());
        String rendered = cfg.titleFrameTemplate().replace("{frame}", frames.get(i));
        if (rendered.equals(lastTitle)) return;
        lastTitle = rendered;
        sidebar.setTitle(Text.raw(rendered));
    }

    private void refreshAll() {
        SidebarConfig cfg = SidebarConfigSection.INSTANCE.current();
        applyEnabledState(cfg);
        if (!cfg.enabled()) return;

        updateGlobalLine(ID_WELCOME, cfg.welcomeText());
        for (int i = 0; i < cfg.descriptionLines().size(); i++) {
            updateGlobalLine(ID_DESC + i, cfg.descriptionLines().get(i));
        }
        updateGlobalLine(ID_MODES_HEADER, cfg.modesHeader());

        for (ServerInfo server : ServerRegistry.LOBBY_SERVERS) {
            updateGlobalLine(serverLineId(server), formatServerLine(server, cfg));
        }

        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            updatePing(player);
        }
    }

    /**
     * Transitions all online players between viewer / not-viewer state when the configured
     * {@code enabled} flag flips (i.e. after /reload). On the steady-state path this method is
     * a single field-compare and returns immediately — no per-player work, no resent packets,
     * no stomping on per-viewer ping overrides.
     */
    private void applyEnabledState(SidebarConfig cfg) {
        boolean enabled = cfg.enabled();
        if (enabled == lastEnabledApplied) return;
        lastEnabledApplied = enabled;
        var players = MinecraftServer.getConnectionManager().getOnlinePlayers();
        if (enabled) {
            for (Player player : players) sidebar.addViewer(player);
        } else {
            for (Player player : players) sidebar.removeViewer(player);
        }
    }

    private void updateGlobalLine(String id, String text) {
        String previous = lastGlobalText.get(id);
        if (text.equals(previous)) return;
        lastGlobalText.put(id, text);
        sidebar.updateLineContent(id, Text.raw(text));
    }

    public void show(Player player) {
        if (!SidebarConfigSection.INSTANCE.current().enabled()) return;
        sidebar.addViewer(player);
        updatePing(player);
    }

    public void hide(Player player) {
        sidebar.removeViewer(player);
        lastPingText.remove(player.getUuid());
    }

    public void updatePing(Player player) {
        SidebarConfig cfg = SidebarConfigSection.INSTANCE.current();
        String rendered = cfg.pingTemplate().replace("{ping}", Integer.toString(player.getLatency()));
        String previous = lastPingText.get(player.getUuid());
        if (rendered.equals(previous)) return;
        lastPingText.put(player.getUuid(), rendered);
        sidebar.updateLineContent(ID_PING, Text.raw(rendered), player);
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
