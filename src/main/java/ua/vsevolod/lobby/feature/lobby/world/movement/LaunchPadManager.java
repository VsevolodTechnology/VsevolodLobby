package ua.vsevolod.lobby.feature.lobby.world.movement;

import net.kyori.adventure.sound.Sound;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerStartFlyingEvent;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;
import ua.vsevolod.lobby.bootstrap.module.InstanceModule;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LaunchPadManager {

    private static final long JUMP_RESTORE_DELAY_MS = 350L;

    private final Set<UUID> activeJump = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> jumpCooldownUntil = new ConcurrentHashMap<>();

    public void onJoin(PlayerSpawnEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setAllowFlying(event.getInstance() == InstanceModule.lobby);
            player.setFlying(false);
        }
    }

    public void onQuit(PlayerDisconnectEvent event) {
        UUID uuid = event.getPlayer().getUuid();
        activeJump.remove(uuid);
        jumpCooldownUntil.remove(uuid);
    }

    public void onStartFlying(PlayerStartFlyingEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (InstanceModule.lobby != event.getInstance()) {
            return;
        }

        UUID uuid = player.getUuid();

        if (!player.isAllowFlying()) {
            player.setFlying(false);
            return;
        }

        if (activeJump.contains(uuid)) {
            player.setAllowFlying(false);
            player.setFlying(false);
            return;
        }

        activeJump.add(uuid);
        jumpCooldownUntil.put(uuid, System.currentTimeMillis() + JUMP_RESTORE_DELAY_MS);

        player.setAllowFlying(false);
        player.setFlying(false);
        player.setVelocity(player.getPosition().direction().mul(14.5D).withY(22.0D));

        player.playSound(
                Sound.sound(
                        SoundEvent.ENTITY_BAT_TAKEOFF,
                        Sound.Source.PLAYER,
                        1.0f,
                        1.5f
                )
        );

        spawnBurst(player);
    }

    public void onMove(PlayerMoveEvent event) {
        // Fast-path: PlayerMoveEvent fires 5-10× per second per player. When nobody is jumping
        // this listener should bail in O(1) without even touching the player object.
        if (activeJump.isEmpty() && jumpCooldownUntil.isEmpty()) return;

        Player player = event.getPlayer();
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return;
        }

        UUID uuid = player.getUuid();

        if (player.isOnGround()) {
            Long cooldownUntil = jumpCooldownUntil.get(uuid);
            if (cooldownUntil != null && System.currentTimeMillis() < cooldownUntil) {
                return;
            }

            activeJump.remove(uuid);
            jumpCooldownUntil.remove(uuid);

            if (!player.isAllowFlying()) {
                player.setAllowFlying(event.getInstance() == InstanceModule.lobby);
            }
            return;
        }

        if (!activeJump.contains(uuid)) {
            return;
        }

        spawnBurst(player);
    }

    private void spawnBurst(Player player) {
        Pos pos = player.getPosition().add(0.0, 0.1, 0.0);

        ParticlePacket flame = new ParticlePacket(
                Particle.FLAME,
                false,
                false,
                pos.x(), pos.y(), pos.z(),
                0.10f, 0.05f, 0.10f,
                0.02f,
                3
        );

        player.sendPacketToViewersAndSelf(flame);
    }
}
