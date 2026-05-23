package ua.vsevolod.lobby.command.admin;

import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.number.ArgumentNumber;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.config.ProxyConfig;
import ua.vsevolod.lobby.util.Messages;

/**
 * {@code /maxplayers} — print the current soft cap.
 * {@code /maxplayers <n>} — set a new cap and persist it to {@code config/network/proxy.yml}.
 *
 * <p>The MOTD, login gate and server-list ping all read {@link LobbyConfig.Settings#MAX_PLAYERS}
 * — the setter mirrors the new value there immediately, so changes are visible without a
 * restart. The file write happens through {@link ProxyConfig#setMaxPlayers(int)} so YAML
 * comments are preserved.</p>
 */
public final class MaxPlayersCommand extends AdminCommand {

    public MaxPlayersCommand() {
        super("maxplayers", true, "maxonline", "slots");

        setDefaultExecutor((sender, ctx) ->
                sender.sendMessage(Messages.info("Текущий лимит игроков: §6" + LobbyConfig.Settings.MAX_PLAYERS)));

        ArgumentNumber<Integer> count = ArgumentType.Integer("count").between(1, 100_000);
        addSyntax((sender, ctx) -> {
            int newValue = ctx.get(count);
            int oldValue = LobbyConfig.Settings.MAX_PLAYERS;
            if (newValue == oldValue) {
                sender.sendMessage(Messages.info("Лимит уже равен " + newValue + "."));
                return;
            }
            try {
                ProxyConfig.setMaxPlayers(newValue);
                sender.sendMessage(Messages.info("Лимит игроков: §6" + oldValue + "§r → §6" + newValue));
            } catch (Exception e) {
                sender.sendMessage(Messages.warning("Не удалось сохранить proxy.yml: " + e.getMessage()));
            }
        }, count);
    }
}
