package ua.vsevolod.lobby.command.admin;

import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.number.ArgumentNumber;
import ua.vsevolod.lobby.feature.admin.restart.RestartConfig;
import ua.vsevolod.lobby.feature.admin.restart.RestartCountdownService;
import ua.vsevolod.lobby.util.Messages;

public class RestartCommand extends AdminCommand {

    public RestartCommand() {
        super("restart", true);   // allowsConsole=true

        ArgumentLiteral cancelArg = new ArgumentLiteral("cancel");
        ArgumentNumber<Integer> secondsArg = ArgumentType.Integer("seconds")
                .between(1, RestartConfig.maxCountdown());

        // /restart — default countdown
        setDefaultExecutor((sender, context) -> {
            boolean started = RestartCountdownService.get().start(RestartConfig.defaultCountdown());
            if (!started) {
                sender.sendMessage(Messages.warning("Перезапуск уже идёт. Используй /restart cancel для отмены."));
            }
        });

        // /restart <seconds>
        addSyntax((sender, context) -> {
            int seconds = context.get(secondsArg);
            boolean started = RestartCountdownService.get().start(seconds);
            if (!started) {
                sender.sendMessage(Messages.warning("Перезапуск уже идёт. Используй /restart cancel для отмены."));
            }
        }, secondsArg);

        // /restart cancel
        addSyntax((sender, context) -> {
            boolean cancelled = RestartCountdownService.get().cancel();
            if (!cancelled) {
                sender.sendMessage(Messages.info("Активного отсчёта нет."));
            }
        }, cancelArg);
    }
}
