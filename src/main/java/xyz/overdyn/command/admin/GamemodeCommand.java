package xyz.overdyn.command.admin;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.Argument;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import xyz.overdyn.config.LobbyConfig;

public class GamemodeCommand extends Command {

    public GamemodeCommand() {
        super(
                LobbyConfig.Commands.GameMode.COMMAND_NAME,
                LobbyConfig.Commands.GameMode.COMMAND_ALIASES.toArray(String[]::new)
        );

        Argument<String> modeArgument = ArgumentType.Word("mode");
        Argument<String> targetArgument = ArgumentType.Word("target");

        setCondition((sender, commandString) ->
                sender instanceof Player player && GamemodeHelper.canUse(player)
        );

        modeArgument.setSuggestionCallback((sender, context, suggestion) -> {
            suggestion.addEntry(new SuggestionEntry("survival"));
            suggestion.addEntry(new SuggestionEntry("creative"));
            suggestion.addEntry(new SuggestionEntry("adventure"));
            suggestion.addEntry(new SuggestionEntry("spectator"));
        });

        setDefaultExecutor((sender, context) ->
                sender.sendMessage(GamemodeHelper.usage(getName()))
        );

        addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(
                        GamemodeHelper.error("Консоль не может изменить режим самой себе. Используй ")
                                .append(GamemodeHelper.primary("/" + getName() + " <режим> <игрок>"))
                                .append(GamemodeHelper.error("."))
                );
                return;
            }

            String input = context.get(modeArgument);
            GameMode gameMode = GamemodeHelper.resolve(input);

            if (gameMode == null) {
                sender.sendMessage(GamemodeHelper.unknownMode(input));
                return;
            }

            GamemodeHelper.setOwnGamemode(player, gameMode);
        }, modeArgument);

        addSyntax((sender, context) -> {
            if (!(sender instanceof Player player) || !GamemodeHelper.canUse(player)) {
                sender.sendMessage(GamemodeHelper.error("Недостаточно полномочий."));
                return;
            }

            String input = context.get(modeArgument);
            GameMode gameMode = GamemodeHelper.resolve(input);

            if (gameMode == null) {
                sender.sendMessage(GamemodeHelper.unknownMode(input));
                return;
            }

            String targetName = context.get(targetArgument);
            Player target = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(targetName);

            if (target == null) {
                sender.sendMessage(
                        GamemodeHelper.error("Игрок ")
                                .append(GamemodeHelper.primary(targetName))
                                .append(GamemodeHelper.error(" не найден."))
                );
                return;
            }

            GamemodeHelper.setTargetGamemode(sender, target, gameMode);
        }, modeArgument, targetArgument);

        MinecraftServer.getCommandManager().register(this);
    }
}
