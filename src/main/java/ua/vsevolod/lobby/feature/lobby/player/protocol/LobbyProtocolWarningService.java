package ua.vsevolod.lobby.feature.lobby.player.protocol;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.timer.TaskSchedule;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.player.prefs.PlayerPreferencesService;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.minestom.server.timer.ExecutionType.TICK_START;

/**
 * Shows a 30-second BossBar to players whose client protocol is below the recommended version.
 *
 * <p><b>Audit MED-06.</b> The previous implementation scheduled a separate 1 s repeating task
 * <i>per warned player</i>. A burst of legacy clients joining together would spawn N parallel
 * tasks, each touching one BossBar. Replaced with one global 1 s tick that walks a
 * {@link ConcurrentHashMap} of active warnings — N players cost one task, one map iteration
 * per second.</p>
 */
public final class LobbyProtocolWarningService {

    /**
     * Protocol players are expected to be on. Tied to {@link MinecraftServer#PROTOCOL_VERSION}
     * so it auto-bumps whenever the Minestom dependency is upgraded — no constant to forget.
     */
    private static int recommendedProtocol() {
        return MinecraftServer.PROTOCOL_VERSION;
    }

    private static final int WARNING_DURATION_SECONDS = 30;

    /** Active warnings keyed by player UUID. */
    private final Map<UUID, Warning> active = new ConcurrentHashMap<>();
    private final PlayerPreferencesService preferencesService;
    private boolean tickerStarted = false;

    public LobbyProtocolWarningService(PlayerPreferencesService preferencesService) {
        this.preferencesService = preferencesService;
    }

    public synchronized void showIfNeeded(Player player) {
        if (!player.hasTag(LobbyConfig.Settings.IDENTIFIER_CLIENT_PROTOCOL)) {
            return;
        }
        int protocol = player.getTag(LobbyConfig.Settings.IDENTIFIER_CLIENT_PROTOCOL);
        if (protocol >= recommendedProtocol()) {
            return;
        }
        // Per-player opt-out from LobbySettingsMenu — pref is `protocolWarningEnabled` (default on).
        if (!preferencesService.get(player.getUuid()).protocolWarningEnabled()) {
            return;
        }
        showWarning(player);
        ensureTickerStarted();
    }

    /** Hide an active warning for a player (called when they opt out via settings menu). */
    public synchronized void hideFor(Player player) {
        Warning w = active.remove(player.getUuid());
        if (w != null) player.hideBossBar(w.bar);
    }

    private void showWarning(Player player) {
        player.playSound(Sound.sound(
                SoundEvent.ENTITY_BREEZE_IDLE_GROUND,
                Sound.Source.RECORD,
                1.0f,
                1.0f
        ));

        BossBar bossBar = BossBar.bossBar(
                LobbyConfig.Messages.buildVersionWarning(WARNING_DURATION_SECONDS),
                1f,
                BossBar.Color.RED,
                BossBar.Overlay.PROGRESS
        );
        player.showBossBar(bossBar);

        // Replace any existing warning for the same UUID (re-join during warning).
        Warning previous = active.put(player.getUuid(), new Warning(bossBar, WARNING_DURATION_SECONDS));
        if (previous != null) {
            player.hideBossBar(previous.bar);
        }
    }

    private synchronized void ensureTickerStarted() {
        if (tickerStarted) return;
        tickerStarted = true;
        MinecraftServer.getSchedulerManager().scheduleTask(
                this::tickOnce,
                TaskSchedule.seconds(1),
                TaskSchedule.seconds(1),
                TICK_START
        );
    }

    private void tickOnce() {
        if (active.isEmpty()) return;

        var connectionManager = MinecraftServer.getConnectionManager();
        for (Iterator<Map.Entry<UUID, Warning>> it = active.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Warning> entry = it.next();
            Warning warning = entry.getValue();
            Player player = connectionManager.getOnlinePlayerByUuid(entry.getKey());

            if (player == null || warning.secondsLeft <= 0) {
                if (player != null) player.hideBossBar(warning.bar);
                it.remove();
                continue;
            }

            warning.bar.name(LobbyConfig.Messages.buildVersionWarning(warning.secondsLeft));
            warning.bar.progress(Math.max(0f, (float) warning.secondsLeft / WARNING_DURATION_SECONDS));

            if ((warning.secondsLeft % 5 == 0 || warning.secondsLeft == 3) && warning.secondsLeft != 0) {
                player.playSound(Sound.sound(
                        SoundEvent.BLOCK_AMETHYST_BLOCK_RESONATE,
                        Sound.Source.MASTER,
                        1f,
                        0.6f
                ));
            }

            warning.secondsLeft--;
        }
    }

    /** Mutable per-warning state — fine here because all access is from the same ticker thread. */
    private static final class Warning {
        final BossBar bar;
        int secondsLeft;
        Warning(BossBar bar, int secondsLeft) { this.bar = bar; this.secondsLeft = secondsLeft; }
    }
}
