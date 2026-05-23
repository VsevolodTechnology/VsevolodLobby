package ua.vsevolod.lobby.feature.lobby.audio.music;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.key.KeyPattern;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;
import net.minestom.server.timer.ExecutionType;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import ua.vsevolod.lobby.feature.lobby.player.join.items.JoinItemsConfig;
import ua.vsevolod.lobby.feature.lobby.player.join.items.ToggleItemDefinition;
import ua.vsevolod.lobby.feature.lobby.player.prefs.PlayerPreferencesService;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class LobbyMusicManager {

    public static final Tag<Byte> MUSIC_TAG = Tag.Byte("music_toggle");

    private static final float MUSIC_VOLUME = 0.55f;
    private static final float MUSIC_PITCH = 1.0f;
    private static final TaskSchedule AMBIENT_SUPPRESS_INTERVAL = TaskSchedule.seconds(5);

    private PlayerPreferencesService preferencesService;

    private final List<Track> allTracks = new ArrayList<>();
    private final Map<String, Track> trackByKey = new HashMap<>();
    private final Map<UUID, PlayerMusicState> states = new ConcurrentHashMap<>();
    private Task globalAmbientTask;

    public LobbyMusicManager() {
        registerAllTracks();
    }

    /** Builds the music toggle item live from {@code config/toggle-items.yml}. */
    public static ItemStack getMusicToggle(boolean enabled) {
        ToggleItemDefinition def = JoinItemsConfig.get().toggleItems.music();
        return ItemStack.builder(def.material(enabled))
                .set(DataComponents.CUSTOM_NAME, def.displayName(enabled))
                .set(DataComponents.LORE, def.lore(enabled))
                .hideExtraTooltip()
                .set(MUSIC_TAG, (byte) 1)
                .build();
    }

    public void startGlobalAmbientSuppressor() {
        if (globalAmbientTask != null) globalAmbientTask.cancel();
        globalAmbientTask = MinecraftServer.getSchedulerManager().scheduleTask(
                () -> {
                    if (states.isEmpty()) return;
                    var cm = MinecraftServer.getConnectionManager();
                    for (Map.Entry<UUID, PlayerMusicState> entry : states.entrySet()) {
                        Player player = cm.getOnlinePlayerByUuid(entry.getKey());
                        if (player == null) continue;
                        player.stopSound(SoundStop.source(Sound.Source.MUSIC));
                        if (entry.getValue().muted) {
                            player.stopSound(SoundStop.source(Sound.Source.RECORD));
                        }
                    }
                },
                TaskSchedule.immediate(),
                AMBIENT_SUPPRESS_INTERVAL,
                ExecutionType.TICK_START
        );
    }

    private PlayerMusicState getOrCreateState(UUID uuid) {
        return states.computeIfAbsent(uuid, k -> new PlayerMusicState());
    }

    public void handleJoin(PlayerSpawnEvent event) {
        var player = event.getPlayer();
        getOrCreateState(player.getUuid());

        if (!isEnabled(player)) {
            stopPlayback(player);
            return;
        }

        start(player);
    }

    public void handleDisconnect(PlayerDisconnectEvent event) {
        UUID uuid = event.getPlayer().getUuid();
        PlayerMusicState state = states.remove(uuid);
        if (state != null) {
            state.nextToken(); // invalidate any pending scheduled playback
            if (state.activeTask != null) {
                state.activeTask.cancel();
                state.activeTask = null;
            }
        }
        // Do not send sound packets to a disconnecting player — connection is already closing.
    }

    public boolean isEnabled(Player player) {
        PlayerMusicState state = states.get(player.getUuid());
        return state == null || !state.muted;
    }

    public boolean isDisabled(Player player) {
        PlayerMusicState state = states.get(player.getUuid());
        return state != null && state.muted;
    }

    public void setPreferencesService(PlayerPreferencesService service) {
        this.preferencesService = service;
    }

    public void applyMusicPreference(UUID uuid, boolean musicEnabled) {
        PlayerMusicState state = getOrCreateState(uuid);
        state.muted = !musicEnabled;
    }

    public void setEnabled(Player player, boolean enabled) {
        PlayerMusicState state = getOrCreateState(player.getUuid());
        state.muted = !enabled;
        if (enabled) {
            start(player);
        } else {
            stop(player);
        }
        if (preferencesService != null) {
            preferencesService.saveMusicEnabled(player.getUuid(), enabled);
        }
    }

    public void toggle(Player player) {
        setEnabled(player, isDisabled(player));
    }

    public void playSpecific(Player player, String trackKey) {
        UUID uuid = player.getUuid();
        PlayerMusicState state = getOrCreateState(uuid);
        state.muted = false;
        stopPlayback(player);

        Track track = findTrack(trackKey);
        if (track == null) return;

        int token = state.nextToken();
        state.currentTrack = track;
        state.lastPlayedTrack = track;
        player.playSound(track.toAdventureSound(), Sound.Emitter.self());

        Task task = MinecraftServer.getSchedulerManager().scheduleTask(
                () -> {
                    if (!player.isOnline() || state.muted || state.playbackToken != token) return;
                    playNext(player, token);
                },
                TaskSchedule.duration(Duration.ofSeconds(track.durationSeconds())),
                TaskSchedule.stop()
        );
        state.activeTask = task;

        if (preferencesService != null) {
            preferencesService.saveMusicEnabled(uuid, true);
        }
    }

    private Track findTrack(String key) {
        return trackByKey.get(key);
    }

    public void start(Player player) {
        UUID uuid = player.getUuid();
        PlayerMusicState state = getOrCreateState(uuid);

        stopPlayback(player);

        if (!player.isOnline() || state.muted) {
            return;
        }

        int playbackToken = state.nextToken();
        playNext(player, playbackToken);
    }

    public void stop(Player player) {
        getOrCreateState(player.getUuid());
        stopPlayback(player);
    }

    private void stopPlayback(Player player) {
        UUID uuid = player.getUuid();
        PlayerMusicState state = states.get(uuid);
        if (state != null) {
            state.nextToken();

            if (state.activeTask != null) {
                state.activeTask.cancel();
                state.activeTask = null;
            }

            stopCurrentTrack(player, state);
        }

        player.stopSound(SoundStop.source(Sound.Source.RECORD));
        player.stopSound(SoundStop.source(Sound.Source.MUSIC));
    }

    private void playNext(Player player, int playbackToken) {
        UUID uuid = player.getUuid();
        PlayerMusicState state = states.get(uuid);

        if (state == null || !player.isOnline() || state.muted || state.playbackToken != playbackToken) {
            return;
        }

        stopCurrentTrack(player, state);

        Track nextTrack = pollNextTrack(state);
        state.currentTrack = nextTrack;
        state.lastPlayedTrack = nextTrack;

        player.playSound(nextTrack.toAdventureSound(), Sound.Emitter.self());

        Task task = MinecraftServer.getSchedulerManager().scheduleTask(
                () -> {
                    if (!player.isOnline() || state.muted || state.playbackToken != playbackToken) {
                        return;
                    }

                    playNext(player, playbackToken);
                },
                TaskSchedule.duration(Duration.ofSeconds(nextTrack.durationSeconds())),
                TaskSchedule.stop()
        );

        state.activeTask = task;
    }

    private void stopCurrentTrack(Player player, PlayerMusicState state) {
        Track playing = state.currentTrack;
        state.currentTrack = null;

        if (playing != null) {
            player.stopSound(SoundStop.named(Key.key(playing.key())));
        }
    }

    private Track pollNextTrack(PlayerMusicState state) {
        if (state.queue.isEmpty()) {
            refillQueue(state);
        }

        Track track = state.queue.pollFirst();
        if (track == null) {
            track = allTracks.get(ThreadLocalRandom.current().nextInt(allTracks.size()));
        }

        return track;
    }

    private void refillQueue(PlayerMusicState state) {
        List<Track> shuffled = new ArrayList<>(allTracks);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());

        Track last = state.lastPlayedTrack;
        if (last != null && shuffled.size() > 1 && shuffled.get(0).key().equals(last.key())) {
            Collections.swap(shuffled, 0, 1);
        }

        state.queue.addAll(shuffled);
    }

    private void registerAllTracks() {
        add("minecraft:music.menu", 180);
        add("minecraft:music.creative", 176);
        add("minecraft:music.game", 240);
        add("minecraft:music.end", 600);
        add("minecraft:music.credits", 600);
        add("minecraft:music.dragon", 600);

        add("minecraft:music.nether.basalt_deltas", 300);
        add("minecraft:music.nether.crimson_forest", 300);
        add("minecraft:music.nether.nether_wastes", 300);
        add("minecraft:music.nether.soul_sand_valley", 300);
        add("minecraft:music.nether.warped_forest", 300);

        add("minecraft:music.overworld.badlands", 300);
        add("minecraft:music.overworld.bamboo_jungle", 300);
        add("minecraft:music.overworld.cherry_grove", 300);
        add("minecraft:music.overworld.deep_dark", 300);
        add("minecraft:music.overworld.desert", 300);
        add("minecraft:music.overworld.dripstone_caves", 300);
        add("minecraft:music.overworld.flower_forest", 300);
        add("minecraft:music.overworld.forest", 300);
        add("minecraft:music.overworld.frozen_peaks", 300);
        add("minecraft:music.overworld.grove", 300);
        add("minecraft:music.overworld.jagged_peaks", 300);
        add("minecraft:music.overworld.jungle", 300);
        add("minecraft:music.overworld.lush_caves", 300);
        add("minecraft:music.overworld.meadow", 300);
        add("minecraft:music.overworld.old_growth_taiga", 300);
        add("minecraft:music.overworld.snowy_slopes", 300);
        add("minecraft:music.overworld.sparse_jungle", 300);
        add("minecraft:music.overworld.stony_peaks", 300);
        add("minecraft:music.overworld.swamp", 300);

        add("minecraft:music.under_water", 300);

        add("minecraft:music_disc.11", 71);
        add("minecraft:music_disc.13", 178);
        add("minecraft:music_disc.5", 178);
        add("minecraft:music_disc.blocks", 345);
        add("minecraft:music_disc.cat", 185);
        add("minecraft:music_disc.chirp", 185);
        add("minecraft:music_disc.creator", 176);
        add("minecraft:music_disc.creator_music_box", 73);
        add("minecraft:music_disc.far", 174);
        add("minecraft:music_disc.lava_chicken", 135);
        add("minecraft:music_disc.mall", 197);
        add("minecraft:music_disc.mellohi", 96);
        add("minecraft:music_disc.otherside", 195);
        add("minecraft:music_disc.pigstep", 148);
        add("minecraft:music_disc.precipice", 299);
        add("minecraft:music_disc.relic", 218);
        add("minecraft:music_disc.stal", 150);
        add("minecraft:music_disc.strad", 188);
        add("minecraft:music_disc.tears", 175);
        add("minecraft:music_disc.wait", 238);
        add("minecraft:music_disc.ward", 251);
    }

    private void add(@KeyPattern String key, int durationSeconds) {
        Track track = new Track(key, durationSeconds);
        allTracks.add(track);
        trackByKey.put(key, track);
    }

    private static final class PlayerMusicState {
        boolean muted;
        Task activeTask;
        final Deque<Track> queue = new ArrayDeque<>();
        Track lastPlayedTrack;
        Track currentTrack;
        int playbackToken;

        int nextToken() {
            return ++playbackToken;
        }
    }

    private record Track(@KeyPattern String key, int durationSeconds) {
        Sound toAdventureSound() {
            return Sound.sound(
                    Key.key(key),
                    Sound.Source.RECORD,
                    MUSIC_VOLUME,
                    MUSIC_PITCH
            );
        }
    }
}
