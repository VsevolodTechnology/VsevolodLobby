package ua.vsevolod.lobby.feature.lobby.bootstrap;

import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerStartFlyingEvent;
import net.minestom.server.instance.Instance;
import ua.vsevolod.lobby.feature.lobby.audio.music.LobbyMusicManager;
import ua.vsevolod.lobby.feature.lobby.audio.music.LobbyMusicPlayerListener;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.LobbyNpcInteractionListener;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.LobbyNpcService;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.NpcActionExecutor;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.NpcManager;
import ua.vsevolod.lobby.feature.lobby.interaction.parkour.LobbyParkourService;
import ua.vsevolod.lobby.feature.lobby.interaction.qr.LobbyQrListener;
import ua.vsevolod.lobby.feature.lobby.player.chat.LobbyPlayerChatListener;
import ua.vsevolod.lobby.feature.lobby.player.configuration.LobbyPlayerConfigurationListener;
import ua.vsevolod.lobby.feature.lobby.player.gamemode.GamemodeRequestListener;
import ua.vsevolod.lobby.feature.lobby.player.inventory.PlayerInventoryLockListener;
import ua.vsevolod.lobby.feature.lobby.player.join.LobbyItemService;
import ua.vsevolod.lobby.feature.lobby.player.join.LobbyJoinInitializer;
import ua.vsevolod.lobby.feature.lobby.player.join.LobbyJoinListener;
import ua.vsevolod.lobby.feature.lobby.player.join.items.JoinItemUseListener;
import ua.vsevolod.lobby.feature.lobby.player.login.LobbyPlayerLoginListener;
import ua.vsevolod.lobby.feature.lobby.player.protocol.LobbyProtocolWarningService;
import ua.vsevolod.lobby.feature.lobby.player.visibility.PlayerHider;
import ua.vsevolod.lobby.feature.lobby.player.workaround.MinestomTagsWorkaround;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.LobbyWelcomeHologramService;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.ParkourLeaderboardHologramService;
import ua.vsevolod.lobby.feature.lobby.ui.menu.LobbyModeSelectorMenu; // kept for the lobbyMenu param even though it's no longer wired directly here (compass click now routes through NpcActionExecutor's open-menu handler registered in LobbyModule).
import ua.vsevolod.lobby.feature.lobby.ui.sidebar.LobbySidebar;
import ua.vsevolod.lobby.feature.lobby.world.movement.LaunchPadManager;
import ua.vsevolod.lobby.feature.lobby.world.protection.LobbyBlockProtectionListener;
import ua.vsevolod.lobby.feature.lobby.world.protection.VoidProtectionListener;
import ua.vsevolod.lobby.feature.parkour.leaderboard.ParkourLeaderboardService;
import ua.vsevolod.lobby.feature.parkour.leaderboard.ParkourLeaderboardStoreFactory;

import java.util.List;

public final class LobbyEventRegistrar {

    private final LobbyMusicManager musicManager;
    private final LaunchPadManager launchPadManager;
    private LobbyProtocolWarningService protocolWarningService;

    public LobbyEventRegistrar(
            GlobalEventHandler events,
            Instance lobbyInstance,
            NpcManager npcManager,
            NpcActionExecutor npcActionExecutor,
            LobbySidebar sidebar,
            LobbyModeSelectorMenu lobbyMenu
    ) {
        this.musicManager = new LobbyMusicManager();
        this.launchPadManager = new LaunchPadManager();

        registerLobbyListeners(events, lobbyInstance, npcManager, npcActionExecutor, sidebar, lobbyMenu);
        registerMovement(events);
        registerVisibility(events);
//        registerProtocolBridge();
    }

    private void registerLobbyListeners(
            GlobalEventHandler events,
            Instance lobbyInstance,
            NpcManager npcManager,
            NpcActionExecutor npcActionExecutor,
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
        LobbyNpcService npcService = new LobbyNpcService(npcManager);
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

        // Now that parkourService exists, register the parkour-start action handler.
        npcActionExecutor.registerSimple("parkour-start", parkourService::startFromNpc);

        List<LobbyEventRegistration> listeners = List.of(
                new MinestomTagsWorkaround(),
                new LobbyPlayerLoginListener(),
                new LobbyPlayerConfigurationListener(lobbyInstance),
                new LobbyPlayerChatListener(),
                new GamemodeRequestListener(),
                new VoidProtectionListener(),
                new LobbyBlockProtectionListener(),
                new LobbyJoinListener(joinInitializer),
                new LobbyNpcInteractionListener(npcManager, npcActionExecutor),
                new JoinItemUseListener(npcActionExecutor),
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
