package ua.vsevolod.lobby.feature.lobby.ui.hologram;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.util.Text;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LobbyWelcomeHologramService {

    private static final Pos WELCOME_POSITION = new Pos(0.5, 80, -2.5);
    private static final double LINE_SPACING = 0.28;

    private final Map<UUID, TextHologram> activeHolograms = new ConcurrentHashMap<>();

    public LobbyWelcomeHologramService() {
        var events = MinecraftServer.getGlobalEventHandler();
    }

    public void showWelcome(Player player, boolean first) {
//        hideWelcome(player, first);

        TextHologram hologram = createWelcomeHologram(player.getUsername());
        hologram.show(player);
        activeHolograms.put(player.getUuid(), hologram);

        if (first) refreshAll();
    }

    public void hideWelcome(Player player, boolean first) {
        TextHologram hologram = activeHolograms.remove(player.getUuid());
        if (hologram != null) {
            hologram.hide(player);
        }

        if (first) refreshAll();
    }

    public void refreshWelcome(Player player) {
        TextHologram hologram = activeHolograms.get(player.getUuid());
        if (hologram == null) {
            return;
        }

        hologram.entries.getFirst().text = buildWelcomeComponent(player.getUsername());
    }

    public void refreshAll() {
        int online = getGlobalOnline();

        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            TextHologram hologram = activeHolograms.get(player.getUuid());
            if (hologram == null) {
                continue;
            }

            hologram.updateLineTextAll(Collections.singleton(player), 0, buildWelcomeComponent(player.getUsername()));
        }
    }

    private int getGlobalOnline() {
        return MinecraftServer.getConnectionManager().getOnlinePlayerCount();
    }

    private TextHologram createWelcomeHologram(String username) {
        return new TextHologramBuilder(WELCOME_POSITION)
                .spacing(LINE_SPACING)
                .line(
                        buildWelcomeComponent(username),
                        TextHologramStyle.defaults()
                                .backgroundColor(0x1C1C1E)
                                .useDefaultBackground(true)
                                .billboard(AbstractDisplayMeta.BillboardConstraints.FIXED)
                                .alignment(TextDisplayMeta.Alignment.CENTER)
                                .scale(new Vec(1.2, 1.2, 1.2))
                                .shadow(true)
                                .seeThrough(true)
                )
                .build();
    }

    private Component buildWelcomeComponent(String playerName) {
        String white = LobbyConfig.Project.WHITE_COLOR_ORIGINAL;

        // Основной оранжевый цвет OVERDYN
        String brand = "&#FF9700";
        // Яркие акцентные цвета для выделения слов внутри текста (в стиле радуги CrazyTime)
        String accent1 = "&#FFBF00"; // Яркий желто-оранжевый
        String accent2 = "&#FF4500"; // Глубокий оранжевый
        String secondary = "&#FFD7A8"; // Светло-персиковый (для ссылок)

        return Text.c(brand + "&lOVERDYN&r") // Огромный, жирный оранжевый заголовок
                .append(Component.newline())
                .append(Component.newline())

                .append(Text.c(white + "Рады видеть тебя, "))
                .append(Text.c(secondary + playerName)) // Акцент на имени игрока
                .append(Text.c(white + ", на нашем сервере"))
                .append(Component.newline())
                .append(Component.newline())

                // Блок навигации с цветовыми акцентами и стрелкой (как в примере)
                .append(Text.c(white + "Пробеги "))
                .append(Text.c(accent1 + "вперёд ↑ ")) // Зеленый заменен на яркий оранжевый
                .append(Text.c(white + "к центру и нажми на "))
                .append(Text.c(accent2 + "NPC")) // Розовый заменен на глубокий оранжевый
                .append(Component.newline())
                .append(Component.newline())

                .append(Text.c(white + "или используй предметы, чтобы выбрать режим."))
                .append(Component.newline())
                .append(Component.newline())

                // Статистика (в стиле примера)
                .append(Text.c(white + "Общий онлайн всех режимов "))
                .append(Text.c(white + "составляет "+brand + getGlobalOnline() +" &lчеловек!&r")) // Динамическое значение, оранжевое, жирное
                .append(Component.newline())
                .append(Component.newline())

                // Подвал (чистый стиль со стрелками, как в примере)
                .append(Text.c(white + "Наш VK → "))
                .append(Text.c(secondary + "vk.com/overdyn")) // Персиковый для ссылок
                .append(Component.newline())

                .append(Text.c(white + "Сайт → "))
                .append(Text.c(secondary + "overdyn.net"));
    }
}
