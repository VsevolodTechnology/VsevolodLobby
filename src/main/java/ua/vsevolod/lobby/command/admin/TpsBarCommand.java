package ua.vsevolod.lobby.command.admin;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.admin.StatsBarService;

public class TpsBarCommand extends Command {

    public TpsBarCommand() {
        super("tpsbar");

        setCondition((sender, commandString) ->
                sender instanceof Player p && LobbyConfig.Settings.BYPASS_USERS.contains(p.getUsername()));

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player p) || !LobbyConfig.Settings.BYPASS_USERS.contains(p.getUsername())) {
                return;
            }
            boolean shown = StatsBarService.get().toggleTps(p);
            p.sendMessage(shown ? "§aTPS-бар включён." : "§eTPS-бар выключен.");
        });

        MinecraftServer.getCommandManager().register(this);
    }
}
