package ua.vsevolod.lobby.feature.lobby.ui.menu;

import net.minestom.server.component.DataComponents;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import ua.vsevolod.lobby.config.server.ServerRegistry;
import ua.vsevolod.lobby.util.Text;

/**
 * Hardcoded fallback mode-selector menu — used when the config-driven {@code mode-selector}
 * menu is missing. The server icon is rendered by the shared {@link ServerItemRenderer} and a
 * click routes through {@link ServerConnector}, so it stays in sync with the config menus.
 */
public final class LobbyModeSelectorMenu {

    private static final int GRIEF_SLOT = 22;

    private static final int[] ORANGE_DECOR_SLOTS = {
            1, 3, 5, 7,
            9, 11, 15, 17,
            18, 19, 25, 26,
            27, 29, 33, 35,
            37, 39, 41, 43
    };

    private static final int[] BLACK_DECOR_SLOTS = {
            0, 2, 4, 6, 8,
            10, 12, 13, 14, 16,
            20, 24,
            28, 30, 31, 32, 34,
            36, 38, 40, 42, 44
    };

    private static final int[] GRAY_DECOR_SLOTS = {
            21, 23
    };

    private final Inventory menu;
    private final LobbyModeSelectionService selectionService;

    public LobbyModeSelectorMenu(GlobalEventHandler eventHandler) {
        this.selectionService = new LobbyModeSelectionService();
        this.menu = createMainMenu();

        eventHandler.addListener(InventoryPreClickEvent.class, event -> {
            if (event.getInventory() == null || !event.getInventory().equals(menu)) {
                return;
            }

            event.setCancelled(true);

            if (event.getSlot() == GRIEF_SLOT) {
                ServerRegistry.findById("adventur")
                        .ifPresent(server -> selectionService.selectServer(event.getPlayer(), server));
            }
        });
    }

    public Inventory getMenu() {
        return menu;
    }

    private Inventory createMainMenu() {
        Inventory inventory = new Inventory(InventoryType.CHEST_5_ROW, Text.c("<dark_gray>Выбор режима"));

        fillDecoration(inventory, ORANGE_DECOR_SLOTS, Material.ORANGE_STAINED_GLASS_PANE);
        fillDecoration(inventory, BLACK_DECOR_SLOTS, Material.BLACK_STAINED_GLASS_PANE);
        fillDecoration(inventory, GRAY_DECOR_SLOTS, Material.GRAY_STAINED_GLASS_PANE);

        ServerRegistry.findById("adventur")
                .ifPresent(server -> inventory.setItemStack(GRIEF_SLOT, ServerItemRenderer.render(server)));

        return inventory;
    }

    private void fillDecoration(Inventory inventory, int[] slots, Material material) {
        ItemStack item = ItemStack.builder(material)
                .set(DataComponents.CUSTOM_NAME, Text.c(""))
                .hideExtraTooltip()
                .build();

        for (int slot : slots) {
            inventory.setItemStack(slot, item);
        }
    }
}
