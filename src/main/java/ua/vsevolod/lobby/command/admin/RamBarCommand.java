package ua.vsevolod.lobby.command.admin;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.admin.StatsBarService;

public class RamBarCommand extends Command {

    public RamBarCommand() {
        super("rambar");

        setCondition((sender, commandString) ->
                sender instanceof Player p && LobbyConfig.Settings.BYPASS_USERS.contains(p.getUsername()));

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player p) || !LobbyConfig.Settings.BYPASS_USERS.contains(p.getUsername())) {
                return;
            }
            boolean shown = StatsBarService.get().toggleRam(p);
            p.sendMessage(shown ? "§aRAM-бар включён." : "§eRAM-бар выключен.");
        });

        MinecraftServer.getCommandManager().register(this);
    }
}
