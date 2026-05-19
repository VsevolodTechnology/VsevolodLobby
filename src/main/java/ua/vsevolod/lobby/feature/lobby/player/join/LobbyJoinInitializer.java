package ua.vsevolod.lobby.feature.lobby.player.join;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.bootstrap.module.LobbyModule;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.audio.music.LobbyMusicManager;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.LobbyNpcService;
import ua.vsevolod.lobby.feature.lobby.player.LobbyPlayerProvider;
import ua.vsevolod.lobby.feature.lobby.player.behavior.PlayerBehaviorConfigSection;
import ua.vsevolod.lobby.feature.lobby.player.prefs.PlayerPreferences;
import ua.vsevolod.lobby.feature.lobby.player.prefs.PlayerPreferencesService;
import ua.vsevolod.lobby.feature.lobby.player.protocol.LobbyProtocolWarningService;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.LobbyWelcomeHologramService;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.ParkourLeaderboardHologramService;
import ua.vsevolod.lobby.feature.lobby.ui.sidebar.LobbySidebar;
import ua.vsevolod.lobby.feature.lobby.ui.sidebar.SidebarToggle;
import ua.vsevolod.lobby.util.Text;

public final class LobbyJoinInitializer {

    private final LobbyMusicManager musicManager;
    private final LobbySidebar sidebar;
    private final SidebarToggle sidebarToggle;
    private final LobbyProtocolWarningService protocolWarningService;
    private final LobbyWelcomeHologramService hologramService;
    private final ParkourLeaderboardHologramService parkourLeaderboardHologramService;
    private final LobbyItemService itemService;
    private final LobbyNpcService npcService;
    private final PlayerPreferencesService preferencesService;

    public LobbyJoinInitializer(
            LobbyMusicManager musicManager,
            LobbySidebar sidebar,
            SidebarToggle sidebarToggle,
            LobbyProtocolWarningService protocolWarningService,
            LobbyWelcomeHologramService hologramService,
            ParkourLeaderboardHologramService parkourLeaderboardHologramService,
            LobbyItemService itemService,
            LobbyNpcService npcService,
            PlayerPreferencesService preferencesService
    ) {
        LobbyPlayerProvider.register();
        this.musicManager = musicManager;
        this.sidebar = sidebar;
        this.sidebarToggle = sidebarToggle;
        this.protocolWarningService = protocolWarningService;
        this.hologramService = hologramService;
        this.parkourLeaderboardHologramService = parkourLeaderboardHologramService;
        this.itemService = itemService;
        this.npcService = npcService;
        this.preferencesService = preferencesService;
    }

    public void initialize(Player player) {
        enterLobby(player, true);
    }

    public void restore(Player player) {
        enterLobby(player, false);
    }

    public void leave(Player player, boolean first) {
        LobbyModule.hologramManager.hideFrom(player);
        sidebar.hide(player);
        hologramService.hideWelcome(player, first);
        parkourLeaderboardHologramService.hideFrom(player);
        npcService.hideFrom(player);
    }

    private void enterLobby(Player player, boolean sendWelcomeMessage) {
        if (sendWelcomeMessage) {
            player.sendMessage(LobbyConfig.Messages.welcome(player.getUsername()));
//            requestClientProtocol(player); TODO
            protocolWarningService.showIfNeeded(player);
        }

        LobbyModule.hologramManager.showTo(player);
        setupProfile(player);
        setupState(player);

        if (sendWelcomeMessage && PlayerBehaviorConfigSection.INSTANCE.current().restoreLastPosition()) {
            PlayerPreferences prefs = preferencesService.get(player.getUuid());
            if (prefs.positionSaveEnabled()) {
                Pos saved = prefs.lastPosition();
                if (saved != null) {
                    player.teleport(saved);
                }
            }
        }

        itemService.giveJoinItems(player);
        if (!sidebarToggle.isHidden(player)) {
            sidebar.show(player);
        }
        hologramService.showWelcome(player, sendWelcomeMessage);
        parkourLeaderboardHologramService.showTo(player);
        npcService.showTo(player);
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
