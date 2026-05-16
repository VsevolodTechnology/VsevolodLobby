package ua.vsevolod.lobby.feature.parkour;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.ChunkLoader;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import net.minestom.server.timer.TaskSchedule;
import net.minestom.server.world.DimensionType;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.parkour.leaderboard.ParkourLeaderboardEntry;
import ua.vsevolod.lobby.feature.parkour.leaderboard.ParkourLeaderboardService;
import ua.vsevolod.lobby.util.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ParkourService {

    public final static Component PARKOUR_TEXT = Component.text("[", NamedTextColor.DARK_GRAY)
            .append(Text.c("&#F1BB58&lП&#F1B958&lа&#F1B658&lр&#F1B458&lк&#F1B158&lу&#F1AF58&lр"))
            .append(Component.text("]", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false).append(Component.space());

    private final InstanceManager instanceManager = MinecraftServer.getInstanceManager();
    private final Map<UUID, ParkourSession> sessions = new ConcurrentHashMap<>();
    private final ParkourLeaderboardService leaderboardService;

    public ParkourService(ParkourLeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    public void start(Player player) {
        stop(player);

        // Pass an explicit noop ChunkLoader so Minestom does not default to AnvilLoader and
        // probe the filesystem for non-existent .mca files. Spark profile (2026-05-16) caught
        // AnvilLoader.loadMCA at 0.30 s of FJ-pool CPU per parkour run — pure waste because
        // this instance is generator-only.
        InstanceContainer instance = instanceManager.createInstanceContainer(
                DimensionType.OVERWORLD, ChunkLoader.noop());
        instance.setGenerator(unit -> {
            unit.modifier().fillHeight(0, 80, Block.AIR);
            unit.modifier().fillHeight(-64, 0, Block.BARRIER);
        });

        Pos start = LobbyConfig.Parkour.START_POS;
        ParkourLeaderboardEntry bestEntry = leaderboardService.bestEntry(player.getUuid()).orElse(null);
        int bestScore = bestEntry != null ? bestEntry.score() : 0;
        long bestDuration = bestEntry != null ? bestEntry.durationMillis() : Long.MAX_VALUE;

        ParkourSession session = new ParkourSession(player, instance, start, bestScore, bestDuration);
        sessions.put(player.getUuid(), session);

        player.setInstance(instance, start).thenRun(session::start);
    }

    public void stop(Player player) {
        ParkourSession old = sessions.remove(player.getUuid());
        if (old == null) return;

        if (old.isFinished()) {
            var result = old.toRunResult();
            if (result.score() > 0) {
                leaderboardService.submit(result);
            }
        }

        InstanceContainer instance = old.getInstance();
        for (var entity : instance.getEntities()) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }

        // Each parkour run created its own InstanceContainer via `instanceManager.createInstanceContainer`.
        // Without this cleanup the InstanceManager keeps a strong reference to every past run's
        // instance — chunks, generator, block storage, the whole graph — forever. A handful of
        // /parkour cycles already shows up in the heap; over a day of uptime it is a true leak.
        // We can't unregister synchronously because the player is still inside the instance at
        // this point (returnToLobby() does setInstance(lobby) AFTER stop()); poll until the
        // player has been moved out, then unregister and stop the task.
        scheduleInstanceUnregister(instance);
    }

    private void scheduleInstanceUnregister(InstanceContainer instance) {
        MinecraftServer.getSchedulerManager().submitTask(() -> {
            if (!instance.getPlayers().isEmpty()) {
                return TaskSchedule.tick(20); // 1 s; still occupied, check again
            }
            try {
                instanceManager.unregisterInstance(instance);
            } catch (Exception e) {
                System.err.println("[ParkourService] Failed to unregister instance: " + e.getMessage());
            }
            return TaskSchedule.stop();
        });
    }

    public ParkourSession getSession(Player player) {
        return sessions.get(player.getUuid());
    }

    public boolean isInParkour(Player player) {
        return sessions.containsKey(player.getUuid());
    }

    /**
     * Lobby-global PlayerMoveEvent fires 5–10×/s per player (so ~2.5k–5k/s at 500 online).
     * The listener body always early-returns when no one is in parkour — but only AFTER paying
     * for an event-dispatch + per-player hash lookup. Expose this O(1) flag so the listener can
     * bail in the very first instruction when the parkour sessions map is empty.
     * Audit HIGH-07 — cheaper than the recommended EventNode scoping because that would require
     * restructuring around the per-run InstanceContainer lifecycle.
     */
    public boolean hasAnySession() {
        return !sessions.isEmpty();
    }
}
