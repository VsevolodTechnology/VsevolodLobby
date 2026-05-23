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
import ua.vsevolod.lobby.feature.lobby.player.join.cutscene.CutsceneService;
import ua.vsevolod.lobby.feature.lobby.player.join.welcome.WelcomeAnimationService;
import ua.vsevolod.lobby.feature.lobby.player.login.LobbyPlayerLoginListener;
import ua.vsevolod.lobby.feature.lobby.player.prefs.FilePlayerDataStore;
import ua.vsevolod.lobby.feature.lobby.player.prefs.MongoPlayerDataStore;
import ua.vsevolod.lobby.feature.lobby.player.prefs.PlayerDataStore;
import ua.vsevolod.lobby.feature.lobby.player.prefs.PlayerPreferencesService;
import ua.vsevolod.lobby.feature.lobby.player.protocol.LobbyProtocolWarningService;
import ua.vsevolod.lobby.feature.lobby.player.visibility.PlayerHider;
import ua.vsevolod.lobby.feature.lobby.player.workaround.MinestomTagsWorkaround;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.ParkourLeaderboardHologramService;
import ua.vsevolod.lobby.feature.lobby.ui.menu.LobbyModeSelectorMenu;
import ua.vsevolod.lobby.feature.lobby.ui.menu.LobbySettingsMenu;
import ua.vsevolod.lobby.feature.lobby.ui.sidebar.LobbySidebar;
import ua.vsevolod.lobby.feature.lobby.ui.sidebar.SidebarToggle;
import ua.vsevolod.lobby.feature.lobby.world.movement.LaunchPadManager;
import ua.vsevolod.lobby.feature.lobby.world.protection.LobbyBlockProtectionListener;
import ua.vsevolod.lobby.feature.lobby.world.protection.VoidProtectionListener;
import ua.vsevolod.lobby.config.StorageConfig;
import ua.vsevolod.lobby.feature.parkour.leaderboard.ParkourLeaderboardService;
import ua.vsevolod.lobby.feature.parkour.leaderboard.ParkourLeaderboardStoreFactory;
import ua.vsevolod.lobby.util.ServerLogger;

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
        ua.vsevolod.lobby.bootstrap.LobbyShutdown.register(preferencesService::flushAll);
        this.sidebarToggle = new SidebarToggle(sidebar);
        this.playerHider = new PlayerHider();
        sidebarToggle.setPreferencesService(preferencesService);
        playerHider.setPreferencesService(preferencesService);

        // Preload preferences as early as possible — before any PlayerSpawnEvent handlers run.
        events.addListener(AsyncPlayerPreLoginEvent.class, event -> {
            var profile = event.getGameProfile();
            preferencesService.preload(profile.uuid());
            ServerLogger.get().playerConnect(profile.name(), extractProtocol(event));
        });

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
        StorageConfig cfg = StorageConfig.get();
        PlayerDataStore store;
        if (cfg.playerPrefs == StorageConfig.Mode.MONGODB) {
            try {
                MongoPlayerDataStore mongo = new MongoPlayerDataStore(
                        cfg.mongoUri, cfg.mongoDatabase);
                mongo.load(new java.util.UUID(0, 0));
                Runtime.getRuntime().addShutdownHook(new Thread(mongo::close, "player-prefs-mongo-close"));
                store = mongo;
                ServerLogger.get().info("Player preferences storage: MongoDB (" + cfg.mongoUri + ")");
            } catch (Exception e) {
                ServerLogger.get().warn("Player preferences MongoDB unavailable, using file storage");
                store = new FilePlayerDataStore();
            }
        } else {
            store = new FilePlayerDataStore();
            ServerLogger.get().detail("Player preferences storage: file");
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
        this.protocolWarningService = new LobbyProtocolWarningService(preferencesService);
        ParkourLeaderboardService parkourLeaderboardService = new ParkourLeaderboardService(
                ParkourLeaderboardStoreFactory.create()
        );
        parkourLeaderboardService.startAutoRefresh();

        ParkourLeaderboardHologramService parkourLeaderboardHologramService =
                new ParkourLeaderboardHologramService(parkourLeaderboardService);
        parkourLeaderboardHologramService.register(events);

        LobbyItemService itemService = new LobbyItemService();
        LobbyNpcService npcService = new LobbyNpcService(npcManager);
        WelcomeAnimationService welcomeAnimationService = new WelcomeAnimationService();
        CutsceneService cutsceneService = new CutsceneService(musicManager);
        cutsceneService.register(events);
        new ua.vsevolod.lobby.command.admin.CutsceneCommand(cutsceneService);
        LobbyJoinInitializer joinInitializer = new LobbyJoinInitializer(
                musicManager,
                sidebar,
                sidebarToggle,
                protocolWarningService,
                parkourLeaderboardHologramService,
                itemService,
                npcService,
                preferencesService,
                welcomeAnimationService,
                cutsceneService
        );
        LobbyMusicSelectorMenu musicSelectorMenu = new LobbyMusicSelectorMenu(musicManager);
        LobbyParkourService parkourService =
                new LobbyParkourService(lobbyInstance, joinInitializer, parkourLeaderboardService, musicManager, musicSelectorMenu);

        // Now that parkourService exists, wire both the legacy and new-style parkour handlers.
        npcActionExecutor.registerSimple("parkour-start", parkourService::startFromNpc);
        npcActionExecutor.registerPrefix("parkour", (player, ignored) -> parkourService.startFromNpc(player));

        LobbySettingsMenu settingsMenu = new LobbySettingsMenu(
                preferencesService, musicManager, sidebarToggle, musicSelectorMenu, protocolWarningService);

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
            ServerLogger.get().playerDisconnect(player.getUsername());
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

    /**
     * Reads the client protocol from a Velocity-forwarded GameProfile property if present;
     * otherwise falls back to the value pulled off the raw {@link AsyncPlayerPreLoginEvent}
     * connection so direct (non-Velocity) joins still log a usable number.
     */
    private static int extractProtocol(AsyncPlayerPreLoginEvent event) {
        for (var property : event.getGameProfile().properties()) {
            if (!property.name().equals(LobbyConfig.Settings.IDENTIFIER_VELOCITY_MESSAGE)) continue;
            try {
                int v = Integer.parseInt(property.value());
                if (v > 0) return v;
            } catch (NumberFormatException ignored) {}
        }
        var conn = event.getConnection();
        if (conn != null) {
            int handshake = conn.getProtocolVersion();
            if (handshake > 0) return handshake;
        }
        return 0;
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
