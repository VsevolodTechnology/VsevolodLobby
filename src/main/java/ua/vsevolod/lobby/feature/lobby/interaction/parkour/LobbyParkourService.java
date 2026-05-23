package ua.vsevolod.lobby.feature.lobby.interaction.parkour;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.potion.PotionEffect;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.audio.music.LobbyMusicManager;
import ua.vsevolod.lobby.feature.lobby.audio.music.LobbyMusicSelectorMenu;
import ua.vsevolod.lobby.feature.lobby.player.join.LobbyJoinInitializer;
import ua.vsevolod.lobby.feature.parkour.ParkourCommand;
import ua.vsevolod.lobby.feature.parkour.ParkourDifficulty;
import ua.vsevolod.lobby.feature.parkour.ParkourListener;
import ua.vsevolod.lobby.feature.parkour.ParkourService;
import ua.vsevolod.lobby.feature.parkour.ParkourSettingsMenu;
import ua.vsevolod.lobby.feature.parkour.ParkourSoundPreset;
import ua.vsevolod.lobby.feature.parkour.ParkourTheme;
import ua.vsevolod.lobby.feature.parkour.leaderboard.ParkourLeaderboardService;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.world.DimensionType;

public final class LobbyParkourService {

    private final Instance lobbyInstance;
    private final LobbyJoinInitializer joinInitializer;
    private final ParkourService parkourService;
    private final ParkourSettingsMenu settingsMenu;

    /** Where each player stood before starting parkour — to put them back, not at spawn. */
    private final java.util.Map<java.util.UUID, Pos> preParkourPos =
            new java.util.concurrent.ConcurrentHashMap<>();

    public LobbyParkourService(
            Instance lobbyInstance,
            LobbyJoinInitializer joinInitializer,
            ParkourLeaderboardService leaderboardService,
            LobbyMusicManager musicManager,
            LobbyMusicSelectorMenu musicSelectorMenu,
            ua.vsevolod.lobby.feature.lobby.player.prefs.PlayerPreferencesService preferencesService
    ) {
        this.lobbyInstance = lobbyInstance;
        this.joinInitializer = joinInitializer;
        this.parkourService = new ParkourService(leaderboardService);
        this.settingsMenu = new ParkourSettingsMenu(parkourService,
                musicManager::toggle, musicSelectorMenu::open, this::returnToLobby, musicManager::isEnabled,
                preferencesService);
    }

    public void register(GlobalEventHandler events) {
        EventNode<Event> parkourNode = EventNode.all("parkour");
        new ParkourListener(parkourService, this::returnToLobby).register(parkourNode);
        settingsMenu.register(parkourNode);
        events.addChild(parkourNode);

        events.addListener(PlayerDisconnectEvent.class, event -> {
            settingsMenu.evict(event.getPlayer().getUuid());
            preParkourPos.remove(event.getPlayer().getUuid());
        });

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

        // Remember where the player stood so parkour can return them here, not to spawn.
        preParkourPos.put(player.getUuid(), player.getPosition());

        ParkourDifficulty difficulty = settingsMenu.getDifficulty(player.getUuid());
        ParkourTheme theme = settingsMenu.getTheme(player.getUuid());
        RegistryKey<DimensionType> dimension = settingsMenu.getDimension(player.getUuid());
        boolean training = settingsMenu.isTrainingMode(player.getUuid());
        ParkourSoundPreset sound = settingsMenu.getSoundPreset(player.getUuid());

        joinInitializer.leave(player, false);
        player.closeInventory();
        parkourService.startWithDimension(player, difficulty, theme, dimension, training, sound);
    }

    public void returnToLobby(Player player) {
        parkourService.stop(player);
        player.removeEffect(PotionEffect.NIGHT_VISION);
        player.closeInventory();

        // Return to the pre-parkour spot if we have it, otherwise the lobby spawn.
        Pos back = preParkourPos.remove(player.getUuid());
        Pos target = back != null ? back : LobbyConfig.Locations.SPAWN_POS_PLAYER;

        if (player.getInstance() == lobbyInstance) {
            joinInitializer.restore(player);
            player.teleport(target);
            return;
        }

        player.setInstance(lobbyInstance, target)
                .thenRun(() -> joinInitializer.restore(player));
    }
}
