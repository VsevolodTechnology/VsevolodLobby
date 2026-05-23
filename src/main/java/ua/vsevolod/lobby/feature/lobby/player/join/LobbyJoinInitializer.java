package ua.vsevolod.lobby.feature.lobby.player.join;

import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.bootstrap.module.LobbyModule;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.config.SocialsConfig;
import ua.vsevolod.lobby.feature.lobby.audio.music.LobbyMusicManager;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.LobbyNpcService;
import ua.vsevolod.lobby.feature.lobby.player.LobbyPlayerProvider;
import ua.vsevolod.lobby.feature.lobby.player.behavior.PlayerBehaviorConfig;
import ua.vsevolod.lobby.feature.lobby.player.join.cutscene.CutsceneConfig;
import ua.vsevolod.lobby.feature.lobby.player.join.cutscene.CutsceneService;
import ua.vsevolod.lobby.feature.lobby.player.join.welcome.WelcomeAnimationService;
import ua.vsevolod.lobby.feature.lobby.player.join.welcome.WelcomeConfig;
import ua.vsevolod.lobby.feature.lobby.player.prefs.PlayerPreferences;
import ua.vsevolod.lobby.feature.lobby.player.prefs.PlayerPreferencesService;
import ua.vsevolod.lobby.feature.lobby.player.protocol.LobbyProtocolWarningService;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.ParkourLeaderboardHologramService;
import ua.vsevolod.lobby.feature.lobby.ui.sidebar.LobbySidebar;
import ua.vsevolod.lobby.feature.lobby.ui.sidebar.SidebarToggle;
import ua.vsevolod.lobby.util.Text;

public final class LobbyJoinInitializer {

    private final LobbyMusicManager musicManager;
    private final LobbySidebar sidebar;
    private final SidebarToggle sidebarToggle;
    private final LobbyProtocolWarningService protocolWarningService;
    private final ParkourLeaderboardHologramService parkourLeaderboardHologramService;
    private final LobbyItemService itemService;
    private final LobbyNpcService npcService;
    private final PlayerPreferencesService preferencesService;
    private final WelcomeAnimationService welcomeAnimationService;
    private final CutsceneService cutsceneService;

    public LobbyJoinInitializer(
            LobbyMusicManager musicManager,
            LobbySidebar sidebar,
            SidebarToggle sidebarToggle,
            LobbyProtocolWarningService protocolWarningService,
            ParkourLeaderboardHologramService parkourLeaderboardHologramService,
            LobbyItemService itemService,
            LobbyNpcService npcService,
            PlayerPreferencesService preferencesService,
            WelcomeAnimationService welcomeAnimationService,
            CutsceneService cutsceneService
    ) {
        LobbyPlayerProvider.register();
        this.musicManager = musicManager;
        this.sidebar = sidebar;
        this.sidebarToggle = sidebarToggle;
        this.protocolWarningService = protocolWarningService;
        this.parkourLeaderboardHologramService = parkourLeaderboardHologramService;
        this.itemService = itemService;
        this.npcService = npcService;
        this.preferencesService = preferencesService;
        this.welcomeAnimationService = welcomeAnimationService;
        this.cutsceneService = cutsceneService;
    }

    public void initialize(Player player) {
        enterLobby(player, true);
    }

    public void restore(Player player) {
        enterLobby(player, false);
    }

    public void leave(Player player, boolean first) {
        welcomeAnimationService.cancel(player.getUuid());
        cutsceneService.cancel(player);
        LobbyModule.hologramManager.hideFrom(player);
        sidebar.hide(player);
        parkourLeaderboardHologramService.hideFrom(player);
        npcService.hideFrom(player);
    }

    private void enterLobby(Player player, boolean sendWelcomeMessage) {
        boolean willPlayCutscene = false;

        if (sendWelcomeMessage) {
            PlayerPreferences prefsBefore = preferencesService.get(player.getUuid());
            boolean firstJoin = prefsBefore.firstSeenEpoch() == 0L;
            long firstSeenEpoch = preferencesService.markFirstSeenIfAbsent(player.getUuid());

            sendWelcomeChat(player);
            protocolWarningService.showIfNeeded(player);
            welcomeAnimationService.play(player, firstJoin, firstSeenEpoch);

            CutsceneConfig cutsceneCfg = CutsceneConfig.get();
            willPlayCutscene = cutsceneCfg.enabled
                    && !cutsceneCfg.waypoints.isEmpty()
                    && (firstJoin || !cutsceneCfg.firstJoinOnly);
        }

        LobbyModule.hologramManager.showTo(player);
        setupProfile(player);
        setupState(player);

        if (sendWelcomeMessage && PlayerBehaviorConfig.get().restoreLastPosition) {
            PlayerPreferences prefs = preferencesService.get(player.getUuid());
            if (prefs.positionSaveEnabled()) {
                Pos saved = prefs.lastPosition();
                if (saved != null) {
                    player.teleport(saved);
                }
            }
        }

        if (!sidebarToggle.isHidden(player)) {
            sidebar.show(player);
        }
        parkourLeaderboardHologramService.showTo(player);
        npcService.showTo(player);

        if (willPlayCutscene) {
            // Defer hotbar items until the camera releases — keeps the cinematic clean of UI.
            cutsceneService.play(player, () -> itemService.giveJoinItems(player));
        } else {
            itemService.giveJoinItems(player);
        }
    }

    private void sendWelcomeChat(Player player) {
        WelcomeConfig cfg = WelcomeConfig.get();
        if (cfg.chatLines.isEmpty()) return;

        String joined = ua.vsevolod.lobby.util.Placeholders.apply(
                String.join("\n", cfg.chatLines).replace("{player}", player.getUsername()));
        player.sendMessage(Text.raw(joined));
    }

    private void setupProfile(Player player) {
        player.setDisplayName(Text.raw(LobbyConfig.Project.WHITE_COLOR_ORIGINAL + player.getUsername()));

        if (LobbyConfig.Settings.BYPASS_USERS.contains(player.getUsername())) {
            player.setPermissionLevel(4);
        }
    }

    private void setupState(Player player) {
        // Order matters: setGameMode resets allowFlying to whatever the new mode permits
        // (false for ADVENTURE/SURVIVAL — see Player#setGameMode in Minestom). Without
        // re-asserting it here the LaunchPadManager double-jump silently stops working on
        // parkour return, because LaunchPadManager's PlayerSpawnEvent listener fires BEFORE
        // the setInstance().thenRun() callback that lands here.
        player.setGameMode(LobbyConfig.Settings.DEFAULT_GAME_MODE);
        player.setAllowFlying(true);
        player.setFlying(false);
    }
}
