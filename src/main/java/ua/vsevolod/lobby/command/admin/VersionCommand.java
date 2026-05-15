package ua.vsevolod.lobby.command.admin;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.number.ArgumentNumber;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.admin.VersionGate;

public class VersionCommand extends Command {

    public VersionCommand() {
        super("version");

        setCondition((sender, commandString) ->
                sender instanceof Player p && LobbyConfig.Settings.OPS_OWNER.equals(p.getUsername()));

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
            if (!ownerOnly(sender)) return;
            VersionGate.setEnabled(true);
            ((Player) sender).sendMessage("§aФильтр версий включён.");
            showStatus((Player) sender);
        }, onArg);

        addSyntax((sender, context) -> {
            if (!ownerOnly(sender)) return;
            VersionGate.setEnabled(false);
            ((Player) sender).sendMessage("§eФильтр версий выключен. Пускаются все.");
        }, offArg);

        addSyntax((sender, context) -> {
            if (!ownerOnly(sender)) return;
            int v = context.get(valueArg);
            VersionGate.setMin(v);
            ((Player) sender).sendMessage("§aМинимальный протокол: §f" + v);
            showStatus((Player) sender);
        }, minArg, valueArg);

        addSyntax((sender, context) -> {
            if (!ownerOnly(sender)) return;
            int v = context.get(valueArg);
            VersionGate.setMax(v);
            ((Player) sender).sendMessage("§aМаксимальный протокол: §f" + v);
            showStatus((Player) sender);
        }, maxArg, valueArg);

        MinecraftServer.getCommandManager().register(this);
    }

    private static boolean ownerOnly(net.minestom.server.command.CommandSender sender) {
        return sender instanceof Player p && LobbyConfig.Settings.OPS_OWNER.equals(p.getUsername());
    }

    private static void showStatus(Player p) {
        p.sendMessage("§6=== Фильтр версий ===");
        p.sendMessage("§7Статус: " + (VersionGate.isEnabled() ? "§aвключён" : "§cвыключен"));
        p.sendMessage("§7Min: §f" + VersionGate.getMin() + "  §7Max: §f" + VersionGate.getMax());
        p.sendMessage("§7Команды:");
        p.sendMessage("§e/version on §7| §e/version off §7| §e/version min <N> §7| §e/version max <N>");
        p.sendMessage("§7Протоколы (см. minecraft.wiki/w/Protocol_version):");
        p.sendMessage("§71.8.x=§f47§7  1.16.5=§f754§7  1.18=§f757§7  1.19=§f759-761");
        p.sendMessage("§71.20=§f763§7  1.20.4=§f765§7  1.20.6=§f766§7  1.21=§f767§7  1.21.5=§f770");
    }
}
