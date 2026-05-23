package ua.vsevolod.lobby.feature.lobby.world.protection;

import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.bootstrap.LobbyEventRegistration;

public final class VoidProtectionListener implements LobbyEventRegistration {

    @Override
    public void register(GlobalEventHandler handler) {
        handler.addListener(PlayerMoveEvent.class, event -> {
            var newPos = event.getNewPosition();
            if (newPos.y() >= LobbyConfig.Locations.VOID_THRESHOLD_Y) return;
            if (event.getInstance() != LobbyConfig.Locations.VOID_THRESHOLD_INSTANCE_WORLD) return;

            var player = event.getPlayer();
            Instance instance = player.getInstance();

            // Plug the hole BEFORE the teleport so the block is committed against the player's
            // current position; doing it after means the setBlock fires for a player that has
            // already been moved to spawn, and the visual block "lags behind" the player.
            if (LobbyConfig.Settings.VOID_GUARD && instance != null) {
                int x = (int) Math.floor(newPos.x());
                int y = (int) Math.floor(newPos.y()) + 1;
                int z = (int) Math.floor(newPos.z());
                if (instance.getBlock(x, y, z).isAir()) {
                    instance.setBlock(x, y, z, Block.GLASS);
                }
            }

            player.teleport(LobbyConfig.Locations.SPAWN_POS_PLAYER);
        });
    }
}

