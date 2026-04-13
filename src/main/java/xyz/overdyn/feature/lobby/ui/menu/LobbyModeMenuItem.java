package xyz.overdyn.feature.lobby.ui.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;
import xyz.overdyn.util.Text;

import java.util.List;
import java.util.stream.Stream;

public final class LobbyModeMenuItem {

    public static final int HOTBAR_SLOT = 4;
    public static final Tag<Byte> MENU_ITEM_TAG = Tag.Byte("lobby-menu-opener");

    private static final Component SPACE_PREFIX = Component.text(" - ", NamedTextColor.GRAY);

    private LobbyModeMenuItem() {
    }

    public static ItemStack create() {
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
                                .append(Component.text("онлайн, загрузка, оффлайн", TextColor.color(0xF1BB58))),

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
                .set(DataComponents.CUSTOM_NAME, Text.c("&#F1BB58&lВ&#F1BA58&lы&#F1B958&lб&#F1B858&lо&#F1B758&lр &#F1B458&lр&#F1B358&lе&#F1B258&lж&#F1B158&lи&#F1B058&lм&#F1AF58&lа").decoration(TextDecoration.ITALIC, false))
                .set(DataComponents.LORE, lore)
                .set(MENU_ITEM_TAG, (byte) 1)
                .hideExtraTooltip()
                .build();
    }
}
