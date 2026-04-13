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
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;
import net.minestom.server.timer.ExecutionType;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.util.Text;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

public final class LobbyMusicManager {

    public static final Tag<Byte> MUSIC_TAG = Tag.Byte("music_toggle");

    private static final float MUSIC_VOLUME = 0.55f;
    private static final float MUSIC_PITCH = 1.0f;
    private static final TaskSchedule AMBIENT_SUPPRESS_INTERVAL = TaskSchedule.seconds(5);

    public final static Component MUSIC_TEXT = Text.c("&#F1BB58&lМ&#F1B958&lу&#F1B658&lз&#F1B458&lы&#F1B158&lк&#F1AF58&lа");
    private final static Component MUSIC_TEXT_ON = MUSIC_TEXT.append(Component.space())
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("Вкл", TextColor.color(0x8EB126)))
            .append(Component.text("]", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false);
    private final static Component MUSIC_TEXT_OFF = MUSIC_TEXT.append(Component.space())
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("Выкл", TextColor.color(0xFA3B3B)))
            .append(Component.text("]", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false);

    private final List<Track> allTracks = new ArrayList<>();

    private final Set<UUID> mutedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Task> activeTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Task> ambientSuppressors = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Track>> playerQueues = new ConcurrentHashMap<>();
    private final Map<UUID, Track> lastPlayedTrack = new ConcurrentHashMap<>();
    private final Map<UUID, Track> currentTrack = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playbackTokens = new ConcurrentHashMap<>();

    public LobbyMusicManager() {
        registerAllTracks();
    }

    public static ItemStack getMusicToggle(boolean enabled) {
        Material material = enabled ? Material.MUSIC_DISC_CAT : Material.MUSIC_DISC_BLOCKS;
        var space = Component.text(" - ", NamedTextColor.GRAY);

        return ItemStack.builder(material)
                .customName(enabled ?
                        MUSIC_TEXT_ON :
                        MUSIC_TEXT_OFF)
                .lore(
                        Component.empty(),
                        Component.text("«Информация»", TextColor.color(0x65D1FC)).decoration(TextDecoration.ITALIC, false),
                        Component.text().append(space).append(Component.text("Фоновая музыка лобби", TextColor.color(LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL))).decoration(TextDecoration.ITALIC, false).build(),
                        Component.text().append(space).append(Component.text("Можно отключить полностью", TextColor.color(LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL))).decoration(TextDecoration.ITALIC, false).build(),
                        Component.empty(),
                        Component.text("➥ Нажмите, чтобы переключиться", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
                )
                // .hideExtraTooltip() теперь делает всю работу за тебя!
                .hideExtraTooltip()
                .set(MUSIC_TAG, (byte) 1)
                .build();
    }

    private void ensureAmbientSuppressor(Player player) {
        UUID uuid = player.getUuid();

        if (ambientSuppressors.containsKey(uuid)) {
            return;
        }

        AtomicReference<Task> taskRef = new AtomicReference<>();
        Task task = MinecraftServer.getSchedulerManager().scheduleTask(
                () -> {
                    if (!player.isOnline()) {
                        Task currentTask = taskRef.get();
                        if (currentTask != null) {
                            currentTask.cancel();
                            ambientSuppressors.remove(uuid, currentTask);
                        }
                        return;
                    }

                    // The lobby owns the audible experience, so vanilla ambient music stays muted.
                    player.stopSound(SoundStop.source(Sound.Source.MUSIC));

                    if (mutedPlayers.contains(uuid)) {
                        player.stopSound(SoundStop.source(Sound.Source.RECORD));
                    }
                },
                TaskSchedule.immediate(),
                AMBIENT_SUPPRESS_INTERVAL,
                ExecutionType.TICK_START
        );

        taskRef.set(task);
        ambientSuppressors.put(uuid, task);
    }

    private void cancelAmbientSuppressor(UUID playerUuid) {
        Task suppressTask = ambientSuppressors.remove(playerUuid);
        if (suppressTask != null) {
            suppressTask.cancel();
        }
    }

    public void handleJoin(PlayerSpawnEvent event) {
        var player = event.getPlayer();
        ensureAmbientSuppressor(player);

        if (!isEnabled(player)) {
            stopPlayback(player);
            return;
        }

        start(player);
    }

    public void handleDisconnect(PlayerDisconnectEvent event) {
        var player = event.getPlayer();
        UUID uuid = player.getUuid();
        stopPlayback(player);
        cancelAmbientSuppressor(uuid);

        mutedPlayers.remove(uuid);
        playerQueues.remove(uuid);
        lastPlayedTrack.remove(uuid);
        currentTrack.remove(uuid);
        playbackTokens.remove(uuid);
    }

    public boolean isEnabled(Player player) {
        return !mutedPlayers.contains(player.getUuid());
    }

    public boolean isDisabled(Player player) {
        return mutedPlayers.contains(player.getUuid());
    }

    public void setEnabled(Player player, boolean enabled) {
        if (enabled) {
            mutedPlayers.remove(player.getUuid());
            start(player);
        } else {
            mutedPlayers.add(player.getUuid());
            stop(player);
        }
    }

    public void toggle(Player player) {
        setEnabled(player, isDisabled(player));
    }

    public void start(Player player) {
        UUID uuid = player.getUuid();

        ensureAmbientSuppressor(player);
        stopPlayback(player);

        if (!player.isOnline() || mutedPlayers.contains(uuid)) {
            return;
        }

        int playbackToken = nextPlaybackToken(uuid);
        playNext(player, playbackToken);
    }

    public void stop(Player player) {
        ensureAmbientSuppressor(player);
        stopPlayback(player);
    }

    private void stopPlayback(Player player) {
        UUID uuid = player.getUuid();
        nextPlaybackToken(uuid);

        Task task = activeTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }

        stopCurrentTrack(player);
        player.stopSound(SoundStop.source(Sound.Source.RECORD));
        player.stopSound(SoundStop.source(Sound.Source.MUSIC));
    }

    private void playNext(Player player, int playbackToken) {
        UUID uuid = player.getUuid();

        if (!player.isOnline() || mutedPlayers.contains(uuid) || !isCurrentPlayback(uuid, playbackToken)) {
            return;
        }

        stopCurrentTrack(player);

        Track nextTrack = pollNextTrack(uuid);
        currentTrack.put(uuid, nextTrack);
        lastPlayedTrack.put(uuid, nextTrack);

        player.playSound(nextTrack.toAdventureSound(), Sound.Emitter.self());

        Task task = MinecraftServer.getSchedulerManager().scheduleTask(
                () -> {
                    if (!player.isOnline() || mutedPlayers.contains(uuid) || !isCurrentPlayback(uuid, playbackToken)) {
                        return;
                    }

                    playNext(player, playbackToken);
                },
                TaskSchedule.duration(Duration.ofSeconds(nextTrack.durationSeconds())),
                TaskSchedule.stop()
        );

        activeTasks.put(uuid, task);
    }

    private void stopCurrentTrack(Player player) {
        UUID uuid = player.getUuid();
        Track playing = currentTrack.remove(uuid);

        if (playing != null) {
            player.stopSound(SoundStop.named(Key.key(playing.key())));
        }
    }

    private Track pollNextTrack(UUID playerUuid) {
        Deque<Track> queue = playerQueues.computeIfAbsent(playerUuid, ignored -> new ArrayDeque<>());

        if (queue.isEmpty()) {
            refillQueue(playerUuid, queue);
        }

        Track track = queue.pollFirst();
        if (track == null) {
            track = allTracks.get(ThreadLocalRandom.current().nextInt(allTracks.size()));
        }

        return track;
    }

    private void refillQueue(UUID playerUuid, Deque<Track> queue) {
        List<Track> shuffled = new ArrayList<>(allTracks);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());

        Track last = lastPlayedTrack.get(playerUuid);
        if (last != null && shuffled.size() > 1 && shuffled.get(0).key().equals(last.key())) {
            Collections.swap(shuffled, 0, 1);
        }

        queue.addAll(shuffled);
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
        allTracks.add(new Track(key, durationSeconds));
    }

    private int nextPlaybackToken(UUID playerUuid) {
        return playbackTokens.merge(playerUuid, 1, Integer::sum);
    }

    private boolean isCurrentPlayback(UUID playerUuid, int playbackToken) {
        return playbackTokens.getOrDefault(playerUuid, 0) == playbackToken;
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
