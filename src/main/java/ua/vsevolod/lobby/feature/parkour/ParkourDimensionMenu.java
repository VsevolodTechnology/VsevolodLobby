package ua.vsevolod.lobby.feature.parkour;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.player.PlayerSwapItemEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.tag.Tag;
import net.minestom.server.world.DimensionType;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.util.Text;

import java.util.List;
import java.util.stream.Stream;

/**
 * Hotbar item + chest menu that lets the player change the parkour instance's
 * dimension (sky/background): Overworld, Nether, or End.
 */
public final class ParkourDimensionMenu {

    public static final int ITEM_SLOT = 4;
    static final Tag<String> DIMENSION_ITEM_TAG = Tag.String("parkour-dimension");

    private static final int OVERWORLD_SLOT = 11;
    private static final int NETHER_SLOT = 13;
    private static final int END_SLOT = 15;

    private final Inventory menu;
    private final ParkourService parkourService;

    public ParkourDimensionMenu(ParkourService parkourService) {
        this.parkourService = parkourService;
        this.menu = createMenu();
    }

    public void register(EventNode<Event> node) {
        // RMB on dimension item: open menu
        node.addListener(PlayerUseItemEvent.class, event -> {
            if (event.getItemStack().getTag(DIMENSION_ITEM_TAG) == null) return;
            event.setCancelled(true);
            if (parkourService.isInParkour(event.getPlayer())) {
                event.getPlayer().openInventory(menu);
            }
        });

        // Dimension menu clicks
        node.addListener(InventoryPreClickEvent.class, event -> {
            // Lock ALL inventory interaction for parkour players
            if (parkourService.isInParkour(event.getPlayer())) {
                event.setCancelled(true);
            }

            // Handle dimension selection
            if (event.getInventory() != null && event.getInventory().equals(menu)) {
                RegistryKey<DimensionType> dim = switch (event.getSlot()) {
                    case OVERWORLD_SLOT -> DimensionType.OVERWORLD;
                    case NETHER_SLOT -> DimensionType.THE_NETHER;
                    case END_SLOT -> DimensionType.THE_END;
                    default -> null;
                };
                if (dim != null) {
                    event.getPlayer().closeInventory();
                    parkourService.changeDimension(event.getPlayer(), dim);
                }
            }
        });

        // Block item drops and swaps for parkour players
        node.addListener(ItemDropEvent.class, event -> {
            if (parkourService.isInParkour(event.getPlayer())) {
                event.setCancelled(true);
            }
        });

        node.addListener(PlayerSwapItemEvent.class, event -> {
            if (parkourService.isInParkour(event.getPlayer())) {
                event.setCancelled(true);
            }
        });
    }

    static ItemStack createItem() {
        Component name = Component.text()
                .append(Text.c("<gradient:#AE3AF3:#985DBC><bold>Измерение</bold></gradient>"))
                .append(Component.text(" [", NamedTextColor.DARK_GRAY))
                .append(Component.text("Выбрать", TextColor.color(0x8EB126)))
                .append(Component.text("]", NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false)
                .build();

        List<Component> lore = Stream.<Component>of(
                Component.space(),
                Component.text(" «Информация»", TextColor.color(0x65D1FC)),
                Component.empty()
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text("Меняет фон/небо", TextColor.color(0xFFF2E0))),
                Component.empty()
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text("паркур-инстанса.", TextColor.color(0xFFF2E0))),
                Component.space(),
                Component.text("➥ Нажмите, чтобы открыть", NamedTextColor.YELLOW)
        ).map(c -> c.decoration(TextDecoration.ITALIC, false)).toList();

        return ItemStack.builder(Material.CLOCK)
                .set(DataComponents.CUSTOM_NAME, name)
                .set(DataComponents.LORE, lore)
                .set(DIMENSION_ITEM_TAG, "selector")
                .build();
    }

    // ── Menu construction ───────────────────────────────────────────────────

    private Inventory createMenu() {
        Inventory inv = new Inventory(InventoryType.CHEST_3_ROW, Text.c("<dark_gray>Измерение"));

        ItemStack decor = ItemStack.builder(Material.BLACK_STAINED_GLASS_PANE)
                .set(DataComponents.CUSTOM_NAME, Text.c(""))
                .hideExtraTooltip()
                .build();
        for (int i = 0; i < 27; i++) {
            if (i != OVERWORLD_SLOT && i != NETHER_SLOT && i != END_SLOT) {
                inv.setItemStack(i, decor);
            }
        }

        inv.setItemStack(OVERWORLD_SLOT, createDimensionItem(
                Material.GRASS_BLOCK,
                "<#8FAE8B><bold>Обычный мир",
                "Дневное небо с облаками."
        ));

        inv.setItemStack(NETHER_SLOT, createDimensionItem(
                Material.NETHERRACK,
                "<#C86E6E><bold>Нижний мир",
                "Темно-красное небо Незера."
        ));

        inv.setItemStack(END_SLOT, createDimensionItem(
                Material.END_STONE,
                "<#B48EDC><bold>Край",
                "Темное звездное небо Энда."
        ));

        return inv;
    }

    private static ItemStack createDimensionItem(Material material, String name, String description) {
        List<Component> lore = Stream.<Component>of(
                Component.space(),
                Component.empty()
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text(description, TextColor.color(0xFFF2E0))),
                Component.space(),
                Component.text("➥ Нажмите, чтобы выбрать", NamedTextColor.YELLOW)
        ).map(c -> c.decoration(TextDecoration.ITALIC, false)).toList();

        return ItemStack.builder(material)
                .set(DataComponents.CUSTOM_NAME, Text.c(name).decoration(TextDecoration.ITALIC, false))
                .set(DataComponents.LORE, lore)
                .hideExtraTooltip()
                .build();
    }
}
