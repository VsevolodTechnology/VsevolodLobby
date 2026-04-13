package ua.vsevolod.lobby.command.lobby;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.config.LobbyConfig;

public class SpawnCommand extends Command {

    public SpawnCommand() {
        super(LobbyConfig.Commands.Spawn.COMMAND_NAME);

        setDefaultExecutor(((sender, i) -> {
            if (sender instanceof Player player) {
                player.teleport(LobbyConfig.Locations.SPAWN_POS_PLAYER);
            }
        }));

        Command command = new Command(
                LobbyConfig.Commands.Spawn.COMMAND_ALIASES.getFirst(),
                LobbyConfig.Commands.Spawn.COMMAND_ALIASES.subList(1,  LobbyConfig.Commands.Spawn.COMMAND_ALIASES.size()).toArray(String[]::new)
        ) {
            {
                setCondition((sender, commandString) -> commandString != null);

                setDefaultExecutor(((sender, i) -> {
                    if (sender instanceof Player player) {
                        player.teleport(LobbyConfig.Locations.SPAWN_POS_PLAYER);
                    }
                }));
            }
        };

        MinecraftServer.getCommandManager().register(this);
        MinecraftServer.getCommandManager().register(command);
    }
}

