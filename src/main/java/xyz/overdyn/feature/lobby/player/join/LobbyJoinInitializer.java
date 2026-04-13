package xyz.overdyn.feature.lobby.player.join;

import net.minestom.server.entity.Player;
import xyz.overdyn.bootstrap.module.LobbyModule;
import xyz.overdyn.config.LobbyConfig;
import xyz.overdyn.feature.lobby.audio.music.LobbyMusicManager;
import xyz.overdyn.feature.lobby.interaction.npc.LobbyNpcService;
import xyz.overdyn.feature.lobby.player.LobbyPlayerProvider;
import xyz.overdyn.feature.lobby.player.protocol.LobbyProtocolWarningService;
import xyz.overdyn.feature.lobby.ui.hologram.LobbyWelcomeHologramService;
import xyz.overdyn.feature.lobby.ui.hologram.ParkourLeaderboardHologramService;
import xyz.overdyn.feature.lobby.ui.sidebar.LobbySidebar;
import xyz.overdyn.util.Text;

public final class LobbyJoinInitializer {

    private final LobbyMusicManager musicManager;
    private final LobbySidebar sidebar;
    private final LobbyProtocolWarningService protocolWarningService;
    private final LobbyWelcomeHologramService hologramService;
    private final ParkourLeaderboardHologramService parkourLeaderboardHologramService;
    private final LobbyItemService itemService;
    private final LobbyNpcService npcService;

    public LobbyJoinInitializer(
            LobbyMusicManager musicManager,
            LobbySidebar sidebar,
            LobbyProtocolWarningService protocolWarningService,
            LobbyWelcomeHologramService hologramService,
            ParkourLeaderboardHologramService parkourLeaderboardHologramService,
            LobbyItemService itemService,
            LobbyNpcService npcService
    ) {
        LobbyPlayerProvider.register();
        this.musicManager = musicManager;
        this.sidebar = sidebar;
        this.protocolWarningService = protocolWarningService;
        this.hologramService = hologramService;
        this.parkourLeaderboardHologramService = parkourLeaderboardHologramService;
        this.itemService = itemService;
        this.npcService = npcService;
    }

    public void initialize(Player player) {
        enterLobby(player, true);
    }

    public void restore(Player player) {
        enterLobby(player, false);
    }

    public void leave(Player player, boolean first) {
        LobbyModule.holo.hide(player);
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

        LobbyModule.holo.show(player);
        setupProfile(player);
        setupState(player);
        itemService.giveJoinItems(player, musicManager.isEnabled(player));
        sidebar.show(player);
        hologramService.showWelcome(player, sendWelcomeMessage);
        parkourLeaderboardHologramService.showTo(player);
        npcService.showTo(player);
    }

    private void setupProfile(Player player) {
        player.setDisplayName(Text.c(LobbyConfig.Project.WHITE_COLOR_ORIGINAL + player.getUsername()));

        if (LobbyConfig.Settings.BYPASS_USERS.contains(player.getUsername())) {
            player.setPermissionLevel(4);
        }
    }

    private void setupState(Player player) {
        player.setGameMode(LobbyConfig.Settings.DEFAULT_GAME_MODE);
    }
}
