package ua.vsevolod.lobby.command.admin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.command.CommandSender;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.config.LobbyConfig;

import java.util.Locale;
import java.util.Map;

public final class GamemodeHelper {

    private static final TextColor PRIMARY_COLOR = TextColor.color(0x59BDFF);
    private static final TextColor PRIMARY_SOFT_COLOR = TextColor.color(0x7CC9FF);
    private static final TextColor TEXT_COLOR = TextColor.color(0xC9D6E5);

    private static final TextColor SUCCESS_COLOR = TextColor.color(0x81E366);
    private static final TextColor SUCCESS_SOFT_COLOR = TextColor.color(0xA2F089);

    private static final TextColor WARNING_COLOR = TextColor.color(0xE3CA66);
    private static final TextColor ERROR_COLOR = TextColor.color(0xE36666);

    private static final Map<String, GameMode> GAME_MODE_MAP = Map.of(
            "0", GameMode.SURVIVAL,
            "1", GameMode.CREATIVE,
            "2", GameMode.ADVENTURE,
            "3", GameMode.SPECTATOR,
            "survival", GameMode.SURVIVAL,
            "creative", GameMode.CREATIVE,
            "adventure", GameMode.ADVENTURE,
            "spectator", GameMode.SPECTATOR
    );

    private GamemodeHelper() {
    }

    public static boolean canUse(Player player) {
        return LobbyConfig.Settings.BYPASS_USERS.contains(player.getUsername());
    }

    public static GameMode resolve(String input) {
        if (input == null) {
            return null;
        }
        return GAME_MODE_MAP.get(input.toLowerCase(Locale.ROOT));
    }

    public static boolean setOwnGamemode(Player player, GameMode gameMode) {
        if (!canUse(player)) {
            player.sendMessage(error("Недостаточно полномочий."));
            return false;
        }

        if (gameMode == null) {
            player.sendMessage(error("Не удалось определить игровой режим."));
            return false;
        }

        if (player.getGameMode() == gameMode) {
            player.sendMessage(
                    warning("У тебя уже установлен режим ")
                            .append(primary(getRussianGameModeName(gameMode)))
                            .append(warning("."))
            );
            return false;
        }

        player.setGameMode(gameMode);
        player.sendMessage(
                success("Твой игровой режим изменён на ")
                        .append(primary(getRussianGameModeName(gameMode)))
                        .append(success("."))
        );
        return true;
    }

    public static boolean setTargetGamemode(CommandSender sender, Player target, GameMode gameMode) {
        if (target == null) {
            sender.sendMessage(error("Игрок не найден."));
            return false;
        }

        if (gameMode == null) {
            sender.sendMessage(error("Не удалось определить игровой режим."));
            return false;
        }

        if (target.getGameMode() == gameMode) {
            sender.sendMessage(
                    warning("У игрока ")
                            .append(primary(target.getUsername()))
                            .append(warning(" уже установлен режим "))
                            .append(primary(getRussianGameModeName(gameMode)))
                            .append(warning("."))
            );
            return false;
        }

        target.setGameMode(gameMode);

        target.sendMessage(
                successSoft("Твой игровой режим изменён на ")
                        .append(primary(getRussianGameModeName(gameMode)))
                        .append(successSoft("."))
        );

        sender.sendMessage(
                success("Игроку ")
                        .append(primary(target.getUsername()))
                        .append(success(" установлен режим "))
                        .append(primary(getRussianGameModeName(gameMode)))
                        .append(success("."))
        );
        return true;
    }

    public static String getRussianGameModeName(GameMode gameMode) {
        return switch (gameMode) {
            case SURVIVAL -> "ВЫЖИВАНИЕ";
            case CREATIVE -> "КРЕАТИВ";
            case ADVENTURE -> "ПРИКЛЮЧЕНИЕ";
            case SPECTATOR -> "НАБЛЮДАТЕЛЬ";
            default -> "НЕИЗВЕСТНО";
        };
    }

    public static Component usage(String commandName) {
        return warning("Использование: ")
                .append(primary("/" + commandName + " <режим> [игрок]"))
                .append(Component.newline())
                .append(text("Доступные режимы: "))
                .append(primarySoft("survival, creative, adventure, spectator"))
                .append(text(" или "))
                .append(primary("0, 1, 2, 3"));
    }

    public static Component unknownMode(String input) {
        return error("Неизвестный игровой режим ")
                .append(primary(input))
                .append(error("."))
                .append(Component.newline())
                .append(text("Доступные режимы: "))
                .append(primarySoft("survival, creative, adventure, spectator"))
                .append(text(" или "))
                .append(primary("0, 1, 2, 3"));
    }

    public static Component text(String text) {
        return Component.text(text, TEXT_COLOR);
    }

    public static Component primary(String text) {
        return Component.text(text, PRIMARY_COLOR);
    }

    public static Component primarySoft(String text) {
        return Component.text(text, PRIMARY_SOFT_COLOR);
    }

    public static Component success(String text) {
        return Component.text(text, SUCCESS_COLOR);
    }

    public static Component successSoft(String text) {
        return Component.text(text, SUCCESS_SOFT_COLOR);
    }

    public static Component warning(String text) {
        return Component.text(text, WARNING_COLOR);
    }

    public static Component error(String text) {
        return Component.text(text, ERROR_COLOR);
    }
}
