package ua.vsevolod.lobby.feature.lobby.ui.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;
import ua.vsevolod.lobby.util.Text;

import java.util.List;
import java.util.stream.Stream;

public final class LobbyModeMenuItem {

    public static final int HOTBAR_SLOT = 4;
    public static final Tag<Byte> MENU_ITEM_TAG = Tag.Byte("lobby-menu-opener");

    private static final Component SPACE_PREFIX = Component.text(" - ", NamedTextColor.GRAY);
    private static final ItemStack CACHED_ITEM = buildItem();

    private LobbyModeMenuItem() {
    }

    public static ItemStack create() {
        return CACHED_ITEM;
    }

    private static ItemStack buildItem() {
        List<Component> lore = Stream.<Component>of(
                        Component.empty(),
                        Component.text(" «Информация»", TextColor.color(0x65D1FC)),

                        Component.empty()
                                .append(SPACE_PREFIX)
                                .append(Component.text("Открывает меню выбора", TextColor.color(0xFFF2E0))),

                        Component.empty()
                                .append(SPACE_PREFIX)
                                .append(Component.text("доступных игровых режимов", TextColor.color(0xFFF2E0))),

                        Component.empty(),

                        Component.empty()
                                .append(SPACE_PREFIX)
                                .append(Component.text("Статусы: ", TextColor.color(0xFFF2E0)))
                                .append(Component.text("онлайн, загрузка, оффлайн", TextColor.color(0xAE3AF3))),

                        Component.empty()
                                .append(SPACE_PREFIX)
                                .append(Component.text("Наведи и выбери нужный", TextColor.color(0xFFF2E0))),

                        Component.empty()
                                .append(SPACE_PREFIX)
                                .append(Component.text("сервер для подключения.", TextColor.color(0xFFF2E0))),

                        Component.empty(),
                        Component.text("➥ Нажмите, чтобы открыть", NamedTextColor.YELLOW)
                )
                .map(component -> component.decoration(TextDecoration.ITALIC, false))
                .toList();

        return ItemStack.builder(Material.COMPASS)
                .set(DataComponents.CUSTOM_NAME, Text.c("<gradient:#AE3AF3:#985DBC><bold>Выбор</bold></gradient> <gradient:#985DBC:#985DBC><bold>режима</bold></gradient>").decoration(TextDecoration.ITALIC, false))
                .set(DataComponents.LORE, lore)
                .set(MENU_ITEM_TAG, (byte) 1)
                .hideExtraTooltip()
                .build();
    }
}
