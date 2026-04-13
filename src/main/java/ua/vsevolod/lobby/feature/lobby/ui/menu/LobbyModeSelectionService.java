package ua.vsevolod.lobby.feature.lobby.ui.menu;

import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.config.server.ServerInfo;
import ua.vsevolod.lobby.util.Text;

import static ua.vsevolod.lobby.config.LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL;

public final class LobbyModeSelectionService {

    public final static Component SERVER_TEXT = Text.c("&8[&#F1BB58&lС&#F1B958&lе&#F1B658&lр&#F1B458&lв&#F1B158&lе&#F1AF58&lр&8] ");

    public void selectServer(Player player, ServerInfo server) {
        player.closeInventory();

        switch (server.getStatusStatic().state()) {
            case OFFLINE -> player.sendMessage(Component.empty().append(SERVER_TEXT).append(Component.text("Данный сервер не онлайн попробуйте позже", WHITE_COLOR_TEXT_ORIGINAL)));
            case LOADING -> player.sendMessage(Component.empty().append(SERVER_TEXT).append(Component.text("Данный сервер загружается попробуйте попозже", WHITE_COLOR_TEXT_ORIGINAL)));
            case ONLINE -> player.sendMessage(Component.empty().append(SERVER_TEXT).append(Component.text("Идет подключение к серверу", WHITE_COLOR_TEXT_ORIGINAL)));
        }
    }
}
