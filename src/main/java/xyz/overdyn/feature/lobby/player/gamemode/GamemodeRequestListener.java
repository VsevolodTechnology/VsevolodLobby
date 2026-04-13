package xyz.overdyn.feature.lobby.player.gamemode;

import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerGameModeRequestEvent;
import xyz.overdyn.command.admin.GamemodeHelper;
import xyz.overdyn.feature.lobby.bootstrap.LobbyEventRegistration;

public final class GamemodeRequestListener implements LobbyEventRegistration {

    @Override
    public void register(GlobalEventHandler handler) {
        handler.addListener(PlayerGameModeRequestEvent.class, event -> {
            Player player = event.getPlayer();

            if (!GamemodeHelper.canUse(player)) {
                player.sendMessage(GamemodeHelper.error("Недостаточно полномочий для смены игрового режима."));
                return;
            }

            if (player.getGameMode() == event.getRequestedGameMode()) {
                player.sendMessage(
                        GamemodeHelper.warning("У тебя уже установлен режим ")
                                .append(GamemodeHelper.primary(
                                        GamemodeHelper.getRussianGameModeName(event.getRequestedGameMode())
                                ))
                                .append(GamemodeHelper.warning("."))
                );
                return;
            }

            player.setGameMode(event.getRequestedGameMode());

            player.sendMessage(
                    GamemodeHelper.success("Твой игровой режим изменён на ")
                            .append(GamemodeHelper.primary(
                                    GamemodeHelper.getRussianGameModeName(event.getRequestedGameMode())
                            ))
                            .append(GamemodeHelper.success("."))
            );
        });
    }
}

