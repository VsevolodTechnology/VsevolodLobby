package ua.vsevolod.lobby.feature.lobby.player.join.cutscene;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerStartSneakingEvent;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.audio.music.LobbyMusicManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plays a cinematic camera flyover for the player. Driven by {@link CutsceneConfig}
 * — see that class for the YAML schema.
 *
 * <h3>How the camera works</h3>
 * The player is switched to {@link GameMode#SPECTATOR} and the {@code Player} entity itself
 * is teleported every tick to the interpolated waypoint pose — the player IS the camera. We
 * previously tried the "spectate an invisible armor stand" approach, but in vanilla Minecraft
 * the spectating client keeps its OWN view rotation (the stand's yaw/pitch is ignored), and
 * the player's body stays at the pre-spectate position so {@link Sound.Emitter#self()}-bound
 * music plays from a stale location. Moving the player directly fixes both: the configured
 * waypoint yaw/pitch is applied verbatim via teleport, and the music emitter follows.
 *
 * <h3>Music handling</h3>
 * If {@link CutsceneConfig#cinematicSound} is non-empty, the lobby music manager is asked
 * to play that single track via {@link LobbyMusicManager#playSpecific}. When the flyover
 * finishes (naturally or via the player sneaking to skip) we stop playback and restart the
 * default rotation with {@link LobbyMusicManager#start}, so a first-time player — who can't
 * have toggled music off yet — hears the lobby's default loop kick in immediately after.
 *
 * <h3>Finish callback</h3>
 * {@link #play(Player, Runnable)} accepts a runnable that fires when the flyover ends. The
 * join initializer uses it to defer giving join items: the player should not see a hotbar
 * full of compasses while the camera is moving.
 */
public final class CutsceneService {

    private static final TextColor PROJECT_WHITE = TextColor.color(0xFFF2E0);
    private static final TextColor ACCENT_HONEY  = TextColor.color(0xC58AF0);

    private final LobbyMusicManager musicManager;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public CutsceneService(LobbyMusicManager musicManager) {
        this.musicManager = musicManager;
    }

    public void register(GlobalEventHandler events) {
        EventNode<net.minestom.server.event.Event> node = EventNode.all("cutscene");
        node.addListener(PlayerDisconnectEvent.class, e -> cancel(e.getPlayer()));
        node.addListener(PlayerStartSneakingEvent.class, e -> {
            if (sessions.containsKey(e.getPlayer().getUuid())
                    && CutsceneConfig.get().skippable) {
                cancel(e.getPlayer());
            }
        });
        events.addChild(node);
    }

    public void play(Player player) {
        play(player, () -> {});
    }

    /**
     * Start a flyover and run {@code onFinish} when it ends (naturally, on skip, or on
     * disconnect). The callback fires at most once. If the cutscene can't start (disabled, no
     * waypoints, player has no instance, already playing) {@code onFinish} fires immediately.
     */
    public void play(Player player, Runnable onFinish) {
        CutsceneConfig cfg = CutsceneConfig.get();
        if (!cfg.enabled || cfg.waypoints.isEmpty() || player.getInstance() == null) {
            onFinish.run();
            return;
        }
        if (sessions.containsKey(player.getUuid())) {
            onFinish.run();
            return;
        }

        GameMode previousMode = player.getGameMode();
        Pos originalPosition = player.getPosition();

        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(posOf(cfg.waypoints.get(0)));

        if (cfg.skippable) {
            player.sendActionBar(Component.text()
                    .append(Component.text("Зажми ", PROJECT_WHITE))
                    .append(Component.text("SHIFT", ACCENT_HONEY).decoration(TextDecoration.BOLD, true))
                    .append(Component.text(" чтобы пропустить ✦", PROJECT_WHITE))
                    .build());
        }
        // Respect the player's music toggle — if they have music off, stay silent and DO NOT
        // resurrect playback at the end either. playSpecific would otherwise stomp the
        // preference back to enabled and save it, which surprises returning admins.
        if (!cfg.cinematicSound.isBlank() && musicManager.isEnabled(player)) {
            musicManager.playSpecific(player, cfg.cinematicSound);
        }

        Session session = new Session(player, cfg, previousMode, originalPosition, onFinish);
        sessions.put(player.getUuid(), session);
        session.start();
    }

    public void cancel(Player player) {
        Session s = sessions.remove(player.getUuid());
        if (s != null) s.finish();
    }

    private static Pos posOf(CutsceneConfig.Waypoint w) {
        return new Pos(w.x(), w.y(), w.z(), w.yaw(), w.pitch());
    }

    // ── Per-player session ───────────────────────────────────────────────────

    private final class Session {

        private final Player player;
        private final CutsceneConfig cfg;
        private final GameMode previousMode;
        private final Pos originalPosition;
        private final Runnable onFinish;

        /** Current segment cursor: gliding from waypoint[idx] to waypoint[idx+1]. */
        private int idx = 0;
        /** Ticks elapsed since entering the current segment. */
        private int elapsed = 0;
        private Task task;
        private boolean finished = false;

        Session(Player player, CutsceneConfig cfg,
                GameMode previousMode, Pos originalPosition, Runnable onFinish) {
            this.player = player;
            this.cfg = cfg;
            this.previousMode = previousMode;
            this.originalPosition = originalPosition;
            this.onFinish = onFinish;
        }

        void start() {
            task = MinecraftServer.getSchedulerManager().scheduleTask(
                    this::tick, TaskSchedule.tick(1), TaskSchedule.tick(1));
        }

        private void tick() {
            if (!player.isOnline()) {
                CutsceneService.this.cancel(player);
                return;
            }

            CutsceneConfig.Waypoint here = cfg.waypoints.get(idx);
            int holdTicks = Math.max(1, here.holdTicks());

            if (elapsed < holdTicks) {
                player.teleport(posOf(here));
                elapsed++;
                return;
            }

            int nextIdx = idx + 1;
            if (nextIdx >= cfg.waypoints.size()) {
                CutsceneService.this.cancel(player);
                return;
            }

            int interp = Math.max(1, cfg.interpolationTicks);
            int glideTick = elapsed - holdTicks;
            if (glideTick >= interp) {
                idx = nextIdx;
                elapsed = 0;
                return;
            }

            double t = (glideTick + 1) / (double) interp;
            t = smoothstep(t);
            CutsceneConfig.Waypoint next = cfg.waypoints.get(nextIdx);
            player.teleport(lerp(here, next, t));
            elapsed++;
        }

        void finish() {
            if (finished) return;
            finished = true;
            if (task != null) task.cancel();
            if (player.isOnline()) {
                player.setGameMode(previousMode);
                Pos target = LobbyConfig.Locations.SPAWN_POS_PLAYER;
                if (target == null) target = originalPosition;
                player.teleport(target);

                musicManager.stop(player);
                // Only re-start music if the player actually wants it. If they have it
                // toggled off (admin replay, or a returning player who muted themselves),
                // leave them in silence — anything else clobbers a saved preference.
                if (musicManager.isEnabled(player)) {
                    String post = cfg.postCutsceneSound;
                    if (post != null && !post.isBlank()) {
                        musicManager.playSpecific(player, post);
                    } else {
                        musicManager.start(player);
                    }
                }
            }
            try {
                onFinish.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        private static double smoothstep(double t) {
            double clamped = Math.max(0.0, Math.min(1.0, t));
            return clamped * clamped * (3.0 - 2.0 * clamped);
        }

        private static Pos lerp(CutsceneConfig.Waypoint a, CutsceneConfig.Waypoint b, double t) {
            double x = a.x() + (b.x() - a.x()) * t;
            double y = a.y() + (b.y() - a.y()) * t;
            double z = a.z() + (b.z() - a.z()) * t;
            float yaw = lerpYaw(a.yaw(), b.yaw(), (float) t);
            float pitch = a.pitch() + (b.pitch() - a.pitch()) * (float) t;
            return new Pos(x, y, z, yaw, pitch);
        }

        private static float lerpYaw(float a, float b, float t) {
            float diff = ((b - a + 540f) % 360f) - 180f;
            return a + diff * t;
        }
    }
}
