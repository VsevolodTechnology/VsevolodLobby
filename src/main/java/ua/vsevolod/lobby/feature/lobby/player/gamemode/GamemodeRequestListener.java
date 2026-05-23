package ua.vsevolod.lobby.feature.lobby.player.gamemode;

import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerGameModeRequestEvent;
import ua.vsevolod.lobby.command.admin.GamemodeHelper;
import ua.vsevolod.lobby.feature.lobby.bootstrap.LobbyEventRegistration;
import ua.vsevolod.lobby.util.Messages;

public final class GamemodeRequestListener implements LobbyEventRegistration {

    @Override
    public void register(GlobalEventHandler handler) {
        handler.addListener(PlayerGameModeRequestEvent.class, event -> {
            Player player = event.getPlayer();

            if (!GamemodeHelper.canUse(player)) {
                player.sendMessage(Messages.error("Недостаточно полномочий для смены игрового режима."));
                return;
            }

            String modeName = GamemodeHelper.getRussianGameModeName(event.getRequestedGameMode());

            if (player.getGameMode() == event.getRequestedGameMode()) {
                player.sendMessage(Messages.compose(
                        Messages.warningText("У тебя уже установлен режим "),
                        Messages.accent(modeName),
                        Messages.warningText(".")));
                return;
            }

            player.setGameMode(event.getRequestedGameMode());

            player.sendMessage(Messages.compose(
                    Messages.successText("Твой игровой режим изменён на "),
                    Messages.accent(modeName),
                    Messages.successText(".")));
        });
    }
}
