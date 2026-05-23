package ua.vsevolod.lobby.feature.lobby.player.join.items;

/**
 * The three togglable hotbar items — music, sidebar and player-visibility switches.
 * Stored under {@code toggleItems} in {@code config/join-items.yml}.
 */
public record ToggleItems(
        ToggleItemDefinition music,
        ToggleItemDefinition sidebar,
        ToggleItemDefinition players
) {
    public ToggleItems {
        if (music == null) music = defaultMusic();
        if (sidebar == null) sidebar = defaultSidebar();
        if (players == null) players = defaultPlayers();
    }

    public static ToggleItems defaults() {
        return new ToggleItems(defaultMusic(), defaultSidebar(), defaultPlayers());
    }

    private static ToggleItemDefinition defaultMusic() {
        return new ToggleItemDefinition(
                "music_disc_cat",
                "music_disc_blocks",
                "<gradient:#AE3AF3:#985DBC><bold>Музыка</bold></gradient>",
                "<dark_gray>[<#8EB126>Вкл<dark_gray>]",
                "<dark_gray>[<#FA3B3B>Выкл<dark_gray>]",
                java.util.List.of(
                        " ",
                        "<#65D1FC> «Информация»",
                        "<gray> - <#FFF2E0>Фоновая музыка лобби",
                        "<gray> - <#FFF2E0>ПКМ — включить/выключить",
                        "<gray> - <#FFF2E0>Клавиша Q — выбор музыки",
                        " ",
                        "<#C58AF0>➥ Нажмите для действия"
                ),
                "<#8EB126>включена",
                "<#FA3B3B>выключена",
                "<dark_gray>[<gradient:#AE3AF3:#985DBC><bold>Музыка</bold></gradient><dark_gray>] "
                        + "<#FFF2E0>Вы <#81E366>включили <#FFF2E0>проигрывание музыки",
                "<dark_gray>[<gradient:#AE3AF3:#985DBC><bold>Музыка</bold></gradient><dark_gray>] "
                        + "<#FFF2E0>Вы <#E36666>выключили <#FFF2E0>проигрывание музыки"
        );
    }

    private static ToggleItemDefinition defaultSidebar() {
        return new ToggleItemDefinition(
                "magenta_dye",
                "gray_dye",
                "<gradient:#AE3AF3:#985DBC><bold>Скорборд</bold></gradient>",
                "<dark_gray>[<#8EB126>Виден<dark_gray>]",
                "<dark_gray>[<#FA3B3B>Скрыт<dark_gray>]",
                java.util.List.of(
                        " ",
                        "<#65D1FC> «Информация»",
                        "<gray> - <#FFF2E0>Статус: {status}",
                        "<gray> - <#FFF2E0>Включает/выключает",
                        "<gray> - <#FFF2E0>таблицу очков.",
                        " ",
                        "<#C58AF0>➥ Нажмите, чтобы переключиться"
                ),
                "<#81E366>отображается",
                "<#E36666>скрыт",
                "<dark_gray>[<gradient:#AE3AF3:#985DBC><bold>Скорборд</bold></gradient><dark_gray>] "
                        + "<#FFF2E0>Вы <#81E366>включили <#FFF2E0>отображение скорборда",
                "<dark_gray>[<gradient:#AE3AF3:#985DBC><bold>Скорборд</bold></gradient><dark_gray>] "
                        + "<#FFF2E0>Вы <#E36666>выключили <#FFF2E0>отображение скорборда"
        );
    }

    private static ToggleItemDefinition defaultPlayers() {
        return new ToggleItemDefinition(
                "lime_dye",
                "gray_dye",
                "<gradient:#AE3AF3:#985DBC><bold>Игроки</bold></gradient>",
                "<dark_gray>[<#8EB126>Видны<dark_gray>]",
                "<dark_gray>[<#FA3B3B>Скрыты<dark_gray>]",
                java.util.List.of(
                        " ",
                        "<#65D1FC> «Информация»",
                        "<gray> - <#FFF2E0>Статус: {status}",
                        "<gray> - <#FFF2E0>Позволяет убрать лишних",
                        "<gray> - <#FFF2E0>игроков в лобби.",
                        " ",
                        "<#C58AF0>➥ ПКМ — переключить"
                ),
                "<#81E366>отображаются",
                "<#E36666>скрыты <gray>(FPS+)",
                "<dark_gray>[<gradient:#AE3AF3:#985DBC><bold>Игроки</bold></gradient><dark_gray>] "
                        + "<#FFF2E0>Вы <#81E366>включили <#FFF2E0>видимость игроков",
                "<dark_gray>[<gradient:#AE3AF3:#985DBC><bold>Игроки</bold></gradient><dark_gray>] "
                        + "<#FFF2E0>Вы <#E36666>выключили <#FFF2E0>видимость игроков"
        );
    }
}
