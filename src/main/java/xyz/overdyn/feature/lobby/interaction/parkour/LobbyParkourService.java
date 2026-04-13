package xyz.overdyn.feature.lobby.interaction.parkour;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.instance.Instance;
import xyz.overdyn.config.LobbyConfig;
import xyz.overdyn.feature.lobby.player.join.LobbyJoinInitializer;
import xyz.overdyn.feature.parkour.ParkourCommand;
import xyz.overdyn.feature.parkour.ParkourListener;
import xyz.overdyn.feature.parkour.ParkourService;
import xyz.overdyn.feature.parkour.leaderboard.ParkourLeaderboardService;

public final class LobbyParkourService {

    private final Instance lobbyInstance;
    private final LobbyJoinInitializer joinInitializer;
    private final ParkourService parkourService;

    public LobbyParkourService(
            Instance lobbyInstance,
            LobbyJoinInitializer joinInitializer,
            ParkourLeaderboardService leaderboardService
    ) {
        this.lobbyInstance = lobbyInstance;
        this.joinInitializer = joinInitializer;
        this.parkourService = new ParkourService(leaderboardService);
    }

    public void register(GlobalEventHandler events) {
        EventNode<Event> parkourNode = EventNode.all("parkour");
        new ParkourListener(parkourService, this::returnToLobby).register(parkourNode);
        events.addChild(parkourNode);

        MinecraftServer.getCommandManager().register(new ParkourCommand(this::toggle));
    }

    public void startFromNpc(Player player) {
        start(player);
    }

    public void toggle(Player player) {
        if (parkourService.isInParkour(player)) {
            returnToLobby(player);
            return;
        }

        start(player);
    }

    public void start(Player player) {
        if (parkourService.isInParkour(player)) {
            return;
        }

        if (player.getInstance() != lobbyInstance) {
            return;
        }

        joinInitializer.leave(player, false);
        player.closeInventory();
        parkourService.start(player);
    }

    public void returnToLobby(Player player) {
        parkourService.stop(player);
        player.closeInventory();

        player.setInstance(lobbyInstance, LobbyConfig.Locations.SPAWN_POS_PLAYER)
                .thenRun(() -> {
                    joinInitializer.restore(player);
                });
    }
}
