package xyz.overdyn.feature.lobby.bootstrap;

import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerStartFlyingEvent;
import net.minestom.server.instance.Instance;
import xyz.overdyn.feature.lobby.audio.music.LobbyMusicManager;
import xyz.overdyn.feature.lobby.audio.music.LobbyMusicPlayerListener;
import xyz.overdyn.feature.lobby.interaction.npc.LobbyNpc;
import xyz.overdyn.feature.lobby.interaction.npc.LobbyNpcActionBinding;
import xyz.overdyn.feature.lobby.interaction.npc.LobbyNpcInteractionListener;
import xyz.overdyn.feature.lobby.interaction.npc.LobbyNpcService;
import xyz.overdyn.feature.lobby.interaction.parkour.LobbyParkourService;
import xyz.overdyn.feature.lobby.interaction.qr.LobbyQrListener;
import xyz.overdyn.feature.lobby.player.chat.LobbyPlayerChatListener;
import xyz.overdyn.feature.lobby.player.configuration.LobbyPlayerConfigurationListener;
import xyz.overdyn.feature.lobby.player.gamemode.GamemodeRequestListener;
import xyz.overdyn.feature.lobby.player.inventory.PlayerInventoryLockListener;
import xyz.overdyn.feature.lobby.player.join.LobbyItemService;
import xyz.overdyn.feature.lobby.player.join.LobbyJoinInitializer;
import xyz.overdyn.feature.lobby.player.join.LobbyJoinListener;
import xyz.overdyn.feature.lobby.player.login.LobbyPlayerLoginListener;
import xyz.overdyn.feature.lobby.player.protocol.LobbyProtocolWarningService;
import xyz.overdyn.feature.lobby.player.visibility.PlayerHider;
import xyz.overdyn.feature.lobby.player.workaround.MinestomTagsWorkaround;
import xyz.overdyn.feature.lobby.ui.hologram.LobbyWelcomeHologramService;
import xyz.overdyn.feature.lobby.ui.hologram.ParkourLeaderboardHologramService;
import xyz.overdyn.feature.lobby.ui.menu.LobbyModeMenuItemListener;
import xyz.overdyn.feature.lobby.ui.menu.LobbyModeSelectorMenu;
import xyz.overdyn.feature.lobby.ui.sidebar.LobbySidebar;
import xyz.overdyn.feature.lobby.world.movement.LaunchPadManager;
import xyz.overdyn.feature.lobby.world.protection.LobbyBlockProtectionListener;
import xyz.overdyn.feature.lobby.world.protection.VoidProtectionListener;
import xyz.overdyn.feature.parkour.leaderboard.ParkourLeaderboardService;
import xyz.overdyn.feature.parkour.leaderboard.ParkourLeaderboardStoreFactory;

import java.util.List;

public final class LobbyEventRegistrar {

    private final LobbyMusicManager musicManager;
    private final LaunchPadManager launchPadManager;
    private LobbyProtocolWarningService protocolWarningService;

    public LobbyEventRegistrar(
            GlobalEventHandler events,
            Instance lobbyInstance,
            LobbyNpc modeSelectorNpc,
            LobbyNpc parkourNpc,
            LobbySidebar sidebar,
            LobbyModeSelectorMenu lobbyMenu
    ) {
        this.musicManager = new LobbyMusicManager();
        this.launchPadManager = new LaunchPadManager();

        registerLobbyListeners(events, lobbyInstance, modeSelectorNpc, parkourNpc, sidebar, lobbyMenu);
        registerMovement(events);
        registerVisibility(events);
//        registerProtocolBridge();
    }

    private void registerLobbyListeners(
            GlobalEventHandler events,
            Instance lobbyInstance,
            LobbyNpc modeSelectorNpc,
            LobbyNpc parkourNpc,
            LobbySidebar sidebar,
            LobbyModeSelectorMenu lobbyMenu
    ) {
        this.protocolWarningService = new LobbyProtocolWarningService();
        LobbyWelcomeHologramService hologramService = new LobbyWelcomeHologramService();
        ParkourLeaderboardService parkourLeaderboardService = new ParkourLeaderboardService(
                ParkourLeaderboardStoreFactory.create()
        );
        parkourLeaderboardService.startAutoRefresh();

        ParkourLeaderboardHologramService parkourLeaderboardHologramService =
                new ParkourLeaderboardHologramService(parkourLeaderboardService);

        LobbyItemService itemService = new LobbyItemService();
        LobbyNpcService npcService = new LobbyNpcService(List.of(modeSelectorNpc, parkourNpc));
        LobbyJoinInitializer joinInitializer = new LobbyJoinInitializer(
                musicManager,
                sidebar,
                protocolWarningService,
                hologramService,
                parkourLeaderboardHologramService,
                itemService,
                npcService
        );
        LobbyParkourService parkourService =
                new LobbyParkourService(lobbyInstance, joinInitializer, parkourLeaderboardService);

        List<LobbyEventRegistration> listeners = List.of(
                new MinestomTagsWorkaround(),
                new LobbyPlayerLoginListener(),
                new LobbyPlayerConfigurationListener(lobbyInstance),
                new LobbyPlayerChatListener(),
                new GamemodeRequestListener(),
                new VoidProtectionListener(),
                new LobbyBlockProtectionListener(),
                new LobbyJoinListener(joinInitializer),
                new LobbyNpcInteractionListener(List.of(
                        new LobbyNpcActionBinding(modeSelectorNpc, player -> player.openInventory(lobbyMenu.getMenu())),
                        new LobbyNpcActionBinding(parkourNpc, parkourService::startFromNpc)
                )),
                new LobbyModeMenuItemListener(lobbyMenu),
                new LobbyQrListener(),
                new LobbyMusicPlayerListener(musicManager),
                new PlayerInventoryLockListener()
        );

        listeners.forEach(listener -> listener.register(events));

        events.addListener(PlayerDisconnectEvent.class, event -> joinInitializer.leave(event.getPlayer(), true));
        parkourService.register(events);
    }

    private void registerMovement(GlobalEventHandler events) {
        events.addListener(PlayerStartFlyingEvent.class, launchPadManager::onStartFlying);
        events.addListener(PlayerMoveEvent.class, launchPadManager::onMove);
        events.addListener(PlayerSpawnEvent.class, launchPadManager::onJoin);
        events.addListener(PlayerSpawnEvent.class, musicManager::handleJoin);
        events.addListener(PlayerDisconnectEvent.class, musicManager::handleDisconnect);
        events.addListener(PlayerDisconnectEvent.class, launchPadManager::onQuit);
    }

    private void registerVisibility(GlobalEventHandler events) {
        EventNode<Event> lobbyNode = EventNode.all("lobby");
        PlayerHider playerHider = new PlayerHider();
        playerHider.register(lobbyNode);
        events.addChild(lobbyNode);
    }

}
