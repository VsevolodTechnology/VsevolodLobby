package ua.vsevolod.lobby.feature.lobby.player.protocol;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import ua.vsevolod.lobby.config.LobbyConfig;

import java.util.concurrent.atomic.AtomicReference;

import static net.minestom.server.timer.ExecutionType.TICK_START;

public final class LobbyProtocolWarningService {

    public void showIfNeeded(Player player) {
        if (!player.hasTag(LobbyConfig.Settings.IDENTIFIER_CLIENT_PROTOCOL)) {
            return;
        }

        int protocol = player.getTag(LobbyConfig.Settings.IDENTIFIER_CLIENT_PROTOCOL);
        int recommendedProtocol = 774;

        if (protocol >= recommendedProtocol) {
            return;
        }

        showWarning(player, 30);
    }

//    public void showIfNeeded(Player player) {
//        if (!(player instanceof LobbyPlayer lobbyPlayer)) {
//            return;
//        }
//
//        Integer protocol = lobbyPlayer.getClientProtocol();
//        if (protocol == null) {
//            return;
//        }
//
//        int recommendedProtocol = 774;
//        if (protocol >= recommendedProtocol) {
//            return;
//        }
//
//        showWarning(player, 30);
//    }
//
    private void showWarning(Player player, int duration) {
        player.playSound(Sound.sound(
                SoundEvent.ENTITY_BREEZE_IDLE_GROUND,
                Sound.Source.RECORD,
                1.0f,
                1.0f
        ));

        BossBar bossBar = BossBar.bossBar(
                LobbyConfig.Messages.buildVersionWarning(duration),
                1f,
                BossBar.Color.RED,
                BossBar.Overlay.PROGRESS
        );

        player.showBossBar(bossBar);

        AtomicReference<Task> taskRef = new AtomicReference<>();

        Task task = MinecraftServer.getSchedulerManager().scheduleTask(new Runnable() {
            int secondsLeft = duration;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    player.hideBossBar(bossBar);
                    Task self = taskRef.get();
                    if (self != null) self.cancel();
                    return;
                }

                bossBar.name(LobbyConfig.Messages.buildVersionWarning(secondsLeft));
                bossBar.progress(Math.max(0f, (float) secondsLeft / duration));

                if ((secondsLeft % 5 == 0 || secondsLeft == 3) && secondsLeft != 0) {
                    player.playSound(Sound.sound(
                            SoundEvent.BLOCK_AMETHYST_BLOCK_RESONATE,
                            Sound.Source.MASTER,
                            1f,
                            0.6f
                    ));
                }

                if (secondsLeft <= 0) {
                    player.hideBossBar(bossBar);
                    Task self = taskRef.get();
                    if (self != null) self.cancel();
                    return;
                }

                secondsLeft--;
            }
        }, TaskSchedule.immediate(), TaskSchedule.seconds(1), TICK_START);

        taskRef.set(task);
    }
}
