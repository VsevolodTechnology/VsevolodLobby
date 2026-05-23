package ua.vsevolod.lobby.command.admin;

import net.kyori.adventure.text.Component;
import net.minestom.server.command.CommandSender;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.util.Messages;

import java.util.Locale;
import java.util.Map;

public final class GamemodeHelper {

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

    private GamemodeHelper() {}

    public static boolean canUse(Player player) {
        return LobbyConfig.Settings.BYPASS_USERS.contains(player.getUsername());
    }

    public static GameMode resolve(String input) {
        if (input == null) return null;
        return GAME_MODE_MAP.get(input.toLowerCase(Locale.ROOT));
    }

    public static boolean setOwnGamemode(Player player, GameMode gameMode) {
        if (!canUse(player)) {
            player.sendMessage(Messages.error("Недостаточно полномочий."));
            return false;
        }
        if (gameMode == null) {
            player.sendMessage(Messages.error("Не удалось определить игровой режим."));
            return false;
        }
        if (player.getGameMode() == gameMode) {
            player.sendMessage(Messages.compose(
                    Messages.warningText("У тебя уже установлен режим "),
                    Messages.accent(getRussianGameModeName(gameMode)),
                    Messages.warningText(".")));
            return false;
        }
        player.setGameMode(gameMode);
        player.sendMessage(Messages.compose(
                Messages.successText("Твой игровой режим изменён на "),
                Messages.accent(getRussianGameModeName(gameMode)),
                Messages.successText(".")));
        return true;
    }

    public static boolean setTargetGamemode(CommandSender sender, Player target, GameMode gameMode) {
        if (target == null) {
            sender.sendMessage(Messages.error("Игрок не найден."));
            return false;
        }
        if (gameMode == null) {
            sender.sendMessage(Messages.error("Не удалось определить игровой режим."));
            return false;
        }
        if (target.getGameMode() == gameMode) {
            sender.sendMessage(Messages.compose(
                    Messages.warningText("У игрока "),
                    Messages.accent(target.getUsername()),
                    Messages.warningText(" уже установлен режим "),
                    Messages.accent(getRussianGameModeName(gameMode)),
                    Messages.warningText(".")));
            return false;
        }
        target.setGameMode(gameMode);
        target.sendMessage(Messages.compose(
                Messages.successText("Твой игровой режим изменён на "),
                Messages.accent(getRussianGameModeName(gameMode)),
                Messages.successText(".")));
        sender.sendMessage(Messages.compose(
                Messages.successText("Игроку "),
                Messages.accent(target.getUsername()),
                Messages.successText(" установлен режим "),
                Messages.accent(getRussianGameModeName(gameMode)),
                Messages.successText(".")));
        return true;
    }

    public static String getRussianGameModeName(GameMode gameMode) {
        return switch (gameMode) {
            case SURVIVAL  -> "ВЫЖИВАНИЕ";
            case CREATIVE  -> "КРЕАТИВ";
            case ADVENTURE -> "ПРИКЛЮЧЕНИЕ";
            case SPECTATOR -> "НАБЛЮДАТЕЛЬ";
            default        -> "НЕИЗВЕСТНО";
        };
    }

    public static Component usage(String commandName) {
        return Messages.compose(
                Messages.warningText("Использование: "),
                Messages.accent("/" + commandName + " <режим> [игрок]"),
                Component.newline(),
                Messages.text("Доступные режимы: "),
                Messages.accent("survival, creative, adventure, spectator"),
                Messages.text(" или "),
                Messages.accent("0, 1, 2, 3"));
    }

    public static Component unknownMode(String input) {
        return Messages.compose(
                Messages.errorText("Неизвестный игровой режим "),
                Messages.accent(input),
                Messages.errorText("."),
                Component.newline(),
                Messages.text("Доступные режимы: "),
                Messages.accent("survival, creative, adventure, spectator"),
                Messages.text(" или "),
                Messages.accent("0, 1, 2, 3"));
    }

    // ── Fragment helpers (NO prefix) — for callers chaining into a single line ─

    /** Plain body text fragment. Use {@link Messages#info(String)} for a prefixed line. */
    public static Component text(String value)    { return Messages.text(value); }
    /** Highlight color fragment (player names, ids, values). */
    public static Component primary(String value) { return Messages.accent(value); }
    /** Green fragment — successes within composed sentences. */
    public static Component success(String value) { return Messages.successText(value); }
    /** Yellow fragment — warnings within composed sentences. */
    public static Component warning(String value) { return Messages.warningText(value); }
    /** Red fragment — errors within composed sentences. */
    public static Component error(String value)   { return Messages.errorText(value); }
}
