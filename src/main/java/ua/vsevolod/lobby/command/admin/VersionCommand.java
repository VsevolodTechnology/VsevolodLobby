package ua.vsevolod.lobby.command.admin;

import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.number.ArgumentNumber;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.feature.admin.VersionGate;
import ua.vsevolod.lobby.util.Messages;

public class VersionCommand extends AdminCommand {

    public VersionCommand() {
        super("version");

        setDefaultExecutor((sender, context) -> {
            if (sender instanceof Player p) {
                showStatus(p);
            }
        });

        ArgumentLiteral statusArg = new ArgumentLiteral("status");
        ArgumentLiteral onArg = new ArgumentLiteral("on");
        ArgumentLiteral offArg = new ArgumentLiteral("off");
        ArgumentLiteral minArg = new ArgumentLiteral("min");
        ArgumentLiteral maxArg = new ArgumentLiteral("max");
        ArgumentNumber<Integer> valueArg = ArgumentType.Integer("value").between(0, 999_999);

        addSyntax((sender, context) -> {
            if (sender instanceof Player p) showStatus(p);
        }, statusArg);

        addSyntax((sender, context) -> {
            if (!(sender instanceof Player p)) return;
            VersionGate.setEnabled(true);
            p.sendMessage(Messages.success("Фильтр версий включён."));
            showStatus(p);
        }, onArg);

        addSyntax((sender, context) -> {
            if (!(sender instanceof Player p)) return;
            VersionGate.setEnabled(false);
            p.sendMessage(Messages.warning("Фильтр версий выключен. Пускаются все."));
        }, offArg);

        addSyntax((sender, context) -> {
            if (!(sender instanceof Player p)) return;
            int v = context.get(valueArg);
            VersionGate.setMin(v);
            p.sendMessage(Messages.compose(
                    Messages.successText("Минимальный протокол: "),
                    Messages.accent(String.valueOf(v))));
            showStatus(p);
        }, minArg, valueArg);

        addSyntax((sender, context) -> {
            if (!(sender instanceof Player p)) return;
            int v = context.get(valueArg);
            VersionGate.setMax(v);
            p.sendMessage(Messages.compose(
                    Messages.successText("Максимальный протокол: "),
                    Messages.accent(String.valueOf(v))));
            showStatus(p);
        }, maxArg, valueArg);
    }

    private static void showStatus(Player p) {
        p.sendMessage(Messages.info("Фильтр версий"));
        p.sendMessage(Messages.compose(
                Messages.muted("Статус: "),
                VersionGate.isEnabled() ? Messages.successText("включён") : Messages.errorText("выключен")));
        p.sendMessage(Messages.compose(
                Messages.muted("Min: "), Messages.accent(String.valueOf(VersionGate.getMin())),
                Messages.muted("  Max: "), Messages.accent(String.valueOf(VersionGate.getMax()))));
        p.sendMessage(Messages.compose(
                Messages.muted("Команды: "),
                Messages.accent("/version on"), Messages.muted(" | "),
                Messages.accent("/version off"), Messages.muted(" | "),
                Messages.accent("/version min <N>"), Messages.muted(" | "),
                Messages.accent("/version max <N>")));
        p.sendMessage(Messages.compose(Messages.muted("Протоколы (см. minecraft.wiki/w/Protocol_version):")));
        p.sendMessage(Messages.compose(
                Messages.muted("1.8.x="), Messages.accent("47"),
                Messages.muted("  1.16.5="), Messages.accent("754"),
                Messages.muted("  1.18="), Messages.accent("757"),
                Messages.muted("  1.19="), Messages.accent("759-761")));
        p.sendMessage(Messages.compose(
                Messages.muted("1.20="), Messages.accent("763"),
                Messages.muted("  1.20.4="), Messages.accent("765"),
                Messages.muted("  1.20.6="), Messages.accent("766"),
                Messages.muted("  1.21="), Messages.accent("767"),
                Messages.muted("  1.21.5="), Messages.accent("770")));
    }
}
