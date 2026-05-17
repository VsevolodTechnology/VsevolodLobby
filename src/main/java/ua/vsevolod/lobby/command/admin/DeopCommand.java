package ua.vsevolod.lobby.command.admin;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentString;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.admin.OpsStore;

public class DeopCommand extends Command {

    public DeopCommand() {
        super("deop");

        setCondition((sender, commandString) ->
                sender instanceof Player p && LobbyConfig.Settings.BYPASS_USERS.contains(p.getUsername()));

        var targetArg = new ArgumentString("target");

        setDefaultExecutor((sender, context) -> {
            if (sender instanceof Player p) {
                p.sendMessage("§cИспользование: /deop <ник>");
            }
        });

        addSyntax((sender, context) -> {
            if (!(sender instanceof Player p) || !LobbyConfig.Settings.BYPASS_USERS.contains(p.getUsername())) {
                return;
            }
            String target = context.get(targetArg);
            if (target == null || target.isBlank()) {
                p.sendMessage("§cНик не указан.");
                return;
            }
            if (!LobbyConfig.Settings.BYPASS_USERS.contains(target)) {
                p.sendMessage("§e" + target + " не оператор.");
                return;
            }
            LobbyConfig.Settings.BYPASS_USERS.remove(target);
            OpsStore.save();
            MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                    .filter(pl -> pl.getUsername().equals(target))
                    .findFirst()
                    .ifPresent(pl -> pl.setPermissionLevel(0));
            p.sendMessage("§aОператор снят: §f" + target);
        }, targetArg);

        MinecraftServer.getCommandManager().register(this);
    }
}
