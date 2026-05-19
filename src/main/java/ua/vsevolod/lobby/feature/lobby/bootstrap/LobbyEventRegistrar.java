package ua.vsevolod.lobby.feature.lobby.bootstrap;

import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerPreLoginEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerStartFlyingEvent;
import net.minestom.server.instance.Instance;
import ua.vsevolod.lobby.bootstrap.module.InstanceModule;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.audio.music.LobbyMusicManager;
import ua.vsevolod.lobby.feature.lobby.audio.music.LobbyMusicSelectorMenu;
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
import ua.vsevolod.lobby.feature.lobby.player.prefs.FilePlayerDataStore;
import ua.vsevolod.lobby.feature.lobby.player.prefs.MongoPlayerDataStore;
import ua.vsevolod.lobby.feature.lobby.player.prefs.PlayerDataStore;
import ua.vsevolod.lobby.feature.lobby.player.prefs.PlayerPreferencesService;
import ua.vsevolod.lobby.feature.lobby.player.protocol.LobbyProtocolWarningService;
import ua.vsevolod.lobby.feature.lobby.player.visibility.PlayerHider;
import ua.vsevolod.lobby.feature.lobby.player.workaround.MinestomTagsWorkaround;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.LobbyWelcomeHologramService;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.ParkourLeaderboardHologramService;
import ua.vsevolod.lobby.feature.lobby.ui.menu.LobbyModeSelectorMenu;
import ua.vsevolod.lobby.feature.lobby.ui.menu.LobbySettingsMenu;
import ua.vsevolod.lobby.feature.lobby.ui.sidebar.LobbySidebar;
import ua.vsevolod.lobby.feature.lobby.ui.sidebar.SidebarToggle;
import ua.vsevolod.lobby.feature.lobby.world.movement.LaunchPadManager;
import ua.vsevolod.lobby.feature.lobby.world.protection.LobbyBlockProtectionListener;
import ua.vsevolod.lobby.feature.lobby.world.protection.VoidProtectionListener;
import ua.vsevolod.lobby.feature.parkour.leaderboard.ParkourLeaderboardService;
import ua.vsevolod.lobby.feature.parkour.leaderboard.ParkourLeaderboardStoreFactory;

import java.util.List;

public final class LobbyEventRegistrar {

    private final LobbyMusicManager musicManager;
    private final LaunchPadManager launchPadManager;
    private final PlayerPreferencesService preferencesService;
    private final SidebarToggle sidebarToggle;
    private final PlayerHider playerHider;
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
        this.musicManager.startGlobalAmbientSuppressor();
        this.launchPadManager = new LaunchPadManager();
        this.preferencesService = createPreferencesService();
        this.sidebarToggle = new SidebarToggle(sidebar);
        this.playerHider = new PlayerHider();
        sidebarToggle.setPreferencesService(preferencesService);
        playerHider.setPreferencesService(preferencesService);

        // Preload preferences as early as possible — before any PlayerSpawnEvent handlers run.
        events.addListener(AsyncPlayerPreLoginEvent.class, event ->
                preferencesService.preload(event.getGameProfile().uuid()));

        // Apply preferences before LobbyJoinListener so giveJoinItems uses correct music/sidebar state.
        // Only for lobby joins — NOT parkour dimension changes which also fire PlayerSpawnEvent.
        events.addListener(PlayerSpawnEvent.class, event -> {
            if (event.getPlayer().getInstance() != InstanceModule.lobby) return;
            var prefs = preferencesService.get(event.getPlayer().getUuid());
            musicManager.applyMusicPreference(event.getPlayer().getUuid(), prefs.musicEnabled());
            sidebarToggle.applyPreference(event.getPlayer().getUuid(), prefs.sidebarHidden());
        });

        registerLobbyListeners(events, lobbyInstance, npcManager, npcActionExecutor, sidebar, lobbyMenu);
        registerMovement(events);
        registerVisibility(events);
//        registerProtocolBridge();
    }

    private static PlayerPreferencesService createPreferencesService() {
        PlayerDataStore store;
        try {
            MongoPlayerDataStore mongo = new MongoPlayerDataStore(
                    LobbyConfig.Parkour.Mongo.URI,
                    LobbyConfig.Parkour.Mongo.DATABASE
            );
            // Quick connectivity test — will timeout in 3 seconds max if MongoDB is down.
            mongo.load(new java.util.UUID(0, 0));
            Runtime.getRuntime().addShutdownHook(new Thread(mongo::close, "player-prefs-mongo-close"));
            store = mongo;
            System.out.println("[PlayerPrefs] Using MongoDB storage at " + LobbyConfig.Parkour.Mongo.URI);
        } catch (Exception e) {
            System.out.println("[PlayerPrefs] MongoDB unavailable (" + e.getMessage()
                    + "), using file storage (storage/player_data/).");
            store = new FilePlayerDataStore();
        }
        return new PlayerPreferencesService(store);
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
                sidebarToggle,
                protocolWarningService,
                hologramService,
                parkourLeaderboardHologramService,
                itemService,
                npcService,
                preferencesService
        );
        LobbyMusicSelectorMenu musicSelectorMenu = new LobbyMusicSelectorMenu(musicManager);
        LobbyParkourService parkourService =
                new LobbyParkourService(lobbyInstance, joinInitializer, parkourLeaderboardService, musicManager, musicSelectorMenu);

        // Now that parkourService exists, wire both the legacy and new-style parkour handlers.
        npcActionExecutor.registerSimple("parkour-start", parkourService::startFromNpc);
        npcActionExecutor.registerPrefix("parkour", (player, ignored) -> parkourService.startFromNpc(player));

        LobbySettingsMenu settingsMenu = new LobbySettingsMenu(
                preferencesService, musicManager, sidebarToggle, musicSelectorMenu);

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
                musicSelectorMenu,
                new PlayerInventoryLockListener()
        );

        listeners.forEach(listener -> listener.register(events));

        EventNode<Event> settingsNode = EventNode.all("settings");
        settingsMenu.register(settingsNode);
        events.addChild(settingsNode);

        events.addListener(PlayerDisconnectEvent.class, event -> {
            var player = event.getPlayer();
            if (player.getInstance() == InstanceModule.lobby) {
                var prefs = preferencesService.get(player.getUuid());
                if (prefs.positionSaveEnabled()) {
                    preferencesService.savePosition(player.getUuid(), player.getPosition());
                }
            }
            joinInitializer.leave(player, true);

        });
        parkourService.register(events);
    }

    private void registerMovement(GlobalEventHandler events) {
        musicManager.setPreferencesService(preferencesService);
        events.addListener(PlayerStartFlyingEvent.class, launchPadManager::onStartFlying);
        events.addListener(PlayerMoveEvent.class, launchPadManager::onMove);
        events.addListener(PlayerSpawnEvent.class, event -> {
            if (event.getPlayer().getInstance() != InstanceModule.lobby) return;
            launchPadManager.onJoin(event);
        });
        events.addListener(PlayerSpawnEvent.class, event -> {
            if (event.getPlayer().getInstance() != InstanceModule.lobby) return;
            musicManager.handleJoin(event);
        });
        events.addListener(PlayerDisconnectEvent.class, musicManager::handleDisconnect);
        events.addListener(PlayerDisconnectEvent.class, launchPadManager::onQuit);
        events.addListener(PlayerDisconnectEvent.class, event ->
                preferencesService.evict(event.getPlayer().getUuid()));
    }

    private void registerVisibility(GlobalEventHandler events) {
        EventNode<Event> lobbyNode = EventNode.all("lobby");
        lobbyNode.addListener(PlayerSpawnEvent.class, event -> {
            if (event.getPlayer().getInstance() != InstanceModule.lobby) return;
            var prefs = preferencesService.get(event.getPlayer().getUuid());
            playerHider.applyVisibilityPreference(event.getPlayer().getUuid(), prefs.playersHidden());
        });
        playerHider.register(lobbyNode);
        sidebarToggle.register(lobbyNode);
        events.addChild(lobbyNode);
    }

}
