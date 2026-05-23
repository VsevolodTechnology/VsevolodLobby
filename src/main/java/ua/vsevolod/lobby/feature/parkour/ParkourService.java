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
import net.minestom.server.timer.TaskSchedule;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.world.DimensionType;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.parkour.leaderboard.ParkourLeaderboardEntry;
import ua.vsevolod.lobby.feature.parkour.leaderboard.ParkourLeaderboardService;
import ua.vsevolod.lobby.util.ServerLogger;
import ua.vsevolod.lobby.util.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ParkourService {

    public final static Component PARKOUR_TEXT = Component.text("[", NamedTextColor.DARK_GRAY)
            .append(Text.c("<gradient:#AE3AF3:#985DBC><bold>Паркур</bold></gradient>"))
            .append(Component.text("]", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false).append(Component.space());

    private final InstanceManager instanceManager = MinecraftServer.getInstanceManager();
    private final Map<UUID, ParkourSession> sessions = new ConcurrentHashMap<>();
    private final ParkourLeaderboardService leaderboardService;

    public ParkourService(ParkourLeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    public void start(Player player, ParkourDifficulty difficulty, ParkourTheme theme) {
        startWithDimension(player, difficulty, theme, DimensionType.THE_NETHER, false, ParkourSoundPreset.STANDARD);
    }

    public void startWithDimension(Player player, ParkourDifficulty difficulty, ParkourTheme theme,
                                   RegistryKey<DimensionType> dimension, boolean trainingMode,
                                   ParkourSoundPreset soundPreset) {
        stop(player);

        InstanceContainer instance = instanceManager.createInstanceContainer(dimension, ChunkLoader.noop());

        Pos start = LobbyConfig.Parkour.START_POS;
        ParkourLeaderboardEntry bestEntry = leaderboardService.bestEntry(player.getUuid()).orElse(null);
        int bestScore = bestEntry != null ? bestEntry.score() : 0;
        long bestDuration = bestEntry != null ? bestEntry.durationMillis() : Long.MAX_VALUE;

        ParkourSession session = new ParkourSession(player, instance, start, bestScore, bestDuration,
                difficulty, theme, trainingMode, soundPreset, leaderboardService);
        session.setCurrentDimension(dimension);

        int chunkX = Math.floorDiv(start.blockX(), 16);
        int chunkZ = Math.floorDiv(start.blockZ(), 16);
        int radius = 2;
        int side = 2 * radius + 1;

        CompletableFuture<?>[] futures = new CompletableFuture[side * side];
        int idx = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                futures[idx++] = instance.loadChunk(chunkX + dx, chunkZ + dz);
            }
        }

        CompletableFuture.allOf(futures).thenRun(() -> {
            if (!player.isOnline()) {
                scheduleInstanceUnregister(instance);
                return;
            }
            session.placeInitialBlocks();
            player.setInstance(instance, start).thenRun(() -> {
                if (!player.isOnline()) {
                    scheduleInstanceUnregister(instance);
                    return;
                }
                sessions.put(player.getUuid(), session);
                session.start();
            });
        });
    }

    public void stop(Player player) {
        ParkourSession old = sessions.remove(player.getUuid());
        if (old == null) return;

        if (old.isScored() && old.getScore() > 0) {
            leaderboardService.submit(old.toRunResult());
            ServerLogger.get().leaderboardSaved(
                    old.toRunResult().playerName(),
                    old.getDifficulty().displayName(),
                    old.getScore());
        } else if (!old.isTrainingMode() && old.getScore() > 0) {
            ServerLogger.get().leaderboardIgnored(old.getPlayerName(), old.getDifficulty().displayName());
        }

        InstanceContainer instance = old.getInstance();
        for (var entity : instance.getEntities()) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }

        scheduleInstanceUnregister(instance);
    }

    private void scheduleInstanceUnregister(InstanceContainer instance) {
        AtomicInteger attempts = new AtomicInteger(0);
        MinecraftServer.getSchedulerManager().submitTask(() -> {
            if (!instance.getPlayers().isEmpty()) {
                if (attempts.incrementAndGet() < 90) {
                    return TaskSchedule.tick(20);
                }
                ServerLogger.get().warn("ParkourService: force-unregistering instance after 90s timeout");
            }
            try {
                instanceManager.unregisterInstance(instance);
            } catch (Exception e) {
                ServerLogger.get().error("ParkourService: failed to unregister instance: " + e.getMessage());
            }
            return TaskSchedule.stop();
        });
    }

    public void changeDimension(Player player, RegistryKey<DimensionType> dimensionType) {
        ParkourSession session = sessions.get(player.getUuid());
        if (session == null || session.isFinished()) return;
        if (session.getCurrentDimension() == dimensionType) return;
        // Guard against rapid concurrent calls before the previous async op completes
        if (session.dimensionChangeInProgress) return;
        session.dimensionChangeInProgress = true;

        InstanceContainer oldInstance = session.getInstance();

        InstanceContainer newInstance = instanceManager.createInstanceContainer(
                dimensionType, ChunkLoader.noop());

        Pos playerPos = player.getPosition();
        int chunkX = Math.floorDiv(playerPos.blockX(), 16);
        int chunkZ = Math.floorDiv(playerPos.blockZ(), 16);
        int radius = 2;
        int side = 2 * radius + 1;

        CompletableFuture<?>[] futures = new CompletableFuture[side * side];
        int idx = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                futures[idx++] = newInstance.loadChunk(chunkX + dx, chunkZ + dz);
            }
        }

        CompletableFuture.allOf(futures).thenRun(() -> {
            session.dimensionChangeInProgress = false;
            if (!player.isOnline()) {
                scheduleInstanceUnregister(newInstance);
                return;
            }
            session.swapInstance(newInstance, dimensionType);
            player.setInstance(newInstance, playerPos).thenRun(() ->
                    player.getInventory().setItemStack(
                            ParkourSettingsMenu.ITEM_SLOT, ParkourSettingsMenu.createItem()));
            scheduleInstanceUnregister(oldInstance);
        });
    }

    public void changeTheme(Player player, ParkourTheme newTheme) {
        ParkourSession session = sessions.get(player.getUuid());
        if (session == null || session.isFinished()) return;
        session.swapTheme(newTheme);
    }

    public void restart(Player player, ParkourDifficulty difficulty, ParkourTheme theme,
                        RegistryKey<DimensionType> dimension, boolean trainingMode,
                        ParkourSoundPreset soundPreset) {
        ParkourSession current = sessions.get(player.getUuid());
        if (current == null) return;
        startWithDimension(player, difficulty, theme, dimension, trainingMode, soundPreset);
    }

    public ParkourSession getSession(Player player) {
        return sessions.get(player.getUuid());
    }

    public boolean isInParkour(Player player) {
        return sessions.containsKey(player.getUuid());
    }

    public boolean hasAnySession() {
        return !sessions.isEmpty();
    }
}
