package ua.vsevolod.lobby.command.admin;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.ArgumentString;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.admin.OpsStore;
import ua.vsevolod.lobby.util.Messages;

public class OpCommand extends AdminCommand {

    public OpCommand() {
        super("op");

        var targetArg = new ArgumentString("target");

        setDefaultExecutor((sender, context) -> {
            if (sender instanceof Player p) {
                p.sendMessage(Messages.compose(
                        Messages.text("Использование: "),
                        Messages.accent("/op <ник>")));
            }
        });

        addSyntax((sender, context) -> {
            if (!(sender instanceof Player p)) return;
            String target = context.get(targetArg);
            if (target == null || target.isBlank()) {
                p.sendMessage(Messages.error("Ник не указан."));
                return;
            }
            if (LobbyConfig.Settings.BYPASS_USERS.contains(target)) {
                p.sendMessage(Messages.compose(
                        Messages.accent(target),
                        Messages.warningText(" уже оператор.")));
                return;
            }
            LobbyConfig.Settings.BYPASS_USERS.add(target);
            OpsStore.save();
            MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                    .filter(pl -> pl.getUsername().equals(target))
                    .findFirst()
                    .ifPresent(pl -> pl.setPermissionLevel(4));
            p.sendMessage(Messages.compose(
                    Messages.successText("Оператор выдан: "),
                    Messages.accent(target)));
        }, targetArg);
    }
}
