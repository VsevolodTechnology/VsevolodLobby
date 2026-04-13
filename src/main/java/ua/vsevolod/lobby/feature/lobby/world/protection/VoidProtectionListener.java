package ua.vsevolod.lobby.feature.lobby.world.protection;

import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.instance.block.Block;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.bootstrap.LobbyEventRegistration;

public final class VoidProtectionListener implements LobbyEventRegistration {

    @Override
    public void register(GlobalEventHandler handler) {
        handler.addListener(PlayerMoveEvent.class, event -> {

            /*
             * Handles player movement to prevent falling into the void.
             * <p>
             * If the player's Y position falls below {@link LobbyConfig.Locations#VOID_THRESHOLD_Y},
             * the player will be teleported back to {@link LobbyConfig.Locations#SPAWN_POS_PLAYER}.
             */
            if (event.getNewPosition().y() < LobbyConfig.Locations.VOID_THRESHOLD_Y && event.getInstance() == LobbyConfig.Locations.VOID_THRESHOLD_INSTANCE_WORLD) {
                var player = event.getPlayer();
                var newPos = event.getNewPosition();

                player.teleport(LobbyConfig.Locations.SPAWN_POS_PLAYER);

                if (!LobbyConfig.Settings.VOID_GUARD) return;

                var instance = player.getInstance();
                if (instance == null) return;

                int x = (int) newPos.x();
                int y = (int) newPos.y() + 1;
                int z = (int) newPos.z() - 1;

                if (instance.getBlock(x, y, z).isAir()) {
                    instance.setBlock(x, y, z, Block.GLASS);
                }
            }
        });
    }
}

