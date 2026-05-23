package ua.vsevolod.lobby.command.admin;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.arguments.ArgumentString;
import net.minestom.server.command.builder.arguments.ArgumentStringArray;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.util.Messages;
import ua.vsevolod.lobby.util.Text;

/**
 * {@code /kick <ник> [причина]} — disconnects an online player with a branded kick screen.
 * Operator-only (see {@link AdminCommand}).
 */
public class KickCommand extends AdminCommand {

    public KickCommand() {
        super("kick");

        var targetArg = new ArgumentString("target");
        var reasonArg = new ArgumentStringArray("reason");

        setDefaultExecutor((sender, context) -> {
            if (sender instanceof Player p) {
                p.sendMessage(Messages.compose(
                        Messages.text("Использование: "),
                        Messages.accent("/kick <ник> [причина]")));
            }
        });

        addSyntax((sender, context) ->
                kick(sender, context.get(targetArg), null), targetArg);

        addSyntax((sender, context) ->
                kick(sender, context.get(targetArg), String.join(" ", context.get(reasonArg))),
                targetArg, reasonArg);
    }

    private void kick(CommandSender sender, String target, String reason) {
        if (!(sender instanceof Player p)) return;

        if (target == null || target.isBlank()) {
            p.sendMessage(Messages.error("Ник не указан."));
            return;
        }

        Player victim = MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                .filter(pl -> pl.getUsername().equalsIgnoreCase(target))
                .findFirst()
                .orElse(null);

        if (victim == null) {
            p.sendMessage(Messages.compose(
                    Messages.accent(target),
                    Messages.errorText(" не найден на сервере.")));
            return;
        }
        if (victim == p) {
            p.sendMessage(Messages.warning("Нельзя кикнуть самого себя."));
            return;
        }

        String shownReason = (reason == null || reason.isBlank())
                ? "Причина не указана"
                : reason;

        victim.kick(Text.raw(
                "<gradient:#AE3AF3:#985DBC><bold>ᴏʀᴊᴜꜱ-ꜱᴛᴜᴅɪᴏ</bold></gradient>\n\n"
                        + "<#FFF2E0>Тебя отключил от сервера администратор\n\n"
                        + "<#9C93B0>Причина<dark_gray>: <#C58AF0>" + shownReason));

        p.sendMessage(Messages.compose(
                Messages.successText("Игрок "),
                Messages.accent(victim.getUsername()),
                Messages.successText(" кикнут "),
                Messages.muted("(" + shownReason + ")")));
    }
}
