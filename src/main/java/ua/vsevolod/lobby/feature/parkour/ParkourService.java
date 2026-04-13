package ua.vsevolod.lobby.feature.parkour;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
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

        InstanceContainer instance = instanceManager.createInstanceContainer(DimensionType.OVERWORLD);
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
        if (old != null) {
            if (old.isFinished()) {
                var result = old.toRunResult();
                if (result.score() > 0) {
                    leaderboardService.submit(result);
                }
            }

            for (var entity : old.getInstance().getEntities()) {
                if (!(entity instanceof Player)) {
                    entity.remove();
                }
            }
        }
    }

    public ParkourSession getSession(Player player) {
        return sessions.get(player.getUuid());
    }

    public boolean isInParkour(Player player) {
        return sessions.containsKey(player.getUuid());
    }
}
